# Step 1: development checkpoint and test environment

Recorded 2026-09-25 (Asia/Calcutta). This records the starting point for the optimization work; it does not certify release readiness.

## Source checkpoint

Base revision: `f5a975ddc21c5913da622483f0229d5ffe5e0159`.

The existing working-tree edits to FailedVideoLogSheet, HubHomePage, HubVideoCard, LibraryFolderUi, ExpressiveSurfaces, and design-inspiration were preserved. The three untracked planning documents were included. No commit or reset was performed.

Initial checkpoint: [20260924T221958188Z](G:/Projects/Miowser/.local/optimization/checkpoints/20260924T221958188Z).

| Identifier | Value |
|---|---|
| Current-source snapshot | 206 copied files, each verified against the working-tree SHA-256 |
| Combined working-tree patch SHA-256 | `6E2527625E25888C383732F544EC00FB85194E33177E6CA2DF10EFE396E51112` |
| Source manifest SHA-256 | `46753F53CB4B380B76A81174EE13C92FDD8BA52A7F20203021274E8AB56C6824` |
| Patch validation | Reverse apply check passed; no patch was applied |

The checkpoint contains a base-revision source archive, combined patch, separate staged patch, working-tree inventory, full current source copy, file manifest, and metadata. Raw captures, local credentials, Gradle caches, and device user data are excluded. This checkpoint includes the first checkpoint script and ignored-evidence rule; it predates the remaining Step 1 documentation and emulator helper. The application source was not changed afterward by this step.

Run [New-OptimizationCheckpoint.ps1](G:/Projects/Miowser/scripts/New-OptimizationCheckpoint.ps1) from PowerShell to create another uniquely named checkpoint:

```powershell
Set-Location -LiteralPath 'G:\Projects\Miowser'
& ./scripts/New-OptimizationCheckpoint.ps1
```

Only folders containing `checkpoint.json` represent completed checkpoints. To recover, inspect the manifest and copy the `source` snapshot to a separate review directory, or reconstruct from the base archive plus patch and untracked source copies. Verify hashes there before selectively restoring anything to the active project. Never apply a blanket reset to recover an experiment.

## Development tools

- Android Studio was asked to open `G:\Projects\Miowser` using the installed launcher. No IDE UI state was verified.
- CLI Gradle runtime verified: Android Studio JBR/OpenJDK **25.0.3**, at `C:\Program Files\Android\Android Studio\jbr`.
- Android Studio project configuration selects `#GRADLE_LOCAL_JAVA_HOME`; its local setting points to installed Temurin **25.0.3+9**. The CLI baseline uses JBR explicitly, so future CLI comparisons should keep that runtime.
- Gradle wrapper: **9.7.0**; Android Gradle Plugin: **9.3.1**.
- Android SDK: `C:\Users\Inja\AppData\Local\Android\Sdk`.
- App: **0.1.0 / versionCode 1**, minimum API **26**, target/compile API **37**.
- Baseline artifact: debug, unminified; no app-owned Baseline Profile. This is a correctness checkpoint, not a release performance baseline.

## Build evidence

Executed `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue --offline` with the verified JBR. `--continue` allowed APK assembly despite the known lint errors.

| Check | Result |
|---|---|
| Debug compilation / APK assembly | Passed, up-to-date tasks reused |
| Unit tests | Up-to-date task reused; archived XML contains 302 tests, 0 failures/errors/skips; fresh execution was verified in the preceding audit |
| Lint analysis | Ran; 14 errors, 74 warnings, 3 hints, matching the prior audit |
| Combined Gradle exit | Failed because lint errors remain; not a clean release check |
| Debug APK SHA-256 | `A9559D7D7FBFE4ED1B5AB43CD3709343AE46A16D2E3605EC238CF35E574857E6` |

The APK, build log, lint HTML/text report, unit-test XML and summary, and runtime version are archived in [local Step 1 evidence](G:/Projects/Miowser/.local/optimization/evidence/step01). Step 2 owns lint fixes; this step does not suppress them.

## Disposable device and fixture

Use [Start-OptimizationEmulator.ps1](G:/Projects/Miowser/scripts/Start-OptimizationEmulator.ps1):

```powershell
Set-Location -LiteralPath 'G:\Projects\Miowser'
& ./scripts/Start-OptimizationEmulator.ps1
```

The helper starts a headless device with a project-local AVD home and refuses an already-connected port. It does not wipe or copy another AVD's user data. It requires the already-installed API 37.1 image; it does not download SDK packages. Existing device data is retained on subsequent starts.

| Setting | Value |
|---|---|
| AVD | `Miowser_Optimization_API37_1` |
| Serial | `emulator-5580`; verify AVD identity before any install/reset |
| User-data location | `G:\Projects\Miowser\.local\optimization\avd\Miowser_Optimization_API37_1.avd` |
| System image | Android 37.1, Google APIs PlayStore, x86_64, 16KB page image, revision 9 |
| Emulator | 37.1.11.0 |
| Hardware configuration | Medium phone, 1080×2400, density 420, 4 vCPUs |
| RAM | 2,048 MiB requested; emulator log increases it to 4,096 MiB for this image |
| Refresh | Guest display mode 1: 60.000004 Hz (60 Hz) |
| Graphics | `auto` graphics backend; software-only boot was slow and replaced. Emulator results are unsuitable for physical-device performance claims. |
| Guest identity | `sdk_gphone16k_x86_64`, Android 17, runtime SDK 37; installed image is labeled 37.1 |
| Guest memory | `/proc/meminfo`: 4,008,376 kB total; configured/effective emulator allocation 4,096 MiB |
| WebView version | `com.google.android.webview` 149.0.7827.5 |
| Reference physical phone | Unselected; required in Step 3 |
| Low-memory physical phone | Unselected; this emulator is not a substitute |

Fixture `empty-v1`: fresh installation with zero user Library items, downloads, History, bookmarks, and custom folders; no account sign-in, no imported browsing session, and default app settings. The app may create its default home tab after launch. Do not equate an empty initial fixture with the populated performance datasets planned for Step 3.

After the device is ready, verify identity, install the archived APK on **this serial only**, launch the app, and record its resumed activity plus a local screenshot. This fixture grants notification and debug local-network permissions; camera, microphone, and location were not granted by this setup. The first launch displayed Android's permission controller as expected. After these two grants and a relaunch, the empty Hub was visible.

To repeat installation/launch without resetting existing fixture data:

```powershell
$adbExe = 'C:\Users\Inja\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$identity = & $adbExe -s emulator-5580 emu avd name
if ($identity -notcontains 'Miowser_Optimization_API37_1') { throw 'Wrong test device' }
& $adbExe -s emulator-5580 install -r 'G:\Projects\Miowser\.local\optimization\evidence\step01\baseline-debug.apk'
& $adbExe -s emulator-5580 shell pm grant com.miowser.app android.permission.POST_NOTIFICATIONS
& $adbExe -s emulator-5580 shell pm grant com.miowser.app android.permission.ACCESS_LOCAL_NETWORK
& $adbExe -s emulator-5580 shell am force-stop com.miowser.app
& $adbExe -s emulator-5580 shell am start -W -n com.miowser.app/.MainActivity
```

If a repeat needs a fresh fixture, clear only `com.miowser.app` on the verified disposable AVD before granting permissions and relaunching. This deletes that fixture's app data, so never use an unqualified device command or wipe the user's regular emulator. Raw launch times from this debug emulator are diagnostic only, not the Step 3 performance baseline.

## Scope and ownership

The release scope remains browse → find media → play/download → return through Library. Preserve current folders, Hub navigation, and the player theme. Major feature expansion remains deferred. Distribution channel is undecided. Minimum API remains 26; API 26–28 runtime coverage was previously deferred and must be revisited explicitly before a public support claim.

Engineering owner: Codex for implementation/checks. Device/product acceptance owner: project owner. Step 1 automation establishes a smoke-test environment; hands-on visual/accessibility acceptance remains user-led as recorded in PROJECT_STATUS.

## Evidence handling

`.local/optimization/` is excluded by the project `.gitignore`. It holds checkpoints, emulator data, and raw diagnostic evidence. These are local, potentially sensitive artifacts: Git exclusion prevents accidental staging but is not encryption or an operating-system access-control boundary. Repository documents contain only the summarized results. Existing root screenshots/trace were left in place rather than moved or deleted.

## Device verification result

**Passed.** After switching from software-only rendering to `auto`, the disposable emulator booted (emulator log: 38,501 ms). The archived APK installed successfully. The permission-prepared relaunch returned `Status: ok` and `Activity: com.miowser.app/.MainActivity`; activity inspection confirmed Miowser was resumed. The [captured Hub](G:/Projects/Miowser/.local/optimization/evidence/step01/hub.png) was visually inspected: Library, 0 videos, All/Unfiled, the empty-Library message, browser button, and bottom navigation were present.

UIAutomator hierarchy capture returned a null-root error, so no successful hierarchy capture is claimed; screenshot and resumed-activity evidence establish this launch smoke check. No browsing/import took place. The debug app's preferences directory contained only its WebView preferences at inspection, consistent with an empty user dataset. Populated data, accessibility, performance, and broader runtime behavior remain later-step checks.

The isolated emulator is stopped after verification to release host resources; its fixture data is retained for the next run. The existing regular emulator was not installed to, reset, or stopped. A source-hash comparison found zero application-file changes since the initial checkpoint.
