# Miowser 0.1.78 (code 79)

- Redesigned the shared subtitle inspector in both players. The selected track
  name appears in the header, with more room for subtitle lines underneath.
- Subtitle lines use Material 3 Expressive segmented rows, with timestamps to
  the left and a highlighted currently playing line.
- Playback following keeps the current line in view, including after seeks.
  Touching or manually scrolling the list pauses following; the refresh button
  resumes it explicitly.
- Subtitle delay opens from the Subtitles Gear button. Apply a typed value or
  use 50 ms adjustments; applying or resetting closes the editor. Reset appears
  only when a delay needs clearing.
- Assisted Delay replaces the old timing action and toast. Instructions and
  delay previews appear inline, with a close button to cancel. Selecting the
  matching line applies its offset and closes the tool.

Device checks are left to you:

1. Open embedded and detected subtitle tracks in both players, in portrait and
   landscape. Check long names, multi-line cues and large system font sizes.
2. Play, pause and seek across subtitle lines and gaps. The matching line should
   stay visible; drag or tap the list to pause following, then use refresh.
3. Open the gear, enter positive and negative delays, use +/- and apply. Reopen
   it and reset a nonzero delay. Back should dismiss uncommitted edits.
4. Start Assisted Delay while playing and while paused. Hold a line to preview
   its offset, tap to apply, and try cancelling. Playback should resume only if
   it was playing before entering the tool.
5. Check image-based subtitle previews and website-native caption selection.
