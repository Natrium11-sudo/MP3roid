# Testing MP3roid in the emulator (Android 10 / API 29)

The reference device runs **Android 10 = API 29**. An emulator with exactly that
version is therefore the most honest test: only there do the legacy storage
permissions (`READ_EXTERNAL_STORAGE` instead of `READ_MEDIA_AUDIO`) and the
first-generation RoleManager behaviour apply.

> Note on UI labels: the app's interface is German, so quoted button and menu
> labels below are kept in German as they appear on screen, with an English
> gloss in brackets where it helps.

## Prerequisites

| Item | Path (example) |
| --- | --- |
| Android SDK for builds | `$ANDROID_HOME` |
| Android SDK with emulator + system images | `$ANDROID_SDK` |
| `sdkmanager` / `avdmanager` | `$ANDROID_SDK/cmdline-tools/latest/bin/` |
| Emulator | `$ANDROID_SDK/emulator/emulator` |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| JDK 17 or newer | `$JAVA_HOME` |

Step 1 downloads the system image for API 29.

> **Note on the rename (October 2026):** the app is now called **MP3roid** –
> visible name, package (`com.mp3roid.player`), class names and project folder
> (`MP3roid`). Only the AVD name stayed `Klang_E7_API29` for
> convenience. Because the `applicationId` changed, MP3roid is a **new**
> package: grant the home-screen role and notification access again, and all
> your own data (theme, favorites, playlists, sleep timer) starts empty. The old
> `com.klang.player` was removed from the test device.

## 1. Install the system image for API 29 (once, ~1 GB)

```bash
export SDK=$ANDROID_SDK
$SDK/cmdline-tools/latest/bin/sdkmanager \
  --install "system-images;android-29;google_apis;x86_64"
```

`google_apis` instead of `google_apis_playstore`: the Play Store is not needed
for testing, and the image is significantly smaller. Spotify & co. can still be
installed via `adb install`.

Important: `x86_64` fits here even though the test device is an ARM device.
MP3roid contains no native code (Media3 doesn't ship any), so the same APK runs
on both architectures.

## 2. Create an AVD matching the test device

Don't pick the Pixel profile: the test device has only **720 × 1600** pixels at
~270 dpi and **2 GB RAM**. That is exactly where it shows whether the UI has
grown too large.

```bash
export SDK=$ANDROID_SDK
export PATH=$SDK/emulator:$SDK/platform-tools:$PATH

$SDK/cmdline-tools/latest/bin/avdmanager \
  create avd -n Klang_E7_API29 \
  -k "system-images;android-29;google_apis;x86_64" \
  -d pixel_2 --force
```

Then edit `~/.android/avd/Klang_E7_API29.avd/config.ini`:

```ini
hw.lcd.width=720
hw.lcd.height=1600
hw.lcd.density=270
hw.ramSize=2048
vm.heapSize=192
disk.dataPartition.size=4096M
hw.keyboard=yes
```

Alternatively, do it all in the Android Studio Device Manager: “Create Virtual
Device” → API 29 → **Show Advanced Settings** at the bottom right, then set
resolution, dpi and RAM as above.

## 3. Start the emulator and install the APK

```bash
export SDK=$ANDROID_SDK
export PATH=$SDK/emulator:$SDK/platform-tools:$PATH

emulator -avd Klang_E7_API29 &
adb wait-for-device
# wait until the home screen is up:
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'

cd MP3roid
ANDROID_HOME=$ANDROID_HOME \
  ./gradlew :app:assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Grant permissions

On launch, MP3roid shows the onboarding, which asks for the three items one
after another. Via `adb` it is faster:

```bash
# Read music (on API 29 this is still READ_EXTERNAL_STORAGE)
adb shell pm grant com.mp3roid.player android.permission.READ_EXTERNAL_STORAGE

# Register as the home screen
adb shell cmd package set-home-activity com.mp3roid.player/.MainActivity

# Notification access for foreign players (alternatives to the UI)
adb shell cmd notification allow_listener \
  com.mp3roid.player/com.mp3roid.player.playback.MP3roidNotificationListener
# if the command is missing on API 29:
adb shell settings put secure enabled_notification_listeners \
  com.mp3roid.player/com.mp3roid.player.playback.MP3roidNotificationListener
```

Notification access can also be granted manually under
Settings → Apps → Advanced → Notification access → MP3roid.

## 5. Add music

```bash
mkdir -p /tmp/opencode/music
# put some MP3s/OGG/M4A in there, then:
adb push /tmp/opencode/music/ /sdcard/Music/

# Make MediaStore pick them up
for f in /sdcard/Music/*; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://$f" > /dev/null
done
```

Alternatively, restart the emulator once – MediaProvider indexes `/sdcard` on
boot. MP3roid listens on a `ContentObserver`, so the library updates itself as
soon as MediaStore knows the files.

## 6. Checkpoints

- **Home screen:** press the home button → MP3roid appears as home. Under
  Settings → Apps → Default apps → Home screen, MP3roid should be listed.
- **Local playback:** tap a track/album in the library. The MiniPlayer must
  appear and the notification must show playback controls.
- **Background:** press home, music keeps playing; use the notification; swipe
  the app out of recents – sound must continue (`PlaybackService` is a
  `MediaSessionService`).
- **Foreign player:** install a second music app, play something → the source
  chips at the top appear in MP3roid; switching must work. Without
  notification access they stay invisible (known limitation).
- **Source bar:** the source that is currently playing is always first. From
  about four sources the bar can be swiped sideways; the source in front is
  visible without swiping. When the source changes, the bar jumps back to the
  start. A very long swipe past the end of the bar turns the page – that is the
  normal chain of nested swipe areas (bar first, then the pages).
- **MiniPlayer:** it does not exist on the Player page, where the full view
  runs; the freed space goes to it. On Library and Apps it shows title, artwork
  and play/next, and tapping it leads to the Player page, where it disappears
  on arrival. It fades in and out only once the page has settled
  (`settledPage`) – not in the middle of a swipe gesture.
- **App list:** the “Apps” page shows every installed program and keeps itself
  up to date. MP3roid listens in two places: the launcher API (`LauncherApps`,
  the official means of a home screen) and the system's package broadcasts.
  When something is installed or removed, the list and the counter at the top
  change on their own; “App-Liste erneuern” (refresh app list) remains as a
  fallback. Switching to the page also re-reads the list, so it stays correct
  even after a longer time in the background.
- **App menu (long press):** a long press on an app in the list opens
  “Öffnen / App-Info / Deinstallieren” (Open / App info / Uninstall). “App-Info”
  opens Android's app settings, “Deinstallieren” opens the system confirmation
  and removes the app afterwards; the list catches up by itself. MP3roid is not
  in its own list, so it cannot remove itself. The page header shows a hint
  about the long press. For the confirmation to appear, the app declares
  `REQUEST_DELETE_PACKAGES` – without that permission `startActivity` throws a
  SecurityException since Android 10.
- **Volume keys:** the keys change the music volume outside the app. The slider
  on the Player page still follows, because it re-reads the value through an
  observer of the system settings and the volume broadcast. The value is never
  taken from the broadcast, but always fetched fresh from the audio service – a
  forged broadcast could at most trigger an unnecessary re-read.
- **What foreign apps cannot do:** seek, shuffle and repeat if the other app
  does not offer it. MP3roid then shows “Diese App erlaubt kein Spulen” (this
  app does not allow seeking) and hides the controls instead of lying.
- **Icons in the control row:** “Vorheriger” and “Nächster” (previous/next) are
  exact mirror images – bars outside, triangle pointing in the respective
  direction, both equally tall and centred. The timer icon of the tool row is a
  round stopwatch with the crown on top and a pointer slot. If an icon looks
  shifted or clipped, it is usually the path: after a `z` no *relative* command
  may follow, because the reader does not reset the current point to the start
  of the subpath there. Four files were affected (`ic_skip_previous`,
  `ic_timer`, `ic_bar_chart`, `ic_playlist_play`); they now use absolute
  coordinates.
- **Tool row:** below the transport row sit the buttons that match the current
  source – favorite, sleep timer, queue, equalizer. With a running timer the
  sleep timer shows the remaining time on the button (“5 Min”) and fills it with
  color; the favorite fills when tapped. Only what does something is visible:
  favorite and queue appear only for local playback (a foreign album cannot be
  favorited, and Android's interface does not expose a foreign queue), the
  equalizer only when the app's own audio session exists. The sleep timer stays
  always, because it also stops foreign playback. The label is tappable too,
  not just the circle.
- **Equalizer:** available only after the first `play`, because it hangs off
  the audio session. Reachable via the tool button in the player or via Player
  settings.
- **Sleep timer:** while it runs, the remaining time appears as a pill directly
  below the tool row (“Sleep-Timer läuft noch 24:49”) and ticks every second;
  tapping it reopens the dialog. There you find the quick presets (Off, 5 to
  90 min) and the “Eigene Minuten” (custom minutes) field for any value up to
  999. A custom value stays saved and is in the field the next time it opens.
- **Player settings:** at the bottom of the Player page. It contains the sleep
  timer, equalizer, library (music only, short files, rescan) and notification
  access for foreign players.
- **Settings:** only home-screen things now – appearance, home-screen role and
  system bars, about. If a permission is missing, an additional “Einrichtung”
  (setup) section appears at the top; when everything is there, the section is
  gone entirely.
- **Notch and system bars:** MP3roid draws its UI across the whole screen,
  including the notch area (`windowLayoutInDisplayCutoutMode = shortEdges`).
  The system therefore no longer reserves the top strip and paints no band
  there. With hidden system bars (the default), content starts at the very top
  and the app's own background fills the area; with visible bars, content
  avoids the status bar and the clock sits over the app background.
  To reproduce this in the emulator (which has no notch by default):
  ```bash
  adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.tall
  adb reboot
  # undo:
  adb shell cmd overlay disable com.android.internal.display.cutout.emulation.tall
  ```
- **Apply a specific wallpaper for measuring:** the most reliable way is via
  the system file (otherwise the screen always shows the same plain AOSP test
  image, on which sharpness cannot be measured):
  ```bash
  adb root
  adb push testbild.png /data/local/tmp/w.png
  adb shell "cp /data/local/tmp/w.png /data/system/users/0/wallpaper && \
             chown system:system /data/system/users/0/wallpaper && \
             chmod 600 /data/system/users/0/wallpaper"
  adb reboot
  # remove again: delete BOTH files and reboot
  adb shell "rm -f /data/system/users/0/wallpaper /data/system/users/0/wallpaper_orig"
  ```
  Both files belong together: if only `wallpaper` is left, the system restores
  it from `wallpaper_orig` on the next start – the test wallpaper would
  suddenly be back after the reboot (observed on 18 Oct 2026). Do not touch
  `wallpaper_info.xml` – the data in it can be outdated and is rewritten by the
  system.
  To measure the crop: correlate a strip without controls (emulator
  y 865–905, phone y 915–955) line by line against candidate windows of the
  original image (subtract line means, so dimming has no influence). Try
  candidates every 100 px – a clear maximum at x=0 means the left crop, at
  x=440 the middle.
- **Themes:** Settings → Appearance. The three JSON themes from
  `assets/themes/` must appear next to the four built-in ones.
- **Create your own theme:** tap the “＋ Eigenes” (custom) tile. A custom theme
  is created immediately with the name “Eigenes Theme 1” (numbered
  consecutively) as a copy of the currently active look, and the editor opens.
  The name is fixed – custom themes are not renamed.
- **In the editor:** preview at the top, below it the colors (background,
  surface, elevated surface, accent, secondary accent, text, secondary text,
  gradient top/bottom). Tapping a color field opens the slider dialog with hue
  (0–360), saturation and brightness (each 0–100 %); the hex value is shown
  next to it for reference only. While dragging, the app recolors immediately.
  Below that are shape sliders for cover corner (0–40), card corner (0–30) and
  control size (56–96). Everything is saved automatically.
- **Reset:** “Auf Startwert zurücksetzen” (reset to initial values, last row in
  the editor) restores the state from when the editor was opened. The button
  at the top right (trash can) deletes the custom theme after a confirmation;
  afterwards the built-in “Dark Void” applies again. Bundled themes can never
  be deleted.
- **Custom tiles:** custom themes appear in the theme row with a pencil badge.
  A long press there opens “Bearbeiten / Duplizieren und bearbeiten / Löschen”
  (edit / duplicate and edit / delete); bundled themes only show “Duplizieren
  und bearbeiten”. That cleanly distinguishes them from the built-in ones.
- **2 GB RAM:** do not raise the AVD's RAM limit. If it stutters in the
  emulator, that is the realistic feedback from the test device.

## 7. When something goes wrong

```bash
adb logcat -d | grep -E "AndroidRuntime|FATAL|mp3roid"          # crashes
adb shell dumpsys activity activities | grep -i mp3roid         # who is on top
adb shell cmd package resolve-activity -c android.intent.category.HOME
adb shell dumpsys media_session | head -60                    # sessions
adb shell dumpsys notification --noredact | grep -i listener  # access
```

`adb uninstall com.mp3roid.player` removes the app, `adb emu kill` the emulator.

If the emulator starts without a picture, append `-gpu swiftshader_indirect`;
that renders on the CPU and works almost everywhere. `-no-snapshot-load`
forces a clean cold boot.

## 8. What has already been verified in this emulator

On API 29, 720 × 1600, 2 GB RAM, against the builds from 17–18 Oct 2026
(the wallpaper last against 1.0-beta10 – the crop and sharpness were re-checked
there; the rows for the finished colors against 1.0-beta7):

Note: the test device has notch simulation enabled
(`com.android.internal.display.cutout.emulation.tall`) so that it matches the
test device. The simulated notch is 81 px taller than the real teardrop notch –
the check is therefore stricter than reality.

| Checkpoint | Result |
| --- | --- |
| Start | MainActivity visible in 567 ms, no crash in the log |
| Onboarding | All three steps, permissions detected correctly |
| Library | 4 MP3s → 2 albums, 3 tracks; the 12-second file is hidden as configured |
| Automatic rescan | Newly added files appear without a restart (ContentObserver) |
| Local playback | MediaSession active, audio focus with MP3roid, notification with 3 buttons |
| Home screen | `set-home-activity` successful; the home button from Settings brings MP3roid to the front, music keeps playing |
| Foreign playback | Second app detected: title, artist, artwork and position come from its session |
| Source switching | Pill bar appears from two sources, switching changes display and controls |
| Playing source first | The playing source is first, even if local is first in the fixed list; when the source changes, the order rotates with it |
| Swiping the bar | In a narrow layout (340 px) the bar overflows: sideways swiping brings the other sources into view without changing the source; swiping on the cover still changes the source |
| MiniPlayer | Not present on the Player page (page area 95–1487 instead of 95–1385), present on Library and Apps with title, artwork and buttons; it fades in and out only after the page has settled |
| Volume keys | Volume 0 → 2 → 4 via the keys: the slider knob on screen moves along (x center 91 → 167 → 243) and back, without leaving the app |
| Remote control | Play/pause, next and previous verifiably reach the foreign app |
| Seeking not possible | Bar becomes a pure progress bar plus the note “Diese App erlaubt kein Spulen” |
| Shuffle/repeat | Greyed out for foreign sources – Android's interface does not provide it |
| Themes | Four built-in plus three JSON themes from `assets/themes/`; switching recolors the whole UI |
| Creating a custom theme | “＋ Eigenes” in the theme row creates `own-1` as a copy of “Dark Void” (file `files/themes/own-1.json`, same fields as `assets/themes/*.json`, three gradient colors) and opens the editor directly; the name is generated as “Eigenes Theme 1” |
| Color in the editor | Tapping “Akzent” opens the slider dialog; dragging the hue slider applies the value immediately (`#C04DFF`, field pixel of the color field (192,77,255)) and writes it to the file after a short pause |
| Color applies app-wide | After setting `#FF4DB2`, the Player page carries 3564 pixels in that color, and 5481 even directly after `am force-stop` and restart – so the choice survives a restart |
| Saving shapes | Card corner dragged from 18 to 5: stored as `cardCornerRadius: 5` in the file and preserved after leaving the editor |
| Reset | “Auf Startwert zurücksetzen” restores the state from when the editor was opened (accent `#ED4DFF` → `#FF4DB2`, card corner back to 18, `isDark` back to `true`) |
| Ready-made colors | The color dialog shows two rows of five fixed colors below the color field. Tapping blue sets field and sliders together to `#2E9BFF` (209°, 82 %, 100 %); the home screen then carries 6079 pixels of that color – the same number after `am force-stop` and restart, so the choice is in the file |
| Color row fits | With the new row, the color field, both rows, all three sliders and “Fertig” fit on screen at the same time (“Fertig” at y 1218, no clipping) |
| Duplicate theme | A long press on a bundled tile shows only “Duplizieren und bearbeiten”; a long press on a custom one additionally shows “Bearbeiten” and “Löschen”. Duplicating creates “Eigenes Theme 2” with its own file `own-2.json` |
| Delete theme | The trash can in the editor header asks for confirmation (“‚Eigenes Theme 2' wird entfernt. Danach gilt wieder ‚Dark Void'”), the file `own-2.json` disappears, the app falls back to “Dark Void” (accent back to `#FFB74D`), and a short message confirms it |
| Bottom row reachable | In the editor the last row (“Auf Startwert zurücksetzen”) is fully visible and tappable (y 1296–1334); the list gets generous bottom padding for this, and lists on pages with a header use `weight(1f)` to take exactly the remaining space instead of `fillMaxSize()` |
| Tool row | Below the transport row only the buttons that match the source are shown; labels fit on one line without wrapping, and the label is tappable too |
| Sleep timer remaining | While the timer runs, a pill below the tool row shows “Sleep-Timer läuft noch 24:49” and visibly counts down (24:56 → 24:49 in six seconds); tapping it reopens the dialog |
| Custom timer time | Typed “25” into the field and pressed “Starten”: the pill runs with 24:49, the button shows “25 Min”; “Starten” stays greyed out while the field is empty |
| Timer presets | Chips from 5 to 90 min and “Aus” work as before; after “Aus” the pill disappears and the button reads “Timer” again |
| Favorite in the player | Tapping the label (not just the circle) switches to the filled heart, the announcement changes from “Zu Favoriten” to “Aus Favoriten entfernen”; afterwards the track is under Library → Favorites (“1 Favoriten”) |
| Queue/equalizer | Open the respective page (“Warteschlange 2 von 3” or “Equalizer aktiv”) |
| Foreign source | Only the sleep timer remains; favorite, queue and equalizer disappear entirely because they depend on the app's own library or audio session (shuffle/repeat greyed out as before) |
| Player settings | New page with playback, library (three rows) and foreign playback; the “Zugriff auf Audiodateien” row appears only without permission |
| Settings | Only appearance, home screen and about; the “Einrichtung” section appears only when music or notification access is missing |
| Rename | Package `com.mp3roid.player`, label and all in-app texts “MP3roid”, classes, resources and project folder renamed; installed on API 29 and re-checked in all points above |
| Optimized beta build | Release APK with R8 (4.3 MB instead of 40 MB) installed and checked: onboarding, themes from the JSON files, foreign source with title and controls, local playback (`state=3`), tool row adapts, no crash in the log |
| App list current without action | Removed “Play Movies & TV” via `pm uninstall --user 0`: counter 20 → 19, entry disappears; reinstalled via `pm install-existing`: 19 → 20, entry is back – both without “App-Liste erneuern” |
| Notch band fixed | With the simulated notch, the top 81 px lay in a black system band (0,0,0 instead of app background 17,17,25). After `shortEdges` plus inner padding with visible bars, the app starts at pixel 0 with its own background; with visible bars the clock sits over the app background and “Einstellungen” starts only below it (y=128 instead of 47, where the header would previously have disappeared behind the status bar). Debug and release build checked |
| Icons corrected | “Vorheriger” sat 6 units too low (triangle tip at y=24 instead of 12, running out of the icon box at the bottom); the timer icon was flattened at the top right and had a detached foot. “Vorheriger” is now the exact mirror image of “Nächster” and the timer is a round stopwatch – in the pixel comparison before/after, in the debug and release build |
| App menu / uninstall | Long press on “Fremdplayer”: dialog with Open, App info, Uninstall. “Deinstallieren” opens the system confirmation (`com.google.android.packageinstaller/UninstallerActivity` with “Do you want to uninstall this app?”), OK removes the package; MP3roid's counter goes from 20 to 19 on its own, reinstalling back to 20. Debug and release build checked, then the test app was reinstalled |
| Limit with system apps | “Play Movies & TV” *cannot* be removed via the dialog: the log says `Attempt to delete unknown system package` / `DELETE_FAILED_INTERNAL_ERROR`. That is a system limitation (the same app could be removed via `pm uninstall --user 0`), not the app – normal apps go through |
| Wallpaper on/off | Switch “Hintergrundbild vom Handy” (wallpaper from phone; Settings → Wallpaper): when on, the system image lies behind the UI and the mean color in the test area (y 400–470) jumps from (22, 22, 30) to (154, 103, 127); when off, the pixels there are exactly as before (0 differences) |
| Only in the gaps | Cards, control panel and bottom bar remain unchanged: the home screen's element tree is character-for-character identical with and without the image (0 differences, 720 × 1438) |
| Dimming | Slider dragged from 35 % to 79 %: the image's mean brightness falls from 121.1 to 45.5; at 10 % it rises again (red channel 208.9 instead of 56.0). The value is then stored as `wallpaper_dim` |
| Blurring | Recomputed: the image is scaled down to half resolution and smoothed with three passes of a box filter – no longer squeezed to 128 px and scaled up 15×. The display matches the model “half resolution + box filter” at 0.9975 (residual error 1.6); the old version matched its own model (128 px, sampled) at 0.966 (residual error 1.9) and was far from the new result (residual error 8.7). On/off/on yields the same image pixel for pixel (0 differences). The blur corresponds to about 5 pixels on screen |
| Image crop correct | Test wallpaper with a grid every 100 px (set as the file `/data/system/users/0/wallpaper`, blur off, dimming 0 %): the display's grid lines are at 0, 100, 200 … 700 – the **left** edge of the original, as Android's wallpaper offset 0.0/0.5 specifies and as the lock screen shows it (the evaluation reports the edge of the line, hence the numbers 1, 99, 199 … 699). Comparing the image strip (y 865–905) against all candidate windows on the 100-px grid: left correlation 1.000, all others at most 0.39. Horizontal and vertical lines both have a spacing of 100 px – no zooming, no distortion |
| Sharp image, pixel-exact | With blur off, the strip without controls matches the original (correlation 1.000; edge energy display 2.35 = original 2.35, at 0 % dimming). Loading happens at window size (`WindowManager.getDefaultDisplay().getRealSize()`, 1600 × 1600) instead of via `displayMetrics` (1473 px), which would be smaller and would have triggered upscaling |
| Large image | With a 3600 × 2400 image as wallpaper (phone-photo size) the grid lines sit exactly again: it is downscaled to 2400 × 1600 (log line `Quelle=3600x2400 Ziel=2400x1600`), the source lines (every 150 px) appear vertically at 100, 200 … 700 and horizontally at 100, 200 … 1400 – a spacing of 100 px in both directions, so evenly scaled, no crash and no `OutOfMemoryError`. The halving-step downscale therefore also works when the image is larger than the screen. The upper limit remains 4 million pixels (16 MB) |
| Crop on the real device | On the test device against the system wallpaper (beta 10): lock screen crop x 0–720, scale 1.000, correlation 0.805; MP3roid likewise x 0–720, scale 1.000, correlation 0.563, while the old centered crop x 440–1160 scores −0.069. Same left edge, same scale – nothing squeezed, nothing shifted |
| Change wallpaper without restart | Changed the wallpaper in the background (live preview “Marvelous Marble” confirmed) and returned to MP3roid: the image is new (71,812 differing pixels) without the app having been terminated |
| Live wallpaper | With a live wallpaper set, the system on API 29 returns a still image; MP3roid displays it – no crash, no error message, no crash loop |
| Setting persists | After `am force-stop` and restart, the switch is on and the image is visible again at 79 % dimming |
| No new permission | Reading uses `READ_EXTERNAL_STORAGE`, which MP3roid already has for music – the APK's permission list is unchanged (`aapt2 dump badging`) |
| Error path covered | The paths “no image / animation / locked image / too large (also `OutOfMemoryError`)” are covered and show a hint in Settings instead of crashing; in the emulator they cannot be triggered because the system always returns a fallback image. From Android 13 the system only returns the bundled image, from Android 14 it always throws `SecurityException` – that is what the hint is for |

Three bugs were found and fixed along the way:

1. Foreign playback could not be controlled. The commands looked up the session
   by package name, but the entries were stored under the session token – every
   command went nowhere.
2. Apps with an open but empty session (no title, no state) appeared as a
   selectable source. Such entries are now filtered out.
3. “Deinstallieren” in the app menu did nothing. Since Android 10 only an app
   with `REQUEST_DELETE_PACKAGES` may open the system confirmation; the
   permission was missing from the manifest, and the `runCatching` around
   `startActivity` swallowed the SecurityException. Now the permission is in
   the manifest, and a failure is shown as a short message instead of silently
   disappearing.

No permanent guarantee: the verification applies to these builds in the
emulator. On real hardware, audio effects, headphone buttons and the lock screen
come on top – those can only be judged on the device.

## 9. Building a beta APK for your own phone

```bash
cd MP3roid
ANDROID_HOME=$ANDROID_HOME \
  ./gradlew :app:assembleRelease --no-daemon
```

The result is in `app/build/outputs/apk/release/app-release.apk`. A copy for
sharing is created under `dist/MP3roid-<version>.apk`.

Signing requires your own key: `mp3roid-beta.jks` and `keystore.properties` live
in the project folder and are listed in `.gitignore`. **Back up both files
separately** – without them no new beta can be installed over the installed
app, and Android refuses the update with a different key. A new build also
needs a higher `versionCode` (`app/build.gradle.kts`, 1, 2, 3 …), otherwise
Android rejects the installation as a downgrade.

The file gets onto the phone via cable

```bash
adb install -r dist/MP3roid-1.0-beta10.apk
```

or by copying it to the device and tapping it there (allow installation from
unknown sources). On first launch the onboarding runs: set MP3roid as the home
screen, allow music access and notification access. The room for error messages
is the same as in the emulator – the app is a private copy, not a Play Store
upload.

## 10. Beta 11 – editing albums and titles (18 Oct 2026)

### What's new
- **Album long press** (album tile in the library or the large cover in the
  album view): dialog “Album bearbeiten” (edit album) with a text field for the
  album name and the buttons “Cover wählen” (choose cover, system image picker)
  and “Cover entfernen” (remove cover). A long press on the large cover area in
  the album view opens the same dialog.
- **Track long press** on every track row (library → tracks, inside albums,
  artists, folders, playlists, favorites): the familiar action menu gets the
  row “Titel bearbeiten” (edit title), which opens the “Titel bearbeiten”
  dialog.
- **MP3roid only:** names and covers apply only inside MP3roid. The files and
  MediaStore entries stay untouched (Android 10 does not allow rewriting
  foreign tags anyway). The dialog notes: “Gilt nur in MP3roid – die Datei
  bleibt unverändert.” (applies only in MP3roid – the file stays unchanged).
- **Storage:** `TitleOverrides` (`filesDir/overrides.json` plus
  `filesDir/covers/<albumId>.jpg`). On import, covers are downscaled to at most
  1024 px and stored as JPEG. The library applies the names when reading
  (`LibraryRepository`), and the cover loader checks the app's own folder first
  (`ArtworkLoader`, the `LruCache` is cleared on save so all visible covers
  reload).
- **Reset:** “Originalen Namen wiederherstellen” (restore original name)
  removes the entry; “Cover entfernen” deletes the app's own image. Both
  dialogs close afterwards.
- **No new permission**, no new permission. versionCode 11, versionName
  `1.0-beta11`.

### Verified on the real device (test device, Android 10)
- Album long press on the tile opens “Album bearbeiten” with the prefilled name
  and cover preview; saving changes the name on the tile (“Recordings” →
  “Stimme”) and in the album view.
- Track long press shows the action menu with the new row “Titel bearbeiten”;
  renaming (“20261018_151312” → “Demo”) is shown in the track list.
- Reset restores the original name (checked for both track and album).
- Cover choice from the system image picker (“Zuletzt verwendet”) changes the
  cover visibly (pixel comparison before/after: mean difference 69.4 instead
  of 0); afterwards “Cover entfernen” appears, and after removing it the tile
  falls back to the placeholder (difference 6.8).
- The changes survive destroying and recreating the activity (the tab was
  recreated several times during the tests) – `overrides.json` is written.
- No crash in the log; `aapt2 dump badging` confirms versionCode 11,
  versionName 1.0-beta11, package `com.mp3roid.player`, label “MP3roid”.
- **Files and MediaStore untouched:** mtimes and sizes of the three recordings
  unchanged (15:12, 15:13, 15:15); the MediaStore title column still shows the
  original names (`20261018_…`), while MP3roid shows its own names. No new
  permission in the APK.

### Note on the test environment
During the round, three new recordings were indexed (`Music/Recordings`, album
“Recordings”, album_id 1). Two of them are shorter than 20 s and therefore stay
hidden as configured – the 20-s limit still applies. Fossify Voice Recorder
initially created no MediaStore entries for the new files; a
`MEDIA_SCANNER_SCAN_DIRECTORY` on the folder indexed them afterwards. That is a
quirk of the recorder, not of MP3roid. At the end the app itself was restored to
its original state (album name “Recordings”, no custom cover file, original
title) – the user can try the feature themselves.

### Beta 12 – recordings appear automatically (18 Oct 2026)
- **Problem:** Fossify Voice Recorder (targetSdk 36) writes files to
  `Music/Recordings` on Android 10 but creates no MediaStore entry; MP3roid
  therefore did not see the recordings (or only after a manual
  `MEDIA_SCANNER_SCAN_DIRECTORY`).
- **Fix:** `LibraryRepository.query()` calls
  `MediaScannerConnection.scanFile()` on the music folder before the MediaStore
  query and waits briefly (3 s) so newly added files show up in MediaStore.
- **Verified on the device:** file `fs_only_9k7.m4a` (not in MediaStore) was
  indexed automatically after app start and shown in the track list (“4
  tracks” instead of “3 tracks”); MediaStore contains it afterwards. No crash.
- versionCode 12, versionName `1.0-beta12`.

### Beta 14 – deleting files from the device (18 Oct 2026)
- **New:** track long press → the action menu gets the row “Löschen” (delete)
  with a confirmation dialog (“Titel löschen? … kann nicht rückgängig gemacht
  werden” – delete track? cannot be undone). Files not owned by the app
  trigger Android 10's system consent dialog (RecoverableSecurityException);
  `DeleteConsentHandler` starts it. After deleting: favorite, playlists, custom
  names/covers are cleaned up and the library is re-read.
- **Verified on the device:** test file `test_delete.m4a` (a copy of a
  recording) was fully removed from the device (file gone, MediaStore entry
  gone, track list reduced from 3 to 2). No crash.
- versionCode 14, versionName `1.0-beta14`.
