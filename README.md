# Deezer Watch

A Wear OS remote for the Deezer app on your phone. The watch lists your Deezer playlists and
controls playback; it never touches the internet. Everything goes watch <-> phone over the
Wear link, and the phone does the Deezer lookups and drives the Deezer app.

```
watch (wear/)  --Wear Data Layer-->  phone (phone/)  --> Deezer API (playlist list)
                                                     --> Deezer app (media session: play/pause/next)
```

**Audio plays from the phone** (or whatever it is connected to, e.g. Bluetooth earbuds),
as full songs through the Deezer app. Deezer's public API only offers 30-second previews, so
the watch controls the real Deezer app instead of streaming by itself.

## Modules
- `phone/`  companion app: settings screen, Deezer API client, Deezer media-session control,
  service answering the watch
- `wear/`   watch app: playlist list, now-playing + prev / play-pause / next
- `common/` `Protocol.java`, message/data paths shared by both (compiled into each)

Both apps use the same `applicationId` and must be signed with the same key (the debug key is
fine if both are built on this machine). The Wear Data Layer drops messages otherwise.

## Build
1. `local.properties` with `sdk.dir=...` (already set up).
2. `gradle assembleDebug` (AGP 8.1.4 works with the Gradle 8.9 install in `~\.gradle\wrapper\dists`).
3. Outputs:
   - `phone\build\outputs\apk\debug\phone-debug.apk`
   - `wear\build\outputs\apk\debug\wear-debug.apk`

## Install
Phone: `adb install -r phone\build\outputs\apk\debug\phone-debug.apk`

Watch (Wear OS 3+): enable Developer options > ADB debugging and Wireless debugging on the
watch, pair it with `adb pair <ip:port>`, then
`adb -s <watch-ip:port> install -r wear\build\outputs\apk\debug\wear-debug.apk`

The watch must be paired to the phone through the Wear OS / Galaxy Wearable app
(needs Google Play services on the phone).

## First run
On the phone, open Deezer Watch:
1. Enable notification access (Android 13+: Settings > Apps > Deezer Watch > three dots >
   "Allow restricted settings" first). This is what lets the watch see and control Deezer's player.
2. Allow "Display over other apps" so the watch can open Deezer when it isn't running.
3. Enter your Deezer user id (the number in `deezer.com/profile/<id>`) and tap "Save and test".
   - Only *public* playlists are visible with just the id.
   - For private playlists paste a Deezer OAuth access token as well
     (create an app at developers.deezer.com; scope `basic_access`, `manage_library`).
4. Open the app on the watch.

## Known limits / things to verify on a real device
- Starting a playlist uses Deezer's own deep link by id,
  `deezer://www.deezer.com/playlist/<id>?autoplay=true` (verified on a Galaxy A53: it plays the
  right playlist, starting on a shuffled track). Playing by *name* was tried first and could pick
  a different playlist with the same title. The Deezer app comes to the front on the phone when
  a playlist starts, so the phone needs "Display over other apps" allowed.
- Playlist list needs the phone to have internet; playback control does not.
