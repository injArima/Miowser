# Miowser audit against Futurice Android best practices

Date: 2026-10-12. Status: **audit and proposed plan; application code unchanged by this audit**.

Deferred at the user's request on October 12. Resume this plan only when requested; the Library cache fix is separate work.

## Summary

Miowser already has sound foundations: Gradle/Kotlin DSL, feature packages, Compose, release shrinking, background persistence, bounded transfers/artwork, explicit player/WebView owners and substantial JVM coverage. The most useful next changes concern connection trust, consistent native request ownership, automated builds and Android integration tests. Large UI controllers and whole-collection persistence are the next maintainability/scaling targets.

Use the [Futurice guide](https://github.com/futurice/android-best-practices) as a checklist, with current Android documentation for implementation choices. Its Java/XML, fragment and older tooling examples should not dictate a Compose rewrite. I would not add Jackson, Retrofit, RxJava, ContentProviders or a new image library merely to match that list.

The work items below use **F1–F12**, separate from the earlier performance and animation plans. They are proposals, not implemented changes.

## Evidence and limits

- Inventory: **193 production Kotlin files, 31,840 lines**, and **101 JVM test source files**, plus manifests, resources, build files and the benchmark module.
- Reviewed representative resource-owning and high-risk paths across startup, browser/tabs/permissions, discovery, HTTP/downloads, player/subtitles, Library/folders, persistence/backup, history/bookmarks/speed dial, settings/UI, diagnostics and build/test infrastructure. This is not an exhaustive verification of every line or a penetration test.
- Latest existing verification from October 12: **489 JVM tests; zero failures/errors/skips; debug lint zero errors, 81 warnings and five hints**. Reused the completed audio-focus-change results; no build/test/device run was performed for this documentation-only audit.
- Largest files: PlayerScreen 2,922 lines; BrowserScreen 2,824; HubHomePage 1,319; Tab 1,164. Size is a review/testability signal, not proof of poor frame performance.
- Checked-in CI workflows, `.editorconfig`, dependency lockfiles and dependency-verification metadata were not found. External CI services were not inspected.
- Existing R8 release builds and benchmark/profile infrastructure are established. Current frame performance and refreshed interaction profiles still need the user's device evidence.

## Comparison with the guide

| Guide topic | Miowser assessment |
|---|---|
| Gradle/default structure | Established; add build integrity and automation. |
| Secrets/build variants | No inline signing passwords found in reviewed build configuration; development isolation and release signing need planning. |
| Reusable networking | Platform HTTP is used; policy and cancellation differ across consumers. |
| Feature-oriented code | Packages follow features; major screens still coordinate many responsibilities. |
| Resources/styles | Shared Compose theme/components exist; UI text mostly lives in Kotlin. |
| WebView lifetime | Lazy engines, limits, cleanup and renderer recovery exist; runtime leak verification is missing. |
| Unit/Android tests | Strong pure-logic suite; no app instrumentation source set found. |
| Shrinking | Release R8 and resource shrinking are enabled. |
| Persistence | Small settings fit their stores; growing collections require a scaling decision. |
| Debugging/leaks | Debug StrictMode and redacted diagnostics exist; leak tooling is absent. |
| Continuous integration | No checked-in workflow found. |

These topics are adapted from the [guide](https://github.com/futurice/android-best-practices); the findings and work sequence below come from Miowser's source.

## Prioritized change plan

| ID | Priority | Change | Main benefit | Acceptance |
|---|---|---|---|---|
| F1 | P0 | Resolve the certificate-error bypass policy | Connection trust | Invalid certificates fail closed by default; no unreviewed bypass. |
| F2 | P0 | Complete shared native HTTP ownership/policy | Predictable concurrency and cancellation | Every intended native request is budgeted, cancellable and redirect-tested. |
| F3 | P0 | Add reproducible local-check CI and build integrity | Catch regressions before delivery | A clean checkout builds/tests/lints with verified inputs. |
| F4 | P1 | Add Android/Compose integration coverage | Catch platform-only failures | Critical journeys are prepared and compiled; user runs device acceptance. |
| F5 | P1 | Add isolated leak/resource diagnostics | Detect retained screens/resources | Repeated lifecycle journeys have usable evidence; diagnostics excluded from release/benchmarks. |
| F6 | P1 | Extract screen coordination and injectable boundaries | Testability and safer edits | Small focused owners preserve commands, lifecycle and motion behavior. |
| F7 | P1 | Measure structured-store scaling; migrate selectively | Less collection rewrite/query work | A migration demonstrates benefit and preserves all data/backup semantics. |
| F8 | P1 | Make permission timing and OS backup policy explicit | Clearer prompts and privacy | Requests occur in context; platform transfer rules match intended exclusions. |
| F9 | P1 | Separate development installs and define release signing | Safer daily testing/distribution | Isolated development package; stable production upgrade path. |
| F10 | P2 | Resource UI text and tighten accessibility reuse | Localization and consistency | Plurals, RTL, focus and scaling work across important screens. |
| F11 | P2 | Add formatting/static-analysis checks; triage lint | Maintainability | New findings are blocked; existing warnings classified rather than blanket-suppressed. |
| F12 | P2 | Finish controlled performance/profile acceptance | Verify actual speed/smoothness | Matched release-equivalent evidence supports the result. |

### F1 — P0: certificate-error handling

**Product decision (14 October 2026):** The user explicitly chose to keep the browser's per-error **Continue anyway** action. This remains an informed, temporary choice; the app does not silently proceed or change native TLS validation. The fail-closed recommendation below is superseded for the browser UI.

**Evidence:** [BrowserScreen.kt:2365](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt:2365) calls `pending.handler.proceed()` after the user selects Continue anyway. [MiowserWebViewClient.kt:142](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/MiowserWebViewClient.kt:142) forwards main-page certificate errors into that flow. This is an explicit user-mediated bypass, not evidence of silent acceptance of every certificate.

**Recommended change:** remove the general proceed path and cancel certificate errors, with a readable explanation. Android's API guidance explicitly recommends cancellation rather than proceeding. If a deliberate browser certificate-exception feature is required, resolve that product policy explicitly before implementation and define its scope and protected capabilities. Do not quietly make exceptions permanent or weaken native TLS validation. [Android WebViewClient guidance](https://developer.android.com/reference/android/webkit/WebViewClient).

**Validate:** expired, untrusted and hostname-mismatched certificates; main frame and subresources; redirects; Back/navigation while the dialog is open; requests associated with a retired tab. Add tests for policy decisions, then user-run WebView acceptance.

**Performance:** a trust/correctness change, not a speed optimization.

### F2 — P0: native network ownership and redirect policy

**Evidence:** [NativeConnections.kt:9](G:/Projects/Miowser/app/src/main/java/com/miowser/app/network/NativeConnections.kt:9) owns leases for a budget of eight native connections, with six background slots. Downloads, discovery and player data sources use this infrastructure. However, [SubtitlePreview.kt:42](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/SubtitlePreview.kt:42), [SpeedDialSiteMetadata.kt:145](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/SpeedDialSiteMetadata.kt:145) and [AdBlockFilters.kt:429](G:/Projects/Miowser/app/src/main/java/com/miowser/app/adblock/AdBlockFilters.kt:429) open connections separately. Favicon fetching has its own three-permit semaphore, so it is locally bounded; it is not accounted for by the global budget.

Discovery/subtitles manually validate redirects, whereas downloads/filter/icon fetches use automatic redirects. [DirectFileDownloader.kt:308](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DirectFileDownloader.kt:308) configures captured cookies/referer before following redirects. This warrants explicit cross-host tests; a credential leak has not been demonstrated by this audit.

**Sequence:**

1. Route the bypassing consumers through a common request/lease owner with explicit priorities, limits and cancellation. Preserve each feature's byte caps and privacy requirements.
2. Define URL/protocol, redirect/downgrade, cookie/referer/origin and error policies per request category. Recompute credentials for the destination where appropriate; never use one unconditional global cookie/header bundle.
3. Tie cancellation to closing the active request, rather than waiting solely for a blocking read timeout. Make release of every lease/response idempotent.
4. Evaluate a shared OkHttp client behind that interface, starting with low-risk public metadata/filter requests. Preserve the existing scheduling budget; a client's dispatcher alone is not the current priority policy. Integrate transfers and Media3 only after range, cookies, caching and cancellation tests pass.

`HttpURLConnection` is a platform implementation, not intrinsically incorrect. A blanket library swap is unnecessary. A mature reusable client can simplify ownership; any latency/throughput gain needs comparison. [OkHttp's documented capabilities](https://github.com/square/okhttp/blob/master/README.md).

**Validate:** loopback-server tests for range/If-Range, redirects, authentication boundaries, compressed responses, oversized/chunked bodies, interrupted reads, retry eligibility and zero leaked leases. User compares concurrent playback/transfers and low-bandwidth behavior. Do not cache private authenticated responses indiscriminately.

### F3 — P0: continuous integration and reproducible inputs

**Evidence:** default Gradle source sets, pinned catalog versions and centralized repositories already exist. No checked-in workflow, dependency lockfiles or `gradle/verification-metadata.xml` were found. [gradle-wrapper.properties](G:/Projects/Miowser/gradle/wrapper/gradle-wrapper.properties) specifies the official distribution URL but no `distributionSha256Sum`. The current local Gradle runtime is Android Studio JBR **25.0.3**; target bytecode 17 is a different setting.

**Change:** create a documented clean-checkout path and local-check CI: unit tests, debug lint, debug build, R8 release build and benchmark compilation. Pin the compatible JDK/SDK/tool inputs, verify the wrapper/distribution, establish reviewed dependency verification/locking and archive XML/lint/mapping artifacts. Keep signing material outside tracked configuration. Add failure visibility; do not allow `--continue` or ignored exit codes to label a failed lint run successful. [Gradle security](https://docs.gradle.org/current/userguide/best_practices_security.html), [dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html).

**Validate:** fresh caches/checkouts, deliberate checksum mismatch, failing test/lint fixtures, unsigned release build without secret material and absence of credentials in logs. CI should not automatically publish or run device jobs under the current user instruction.

**Performance:** build reliability; no promised runtime speedup.

### F4 — P1: Android integration tests

**Evidence:** 101 JVM source files cover substantial parsing, scheduling, routing, persistence and motion logic. `app/src/androidTest` is absent; `app/build.gradle.kts` only declares JUnit for app tests. The separate benchmark module uses Android instrumentation, but it is not a substitute for asserting functional behavior of the production UI/platform flows.

**Change:** prepare focused Compose/Android tests and injectable fixtures for subtitle attachment/restyling, overlay dismissal/focus, saved Hub state, tab retirement/recovery, permission results, SAF backup destinations, interrupted restore, MediaStore downloads, notifications and player audio policy. Keep genuine pure-logic tests on the JVM. [Android testing guidance](https://developer.android.com/training/testing).

Compile these tests locally; the user runs physical/device checks. No emulator/device jobs should be launched by the assistant. Start with regressions previously reported in this chat rather than a large screenshot suite that mirrors the UI implementation.

**Accept:** clear assertions for saved subtitles remaining attached after styling; no modal click-through; correct folder/count restoration; preserved downloaded media; paused notifications dismissible; no stale resource callback. Report platform/device results separately from JVM totals.

### F5 — P1: leak and lifecycle diagnostics

**Evidence:** [TabController.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabController.kt) implements the six/three-engine limits, bounded navigation snapshots and destruction. Player session/overlay/clock owners and callback cancellation are already extracted. Debug StrictMode currently detects disk reads/writes. LeakCanary and VM leak checks are not configured. No actual retained-Activity leak is established here.

**Change:** add leak detection in an isolated development variant, expand useful StrictMode checks and expose bounded diagnostic counters for engines/sessions/listeners/requests. Keep diagnostics out of release and benchmark variants. Current LeakCanary setup uses a debug-scoped dependency; no release no-op library is required merely to follow an older example. [LeakCanary setup](https://square.github.io/leakcanary/getting_started/).

**User journeys:** repeat tab/popup creation/closure, private-mode exits, player/playlist/subtitle opening, rotation/backgrounding, blob transfers and renderer recovery. Distinguish app heap retention from Chromium renderer memory and legitimate process-lifetime repositories. Never make every application singleton screen-scoped just to reduce a leak counter.

### F6 — P1: smaller screen coordination and test seams

**Evidence:** the large [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt), [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt) and [HubHomePage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/HubHomePage.kt) combine rendering, events, platform handoffs and repository mutations. Existing TabController, PlaybackSession, timeline, subtitle and overlay owners are useful separations.

**Change:** extract browser permission/file-selection/download handoff coordination and player subtitle/track/workspace UI in small patches. Expose repository/network/clock interfaces where tests need control. Use screen state holders or ViewModels for durable business/UI state where appropriate; keep WebViews, player surfaces, Activity references and their cleanup with lifecycle-compatible owners. Manual constructor/factory injection is sufficient initially; neither Hilt nor a multi-module migration is mandatory. [Current Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

**Accept:** commands retain ordering and start immediately; no extra focus/timing owner, stale callback or state loss; existing pure tests plus focused integration acceptance. Refactoring size alone is not a speedup—composition/layout work must still be measured.

### F7 — P1: structured-data scaling, with a measurement gate

**Evidence:** [LibraryStore.kt:133](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryStore.kt:133), [HistoryStore.kt:52](G:/Projects/Miowser/app/src/main/java/com/miowser/app/history/HistoryStore.kt:52), BookmarkStore and DownloadStore serialize whole collections into preference strings. Ordered asynchronous writes and queued snapshot coalescing already reduce UI blocking; this is not the earlier unfixed synchronous-write issue.

**First:** use existing encoded-byte/queue metrics on 500/5,000 records and representative mutation rates. Compare partial row updates and indexed queries in a small Room prototype. Consider Room for Library/folders/history/download metadata if it materially reduces I/O, allocations or query latency; small settings can stay in their current store or migrate separately to DataStore for a demonstrated reason. Current Android guidance distinguishes structured/partially updated datasets from small whole-value settings. [Data-layer storage guidance](https://developer.android.com/topic/architecture/data-layer).

**Migration gate:** preserve identity keys, hierarchy/manual-move priority, title/domain rules, subtitle asset references, resume positions, retained records and all old codec import paths. Coordinate immutable backup exports, restore journal/fence and download recovery with the new store. Do not copy a live database file as an unsafe backup shortcut. Benchmark correctness and performance before removing the old representation.

### F8 — P1: permission timing and explicit platform backup exclusions

**Evidence:** [MainActivity.kt:56](G:/Projects/Miowser/app/src/main/java/com/miowser/app/MainActivity.kt:56) requests debug local-network access, notifications and legacy storage during creation; launcher callbacks discard results. [AndroidManifest.xml](G:/Projects/Miowser/app/src/main/AndroidManifest.xml) disables automatic backup, but no explicit data-extraction rules are declared.

**Change:** request notification/legacy storage permissions when the user initiates the relevant feature, with denial/retry states and a serialized request coordinator. Keep site camera/mic/location grants origin-scoped. Audit explicit cloud and device-transfer exclusions on Android 12+; `allowBackup=false` alone can behave differently for device transfers on some manufacturers. This is a policy gap to verify, not proof Miowser data has been transferred. [Android backup guidance](https://developer.android.com/identity/data/autobackup).

Do not enable automatic OS backup as a lint fix. Preserve the user's custom backup paths, exclusion of normal history/tabs and private/login data, and uninstall-safe public downloaded media. Verify denial, restored URI permissions, API 26–28 storage and API 33+ notification flows on the user's device.

### F9 — P1: build identity and release distribution

**Evidence:** the benchmark target has an application ID suffix; ordinary debug and release share the production ID. Release shrinking exists, but no production signing configuration is defined in the reviewed build file; recent release outputs are intentionally unsigned evidence. `.gitignore` excludes machine/build files but lacks explicit keystore/secret-property patterns.

**Change:** add an opt-in development variant with its own label/icon/package, preserving the user's existing debug install and its upgrade/data path. Define release signing through untracked credentials or the chosen CI secret store only when distribution is required. Add preventive keystore/secret-file ignores and document version-code progression, mappings and artifact checksums. Do not ignore all `gradle.properties`: its current contents are legitimate non-secret build configuration. No secret exposure was found by this scoped source review.

**Accept:** separate installations, correct FileProvider authorities, preserved existing Library/download references, unsigned CI validation without secrets, and a deliberate signed release path before publication. No automatic upload/publication is part of this proposal.

### F10 — P2: string resources and accessibility consistency

**Evidence:** [strings.xml](G:/Projects/Miowser/app/src/main/res/values/strings.xml) contains only the app name, while screen text, content descriptions and singular/plural selection mostly live in Kotlin. Shared Material/Expressive tokens and compact components already exist.

**Change:** resource important labels, errors, descriptions and plurals in small feature batches using Compose resource APIs. Consolidate genuinely repeated UI values, preserving intentional video/subtitle styling choices. Validate focus, hidden-content semantics, contrast, RTL, 150/200% fonts and touch targets; test selectors should prefer stable tags where translated text would change. [Compose resources](https://developer.android.com/develop/ui/compose/resources).

This is localization/accessibility work, not a redesign of the compact sheets, dark player overlays or grey subtitle sample.

### F11 — P2: lint triage and formatting

Latest lint categories: 58 UseKtx, nine GradleDependency, five AutoboxingStateCreation, four ObsoleteSdkInt, three MissingOnRenderProcessGone, two AndroidGradlePluginVersion, two ModifierParameter, and one each NewerVersionAvailable, DataExtractionRules and UnusedResources.

**Important correction:** renderer handlers already exist in [MiowserWebViewClient.kt:62](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/MiowserWebViewClient.kt:62) and popup [Tab.kt:247](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt:247). The three report entries are not three independently confirmed crash gaps; one location is duplicated. Investigate lint analysis/caching and reproduce before fixing or narrowly suppressing a verified false positive.

Add `.editorconfig` and one Kotlin formatting check. Start static-analysis rules with actual project value; avoid a wholesale formatting diff or arbitrary function-size enforcement. Address state boxing/modifier contracts and unused resources, justify platform-backup policy, and review dependency notices for compatibility rather than updating everything at once. Keep zero new lint errors and a reviewed warning inventory; do not blanket-baseline away real problems.

### F12 — P2: measured performance and refreshed profiles

The [completed animation work](G:/Projects/Miowser/docs/ANIMATION_STEP_5_VERIFICATION_2026-10-09.md) and P0/P1/P2 optimizations should stay intact. Prepared Hub/profile journeys do not establish current production frame performance.

Complete fixtures for interrupted tab gestures, jump-to-top reversals, player controls/overlays, real subtitle playback and active transfers. The user runs matched release-equivalent journeys, records frame deadlines/overruns, first response/frame, CPU, memory/GC, thermal/refresh state, and regenerates profiles after hot paths stabilize. Compare profile benefits separately from architectural or transport changes.

Room/client/state-owner changes need before/after evidence. CI, signing, localization and formatting mostly improve reliability/maintainability and should not be sold as direct runtime speedups.

## Delivery sequence and regression contract

1. **F1:** decide and implement the certificate-error policy, with negative tests.
2. **F2:** fix uncovered request ownership/cancellation first; establish redirect/authentication tests before any client migration.
3. **F3:** establish reproducible local-check automation and reviewed build inputs.
4. **F4–F5:** prepare the platform/lifecycle regression harness and isolated diagnostics. User runs device checks.
5. **F8–F9:** close permission/backup-policy and release/development-identity gaps.
6. **F6:** extract controllers/components incrementally, protected by the harness.
7. **F7:** measure structured persistence; migrate only after correctness/performance gates pass.
8. **F10–F11:** resource/accessibility and focused static cleanup; no full-tree formatting sweep.
9. **F12:** repeat measurement/profile acceptance throughout changes and before a release claim.

For every patch preserve: saved VTT/SRT and millisecond delay; title stability; compact player controls and playlist location; manual folder priority and domain/title rules; download pause/recovery/notification behavior; public user media; backup consistency/exclusions; browser engine budgets/private cleanup; immediate commands and reduced-motion completion.

**The user's October 12 audio-focus policy remains deliberate:** [PlaybackSession.kt:42](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlaybackSession.kt:42) uses `handleAudioFocus=false`. This audit does not propose re-enabling it as an automatic best-practice correction.

No implementation, dependency upgrade, commit, publication or device operation was performed by this audit. Recommended starting scope: **F1–F3**.
