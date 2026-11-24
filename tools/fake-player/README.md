# Counterpart for testing foreign playback

A small Android app that deliberately does what Spotify & co. do: it plays a
file from MediaStore and opens a real `android.media.session.MediaSession` for
it – with title, artist, album, artwork and a set of buttons.

This makes it possible to test the part of MP3roid that cannot be tested
without a second app: detecting foreign playback, switching sources, controlling
it from MP3roid and honestly hiding the seek bar.

```bash
cd tools/fake-player
ANDROID_HOME=$ANDROID_HOME \
  ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.example.fakeplayer android.permission.READ_EXTERNAL_STORAGE
adb shell am start -n com.example.fakeplayer/.MainActivity
```

The **Spulen erlauben** (allow seeking) button toggles `ACTION_SEEK_TO` on and
off. MP3roid must react to it: with permission a draggable bar, without it a
plain progress bar with the note “Diese App erlaubt kein Spulen” (this app does
not allow seeking).
