#!/bin/sh
# Builds the Rust core (../android/core) as a static library for the iOS
# device and the simulator, into ios/build/. Needs Xcode and the Rust
# targets aarch64-apple-ios and aarch64-apple-ios-sim.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
cd "$here/../android/core"
export IPHONEOS_DEPLOYMENT_TARGET=17.0
for target in aarch64-apple-ios aarch64-apple-ios-sim; do
    cargo rustc --lib --release --target "$target" --crate-type staticlib
done
mkdir -p "$here/build/ios" "$here/build/ios-sim"
cp target/aarch64-apple-ios/release/libspotifast_android.a "$here/build/ios/"
cp target/aarch64-apple-ios-sim/release/libspotifast_android.a "$here/build/ios-sim/"
