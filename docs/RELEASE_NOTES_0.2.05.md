# Miowser 0.2.05 (code 100)

- Added separate controls for simultaneous downloads, default connections per file, connections per site, and automatic retry attempts in Media settings → Network. The old eight-connection preference migrates to six, matching the shared background budget while preserving playback capacity.
- Added Download next for queued files and a saved connection override for individual direct downloads. Running transfers keep their work when queue limits change.
- Added Verify file to saved-download menus in both list and grid. SHA-256 and MD5 are calculated from the saved file in one background pass, can be copied, and can be compared with an expected checksum. Closing the dialog cancels the calculation.
- Added received-byte totals, transfer speed, active connection count and retry information to download rows and expanded notifications. Segment progress remains separate from received bytes.
- Reject incomplete direct-file streams before publishing them. Network failures and server throttling use bounded retries; authorization, unsupported-file, storage and certificate failures stop for user attention. Server Retry-After values are honored.
- Check temporary and destination storage before allocation/publication, reserve headroom across concurrent jobs and check free space during writes. New jobs can stage on writable app-owned external storage with more available space; existing jobs retain their staging location for recovery.
- Preserve existing recovery records and decode prior download-history formats. Updated settings search and onboarding wording.

Device testing remains with the user. See [download implementation notes](DOWNLOAD_ENGINE_0.2.05.md) for behavior, limits and checks.
