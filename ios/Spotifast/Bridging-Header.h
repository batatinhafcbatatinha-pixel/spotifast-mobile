// The Rust core's C ABI (android/core/src/ffi.rs). Every call returns a JSON
// envelope, {"ok":true,"value":"…"} or {"ok":false,"error":"…"}, that the
// caller frees with spotifast_free.
#ifndef SPOTIFAST_BRIDGING_HEADER_H
#define SPOTIFAST_BRIDGING_HEADER_H

char *spotifast_init(const char *data_dir, const char *cache_dir);
char *spotifast_status(void);
char *spotifast_begin_web_login(const char *client_id);
char *spotifast_begin_playback_login(void);
char *spotifast_await_login(void);
char *spotifast_cancel_login(void);
char *spotifast_sign_out(void);
char *spotifast_api(const char *method, const char *path, const char *body);
char *spotifast_connect(const char *device_name);
char *spotifast_disconnect(void);
char *spotifast_command(const char *json);
char *spotifast_player_state(void);
void spotifast_free(char *value);

#endif
