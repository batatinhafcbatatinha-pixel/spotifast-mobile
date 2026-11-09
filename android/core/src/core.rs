//! The process-wide state: the async runtime, the HTTP client, the two
//! Spotify grants and the playback engine.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

use anyhow::{Context, Result, anyhow, bail};
use serde::Serialize;
use tokio::sync::{oneshot, watch};

use crate::auth::{self, Grant, StoredToken};
use crate::engine::{Command, Engine};

const API_BASE: &str = "https://api.spotify.com/v1";

static CORE: OnceLock<Core> = OnceLock::new();

pub struct Core {
    rt: tokio::runtime::Runtime,
    http: reqwest::Client,
    data_dir: PathBuf,
    cache_dir: PathBuf,
    web: tokio::sync::Mutex<Option<StoredToken>>,
    /// The playback grant's access token between the browser and the first
    /// connection, which turns it into reusable credentials in librespot's
    /// cache.
    playback_token: Arc<Mutex<Option<String>>>,
    /// Cancels the sign-in waiting for the browser. Kept apart from its
    /// result, which `await_login` holds while it waits.
    login_cancel: Mutex<Option<watch::Sender<bool>>>,
    login_done: Mutex<Option<oneshot::Receiver<Result<()>>>>,
    engine: tokio::sync::Mutex<Option<Arc<Engine>>>,
}

#[derive(Serialize)]
pub struct Status {
    web_signed_in: bool,
    client_id: Option<String>,
    playback_signed_in: bool,
    engine_connected: bool,
}

impl Core {
    pub fn init(data_dir: PathBuf, cache_dir: PathBuf) -> Result<&'static Core> {
        if let Some(core) = CORE.get() {
            return Ok(core);
        }
        std::fs::create_dir_all(&data_dir)?;
        // Keep storage at its minimum: drop the streaming buffers left by
        // the last run and the audio cache older versions kept.
        for stale in ["tmp", "audio"] {
            let _ = std::fs::remove_dir_all(cache_dir.join(stale));
        }
        std::fs::create_dir_all(cache_dir.join("tmp"))?;
        let rt = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(3)
            .thread_name("spotifast-core")
            .enable_all()
            .build()?;
        let http = reqwest::Client::builder()
            .user_agent(concat!("Spotifast-Android/", env!("CARGO_PKG_VERSION")))
            .timeout(Duration::from_secs(30))
            .build()?;
        let web = StoredToken::load(&data_dir.join("web-token.json"));
        let core = Core {
            rt,
            http,
            data_dir,
            cache_dir,
            web: tokio::sync::Mutex::new(web),
            playback_token: Arc::default(),
            login_cancel: Mutex::new(None),
            login_done: Mutex::new(None),
            engine: tokio::sync::Mutex::new(None),
        };
        let _ = CORE.set(core);
        Ok(CORE.get().expect("core was just set"))
    }

    pub fn get() -> Result<&'static Core> {
        CORE.get().ok_or_else(|| anyhow!("Native.init was not called"))
    }

    pub fn status(&self) -> Status {
        self.rt.block_on(async {
            let web = self.web.lock().await;
            Status {
                web_signed_in: web.is_some(),
                client_id: web.as_ref().map(|token| token.client_id.clone()),
                playback_signed_in: self.playback_token.lock().unwrap().is_some()
                    || Engine::has_credentials(&self.data_dir),
                engine_connected: self
                    .engine
                    .lock()
                    .await
                    .as_ref()
                    .is_some_and(|engine| engine.connected()),
            }
        })
    }

    // ----- Sign-in -----

    /// Starts the Web API sign-in for the listener's own Spotify app and
    /// returns the URL to open in the browser.
    pub fn begin_web_login(&'static self, client_id: &str) -> Result<String> {
        if !auth::looks_like_client_id(client_id) {
            bail!("A Client ID is 32 letters and digits from the Spotify developer dashboard.");
        }
        let grant = Grant::personal_web_api(client_id)?;
        self.begin_login(grant, move |core, grant, response| {
            Box::pin(async move {
                let token = StoredToken::from_response(&grant.client_id, response, None)?;
                core.save_web(&token)?;
                *core.web.lock().await = Some(token);
                Ok(())
            })
        })
    }

    /// Starts the playback sign-in with the identity librespot streams as.
    pub fn begin_playback_login(&'static self) -> Result<String> {
        self.begin_login(Grant::playback(), move |core, _grant, response| {
            Box::pin(async move {
                *core.playback_token.lock().unwrap() = Some(response.access_token);
                Ok(())
            })
        })
    }

    fn begin_login(
        &'static self,
        grant: Grant,
        finish: impl FnOnce(
            &'static Core,
            Grant,
            auth::TokenResponse,
        ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<()>> + Send>>
        + Send
        + 'static,
    ) -> Result<String> {
        self.cancel_login();
        let flow = auth::begin(grant.clone());
        // A cancelled sign-in lets go of the port as its task ends.
        let listener = self.rt.block_on(async {
            let mut tries = 0;
            loop {
                match auth::listen_for_redirect(grant.redirect_port) {
                    Ok(listener) => break Ok(listener),
                    Err(error) if tries >= 20 => break Err(error),
                    Err(_) => {
                        tries += 1;
                        tokio::time::sleep(Duration::from_millis(100)).await;
                    }
                }
            }
        })?;
        let (cancel, cancel_rx) = watch::channel(false);
        let (done_tx, done) = oneshot::channel();
        let state = flow.state.clone();
        let verifier = flow.verifier.clone();
        self.rt.spawn(async move {
            let result = async {
                let code = auth::wait_for_code_on_listener(listener, &state, cancel_rx).await?;
                let response = auth::exchange_code(&self.http, &grant, &code, &verifier).await?;
                finish(self, grant, response).await
            }
            .await;
            let _ = done_tx.send(result);
        });
        *self.login_cancel.lock().unwrap() = Some(cancel);
        *self.login_done.lock().unwrap() = Some(done);
        Ok(flow.url)
    }

    /// Waits for the sign-in started last to finish.
    pub fn await_login(&self) -> Result<()> {
        let done = self
            .login_done
            .lock()
            .unwrap()
            .take()
            .ok_or_else(|| anyhow!("no sign-in in progress"))?;
        self.rt
            .block_on(done)
            .map_err(|_| anyhow!("sign-in cancelled"))?
    }

    pub fn cancel_login(&self) {
        if let Some(cancel) = self.login_cancel.lock().unwrap().take() {
            log::info!("cancelling the sign-in");
            let _ = cancel.send(true);
        }
    }

    pub fn sign_out(&self) -> Result<()> {
        self.rt.block_on(async {
            if let Some(engine) = self.engine.lock().await.take() {
                engine.shutdown();
            }
            *self.web.lock().await = None;
        });
        *self.playback_token.lock().unwrap() = None;
        let _ = std::fs::remove_file(self.web_path());
        Engine::forget_credentials(&self.data_dir);
        Ok(())
    }

    fn web_path(&self) -> PathBuf {
        self.data_dir.join("web-token.json")
    }

    fn save_web(&self, token: &StoredToken) -> Result<()> {
        write_atomic(&self.web_path(), &serde_json::to_vec(token)?)
    }

    // ----- Web API -----

    async fn access_token(&self, force_refresh: bool) -> Result<String> {
        let mut guard = self.web.lock().await;
        let token = guard
            .as_mut()
            .ok_or_else(|| anyhow!("Sign in to Spotify first."))?;
        if force_refresh || token.needs_refresh() {
            let response = auth::refresh(&self.http, &token.client_id, &token.refresh_token)
                .await
                .map_err(|error| anyhow!("{error}"))?;
            let renewed = StoredToken::from_response(
                &token.client_id,
                response,
                Some(&token.refresh_token),
            )?;
            self.save_web(&renewed)?;
            *token = renewed;
        }
        Ok(token.access_token.clone())
    }

    /// One Web API request. `path` is either a path under `/v1` or a full
    /// URL (the `next` link of a page). Returns the response body, `{}` when
    /// Spotify sends none.
    pub fn api(&self, method: &str, path: &str, body: Option<String>) -> Result<String> {
        let answer = self.web_api(method, path, body);
        // Spotify refuses an artist's popular songs to apps in development
        // mode; the playback session still has them.
        if answer.is_err()
            && method == "GET"
            && let Some(id) = path
                .strip_prefix("/artists/")
                .and_then(|rest| rest.strip_suffix("/top-tracks"))
            && let Ok(tracks) = self.session_top_tracks(id)
        {
            return Ok(tracks);
        }
        answer
    }

    fn session_top_tracks(&self, id: &str) -> Result<String> {
        self.rt.block_on(async {
            let engine = self.engine.lock().await.clone();
            let engine = engine.ok_or_else(|| anyhow!("Playback is not connected."))?;
            engine.artist_top_tracks(id).await
        })
    }

    fn web_api(&self, method: &str, path: &str, body: Option<String>) -> Result<String> {
        let url = if path.starts_with("https://") {
            path.to_string()
        } else {
            format!("{API_BASE}{path}")
        };
        let method = reqwest::Method::from_bytes(method.as_bytes())?;
        self.rt.block_on(async {
            let mut refreshed = false;
            let mut attempt = 0;
            loop {
                attempt += 1;
                let token = self.access_token(false).await?;
                let mut request = self
                    .http
                    .request(method.clone(), &url)
                    .bearer_auth(&token);
                if let Some(body) = &body {
                    request = request
                        .header(reqwest::header::CONTENT_TYPE, "application/json")
                        .body(body.clone());
                } else if method != reqwest::Method::GET {
                    request = request.header(reqwest::header::CONTENT_LENGTH, "0");
                }
                let response = request.send().await.context("Spotify could not be reached")?;
                let status = response.status();
                if status == reqwest::StatusCode::UNAUTHORIZED && !refreshed {
                    refreshed = true;
                    self.access_token(true).await?;
                    continue;
                }
                if status == reqwest::StatusCode::TOO_MANY_REQUESTS && attempt < 3 {
                    let wait = response
                        .headers()
                        .get(reqwest::header::RETRY_AFTER)
                        .and_then(|value| value.to_str().ok())
                        .and_then(|value| value.parse::<u64>().ok())
                        .unwrap_or(2)
                        .min(10);
                    tokio::time::sleep(Duration::from_secs(wait)).await;
                    continue;
                }
                let text = response.text().await.unwrap_or_default();
                if !status.is_success() {
                    let message = serde_json::from_str::<serde_json::Value>(&text)
                        .ok()
                        .and_then(|value| value["error"]["message"].as_str().map(str::to_string))
                        .unwrap_or_else(|| status.to_string());
                    bail!("Spotify: {message}");
                }
                return Ok(if text.trim().is_empty() {
                    "{}".to_string()
                } else {
                    text
                });
            }
        })
    }

    // ----- Playback -----

    /// Connects this phone to Spotify Connect, signing librespot in with
    /// the playback grant or the credentials it kept from last time.
    pub fn connect(&self, device_name: &str) -> Result<()> {
        let token = self.playback_token.lock().unwrap().clone();
        let grant = match token {
            Some(token) => {
                let me: serde_json::Value = serde_json::from_str(&self.api("GET", "/me", None)?)?;
                let username = me["id"]
                    .as_str()
                    .filter(|id| !id.is_empty())
                    .ok_or_else(|| anyhow!("Spotify did not say which account is signed in."))?
                    .to_string();
                Some((username, token))
            }
            None => None,
        };
        self.rt.block_on(async {
            let mut engine = self.engine.lock().await;
            if engine.as_ref().is_some_and(|engine| engine.connected()) {
                return Ok(());
            }
            if let Some(old) = engine.take() {
                old.shutdown();
            }
            let connected =
                Engine::connect(&self.data_dir, &self.cache_dir, device_name, grant).await;
            // Used or refused, the one-time token is spent: a refusal brings
            // back "Set up playback here" instead of failing every song.
            *self.playback_token.lock().unwrap() = None;
            let connected = connected?;
            *engine = Some(Arc::new(connected));
            Ok(())
        })
    }

    pub fn command(&self, json: &str) -> Result<()> {
        let command: Command = serde_json::from_str(json).context("unknown player command")?;
        self.rt.block_on(async {
            let engine = self.engine.lock().await.clone();
            let engine = engine.ok_or_else(|| anyhow!("Playback is not connected."))?;
            engine.command(command)
        })
    }

    pub fn player_state(&self) -> Result<String> {
        self.rt.block_on(async {
            let engine = self.engine.lock().await.clone();
            Ok(match engine {
                Some(engine) => serde_json::to_string(&engine.state())?,
                None => "{\"connected\":false}".to_string(),
            })
        })
    }

    pub fn disconnect(&self) {
        self.rt.block_on(async {
            if let Some(engine) = self.engine.lock().await.take() {
                engine.shutdown();
            }
        });
    }
}

pub(crate) fn write_atomic(path: &Path, contents: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let temporary = path.with_extension("tmp");
    std::fs::write(&temporary, contents)?;
    std::fs::rename(&temporary, path)?;
    Ok(())
}
