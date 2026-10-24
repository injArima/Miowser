# Player audio-focus change

Date: 2026-10-12.

The shared player factory now passes `handleAudioFocus = false` to `setAudioAttributes`, for both local and remote playback. Media3 no longer requests audio focus or automatically pauses, suppresses or ducks this player in response to focus changes. This reverses the automatic handling introduced by P2 on October 9.

This disables focus management rather than guaranteeing permanent ownership of Android audio focus. Device audio routing and system policies remain platform-controlled. Android documents its focus/call handling in [Manage audio focus](https://developer.android.com/media/optimize/audio-focus). App-lifecycle pause/resume and headphone-disconnect handling are separate from this setting.

## Local verification

- 489 JVM tests passed; zero failures, errors or skips.
- Debug lint: zero errors, 81 warnings and 5 hints.
- Debug APK built successfully; scoped whitespace check passed.
- Source comparison confirms only the audio-focus flag and its explanatory comment changed in the shared factory.

No device/emulator, ADB or instrumentation checks were run. The existing suite was used; no test was added just to mirror the boolean setting. Local evidence: `G:/Projects/Miowser/.local/optimization/audio-focus-2026-10-12/`.

## APK

[Install the audio-focus debug APK](G:/Projects/Miowser/Miowser-0.1.0-audio-focus-debug.apk).

SHA-256: `24af9f399db099bee926dbac4afd6223ae8fc7906571b6e2a50bf39ecf113500`.

## Device checklist

- [ ] Start a local video while a call is already active, with Miowser in the foreground. Check the play button, audio, video and subtitles.
- [ ] Repeat with remote MP4, HLS and DASH, including pause/resume and seeking during the call.
- [ ] Receive and end a call during playback. Distinguish a focus interruption from the call screen taking Miowser out of the foreground; check behavior after returning to the player.
- [ ] Trigger notification sounds and another foreground audio source. Check that the player itself does not automatically pause or lower its volume on focus changes.
- [ ] Test speaker, wired headphones and Bluetooth routes. Check call/video volume and playback after returning to the player; disconnection still follows its separate pause policy.
- [ ] Recheck ordinary playback, buffering, subtitles, manual pause/resume and closing/reopening the player.
