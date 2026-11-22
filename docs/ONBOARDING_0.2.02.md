# Onboarding guide — 0.2.02

The guide explains the current app without changing settings or starting a
customization session. `settings/OnboardingContent.kt` builds pure text content
from the active `Settings`; `ui/OnboardingGuide.kt` supplies icons and the UI.

## Pages

1. Welcome and mode-specific navigation/startup.
2. Device videos, access, folders, scanning, and local progress, if available.
3. Browser video detection, WebView/Rich players, manual side-pane handoff, and
   website limitations, if the browser is available.
4. Subtitle appearance, supported tracks, inspection, follow playback, and
   Assisted Delay confirmation.
5. Downloads, mode-specific navigation/folder controls, and parallel connections.
6. Online watch catalog, with manual/automatic wording, if enabled.
7. Customize, priorities, modes, history choices, Review/Apply/Cancel, and
   retained data.
8. Master settings, inline search/jump/highlight, backup references, and Help.

Both with history enabled gets eight pages. Browser only omits Device; Player
only omits browser-video and online-catalog pages. Disabled history also omits
the catalog page while customization still explains how to enable it again.

## Navigation and state

Getting started is in Help under More/master Settings, and the existing Browser
settings entry remains. The origin page is saved for Close/Skip/Finish and Back
from the first page. System Back otherwise moves to the previous page. Guide
page state survives configuration changes, and each page has its own scroll
state while composed. The index is clamped to available pages.

`OnboardingStore` is unchanged. Updating the app does not force a guide replay
or alter experience/history preferences. Existing brand text, neutral surfaces,
tertiary decorative icons, primary progress, and shared buttons are retained.
`OnboardingTokens` centralizes marker and spacing dimensions.

## Verification

JVM tests cover all mode/priority/history combinations, topic filtering, manual
history wording, standalone navigation instructions, player enhancements in
Browser only, priority-sensitive startup text, and player-only search access.
The debug build and existing regression suite are run. On-device verification
is left to the user: first run, revisit from each entry, Back/Close/Finish,
rotation, large text, and scrolling on short windows.
