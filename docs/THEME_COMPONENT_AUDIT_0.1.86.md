# Color-family component audit — 0.1.86

The previous change reassigned Night's input swatches but left old component
assignments in place. It also mixed the neutral source with 82% gray (68% for
variants), so navy did not remain visibly navy. This update corrects both.

The contract is 1 Primary for main actions/highlights/progress; 2 Secondary for
tonal actions and selected controls; 3 Tertiary for complementary accents; and
4 Neutral for backgrounds, cards, body text, and borders. Filled-control text
uses a contrasting neutral tone. Delete/error semantics remain separate.

| Area / component | Previous mismatch | Corrected assignment |
| --- | --- | --- |
| All 14 custom schemes | Neutral source mostly replaced by gray; elevated surfaces tinted with primary | Direct neutral-derived surfaces/text/borders and neutral surface tint |
| Text on filled primary/secondary/tertiary controls | Foreground derived from the accent source | Contrasting neutral-family foreground |
| Hub bottom navigation | Primary selected pill | Secondary selected pill and neutral text |
| Downloads Downloading/Downloaded toggle | Material default checked color | Central secondary checked fill |
| Theme gallery | Primary selected border with neutral fill | Secondary selected fill, neutral border/text |
| Browser tab-mode selectors | Primary checked fill | Secondary checked fill |
| Browser tab cards | Primary active card background and border | Neutral card background/border with a secondary selected footer; tab-mode control also uses secondary |
| Browser speed-dial tiles | Primary normal background and tertiary selection | Neutral normal background, secondary selection, neutral border/text |
| Settings, site controls, ad blocking, browsing-data selection, media directories, browser menu | Switch/checkbox/radio defaults could use primary | Central `AppSelectionColors` uses secondary for selection and neutral for boundaries/text |
| Library video thumbnail placeholders | Primary-to-tertiary gradient | Neutral surface gradient; primary playback action remains |
| Library health summary | Primary/tertiary full-card fill | Neutral card |
| Library header/category icon backgrounds; onboarding icon background | Accent-family surface fills | Neutral backgrounds with complementary tertiary icons |
| History site placeholders and headings | Rotating accent backgrounds and primary ordinary text | Neutral backgrounds, text, and borders |
| Address-bar background | Secondary background in private mode | Neutral background, complementary private-mode icon |
| Detector metadata/type labels | Secondary type surface and primary resolution text | Neutral metadata surfaces/text; selected filter stays secondary |
| Subtitle appearance preset/font selection | Primary selected fill | Secondary selected fill; actual user-picked subtitle colors are unchanged |
| Multi-connection download progress | Primary, secondary, tertiary, and error chunks | Primary progress color; gaps distinguish connections |

Chip and tonal-button defaults already use secondary-family fills. Shared main
action buttons, new-tab/browser/play actions, loading indicators, cursors, and
progress continue to use primary. Decorative lock/category icons can use
tertiary. Website content, media artwork, user subtitle colors, fixed HTTPS
status colors, and the fixed video-overlay palette are separate contracts.

Night keeps the requested sources: 1 `#ABD2FA`, 2 `#7692FF`, 3 `#1B2CC1`, and
4 `#091540`. Light mode uses pale-blue neutrals; Dark mode uses navy neutrals.
AMOLED keeps its black/gray surface override.

The JVM checks cover all palette foreground/outline contrast and guard Night's
neutral-family surfaces, text, and borders against gray or accent substitution.
On-device testing remains with the user.
