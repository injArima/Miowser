# Step 2: lint blockers and compatibility fixes

Date: 2026-09-25. Implementation and automated checks passed. Older-device runtime verification remains open.

## Changes

- Thumbnail eviction uses `removeAt(lastIndex)` instead of the API 35 `List.removeLast` method. Cache size/order policies are unchanged.
- The three private MediaStore helpers declare API 29 requirements. Their two public entry points retain the existing API 29 branches and legacy-storage fallback. This makes the existing boundary visible to lint rather than suppressing API checks.
- Four narrowly scoped AndroidX opt-in annotations cover Media3 surface access, resize constants, track-group identity, and bitrate metadata. The existing Media3 version is unchanged; these call sites must be reviewed on upgrades.
- Camera, autofocus, microphone, location, and network-location hardware are explicitly optional. Site permission requests remain permission-gated; devices lacking these capabilities can still install the browser.
- Document-start JavaScript calls now sit inside positive WebView capability checks. Unsupported providers still log the same degraded-feature message. This resolves both feature-check warnings without suppressions.

No broad lint baseline, global suppression, dependency upgrade, data migration, or unrelated UI edit was added.

## Automated evidence

Command: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue --offline`, using Android Studio JBR 25.0.3.

| Check | Result |
|---|---|
| Compilation / debug APK | Passed |
| Unit suite | Executed after source changes; 302 tests, zero failures/errors/skips |
| Android lint | Passed: **0 errors, 72 warnings, 3 hints**, down from 14 errors and 74 warnings |
| Compiled cache bytecode | Calls `java.util.List.remove(int)`; no `java.util.List.removeLast` call |
| Merged debug manifest | All five added hardware features have `required=false` |
| Whitespace/diff validation | Passed |
| APK SHA-256 | `5F9AB4BE6679656F7B1C5E3F78E81084CB97590C6839F465E4A25B05669AB3D5` |

Raw reports, APK, build log, and bytecode evidence are in the ignored [Step 2 evidence directory](G:/Projects/Miowser/.local/optimization/evidence/step02).

## Remaining warning disposition

All remaining categories were reviewed. Retention here is not a blanket suppression or a claim that every warning is harmless.

| Rule | Count | Category | Disposition |
|---|---:|---|---|
| ApplySharedPref | 12 warnings | Performance/durability | Step 4 ordered persistence; retain explicit recovery/privacy durability boundaries |
| MissingOnRenderProcessGone | 2 warnings | Correctness | Step 7 must add renderer recovery; same underlying client appears twice |
| GradleDependency | 9 warnings | Dependency maintenance | Step 17 compatibility review; do not auto-upgrade while clearing lint |
| AndroidGradlePluginVersion | 2 warnings | Build tooling | Step 17 controlled tooling review |
| NewerVersionAvailable | 1 warning | Build tooling | Step 17 Kotlin/toolchain review |
| ObsoleteSdkInt | 4 warnings | Compatibility cleanup | Review during Step 15 cleanup; leave guards until their callers and support range are checked |
| UseKtx | 38 warnings | Style | Opportunistic cleanup only; no demonstrated performance defect from these suggestions |
| ModifierParameter | 2 warnings | Compose conventions | Step 13 component-boundary cleanup |
| UseOfNonLambdaOffsetOverload | 1 warning | Compose performance | Step 13: inspect moving UI state and use deferred reads where warranted |
| UnusedResources | 1 warning | Cleanup | Step 15: verify references before removal |
| AutoboxingStateCreation | 3 hints | Allocation efficiency | Step 13: use primitive state where appropriate during the measured UI pass |

Totals: 72 warnings and 3 hints. Renderer recovery remains a release blocker assigned to its own implementation step; passing lint alone does not establish ship readiness.

## Runtime verification

The user's `Medium_Phone` emulator (`emulator-5556`) is API 37 and has an active download foreground service. It was inspected read-only and was not upgraded, restarted, or cleared. Smoke testing uses the separate disposable `emulator-5580` from Step 1.

The updated APK installed successfully on the disposable emulator and MainActivity resumed. A settled screenshot was visually checked: the empty Library, folder controls, bottom navigation, and browser action all rendered. Launch output and `hub.png` are archived with the Step 2 evidence. This is a debug launch smoke check, not a performance measurement or download regression test.

Only the API 37.1 image is installed locally. The API-below-35 thumbnail-eviction runtime check cannot be established on either current emulator. Bytecode inspection and lint verify removal of the incompatible call, but do not replace that device check.

Remaining focused checklist (project owner or a later compatible test device):

- On API 29–34, accumulate more than 30 distinct cached tab previews, then reopen Tabs and switch among retained tabs. Confirm cache eviction causes no crash and recent previews remain usable.
- Download a small known file to the ordinary public Downloads path and open it. The storage implementation was not changed beyond API annotations, but its runtime regression check remains to be recorded.
- API 26–28 storage testing remains deferred under the earlier project decision; this change does not claim it passed or change minimum SDK.

Step 2 remains **Awaiting device verification** for those checks; all requested source fixes and automated lint triage are implemented.
