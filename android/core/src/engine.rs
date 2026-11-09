//! Local playback: one librespot session, player and Spirc, which makes this
//! phone a Spotify Connect device other clients can see and control.

use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, anyhow};
use librespot_connect::{
    ConnectConfig, LoadContextOptions, LoadRequest, LoadRequestOptions, Options, PlayingTrack,
    Spirc,
};
use librespot_core::{
    SpotifyUri,
    authentication::Credentials,
    cache::Cache,
    config::{DeviceType, SessionConfig},
    session::Session,
};
use librespot_protocol::authentication::AuthenticationType;
use librespot_metadata::audio::{AudioItem, UniqueFields};
use librespot_playback::{
    audio_backend,
    config::{AudioFormat, Bitrate, PlayerConfig, VolumeCtrl},
    mixer::{self, Mixer, MixerConfig},
    player::{Player, PlayerEvent},
};
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Default, Serialize)]
pub struct Track {
    uri: String,
    title: String,
    artists: Vec<String>,
    album: String,
    art_url: Option<String>,
    duration_ms: u32,
}

/// What the interface and the media session show. `position_at` is the
/// Unix time in milliseconds when `position_ms` was true; while playing,
/// the position now is `position_ms + (now - position_at)`.
#[derive(Clone, Debug, Default, Serialize)]
pub struct State {
    connected: bool,
    playing: bool,
    loading: bool,
    position_ms: u32,
    position_at: u64,
    track: Option<Track>,
    volume: u16,
    shuffle: bool,
    repeat: &'static str,
    /// The Connect client that last told this device what to play.
    active_client: String,
    error: Option<String>,
}

#[derive(Deserialize)]
#[serde(tag = "cmd", rename_all = "snake_case")]
pub enum Command {
    /// Play a context (playlist, album, artist) or a list of tracks,
    /// starting at `offset` (a track URI) or `index`.
    Load {
        context: Option<String>,
        #[serde(default)]
        uris: Vec<String>,
        offset: Option<String>,
        index: Option<u32>,
        #[serde(default)]
        shuffle: bool,
    },
    Toggle,
    Play,
    Pause,
    Next,
    Prev,
    Seek {
        ms: u32,
    },
    Volume {
        value: u16,
    },
    Shuffle {
        on: bool,
    },
    Repeat {
        mode: String,
    },
    Queue {
        uri: String,
    },
    /// Take over whatever is playing on another device.
    Transfer,
}

pub struct Engine {
    spirc: Spirc,
    session: Session,
    state: Arc<Mutex<State>>,
    alive: Arc<AtomicBool>,
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn librespot_dir(data_dir: &Path) -> std::path::PathBuf {
    data_dir.join("librespot")
}

fn device_id(data_dir: &Path) -> String {
    let path = data_dir.join("device-id");
    if let Ok(id) = std::fs::read_to_string(&path)
        && id.trim().len() == 40
    {
        return id.trim().to_string();
    }
    let mut bytes = [0u8; 20];
    rand::RngCore::fill_bytes(&mut rand::rng(), &mut bytes);
    let id: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
    let _ = crate::core::write_atomic(&path, id.as_bytes());
    id
}

impl Engine {
    pub fn has_credentials(data_dir: &Path) -> bool {
        librespot_dir(data_dir).join("credentials.json").exists()
    }

    pub fn forget_credentials(data_dir: &Path) {
        let _ = std::fs::remove_file(librespot_dir(data_dir).join("credentials.json"));
    }

    pub async fn connect(
        data_dir: &Path,
        cache_dir: &Path,
        device_name: &str,
        // The playback grant's token, with the account it belongs to.
        access_token: Option<(String, String)>,
    ) -> Result<Self> {
        // No audio cache: every song streams, so storage never grows. Only
        // the credentials and the volume are kept, a few hundred bytes.
        let cache = Cache::new(
            Some(librespot_dir(data_dir)),
            Some(librespot_dir(data_dir)),
            None,
            None,
        )
        .context("unable to open the playback cache")?;
        let credentials = match access_token {
            // Spotify refuses a token login without the account name, as
            // the desktop's `playback_credentials` does it.
            Some((username, token)) => Credentials {
                username: Some(username),
                auth_type: AuthenticationType::AUTHENTICATION_SPOTIFY_TOKEN,
                auth_data: token.into_bytes(),
            },
            None => cache
                .credentials()
                .ok_or_else(|| anyhow!("Set up playback on this phone first."))?,
        };

        let session = Session::new(
            SessionConfig {
                device_id: device_id(data_dir),
                // Streaming buffers; emptied each time the app starts.
                tmp_dir: cache_dir.join("tmp"),
                ..SessionConfig::default()
            },
            Some(cache),
        );

        let mixer_builder =
            mixer::find(Some("softvol")).ok_or_else(|| anyhow!("soft volume mixer missing"))?;
        let mixer: Arc<dyn Mixer> = mixer_builder(MixerConfig {
            volume_ctrl: VolumeCtrl::Cubic(VolumeCtrl::DEFAULT_DB_RANGE),
            ..MixerConfig::default()
        })
        .context("unable to create the mixer")?;

        let backend = audio_backend::find(Some("rodio".to_string()))
            .ok_or_else(|| anyhow!("audio output missing"))?;
        let player = Player::new(
            PlayerConfig {
                bitrate: Bitrate::Bitrate160,
                position_update_interval: Some(Duration::from_secs(1)),
                ..PlayerConfig::default()
            },
            session.clone(),
            mixer.get_soft_volume(),
            move || backend(None, AudioFormat::default()),
        );

        let state = Arc::new(Mutex::new(State {
            repeat: "off",
            volume: u16::MAX,
            ..State::default()
        }));
        tokio::spawn(run_events(
            player.get_player_event_channel(),
            Arc::clone(&state),
        ));

        let (spirc, task) = Spirc::new(
            ConnectConfig {
                name: device_name.to_string(),
                device_type: DeviceType::Smartphone,
                initial_volume: u16::MAX,
                ..ConnectConfig::default()
            },
            session.clone(),
            credentials,
            Arc::clone(&player),
            mixer,
        )
        .await
        .context("unable to connect to Spotify")?;

        let alive = Arc::new(AtomicBool::new(true));
        {
            let alive = Arc::clone(&alive);
            let state = Arc::clone(&state);
            tokio::spawn(async move {
                task.await;
                alive.store(false, Ordering::SeqCst);
                let mut state = state.lock().unwrap();
                state.connected = false;
                state.playing = false;
            });
        }
        state.lock().unwrap().connected = true;

        Ok(Self {
            spirc,
            session,
            state,
            alive,
        })
    }

    pub fn connected(&self) -> bool {
        self.alive.load(Ordering::SeqCst) && !self.session.is_invalid()
    }

    pub fn state(&self) -> State {
        let mut state = self.state.lock().unwrap().clone();
        state.connected = self.connected();
        state
    }

    pub fn shutdown(&self) {
        let _ = self.spirc.shutdown();
    }

    pub fn command(&self, command: Command) -> Result<()> {
        let spirc = &self.spirc;
        match command {
            Command::Toggle => spirc.play_pause()?,
            Command::Play => spirc.play()?,
            Command::Pause => spirc.pause()?,
            Command::Next => spirc.next()?,
            Command::Prev => spirc.prev()?,
            Command::Seek { ms } => spirc.set_position_ms(ms)?,
            Command::Volume { value } => spirc.set_volume(value)?,
            Command::Shuffle { on } => spirc.shuffle(on)?,
            Command::Repeat { mode } => match mode.as_str() {
                "track" => {
                    spirc.repeat(false)?;
                    spirc.repeat_track(true)?;
                }
                "context" => {
                    spirc.repeat_track(false)?;
                    spirc.repeat(true)?;
                }
                _ => {
                    spirc.repeat_track(false)?;
                    spirc.repeat(false)?;
                }
            },
            Command::Queue { uri } => spirc.add_to_queue(SpotifyUri::from_uri(&uri)?)?,
            Command::Transfer => spirc.transfer(None)?,
            Command::Load {
                context,
                uris,
                offset,
                index,
                shuffle,
            } => {
                let repeat = self.state.lock().unwrap().repeat;
                let options = LoadRequestOptions {
                    start_playing: true,
                    seek_to: 0,
                    playing_track: offset
                        .map(PlayingTrack::Uri)
                        .or(index.map(PlayingTrack::Index)),
                    context_options: Some(LoadContextOptions::Options(Options {
                        shuffle,
                        repeat: repeat == "context",
                        repeat_track: repeat == "track",
                    })),
                };
                let request = match context {
                    Some(context) => LoadRequest::from_context_uri(context, options),
                    None if !uris.is_empty() => LoadRequest::from_tracks(uris, options),
                    None => anyhow::bail!("nothing to play"),
                };
                spirc.activate()?;
                spirc.load(request)?;
            }
        }
        Ok(())
    }
}

async fn run_events(
    mut events: tokio::sync::mpsc::UnboundedReceiver<PlayerEvent>,
    state: Arc<Mutex<State>>,
) {
    while let Some(event) = events.recv().await {
        let mut state = state.lock().unwrap();
        apply_event(&mut state, event);
    }
}

fn apply_event(state: &mut State, event: PlayerEvent) {
    match event {
        PlayerEvent::Stopped { .. } => {
            state.playing = false;
            state.loading = false;
            state.position_ms = 0;
            state.position_at = now_ms();
        }
        PlayerEvent::Loading { position_ms, .. } => {
            state.loading = true;
            state.position_ms = position_ms;
            state.position_at = now_ms();
            state.error = None;
        }
        PlayerEvent::Playing { position_ms, .. } => {
            state.playing = true;
            state.loading = false;
            state.position_ms = position_ms;
            state.position_at = now_ms();
        }
        PlayerEvent::Paused { position_ms, .. } => {
            state.playing = false;
            state.loading = false;
            state.position_ms = position_ms;
            state.position_at = now_ms();
        }
        PlayerEvent::PositionChanged { position_ms, .. }
        | PlayerEvent::Seeked { position_ms, .. } => {
            state.position_ms = position_ms;
            state.position_at = now_ms();
        }
        PlayerEvent::TrackChanged { audio_item } => {
            state.track = Some(track(&audio_item));
            state.error = None;
        }
        PlayerEvent::Unavailable { .. } => {
            state.loading = false;
            state.error = Some("This song isn't available.".into());
        }
        PlayerEvent::VolumeChanged { volume } => state.volume = volume,
        PlayerEvent::ShuffleChanged { shuffle } => state.shuffle = shuffle,
        PlayerEvent::RepeatChanged { context, track } => {
            state.repeat = if track {
                "track"
            } else if context {
                "context"
            } else {
                "off"
            };
        }
        PlayerEvent::SessionClientChanged { client_name, .. } => state.active_client = client_name,
        PlayerEvent::SessionDisconnected { .. } => {
            // Another device took over: this one is no longer playing.
            state.active_client.clear();
            state.playing = false;
        }
        _ => {}
    }
}

fn track(item: &AudioItem) -> Track {
    let (artists, album) = match &item.unique_fields {
        UniqueFields::Track { artists, album, .. } => (
            artists.iter().map(|artist| artist.name.clone()).collect(),
            album.clone(),
        ),
        UniqueFields::Episode { show_name, .. } => (vec![show_name.clone()], show_name.clone()),
        UniqueFields::Local { artists, album, .. } => {
            (artists.iter().cloned().collect(), album.clone().unwrap_or_default())
        }
    };
    let art_url = item
        .covers
        .iter()
        .max_by_key(|cover| cover.width)
        .map(|cover| cover.url.clone());
    Track {
        uri: item.uri.to_string(),
        title: item.name.clone(),
        artists,
        album,
        art_url,
        duration_ms: item.duration_ms,
    }
}
