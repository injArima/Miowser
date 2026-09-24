# Step 9: Android 8–9 download test checklist

> Deferred by user request. Revisit this older-Android matrix only if an issue appears.

Run this on an **API 26** device/emulator and an **API 28** device/emulator. Use your debug build
and authorized/public test downloads. Record the Android version and the expected/actual result
for any failure. The assistant has not run this device matrix.

## Basic permission and output

1. Fresh install, grant the storage permission prompt, download a small direct MP4. Confirm it
   completes, appears under public `Download/Miowser`, opens from Hub, and appears in a system
   file manager after the media scan.
2. Fresh install or reset permissions, deny storage permission. Try a small download. Confirm it
   fails clearly, creates no partial public file, and does not crash. Grant permission in Android
   app settings and retry; it should then complete.
3. Revoke storage permission after a successful download, then attempt another. Confirm the
   existing file remains intact and the new attempt fails without a crash.

## Transfer types and lifecycle

4. Download one direct file with a range-supporting server, using multiple connections. Confirm
   the divided progress bar and final playable file. Repeat with thread count set to one.
5. Download a short public HLS stream and a short public DASH stream. Confirm the completed MP4s
   appear in the same public folder and open in the player.
6. During a download, pause/resume, cancel, Retry, and delete. For delete, test both record-only
   and file-and-record. No canceled or deleted partial output should remain visible.
7. Kill the app during a direct, HLS, and DASH download; reopen and resume. Confirm previously
   verified work is reused, the finished file opens, and notifications/elapsed time remain sane.

## Filename and storage edges

8. Create an unrelated `sample.mp4` in `Download/Miowser`, then download another `sample.mp4`
   through Miowser. Confirm the original bytes are untouched, a `sample (2).mp4` file appears,
   and the Downloads row shows that exact name. Repeat while two same-name downloads are queued.
9. Try an APK and a generic-file download. Confirm filename/extension and system open action are
   correct, including the APK install flow.
10. With low free storage, attempt a download larger than the remaining space. Confirm a failed
    record, no crash, and no completed file presented as valid. Free space and retry.
11. Restart the app and check that completed rows still open their files, and canceled/deleted
    files do not remain as stale entries in the system file manager.

Finally, run one small direct download on **API 29 or newer** as a regression check. That path
uses MediaStore and was not changed by the Step 9 filename fix.
