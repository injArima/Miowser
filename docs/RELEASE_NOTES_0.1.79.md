# Miowser 0.1.79 (code 80)

- Subtitle delay controls now expand beside the gear button, using a compact
  number field and moving Assisted Delay along the same toolbar. Controls wrap
  on smaller screens rather than stretching the field across the panel.
- The subtitle header shows the current video playback time. Follow Playback
  moves to the far right of this header, separate from timing adjustments.
- Assisted Delay now opens the same delay editor. Tap different subtitle lines
  to update the proposed delay, then use the tick to apply it. Selecting a line
  or releasing a held preview no longer saves the delay or exits the mode.
- Assisted Delay help opens in a message bubble from its info button. It
  dismisses automatically or when tapped outside. The cross discards the draft;
  applying or cancelling restores the previous playback/pause behavior.

Device checks are left to you:

1. In both players, open the gear and check the compact inline controls in
   landscape and portrait. Apply a manual delay and reset a nonzero value.
2. Check the video clock beside the subtitle name, including while playing,
   paused and after seeking. Scroll the cues and use the header's refresh icon
   to resume following. It should not change the subtitle delay.
3. Open Assisted Delay and tap several different lines. The field should update
   each time while playback stays paused and the saved delay remains unchanged.
4. Hold a different line to preview its delay. Release it: the previously chosen
   draft should return, without applying anything. Use the tick to save it.
5. Check help, outside dismissal, Android Back and the cancel cross. Cancel must
   keep the previous delay, and resume only if playback was running beforehand.
