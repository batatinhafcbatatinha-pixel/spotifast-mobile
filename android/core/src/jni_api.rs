//! The JNI surface: `io.github.batatinha.spotifast.Native`.
//!
//! Every call that can block (sign-in, network, connecting) is made by the
//! Kotlin side off the main thread. Errors become a `RuntimeException`
//! carrying a message fit to show; panics are caught so they never cross
//! into the JVM.

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::PathBuf;

use anyhow::Result;
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jstring};

use crate::core::Core;

fn run<T>(env: &mut JNIEnv, fallback: T, body: impl FnOnce(&mut JNIEnv) -> Result<T>) -> T {
    let outcome = catch_unwind(AssertUnwindSafe(|| body(env)));
    let message = match outcome {
        Ok(Ok(value)) => return value,
        Ok(Err(error)) => format!("{error:#}"),
        Err(panic) => panic
            .downcast_ref::<&str>()
            .map(|s| s.to_string())
            .or_else(|| panic.downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "internal error".into()),
    };
    log::warn!("native call failed: {message}");
    let _ = env.throw_new("java/lang/RuntimeException", message);
    fallback
}

fn text(env: &mut JNIEnv, value: &JString) -> Result<String> {
    Ok(env.get_string(value)?.into())
}

fn optional_text(env: &mut JNIEnv, value: &JString) -> Result<Option<String>> {
    if value.is_null() {
        Ok(None)
    } else {
        text(env, value).map(Some)
    }
}

fn java(env: &mut JNIEnv, value: String) -> Result<jstring> {
    Ok(env.new_string(value)?.into_raw())
}

/// `init(context, dataDir, cacheDir)`. The application context lets cpal
/// reach Android's audio services through `ndk-context`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_init(
    mut env: JNIEnv,
    _class: JClass,
    context: JObject,
    data_dir: JString,
    cache_dir: JString,
) {
    run(&mut env, (), |env| {
        #[cfg(target_os = "android")]
        {
            android_logger::init_once(
                android_logger::Config::default()
                    .with_max_level(log::LevelFilter::Info)
                    .with_tag("spotifast"),
            );
            static CONTEXT: std::sync::Once = std::sync::Once::new();
            let vm = env.get_java_vm()?;
            let global = env.new_global_ref(&context)?;
            CONTEXT.call_once(|| unsafe {
                ndk_context::initialize_android_context(
                    vm.get_java_vm_pointer().cast(),
                    global.as_obj().as_raw().cast(),
                );
                // The context lives as long as the process.
                std::mem::forget(global);
            });
        }
        #[cfg(not(target_os = "android"))]
        let _ = context;
        let data_dir = PathBuf::from(text(env, &data_dir)?);
        let cache_dir = PathBuf::from(text(env, &cache_dir)?);
        Core::init(data_dir, cache_dir)?;
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_status(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    run(&mut env, std::ptr::null_mut(), |env| {
        let status = serde_json::to_string(&Core::get()?.status())?;
        java(env, status)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_beginWebLogin(
    mut env: JNIEnv,
    _class: JClass,
    client_id: JString,
) -> jstring {
    run(&mut env, std::ptr::null_mut(), |env| {
        let client_id = text(env, &client_id)?;
        let url = Core::get()?.begin_web_login(&client_id)?;
        java(env, url)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_beginPlaybackLogin(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    run(&mut env, std::ptr::null_mut(), |env| {
        let url = Core::get()?.begin_playback_login()?;
        java(env, url)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_awaitLogin(
    mut env: JNIEnv,
    _class: JClass,
) {
    run(&mut env, (), |_| Core::get()?.await_login())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_cancelLogin(
    mut env: JNIEnv,
    _class: JClass,
) {
    run(&mut env, (), |_| {
        Core::get()?.cancel_login();
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_signOut(
    mut env: JNIEnv,
    _class: JClass,
) {
    run(&mut env, (), |_| Core::get()?.sign_out())
}

/// `api(method, path, body?)`: one Web API request, the body back as text.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_api(
    mut env: JNIEnv,
    _class: JClass,
    method: JString,
    path: JString,
    body: JString,
) -> jstring {
    run(&mut env, std::ptr::null_mut(), |env| {
        let method = text(env, &method)?;
        let path = text(env, &path)?;
        let body = optional_text(env, &body)?;
        let response = Core::get()?.api(&method, &path, body)?;
        java(env, response)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_connect(
    mut env: JNIEnv,
    _class: JClass,
    device_name: JString,
) {
    run(&mut env, (), |env| {
        let name = text(env, &device_name)?;
        Core::get()?.connect(&name)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_disconnect(
    mut env: JNIEnv,
    _class: JClass,
) {
    run(&mut env, (), |_| {
        Core::get()?.disconnect();
        Ok(())
    })
}

/// `command(json)`, for example `{"cmd":"toggle"}` or
/// `{"cmd":"load","context":"spotify:playlist:…","offset":"spotify:track:…"}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_command(
    mut env: JNIEnv,
    _class: JClass,
    json: JString,
) -> jboolean {
    run(&mut env, 0, |env| {
        let json = text(env, &json)?;
        Core::get()?.command(&json)?;
        Ok(1)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_batatinha_spotifast_Native_playerState(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    run(&mut env, std::ptr::null_mut(), |env| {
        let state = Core::get()?.player_state()?;
        java(env, state)
    })
}
