# 1DM download engine comparison with Miowser

Audit date: 25 November 2026. Miowser: 0.2.04, commit `6f078c07bb8d80afc0751503026f2dd30ac1f89d`.

Miowser already implements the central transfer mechanisms: parallel HTTP ranges, pause/resume, persistent recovery, HLS/DASH segment recovery, browser session credentials, refreshed links, and public file publication. Its largest gaps against the inspected 1DM+ build are download policies, per-download controls, scheduling, request configuration, and batch management. Several correctness issues should take priority over adding more features.

This compares the supplied **1DM+ 18.2**, package `idm.internet.download.manager.plus`, version code 30249, with the current source. The APK was decompiled with REA/JADX; neither app was executed and no speed comparison was measured. Decompiled methods with branch warnings support only the specific operations cited below.

## Features Miowser is missing

| Capability | Evidence in 1DM+ | Miowser today | Value and priority |
| --- | --- | --- | --- |
| Scheduled start and stop | `Scheduler` stores time windows, Wi-Fi requirement, speed limit and retry count; `DownloadService.m14826` calculates the stop deadline and starts scheduled processing. E1 | Downloads start when enqueued. No schedule model or dispatch policy. | P1: download during a preferred window. |
| Optional recovery after reboot | `DownloadReceiver.onReceive` starts the service on boot when Smart Download is enabled. E2 | Saved work is restored as paused when Miowser restores its queue. No boot receiver or unattended restart policy. | P1, opt-in. Respect current Android restrictions; do not copy the older app's service-start code literally. |
| Wi-Fi-only downloads | Schedule and download models carry Wi-Fi policy; worker checks can stop disallowed transfers. E1, E3 | No download-specific Wi-Fi/metered policy. | P1: prevent unwanted mobile-data use and wait for an eligible network. |
| Data allowance and low-battery conditions | Service implements session data-limit and low-battery checks; worker stream loops call their policy-check path. E3 | No download data quota, charging requirement or low-battery threshold. | P1: add conditions with an explicit waiting reason. A charging-only feature in 1DM was not independently established. |
| Speed limits | `i.qj.mo11207` calculates a byte allowance; `i.hi.m11308` limits stream reads and waits when the allowance is exhausted. E4 | Connection-count limits exist, but no bytes-per-second throttle. | P1: per-download and shared bandwidth limits. More connections do not substitute for throttling. |
| Adjustable concurrent jobs | Service compares active jobs against configuration before dispatching or queuing work. E5 | Fixed `ThreadPoolExecutor(2, 2, ...)`. No user-set simultaneous-download count or persistent queue order. | P1: expose job concurrency separately from connections per file. Custom drag ordering in 1DM was not established. |
| Per-domain job and connection limits | Domain settings persist limits; `i.UZ.m8275` resolves exact/parent-domain part limits; `i.qj.m12863` applies them. Service dispatch also consults website limits. E5 | One global 1/2/4/8 connection choice; no host limit or per-job override. | P1: avoid overwhelming a server and allow a constrained host to use fewer connections. |
| Download request editor | Worker constructs Authorization, Origin, Referer, Cookie and custom headers, reserving Range/Accept-Encoding for the engine. E6 | Download model retains User-Agent, Referer and one cookie string; no general header map or authorization editor. | P2: compatibility with legitimate downloads requiring extra request headers. |
| Explicit download proxy | `i.UZ.m8238` creates direct and configured-proxy clients; the transfer path selects between them. E6 | No app-configured downloader proxy. Platform/network routing can still apply. | P2, if needed by the product. |
| Batch URL jobs | `DownloadService.m14843` loops over supplied links and creates download records; packaged resources also expose batch/link import actions. E7 | Browser and detector enqueue individual requests. No multi-URL import/editor, range expansion or batch profile. | P2: useful for a download-manager workflow. Numeric URL expansion in 1DM was not traced in this pass. |
| User-facing whole-file checksum validation | `i.UZ.m8225` and `m8227` calculate SHA-256 and MD5 for a downloaded file. E8 | Internal segment digests protect recovery, but there is no final-file hash display or comparison with a user-supplied expected hash. | P2: distinguish resume-cache integrity from validating a published file. |
| Per-download destination and completion moves | Destination is part of 1DM's download record; `i.qj.m12876` moves a completed file to a configured destination, with rename/copy paths. E9 | A global default/custom SAF folder is consulted during publication. No destination stored in `SavedDownload`, type-based routing, or automatic completion move. | P2: pin the chosen destination to each job before adding elaborate folder rules. |

“Missing” means absent from the inspected Miowser download model, settings, dispatchers, transfer implementations and UI entry points. It does not mean Android itself lacks the capability.

## Features present but incomplete

| Area | Existing Miowser behavior | What remains |
| --- | --- | --- |
| Retry and reconnection | Three queue retries with short exponential delays; ranged transfers also have per-range retries. Verified work is retained. | Classify permanent versus transient failures, preserve structured HTTP errors, handle server retry timing, and wait for connectivity instead of exhausting retries while offline. 1DM's failure callback distinguishes error codes and increases waits for HTTP 429/509; exact Retry-After parsing was not established. E10 |
| Multipart control | Direct files are split into fixed ranges with saved progress and response-range checks. | Separate requested parts from actual active connections; add per-file/host policy. There is no evidence here that simply increasing the thread count would improve speed. |
| Recovery after restart | Records and recovery artifacts survive process loss; verified interrupted work becomes paused and can resume. | Automatic policy-driven resumption is missing. A source that cannot serve ranges cannot safely resume a partial byte stream; restarting that transfer is not itself a defect. |
| Expired-link repair | `DownloadQueue.refreshLink` keeps the job ID and recovery artifacts, switches the URL and checks reuse. | HLS/DASH fingerprints contain segment/init URLs. Changed signed query parameters can invalidate otherwise identical cached work. Stable segment matching requires careful identity verification; 1DM behavior for this exact case remains unverified. |
| Transfer information | Progress percentage, per-range bars, active elapsed time and ETA. | Surface actual network bytes and bytes/sec. HLS/DASH currently store segment counts in `bytesDownloaded`/`totalBytes`, so their progress and ETA are based on items, not byte totals. |
| Large download history | Presentation records are persisted separately from recovery plans. | All records are encoded into one SharedPreferences string and rewritten as a snapshot. 1DM persists downloads and thread rows through SQLite, including transactional bulk inserts. E11 A database is useful for incremental updates and paging; storage format alone does not prove a speed advantage. |
| Media download choices | HLS AES-128, byte ranges and initialization segments; DASH templates/lists/SegmentBase; separate DASH video/audio remuxing. | The DASH worker chooses the highest-bandwidth video and audio; an HLS master reaching the worker chooses its best variant. No durable download-level audio/language/representation selection. Existing choices that pass a specific HLS leaf URL remain useful. |
| HLS structure coverage | A leaf playlist is enumerated, verified segments are downloaded and remuxed. | No continuous live-playlist polling, external HLS audio-rendition pairing, or explicit discontinuity timeline model. Multiple initialization segments are rejected. These are Miowser source limits; complete 1DM parity for these cases was not established. |
| Notifications | Per-job pause/resume, progress, elapsed/remaining time and timeout checkpoint handling. | Persistent terminal success/failure notifications with a direct open/retry action. Current notifications are removed when work is no longer active after record saving. 1DM's success/error notification paths are visible in its service callbacks. E10 |

## Correctness and resource issues to address first

### Verify single-stream completion

`DirectFileDownloader.runFreshStream` reads to EOF and publishes the file without an explicit `written == Content-Length` check when the expected length is known. `publishCompleted` then sets progress to the expected total. Some transport implementations throw for truncation, but this engine does not independently enforce completeness. Add an explicit validation before publication and test truncated responses. The ranged path already checks exact byte counts.

### Stop retrying permanent failures

`DownloadQueue.dispatchOnOwner` retries almost every `FAILED` result, apart from selected renderer-only failures. Unsupported manifests, permission failures, storage errors and HTTP authorization errors can therefore repeat without any condition changing. Use typed failures with separate actions: retry, wait for network, require login/link refresh, require storage access, or fail permanently. The existing recovery data should remain available when retrying is meaningful.

### Account for temporary and destination storage

Recovery artifacts always live under `filesDir/download_recovery/<id>`, even when the final destination is an SD card or SAF folder. Direct files are copied to their public destination at completion. HLS/DASH can additionally retain segments, joined tracks and a muxed file before that final copy. Large downloads can exhaust internal space despite free external space. No download free-space preflight/reservation was found. Estimate headroom for each stage, monitor actual space and report which storage needs attention. Where the destination supports seeking, assess reducing redundant copies while preserving safe publication.

### Make connection controls match actual capacity

The UI offers eight connections per direct file. `ConnectionBudget` permits at most six simultaneous native background connections across transfers/discovery/prefetch and reserves capacity for playback. Eight ranges can exist, but eight transfer sockets cannot be active at once under this budget. Keep the playback reservation; make the setting and active-connection display reflect the policy instead of silently implying eight active connections.

### Centralize request context

Direct, HLS and DASH each configure their own requests. One captured cookie string is reused for manifests, segments and keys, and those paths enable automatic redirects. Add explicit per-target cookie/header selection and redirect rules rather than assuming every URL shares the source session requirements. This is a code-path gap, not a claim that a credential leak was observed. The public-resource redirect helper in `NativeConnections` is not the downloader's request path.

## Existing work worth keeping

- Pause/cancel interrupts network work and releases ownership cooperatively; paused jobs need not hold a foreground-service slot.
- Ranged recovery checks Content-Range, source prefix fingerprint and response validators before reuse, and safely falls back when ranges are not honored.
- HLS/DASH recovery verifies cached segments with length and SHA-256 markers. Browser-generated blobs remain tied to their originating page and are not falsely treated as resumable remote files.
- Filename/MIME resolution, conflict handling, configured SAF destinations and MediaStore publication already exist.
- Playback has reserved native connection capacity. Chromium's sockets are outside this budget, so it is not a whole-device traffic limit.
- Raw browser cookies are reacquired rather than stored permanently in download history.

Relevant source: [DownloadQueue](../../app/src/main/java/com/miowser/app/downloader/DownloadQueue.kt), [DirectFileDownloader](../../app/src/main/java/com/miowser/app/downloader/DirectFileDownloader.kt), [DownloadRecoveryStore](../../app/src/main/java/com/miowser/app/downloader/DownloadRecoveryStore.kt), [DownloadStore](../../app/src/main/java/com/miowser/app/downloader/DownloadStore.kt), [HlsDownloader](../../app/src/main/java/com/miowser/app/downloader/HlsDownloader.kt), [DashDownloader](../../app/src/main/java/com/miowser/app/downloader/DashDownloader.kt), [MediaStoreOutput](../../app/src/main/java/com/miowser/app/downloader/MediaStoreOutput.kt), [DownloadForegroundService](../../app/src/main/java/com/miowser/app/downloader/DownloadForegroundService.kt), [ConnectionBudget](../../app/src/main/java/com/miowser/app/network/ConnectionBudget.kt), [Settings](../../app/src/main/java/com/miowser/app/settings/Settings.kt).

## Suggested implementation order

1. **Correctness:** verify completion; classify failures and server throttling; check storage; centralize request context; reconcile the connection control with the shared budget.
2. **Control:** configurable active jobs, per-file/per-host connections, bandwidth limits, Wi-Fi/metered/data/battery conditions, and terminal notifications.
3. **Persistence and convenience:** incremental download database, pinned destinations, richer transfer metrics, schedules, optional eligible restart, batch URL entry and whole-file checksums.
4. **Media coverage:** explicit track selection, external HLS audio and discontinuity/init-change support based on actual failing samples. Continuous live recording is a separate feature.

1DM also has a torrent subsystem: the inspected service uses FrostWire/libtorrent models and resume-data paths. Miowser has none. Torrent support is an optional expansion with a separate engine, not a prerequisite for reliable browser/video downloads. FTP support, every cloud-host extractor, numeric batch expansion, full FFmpeg codec coverage and 1DM live-stream behavior were not established by this audit. DRM bypass is not a parity requirement.

## Evidence and coverage

Base APK: `.local/analysis/1dm/base.apk`; SHA-256 `8f24b5aff4a777c154549e890d44f1f356b8251553f83c009a3b2e9de1eea4a1`. It matches the base extracted from the previously supplied `1DM+_18.2 (2).apks`. Signatures were not verified. Package inspection covered 5,752 top-level class rows. APK resource decoding through apktool was unavailable; named DEX resource fields were inspected through JADX instead. Resource names alone were not treated as proof of runtime functionality.

The exported [REA evidence bundle](../../build/reports/rea-1dm-download-audit/evidence.json) retains complete method records. Its session includes earlier investigation records; the entries below identify this comparison's relevant observations. JADX aliases are scoped to this APK and engine; some large methods fall back to smali, and some Java bodies contain duplicated-branch warnings.

| Reference | Observation | Evidence ID |
| --- | --- | --- |
| Identity | APK package/version/digest | `ev_b64865023deb53bd06d524a4700ad801559872e96c58b646bc4ec61fdc27afcb` |
| E1 | Scheduler fields; service time-window processing | `ev_8d4423c9fa112d3aeeb6911f50e4c99c5afb0d7e4dab29af34a1e1927c7d53ee`, `ev_51947d7ead74f7afd63e59558da6c750a86b99092092fc58b34537509b4bbaa1` |
| E2 | Smart Download boot receiver | `ev_5281546de77d4cf25b39dc2f1833e2f596e8fb6d0dcaf69832777efb8207516f` |
| E3 | Battery/data service checks; worker policy checks; download network policy | `ev_af5e7d18d1d91e15631480b1afbb784387bcaf1eca647394a09e5a06a49ffe97`, `ev_5ae2a9a575696129db6236a2b270794b508a0f5c06063e48be35dc4e91a381d8`, `ev_6e3aa0652f1824fff3e918151e2d960c317c2b7d847ca4c464fc34d1a98e6765`, `ev_d0ae2e8f595003f50ee725b41aeb5bc20323a42e1b7588627edef5a64971168b` |
| E4 | Byte allowance and throttled reads | `ev_fc87fbda71d68ca32520ccdae340bc23c5d840c7fed072e44de41d720d72d6e4`, `ev_c2ab85e96550bb6eb3d9838568ec93182f4931b21443b7ae3974cb0da6d19850` |
| E5 | Domain part lookup and application; concurrent-job dispatch | `ev_52f565a33c43a56a3f05125612d06af0d933efad552b70396b435c2fd061e370`, `ev_3d62671759862a16adfa7dac6d81b8bc1a6c0161a93d35a85446e2d91a4624aa`, `ev_11f01c7d2e089dd6c41cfdcf85c038033a1b3944e4e65304b9b57514e8dc6f28` |
| E6 | Request headers; proxy client creation and selection | `ev_52023cdbdc3c57089e70611bcd39a8e3f3247a444474efcd41e6c3eb3ab1ebdd`, `ev_1ca8bffe9148b9bf7070d26ec880db70a150b8fc4e70dc685d22e55f242d3f61`, `ev_7b61eacb847b3512f4b6a4c82adc974aa83cca8caab2a215bbe2e17ebd4f1c28` |
| E7 | List-based download creation | `ev_9313077b6078de5b50b69bac8dc5631c3de33a5ea966a942e962b6d9cd2ce948` |
| E8 | Completed-file SHA-256 and MD5 helpers; SHA-256 result forwarded to the main activity UI | `ev_a0c29fc031a863f1c52b3c1dd0af54114d4f89f2f87c398c3d78ccaf6476a5a4`, `ev_7583ab89244d1abe76b728fd070ff11f2121f5928c59b8719bf045a68ae0a0b8`, `ev_ba32212b95266f9b5d17d5e82030747515a60b673c9876027721abb6030e0d4f` |
| E9 | Completion move and rename/copy | `ev_9b8a3426c0133137c91b2196ccaa5cf28b9561a2293011295e68a3163a98dadb` |
| E10 | Failure classification, configurable delay and 429/509 wait | `ev_513e9bc092cdb9c729b17d0e41d4f162b041dc7b7afa2e90b19265c7fda72bc9` |
| E11 | Download/thread persistence and transactional thread inserts | `ev_437b1e01a9f5276ce76b8a58e50ca49914713e0451bec11b170d682fec9c029a`, `ev_6847eec363bd72084d4b2191d1c1f804fe604f3438e7c7b811fb60b3f1257f64` |
| E12 | Torrent manager and resume-data entry points | `ev_1758a4d14c50f41eef0f088f69e7fb3cc088bff4c2d74baec61918d35d9900b2`, `ev_bf0577c79e4bf2f2914f0862f9f82d9518e132e79a34e29f0170887c33892037` |

Transfer mechanisms, persistent state, request configuration, queue policy, network/power conditions, storage and user controls were compared. Scheduling internals, policies and selected worker paths are statically evidenced. Actual throughput, battery cost, OEM background behavior, private runtime databases and the untraced media/host cases above remain outside this comparison. No application code or version was changed.
