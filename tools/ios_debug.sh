#!/bin/sh
# Copies a debug build's log, latest camera frame and (optionally) a fresh screenshot off a
# connected iPhone, into ./ios-debug. Needs Xcode; works without Xcode attached to the app.
#
#   tools/ios_debug.sh            log + frame
#   tools/ios_debug.sh screen     also asks the app for a screenshot first
set -e
device=${IOS_DEVICE:-$(xcrun devicectl list devices 2>/dev/null | grep ' connected ' | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)}
[ -n "$device" ] || { echo "No connected device (set IOS_DEVICE to its identifier)"; exit 1; }
if [ "$1" = "screen" ]; then
  xcrun devicectl device notification post --device "$device" --name com.uri.lee.dl.debug.screenshot >/dev/null
  sleep 1
fi
rm -rf ios-debug
xcrun devicectl device copy from --device "$device" --domain-type appDataContainer \
  --domain-identifier com.uri.lee.dl --source Documents/debug --destination ios-debug >/dev/null
ls -l ios-debug
