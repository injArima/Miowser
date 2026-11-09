# Miowser 0.1.52 (code 53)

- Added repeatable Library benchmarks for startup, warm navigation, thumbnails, folder and search changes, scrolling, and saved progress.
- Added trace sections for folder scanning, thumbnail decoding, Library filtering and sorting, and storage writes.

This release prepares the performance baseline for the two-tab Hub. The Hub layout is unchanged in P0.

## Run the Library baseline

From the project root, connect one physical phone and run both fixture sizes.

```powershell
.\scripts\Run-OptimizationBenchmarks.ps1 -Serial <serial> -Journey libraryP0 -Library 1000
.\scripts\Run-OptimizationBenchmarks.ps1 -Serial <serial> -Journey libraryP0 -Library 5000
```

Repeat on a lower-memory phone and a representative current phone. Use 60 Hz and a higher supported refresh mode where available. Keep build options, orientation, power mode, thermal state, and compilation policy comparable. The runner saves JSON, Perfetto traces, and device metadata under `.local/optimization/evidence/step03`. It seeds only `com.miowser.app.benchmark`.

| Journey | Evidence |
| --- | --- |
| `coldRows` | Cold process startup timing and launch to the first observable Library row. |
| `warmNavigation` | Downloads to the populated Library in the same process. |
| `coldArtwork`, `warmArtwork` | Launch or tab return to visible artwork. Cold resets the process bitmap cache and keeps the same disk JPEGs. Warm primes the first viewport before tab return. |
| `folderAndSearch`, `longFling` | Time to matching rows after a folder switch and a search, projection cost, and frame timing over repeated flings. |
| `scanBatchCommit`, `progressCommit` | Time to the writer’s commit acknowledgement after a fixed 64-record import batch or one watch-position update. The import journey starts after discovery. A manual SAF import provides the separate `LibraryFolderScan` span. |

The `P0*` elapsed sections include UI Automator action and observation latency. Compare those sections with the same harness across versions and use the app trace sections to isolate worker time. The fixture uses synthetic JPEGs and one shared video file. It establishes a saved-library baseline for app behavior, with real-file and provider benchmarks to follow when the device catalog exists.
