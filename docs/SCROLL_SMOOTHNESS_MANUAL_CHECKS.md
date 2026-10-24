# Hub and browser smoothness changes

Source: `G:\Projects\Miowser`. This is a follow-up to Step 6, not Step 7.

Status: implemented, unbuilt and untested at the user's request. No performance improvement has been measured. No emulator, device, automated tests, lint, or stress tests were run for this change. Ask the user before running a stress test.

## Changes

- Library snapshots are taken on the UI owner, then folder filtering, searching and sorting run on a worker. New requests cancel publication of older results. Native Compose lazy grids keep stable item keys and explicit content types for reuse.
- Library and Download grids retain their scroll state. Artwork loading and thumbnail repair wait for a short idle period; cards passed during a fling can cancel pending coroutine work. Existing memory-cached artwork displays immediately.
- Download-card composition no longer checks thumbnail files synchronously. Artwork decoding, download extraction and APK icons use a shared two-thread pool instead of flooding general I/O workers.
- Thumbnail decoding deduplicates overlapping requests through bounded locks, samples oversized images, prepares decoded bitmaps for drawing, and keeps the existing 24 MiB memory cache. Video-frame extraction requests a scaled frame instead of allocating a full-resolution frame before shrinking it.
- Browser scrolling no longer starts periodic PixelCopy captures during a fling. A preview is captured after scrolling settles, or explicitly when needed for the tab overview.
- Browser load progress is supplied as a lambda to the progress indicator, avoiding reading every progress update in the containing browser UI.
- Browser-entry filter subscription/manual-rule reads move to I/O. UI state is published on main; periodic refresh scheduling runs on a worker.
- Automatic static media discovery waits one second after page completion, with pending work postponed during scrolling and cancelled on navigation/disposal. Candidate ad-block matching runs on the discovery worker; silent scans no longer post unused progress callbacks to main. Explicit discovery remains immediate.

Already-running platform thumbnail extraction is not interruptible; idle gating avoids starting most transient work, but does not forcibly stop an extraction already in progress. WebView construction and access remain on main as required by Android. No dependencies, stored formats, or background-tab eviction rules changed.

## Build it yourself

The existing `Miowser-0.1.0-step6-debug.apk` predates these edits and does not contain them.

From PowerShell in `G:\Projects\Miowser`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Install `G:\Projects\Miowser\app\build\outputs\apk\debug\app-debug.apk`, or use Android Studio's Run action. Report any compiler error before testing behavior. A debug build is useful for comparing behavior, but is not a release-performance measurement.

## Manual acceptance checklist

1. Close and reopen the app with your usual Library. Watch initial rendering, then scroll normally and fling in both directions. Pause: visible artwork should fill in; returning to cached cards should show artwork promptly. Check that thumbnails do not jump between cards or become stretched.
2. Change Library sort, search quickly, switch folders, and clear search. Confirm the final query/order is correct. Move/remove a record and verify it updates without reopening the app. Check empty Library, empty folder and no-match messages.
3. Open Downloads. Scroll completed files, pause, then return to previously seen cards. Verify video thumbnails and APK icons appear, and play/open/install actions still match their files. Confirm a removed or unavailable file is handled correctly.
4. Open the same ordinary web pages you used before this change. Compare initial loading and scroll responsiveness under similar conditions. Check the progress indicator completes and Reload/Stop still work.
5. Scroll a page, pause, then open Tabs. Its preview should represent the recent viewport. Switch away/back and confirm page state remains. Open Tabs immediately after a fling as well.
6. On a familiar media page, wait briefly after loading and stopping scroll; confirm detections appear. Try explicit discovery, navigate away during a pending scan, and confirm no old-page detections appear on the new page. Check your usual ad-block behavior and custom subscription settings.

Please report device/Android version, whether this was a fresh launch or a return visit, which page/section lagged, and whether it happened while thumbnails were appearing. Large-dataset, repeated-launch, thermal, or endurance stress tests require separate approval; they are not part of this checklist.
