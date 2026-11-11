# Miowser 0.1.80 (code 81)

- Subtitle hold-to-preview now waits at least 750 ms and uses the platform's
  long-press gesture recognition. Short presses and swipes no longer start an
  immediate preview; movement that becomes scrolling cancels the hold. Longer
  system accessibility hold durations remain respected.
- Assisted Delay preserves the current playback state. Opening, applying or
  cancelling the tool no longer pauses or resumes the video automatically.
- Subtitle headers in both players include compact 10-second rewind,
  play/pause and 10-second forward controls, within the existing header height.
  Seeking is disabled when the current stream does not support it.
- Assisted selections and held previews sample the current video time, including
  after seeking. A held preview keeps its sampled value until release; applying
  a delay still requires the confirmation tick.

Device checks are left to you:

1. In both players, enable Assisted Delay during playback. The video should keep
   playing. Repeat while already paused; it should stay paused.
2. Swipe the subtitle list quickly and slowly. The field should not flash a held
   preview. Hold a stationary row for at least 750 ms, then release: preview
   should end without applying the delay or changing the pending selection.
3. Use the header controls to pause, resume and seek in each direction. Check
   seeking near the beginning/end and a stream without seek support. The
   subtitle panel must stay open and the header must not grow taller.
4. Select matching lines while playback advances and after a seek. The proposed
   delay should use that moment's playback time; only the tick saves it.
5. Manually pause or play while Assisted Delay is open, then apply or cancel.
   Your selected playback state should remain unchanged.
