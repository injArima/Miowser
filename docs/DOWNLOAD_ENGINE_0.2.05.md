# Download controls and reliability in Miowser 0.2.05

This change follows the selected parts of the [1DM comparison](research/1dm-download-engine-audit.md): queue controls, connection controls, retry handling, file verification and transfer information. Scheduling, network/battery policies, bandwidth caps, proxies, batch URL entry and torrents remain outside this change.

## Controls and location

| Location | Control | Behavior |
| --- | --- | --- |
| Media settings → Network | Simultaneous downloads | 1–4 jobs, default 2. Lowering the limit does not abort workers already running. |
| Media settings → Network | Connections per download | Default 1/2/4/6 direct-file connections. Disabled means no splitting; a single range can still support recovery. |
| Media settings → Network | Connections per site | Limit shared by downloads requesting the same host. The six shared background slots and playback reservation still apply. |
| Media settings → Network | Automatic download retries | Disabled, 1, 3 or 5. Temporary failures share one retry budget across file ranges and queue reconnects. |
| Queued download menu | Download next | Promote waiting work without interrupting running files. Priority is saved with the record. |
| Inactive direct-download menu | Connections | Override the default for one file; its range plan can retain existing partitions while the worker count follows the override. |
| Saved download menu, list and grid | Verify file | Read the actual saved URI on a background thread, calculate SHA-256 and MD5, copy either hash, and compare an expected value. |

Settings search includes each new global control and retains the existing scroll-and-highlight behavior. Connection limits are upper bounds, not a speed guarantee. HLS/DASH use bounded segment workers; the direct-file splitting preference does not change their segment counts. Per-site limits use the requested URL host; platform-managed redirects are not a separate per-destination host scheduler.

## Completion and retries

Direct streams enforce the known Content-Length before publishing; ranged work retains exact range validation. HLS and DASH responses also validate known lengths. Unknown-length streams can only be checked against EOF and available format/recovery validation; they cannot prove an unavailable expected length.

HTTP 401/403 require sign-in or a refreshed link. 404/410 indicate unavailable files. Temporary HTTP 408/425/429/500/502/503/504 responses and network I/O failures can retry. Certificate errors, unsupported structures, unavailable storage and permission errors stop rather than looping. Retry-After accepts both seconds and HTTP dates, and waiting remains cancellable/pausable. Range retries consume the same budget as queue retries.

The existing verified parts remain available for a meaningful retry. Servers without range support still require a fresh single-stream attempt after interruption. Manual checksum comparison is independent of the internal segment hashes used for recovery; a calculated hash alone does not prove that a file matches its publisher's original.

## Storage and recovery

New recovery directories are selected from app-owned internal/external file directories and pinned in private location preferences. A usable removable volume can carry staging files. Existing internal recovery directories retain their location. If a pinned volume is unavailable after restart, the job requires that storage to be reconnected; it does not silently start over on another volume.

Free-space reservations account for concurrent jobs on the same filesystem, leave 32 MiB of reserve, and are released when ownership ends. Unknown-size transfers get ongoing free-space checks. Final publication checks destination space where Android exposes a local volume. Remote SAF providers may not expose capacity; their actual write failures still stop the transfer and clean up incomplete public output.

Downloads still use private staging and safe copy/publication rather than writing all partial files directly into arbitrary SAF providers. Copying and media remuxing require additional space. Removing all duplicate writes across providers is a separate storage architecture change.

Download records gain per-job connection limit, queue rank and received-byte counter fields. The decoder accepts earlier formats. Browser cookies remain excluded from persisted records. Recovery manifests and verified segments keep their existing format; existing parts remain reusable subject to source identity checks. Backup continues to include completed records rather than in-flight private artifacts.

## Transfer information

HTTP response-body bytes are metered independently of logical progress. For direct files, rows show saved logical bytes versus expected size; for HLS/DASH, rows show received bytes alongside completed/total segments. Current payload bytes/sec, active native connections and recovered retry counts are shown while relevant. Expanded notifications use the same presentation data.

Received totals include manifest/key/probe bodies and retransmissions. They exclude HTTP/TLS overhead and page-renderer-managed networking, so they are not a mobile-data billing meter. Totals are saved with job checkpoints; logical resume positions remain determined by verified recovery artifacts. ETA for segmented media still uses segment completion rate because the full byte total can be unknown.

## Verification

Regression checks cover short/overlong EOF, exact and unknown sizes, HTTP retry categories, Retry-After seconds/dates, shared retry budgets under parallel failures, host isolation/cancellation/playback capacity, bounded checksum calculation/cancellation, expected-hash comparison, history migration, byte counters independent of segments and overflow-safe storage headroom.

The final debug APK build and all **858 JVM tests** passed. The APK reports version 0.2.05-debug, code 100. Download preparation loads saved settings before dispatch, including when work starts from a notification without opening the main screen.

Device checks are left to the user: multiple downloads at different limits; promotion of a queued job; pause/change/resume a ranged file; interrupted HTTP/HLS/DASH recovery; server 429/503 waits; unavailable storage; removable staging disconnect/reconnect; low space during mux/publication; list/grid checksum actions; and notification metrics while backgrounded.
