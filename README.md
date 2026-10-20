# ✦ Phoenicx Nova

A futuristic, remote-first Jellyfin client for **Fire TV** and **Android TV** — made by **Hanson**.

- Cinematic home carousel with slow Ken-Burns backdrops, title logos and auto-rotation
- Ambient home: the background and info panel follow whatever card you focus
- Continue Watching, Next Up, Recently Added per library, Favorites
- Rich detail pages: seasons & episodes, cast, "More Like This", resume / from start, watched & favorite toggles
- Custom player (ExoPlayer/Media3): direct play when your TV supports the codecs, automatic transcode fallback, subtitle & audio picker, synopsis + cast overlay, auto-play next episode, progress synced to your server
- Quick Connect sign-in (no typing on the remote)

## Install
Download **PhoenicxNova.apk** from the [latest release](../../releases/latest) and sideload it (e.g. with the *Downloader* app on Fire TV).

## Build
Every push builds the APK with GitHub Actions (`.github/workflows/build.yml`).

> The signing key in `keystore/` is for personal sideload builds so updates install over each other. Replace it before publishing to a store.
