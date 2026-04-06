# InstaDMGuard

InstaDMGuard is a personal-use Android accessibility app that blocks Instagram reels by default and only grants short-lived access to reels that appear to have been opened directly from Instagram DMs.

## What it does

- Watches only `com.instagram.android`
- Detects likely Instagram screen states with weighted heuristics, not a single brittle string check
- Blocks reel surfaces with one of three modes:
  - overlay only
  - back only
  - overlay + back
- Allows a temporary reel session when the service sees:
  - DM list or DM thread context
  - a click inside that DM context
  - a reel viewer opening immediately after the click
- Expires the DM reel allowance after the configured grace window
- Clears the DM allowance early when navigation clearly moves into broader reel entry points like the Reels tab, Explore reels, or Home-feed reel surfaces
- Supports temporary pauses for 5 or 15 minutes
- Includes an experimental daily-limit mode for general reels when DM-only mode is turned off
- Shows a debug view with the last 20 detector/service events

## Project structure

```text
app/
  src/main/java/com/example/instadmguard/
    data/
    detector/
    model/
    service/
    ui/
    util/
  src/main/res/
settings.gradle.kts
build.gradle.kts
```

## Build requirements

- Android Studio with Android SDK Platform 34 or 35 installed
- JDK 17 or newer
- A device with Instagram installed

## Open in Android Studio

1. Open Android Studio.
2. Choose `Open`.
3. Select the `Scrollstop` folder that contains this project.
4. Let Gradle sync finish.
5. If Android Studio prompts for SDK Platform 34, install it.

## Build the APK

1. In Android Studio, choose `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.
2. Or run:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew assembleDebug
```

3. The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on your phone

1. Enable developer options and USB debugging on your Android phone.
2. Connect the phone by USB.
3. Install with Android Studio, or run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. If prompted on-device, allow installs from your chosen source.

## Enable the accessibility service

1. Open `InstaDMGuard`.
2. Tap `Accessibility settings`.
3. Find `InstaDMGuard Protection`.
4. Turn the service on and confirm the warning dialog.
5. Return to the app and confirm the status now says accessibility is enabled.

## Test the DM-only reel allowance

1. Keep `Master protection` on.
2. Keep `Allow DM-opened reels only` on.
3. Set `Block mode` to `Overlay + back` for the clearest first test.
4. Open Instagram Home or Reels and verify general reels get blocked.
5. Open Instagram DMs.
6. Open a DM thread sent by a friend that contains a reel preview or reel link.
7. Tap the reel from inside that DM thread.
8. The reel should stay available for roughly the configured grace duration.
9. Wait until the grace window expires, or move into broader reel navigation.
10. Verify the service blocks again.

## Limitations

- Instagram changes its UI often. Accessibility text, content descriptions, and view IDs can change without notice.
- The DM-to-reel allow flow is heuristic-based. It works by detecting a DM context, a click inside that context, and a reel viewer opening immediately after.
- If Instagram opens a DM-linked reel in the same viewer used by general reels, the app cannot perfectly distinguish every swipe after the initial DM-opened reel. The grace timer is the main fallback.
- Accessibility overlays and global back behavior can feel aggressive on rapid UI transitions. The service already debounces events and rate-limits repeated back actions.
- The experimental daily-limit mode is intentionally secondary to the main DM-only workflow.

## How to tweak detection strings later

All detection strings live in:

- `app/src/main/java/com/example/instadmguard/detector/DetectionHeuristics.kt`

Most future Instagram UI changes can be handled by editing the clue lists there:

- `dmListTextClues`
- `dmThreadTextClues`
- `dmViewIdClues`
- `reelActionTextClues`
- `reelViewerTextClues`
- `reelViewIdClues`
- `reelsTabClues`
- `exploreClues`
- `homeClues`

After changing clue lists, re-run:

```bash
./gradlew testDebugUnitTest assembleDebug
```
