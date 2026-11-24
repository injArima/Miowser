# Miowser 0.2.03 (code 98)

- Website permission prompts now name the requesting origin and exact capability, including camera, microphone, protected content/DRM, and MIDI device control.
- Removed the camera/microphone-only restriction. Protected-media and MIDI requests can be allowed after two-step confirmation, either once or remembered for that origin. Private tabs do not save decisions.
- Unfamiliar WebView resources show their actual identifier and an explicit two-step Allow once path rather than the generic hard block. Invalid requests and insecure origins explain why access is unavailable.
- Remembered decisions are honored per resource; allowing one capability cannot also grant another blocked capability. Android permissions are requested only for reviewed camera/microphone access.
- Completed permission requests are released immediately, and canceled requests cannot be granted later. This avoids retaining a growing request trail in long-lived tabs.
- Performed a short ARY Plus desktop page/network check; see `ARYPLUS_PERMISSION_CHECK_0.2.03.md` for findings and limits.

DRM licensing and device support still depend on the website and Android WebView. Device playback testing is left to you.
