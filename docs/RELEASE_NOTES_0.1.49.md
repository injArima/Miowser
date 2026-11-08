# Miowser 0.1.49 (code 50)

- Removed playback-speed presets from both players.
- Switched to the Material 3 horizontal centered slider, with no stop or tick dots.
- Increased slider and minus/plus precision to 0.01× within the existing 0.25×–3× range.
- Added an exact-speed field with an Apply check button and keyboard Done action. Decimal dots and commas are accepted; invalid or out-of-range input is not applied. Values are rounded to two decimal places.
- Holding minus or plus repeats adjustments and accelerates over time. Releasing, cancelling, closing the panel, or reaching a range limit stops repetition.
- Kept the controls shared between rich and WebView playback, with centralized sizing, precision and repeat timing.

Debug and release Kotlin compilation passed. No tests or device checks were run;
testing remains with the user.

Suggested checks in both players:

1. Drag the centered slider; verify there are no dots and the applied value matches on release.
2. Enter an exact value such as 1.37, apply it using the check button and keyboard Done, and verify actual playback speed. Try a decimal comma too.
3. Try empty, invalid and out-of-range input. The playback rate should remain unchanged.
4. Tap ± once: verify a 0.01× change. Hold: verify repetition accelerates and stops on release or cancellation, without an extra release click.
5. Hold to each range limit, close the panel while adjusting, and reopen it. Verify the value stays within 0.25×–3× and no repeat continues in the background.
6. Check portrait/landscape layouts, keyboard visibility, and a website that rejects a requested playback speed.
