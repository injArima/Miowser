# Notification artwork and icon sizing — 0.2.04

## Brave comparison

Inspected the supplied `Bravearm64Universal.apk` with REA/JADX, without running
the APK. Artifact SHA-256:
`256cc1fbc1b57f8cf449d15c51fa9535f187ea1e48130d4d332f24e24f4a0665`.

Focused observations from this exact artifact:

- `org.chromium.components.browser_ui.media.MediaSessionHelper.createMediaSessionObserver`
  creates the `hzb` observer.
- `hzb.b(List)` scores `MediaImage` candidates using their declared dimensions
  and image format, then asks its WebContents-backed downloader for the chosen
  image. A changed image URL triggers a new load rather than selecting the first
  thumbnail unconditionally.
- `pub.a(Bitmap)` retains small bitmaps and bounds larger artwork to 512 pixels,
  or 256 on its low-memory path, with a proportional transform.
- `MediaSessionHelper.s(Bitmap)` handles a secondary image path, rejecting tiny
  images below 114 pixels or when primary artwork is already available. Changes
  update notification-builder bitmap fields and call `showNotification`.
- `showNotification` cancels a pending notification runnable and passes the
  current information builder to its notification dispatcher.

This is static APK evidence of the inspected pipeline, not a device rendering
comparison or a claim that WebView exposes all of Chromium's native image APIs.
Miowser uses site metadata through its existing WebView bridge.

## Causes in Miowser

Both player services passed `ic_launcher_monochrome` to `setSmallIcon`. That
resource was an adaptive-icon inset layer with **18 dp on each side**; it is not
appropriate for a small notification slot. Foreground launcher artwork also
used fixed 18 dp insets on top of transparent space already in the source
viewport, making the app-info mark unnecessarily small. Fixed dp insets do not
scale proportionally across the different sizes Android requests.

The artwork bridge selected the first HTTP(S) metadata image, even when a
better-sized image existed. It had no poster/current-player cover fallback.
The native loader decoded to 256 pixels, sent no browser user agent or origin
referrer, and stopped after one failed attempt. It published only ALBUM_ART.
There was already a bounded cache and asynchronous owner checking; those
protections have been retained and extended.

## Changes and reference

| Source | Responsibility |
| --- | --- |
| `res/values/icon_tokens.xml` | Shared 6.5% launcher inset. Proportional scaling applies to launcher/app-info/themed icon slots. |
| `res/drawable/ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` | Adaptive icon layers reference the shared inset. |
| `res/drawable/ic_notification_miowser.xml` | Notification-only 24 dp white alpha glyph, with original logo paths in a tighter viewport; no fixed dp inset. |
| `BrowserPlaybackService` / `PlayerPlaybackService` | Both services use the dedicated notification icon. |
| `media/MediaArtworkScript.kt` | Rank up to eight valid metadata images by size/aspect, then try the current YouTube Music player-bar image or selected video's poster. |
| `media/PageMediaScript.kt` | Inserts selection helper; observes src, srcset, and poster changes so late artwork can republish. |
| `media/MediaArtworkPolicy.kt` | 512 px sampled decoding, 2 MiB encoded limit, two attempts separated by one second, 4 MiB memory cache with at most eight small entries, and origin-only referrer policy. |
| `BrowserPlaybackService` | Background fetching/decoding, bounded redirects, no HTTPS downgrade, browser user agent, no cookie/auth forwarding, owner/private checks, ART + ALBUM_ART + notification large icon. |

The selected-cover fallback is scoped to YouTube Music's current player bar;
it never scans recommendations for an arbitrary image. Primary Media Session
artwork takes precedence. Page paths, queries, userinfo and cookies are not sent
by the artwork downloader. Redirects are revalidated, and private publication
removes both page and artwork URLs before loading.

The live system chooses cover placement, cropping, tint, and app-icon slot size.
Sites may withhold images, change their markup, or return unavailable/unsupported
artwork. The current-player selector is a conditional fallback, not a guarantee
for every future YouTube Music DOM version. Native Rich-player notifications
receive the icon fix; this change does not add a separate local-video thumbnail
loader to that service.

Android references: [adaptive icon design](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive),
[fractional insets](https://developer.android.com/reference/android/graphics/drawable/InsetDrawable),
and [MediaMetadata artwork](https://developer.android.com/reference/android/media/MediaMetadata).

## Verification

The JavaScript selection harness exercises nine scenarios using the actual
Kotlin-embedded helper and URL validator, including tiny-versus-cover choice,
aspect ratio, large images, unsafe URLs, late cover changes, and poster fallback.
JVM tests check referrer privacy and sampled decode bounds, alongside existing
private-publication and notification-owner regressions. Resource compilation
and the debug APK build check both icon resource paths.

Device checks are left to the user: Samsung notification/lock screen, track
changes, artwork arriving late, temporary connectivity failure, private tabs,
both players' small icons, app info, launcher masks and themed icons. Android's
launcher/system may retain an icon cache briefly after installing an update.
