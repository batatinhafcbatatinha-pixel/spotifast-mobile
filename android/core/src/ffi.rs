//! The C ABI for iOS (and any other non-Android host).
//!
//! Every call returns a heap string the caller frees with
//! `spotifast_free`: a JSON envelope, `{"ok":true,"value":"…"}` or
//! `{"ok":false,"error":"…"}`. `value` is always a string; calls that
//! answer with JSON (status, the Web API, the player) carry it as text.
//! Calls that block (sign-in, network, connecting) are made off the main
//! thread by the caller. Panics are caught and returned as errors.

use std::ffi::{CStr, CString, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::PathBuf;

use anyhow::{Result, anyhow};
use serde_json::json;

use crate::core::Core;

fn envelope(result: Result<String>) -> *mut c_char {
    let body = match result {
        Ok(value) => json!({ "ok": true, "value": value }),
        Err(error) => json!({ "ok": false, "error": format!("{error:#}") }),
    };
    // serde_json never emits an interior NUL.
    CString::new(body.to_string()).unwrap_or_default().into_raw()
}

fn run(body: impl FnOnce() -> Result<String>) -> *mut c_char {
    let outcome = catch_unwind(AssertUnwindSafe(body)).unwrap_or_else(|panic| {
        Err(anyhow!(
            "{}",
            panic
                .downcast_ref::<&str>()
                .map(|s| s.to_string())
                .or_else(|| panic.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "internal error".into())
        ))
    });
    envelope(outcome)
}

/// # Safety
/// `value` is null or a NUL-terminated UTF-8 string.
unsafe fn text(value: *const c_char) -> Result<Option<String>> {
    if value.is_null() {
        return Ok(None);
    }
    let text = unsafe { CStr::from_ptr(value) }.to_str()?;
    Ok(Some(text.to_string()))
}

unsafe fn required(value: *const c_char, name: &str) -> Result<String> {
    unsafe { text(value) }?.ok_or_else(|| anyhow!("{name} is required"))
}

/// Frees a string returned by any other call.
///
/// # Safety
/// `value` came from this library and is freed once.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_free(value: *mut c_char) {
    if !value.is_null() {
        drop(unsafe { CString::from_raw(value) });
    }
}

/// # Safety
/// Both arguments are NUL-terminated UTF-8 paths.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_init(data_dir: *const c_char, cache_dir: *const c_char) -> *mut c_char {
    run(|| {
        let data = unsafe { required(data_dir, "data_dir") }?;
        let cache = unsafe { required(cache_dir, "cache_dir") }?;
        Core::init(PathBuf::from(data), PathBuf::from(cache))?;
        Ok(String::new())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_status() -> *mut c_char {
    run(|| Ok(serde_json::to_string(&Core::get()?.status())?))
}

/// # Safety
/// `client_id` is a NUL-terminated UTF-8 string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_begin_web_login(client_id: *const c_char) -> *mut c_char {
    run(|| {
        let client_id = unsafe { required(client_id, "client_id") }?;
        Core::get()?.begin_web_login(&client_id)
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_begin_playback_login() -> *mut c_char {
    run(|| Core::get()?.begin_playback_login())
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_await_login() -> *mut c_char {
    run(|| Core::get()?.await_login().map(|_| String::new()))
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_cancel_login() -> *mut c_char {
    run(|| {
        Core::get()?.cancel_login();
        Ok(String::new())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_sign_out() -> *mut c_char {
    run(|| Core::get()?.sign_out().map(|_| String::new()))
}

/// # Safety
/// `method` and `path` are NUL-terminated UTF-8; `body` may be null.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_api(
    method: *const c_char,
    path: *const c_char,
    body: *const c_char,
) -> *mut c_char {
    run(|| {
        let method = unsafe { required(method, "method") }?;
        let path = unsafe { required(path, "path") }?;
        let body = unsafe { text(body) }?;
        Core::get()?.api(&method, &path, body)
    })
}

/// # Safety
/// `device_name` is a NUL-terminated UTF-8 string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_connect(device_name: *const c_char) -> *mut c_char {
    run(|| {
        let name = unsafe { required(device_name, "device_name") }?;
        Core::get()?.connect(&name).map(|_| String::new())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_disconnect() -> *mut c_char {
    run(|| {
        Core::get()?.disconnect();
        Ok(String::new())
    })
}

/// # Safety
/// `json` is a NUL-terminated UTF-8 player command.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn spotifast_command(json: *const c_char) -> *mut c_char {
    run(|| {
        let json = unsafe { required(json, "command") }?;
        Core::get()?.command(&json).map(|_| String::new())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn spotifast_player_state() -> *mut c_char {
    run(|| Core::get()?.player_state())
}
