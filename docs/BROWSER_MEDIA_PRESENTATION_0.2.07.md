# Browser media notification parity — 0.2.07

## The missing layer

The supplied screenshots show Brave publishing a track title, artist, cover and previous/next actions. Miowser instead falls back to the document title, generic browser text and play/pause. The old host harness always supplied a working navigator.mediaSession; it never tested the actual missing-API WebView environment.

Chromium's Android WebView embedder explicitly disables the Media Session API. Miowser's hookSession returned early when navigator.mediaSession was absent. Consequently websites feature-detected no API, did not register media handlers or metadata, and Android received only the underlying HTML media information. This is a verified source-level explanation of that missing path, not a captured diagnosis of the user's physical device. See [Chromium's embedder implementation](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/lib/aw_main_delegate.cc).

## Brave comparison

Re-inspected the supplied Bravearm64Universal.apk through REA/JADX without installing or running it. SHA-256: 256cc1fbc1b57f8cf449d15c51fa9535f187ea1e48130d4d332f24e24f4a0665; package version 1.99.56.

- MediaSessionHelper.createMediaSessionObserver creates hzb.
- hzb.a(HashSet) stores the advertised media-session actions and refreshes the helper.
- hzb.e(MediaMetadata) replaces the current metadata and refreshes its presentation.
- hzb.f(MediaPosition) updates the notification's media position.
- hzb.b(List) ranks MediaImage candidates and asks the WebContents-backed downloader for the selected URL, updating notification artwork on completion.

Retained REA method evidence: ev_4123e109c4b8f8281a136364d2f677825dc72140372b5ccda1bfd986d7e8bfc5, ev_b0add1a2cb04b4804ea90e2da3d2dea55081834c90d99ff1e06a29a96faa813b, ev_598e981fc8c58bb745c4243e902d93d41a7e6f06726afa51a880c6532829e49a, ev_d2c8d25e23a1e23024dc722ad1589d2f4cb07bf8ed18f77077d07161b303b246, ev_f0af92325a18f05575c7cb307d211e9bc8fb3abcb0e4938e59964ab062cd6b58.

This establishes the inspected APK's static pipeline, not runtime success on every site. Miowser cannot use Brave's native WebContents API through public WebView; its adapter connects website registrations to the existing authenticated frame bridge instead.

## Implementation reference

| File | Responsibility |
| --- | --- |
| MediaSessionFallbackScript.kt | Before site scripts run, supply navigator.mediaSession and MediaMetadata only when the native session API is missing. Store title/artist/album/artwork, playback state, supported action handlers and validated position state. Preserve callback replacement/removal and registrations across BFCache. Unsupported non-playback actions remain unsupported. |
| SiteMediaPresentationScript.kt | Cache only YouTube Music's current player-bar root. Read fallback title/byline and enabled previous/next controls. Revalidate a button before an explicit system command; do not choose controls from queues/recommendations. |
| MediaArtworkScript.kt | Prefer ranked website Media Session images, then a current player-bar image including direct yt-img-shadow/img.image markup and resolved currentSrc, then the selected media's poster. |
| PageMediaScript.kt | Compose the adapter at document start, retain real native APIs, publish supported capabilities and route commands to website handlers first. Attach an early position sample to the first real media owner. |
| BrowserPlaybackService.kt | Publish title/artist/album and display metadata, ART/ALBUM_ART/DISPLAY_ICON bitmaps, MediaStyle large icon, transport category and the current session activity. Existing PlaybackState action flags drive modern Android previous/next controls. |

The adapter implements playback data/control registration; it does not add capture, slide, chapter, advertising or other unimplemented Media Session features, and does not create a second audio/video player. It is not a complete browser engine or an unrestricted native command interface. The frame bridge still requires a real played media owner and its matching document/navigation/source token before executing supported commands. Metadata alone never starts playback or acquires a system session. Existing private publication removes site title/artist/album/artwork/URLs before artwork loading or Android publication.

Artwork still uses the existing asynchronous, sampled 512 px decoder, bounded redirects/retries, 2 MiB encoded limit and 4 MiB memory cache. Cookies and authentication are not forwarded. Network failures, authenticated-only or unsupported image formats can still prevent an image from loading. No new full-document thumbnail scans or network polling are introduced. The YouTube Music DOM fallback is conditional on its current markup; standard website registrations take precedence.

The Android surface controls its background crop, tint, progress animation and output-device UI. Miowser supplies data and supported commands rather than copying a notification layout. [Android's media controls guide](https://developer.android.com/media/implement/surfaces/mobile) explains action derivation from PlaybackState; the [Media Session specification](https://www.w3.org/TR/mediasession/) defines the website metadata and handler interfaces.

## Verification

The expanded host harness runs both native-API and missing-API environments. It checks cover/title/artist/album publication, previous/next dispatch and handler replacement/removal, in-place artwork changes, position validation and early registration, BFCache identity, metadata-only inactivity, hidden-tab command rejection and a locked-provider YouTube Music fallback with disabled buttons. Existing bridge authentication, source replacement, lifecycle and private-publication checks remain in place.

Results:

- `:app:assembleDebug :app:testDebugUnitTest`: successful; 861 JVM tests, zero failures/errors/skips.
- `scripts/check-page-media.cjs`: 35 scenarios, including both native and missing Media Session APIs.
- `scripts/check-live-video-sessions.cjs`: 142 scenarios.
- `scripts/check-browser-page-interactions.cjs`: 25 scenarios.
- `scripts/check-media-discovery.cjs`: six scenarios and two composed-script syntax checks.
- `scripts/verify-media-artwork.cjs`: nine artwork-selection scenarios.

No emulator, physical-device, instrumentation or runtime notification rendering test was run. Device review should include a fresh YouTube Music page (so document-start feature detection runs), two track changes, next/previous and seeking from the system surface, pause/resume, background playback, notification tap routing, private tabs and switching tabs. Also check a page with no next/previous capability to ensure those controls are absent.
