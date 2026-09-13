# Crosshair Companion (for GameNative)

An Android companion app that automatically draws a crosshair in the middle of the screen while your games are running — built for playing titles like **Metal Gear Solid: Peace Walker** in [GameNative](https://github.com/utkarshdalal/GameNative) with a controller.

**[⬇ Download the latest APK from Releases](../../releases/latest)**

## What it does

- **Automatic overlay** — a click-through crosshair appears only while your selected games are in the foreground, and disappears everywhere else (launcher, messaging, settings…). Foreground detection uses the Usage Access API, so no root or Xposed is needed.
- **Pick which games get the crosshair** — every installed app can be toggled on/off individually. `app.gamenative` is pre-configured, so Peace Walker works out of the box.
- **10 built-in crosshairs** — Classic Cross, Cross + Dot, Center Dot, Tactical Green, Ring, Ring + Dot, Diagonal X, Chevron, T-Post, Gold Dot.
- **Full style editor** — live preview, shape, size, thickness, center gap, center dot, opacity, X/Y offsets (for games whose aim point isn't perfectly centered), 8 colors, and an outline toggle for contrast on bright scenes.
- **Per-game style overrides** — assign any style to any game; everything else uses the *active* style.
- **In-game controls** — notification buttons: *Pause/Resume*, *Next style*, *Stop*. Plus a Quick Settings tile for instant toggling.
- **Battery friendly** — pauses the overlay when the screen is off.

## Install

1. Download the APK from [Releases](../../releases/latest) and install it (allow "install unknown apps" for your browser/file manager).
2. Open **Crosshair Companion** and grant the four items on the checklist:
   - **Display over other apps** (required — draws the overlay)
   - **Usage access** (required — detects which game is running)
   - **Notifications** (optional — in-game controls)
   - **Battery optimization → Allow** (recommended — keeps the service alive)
3. Flip **Show crosshair overlay** on. GameNative is already in the games list.

That's it — launch Peace Walker in GameNative and the crosshair appears; close the game and it's gone.

## Customizing

- **Games & apps** — toggle games on/off, long-press to remove, or tap `Style: …` to give a specific game its own crosshair. `Add app` lists everything installed.
- **Crosshair styles** — tap a style to edit it (changes apply to the overlay within a second), mark one as *Active* to use it by default, long-press to delete, or create new ones.

Suggested starting point for Peace Walker: **Tactical Green** (Cross + Dot) — thin cross with a small center dot, green for the MGS codec vibe.

## Per-game auto-switching inside GameNative (optional)

The app understands a simple broadcast from GameNative that reports which game is currently running:

```kotlin
// inside GameNative, when a game container/shortcut starts:
val i = Intent("com.zcc09.crosshaircompanion.action.GAME_CHANGED")
    .setPackage("com.zcc09.crosshaircompanion")
    .putExtra("game", shortcut.name)   // e.g. "MGS Peace Walker"
sendBroadcast(i)
```

Add a game in **Games & apps → Add GameNative game** with the exact name, assign it a style, and the crosshair will switch automatically whenever that game runs. Without the GameNative-side patch this rule simply stays inactive — you can still use the notification's *Next style* button.

## How it works

- `SYSTEM_ALERT_WINDOW` overlay window (`TYPE_APPLICATION_OVERLAY`, non-focusable, non-touchable) drawn by a foreground service.
- Foreground app detection via `UsageStatsManager` query events (Usage Access).
- While the master switch is off the service idles (no drawing); while the screen is off nothing is drawn. Removing the app anytime is safe — it leaves no background state behind except the service you enable.

## Build (CI)

No local Android SDK/JDK needed — GitHub Actions builds both APKs:

- Workflow: `.github/workflows/build.yml` (Gradle 8.12.1, AGP 8.8.0, Kotlin 2.1.21, JDK 17, compileSdk 35, minSdk 26, targetSdk 34).
- Release APKs are signed with a stable CI keystore (repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Without the secrets it falls back to debug signing so forks still build.
- Tag a release with `git tag v1.x.y && git push origin v1.x.y`, or dispatch the workflow with a tag name.

### End-to-end test

Every CI run boots an Android 14 emulator, installs the debug APK, configures a target app, starts the overlay service, and pixel-verifies two screenshots:

1. crosshair **visible** over a targeted foreground app,
2. crosshair **gone** over a non-targeted app (launcher).

Screenshots and dumps are uploaded as the `e2e-evidence` artifact.

## Troubleshooting

- **Crosshair doesn't appear** — check that *Display over other apps* and *Usage access* are granted, the master switch is on, and the game is enabled in *Games & apps*.
- **Crosshair shows everywhere** without Usage access granted, the app intentionally falls back to showing over all apps.
- **Crosshair flickers off briefly** when switching apps — expected; it re-checks the foreground app every ~1s.
- **Aim point feels off** — use the *Offset X/Y* sliders in the editor, and test with the Dark/Light preview backgrounds.

## License

MIT — see [LICENSE](LICENSE).
