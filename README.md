# Breathe

An Android screensaver that shows you a guided breathing animation when your phone is plugged in and rotated to a landscape orientation.

![The breathing guide mid-inhale: a triangle, square and pentagon on one base, with "breathe in" below](docs/breathe.png)

## Install

1. Download the APK from [Releases](../../releases) and install it.
2. Choose Breathe in Settings › Display › Screen saver, and set it to start while charging.
3. Plug your phone in and stand it on its side.

Needs Android 14 or later. If Developer options › Stay awake is on, the screen never times out while charging, so no screensaver starts.

## How it works

- Android starts the screensaver when the screen would turn off while charging. Breathe checks gravity once: on its side, it breathes; upright or flat, it steps aside and the screen turns off as usual.
- A screensaver that ends while it's keeping the screen on wakes the phone up again. So after five minutes Breathe lets go of the screen first, and finishes once the screen timeout has passed.
- he label is set in [Geist](https://vercel.com/font).

## Build

```
./gradlew assembleDebug
```

Release builds are signed with the key described in `keystore.properties`, which is not in this repo. Without it, release builds fall back to the debug key.
