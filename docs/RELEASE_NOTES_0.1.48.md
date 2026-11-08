# Miowser 0.1.48 (code 49)

- Fixed pull-to-refresh feedback continuing after an abandoned or interrupted swipe. During a pull, the contained Expressive indicator follows gesture progress instead of looping; it loops only when a refresh is committed.
- Reduced the pull-to-refresh indicator to a centralized 40dp size.
- Added direct mute/unmute controls to the top bar of both players. The rich player restores its previous audio level when unmuted; its volume gesture also unmutes playback.
- Replaced the speed-option list in both players with a shared slider, 0.05× minus/plus adjustments, and a horizontally scrollable row of preset chips. The range is 0.25×–3×.
- Speed controls remain open while adjusting. Slider changes are applied on release, limiting repeated commands to the playback engine.
- Top-bar actions scroll horizontally when necessary to keep controls reachable on narrow screens.

Debug and release Kotlin compilation passed. No tests or device checks were run;
testing is left to the user.

Suggested checks:

1. Pull partway and hold still: the indicator should hold its shape instead of looping. Release below the refresh threshold: it should disappear without reloading.
2. Interrupt a pull by switching tabs, opening another surface, or leaving the app. Return and try another pull: no stuck indicator, and refresh should still work.
3. Pull past the threshold and release: the smaller contained indicator should animate until page loading finishes. Ordinary URL navigation should retain its existing address-bar indicator.
4. In both players, mute and unmute from the top bar. Check audio-only mode, playback controls, and volume gestures still work.
5. Drag the speed slider, tap ± repeatedly, and choose presets. Check the value and actual playback rate, bounds, and outside-tap dismissal. In WebView playback, also check a site that rejects a requested speed.
6. Check narrow portrait and landscape layouts: top-bar controls and preset chips should remain reachable by horizontal scrolling.
