# Miowser

Miowser is an Android browser built for discovering, playing, downloading, and organizing web media. It combines everyday browsing with media detection, two purpose-built playback experiences, a folder-based Library, private browsing, and a capable ad blocker—all in one app.

## What makes Miowser different

| Feature | What it does | How it helps |
| --- | --- | --- |
| **Media-aware browsing** | Detects playable video, audio, HLS, DASH, subtitles, and available stream qualities on supported pages. | You can move from a webpage to the right stream, subtitle, player, or download without manually hunting for media links. |
| **One-tap video assistant** | Offers an enhanced, gesture-friendly player for meaningful videos found inside webpages while leaving DRM-protected playback in the website. | Gives web video better controls without forcing every video through a separate player or interrupting protected content. |
| **Rich media player** | Plays detected streams, local files, and downloads with quality selection, audio and subtitle tracks, gestures, PiP, background audio, playlists, aspect-ratio controls, and remembered preferences. | Turns media found on the web into a comfortable, consistent viewing experience. |
| **Media Library** | Organizes videos into primary folders and subfolders, remembers progress and per-folder sorting, supports grid or list layouts, and can link device folders without copying files. | Keeps web, downloaded, and local media together while preserving the files in their original location. |
| **Smart media recovery** | Can revisit a video's source when its old link expires, refresh the entry, and keep it in its original Library folder. | Reduces dead entries without losing organization or watch progress. |
| **Flexible downloads** | Downloads direct files and supported HLS or DASH streams, with pause, resume, retry, recovery, and configurable destinations. | Handles more than simple file links and keeps long downloads manageable. |
| **Download-folder reconnection** | Saves the chosen default or custom download location and can reconnect restored download records to files already on the device. | After reinstalling or restoring a backup, existing downloads can return without being located one by one. |
| **Built-in ad blocking** | Supports standard and strict site modes, filter lists, custom rules, cosmetic filtering, per-site controls, and visibility into which lists blocked a page. | Gives users practical control over ads and trackers while making overblocking easier to diagnose. |
| **Private, portable setup** | Provides private tabs and user-controlled local backups for settings, Library records, download records, bookmarks, speed dials, site choices, and player preferences. | Keeps personal browsing separate and makes moving or restoring the app less painful without uploading a backup to a Miowser account. |

## Browser features

- Address-bar and homepage suggestions from bookmarks, history, and supported search engines
- Multiple search engines, including a custom search option
- Regular and private tabs, tab previews, session restore, and inactive-tab archiving
- Bookmarks, browsing history search, speed dial, and Android's **Search with Miowser** text action
- Per-site privacy controls, permissions, desktop mode, hard reload, cache clearing, and cookie clearing
- Find in page, zoom, text size, sharing, printing/PDF, and pull to refresh
- Camera, microphone, and location permission handling
- AMOLED theme and accessibility options such as reduced motion

## Media and playback

- Media detection grouped by video, audio, stream, subtitle, and page-rendered media
- HLS rendition grouping and in-player quality switching where supported
- Rich-player subtitle and audio-track selection, external subtitles, subtitle delay, and subtitle styling
- Seek, brightness, volume, playback-speed, aspect-ratio, rotation, and lock gestures
- Picture-in-picture and audio-only background playback
- Playback progress, playlists, generated thumbnails, and resume support

## Downloads and Library

- Direct, HLS, DASH, encrypted HLS, and supported page-rendered downloads
- Download queue with pause, resume, retry, cancel, recovery, and duplicate handling
- Default `Downloads/Miowser` destination or a user-selected folder
- Automatic and manual reconnection of restored records to existing files
- Folder and subfolder organization, per-folder sorting, grid/list views, rules, imports, undoable actions, and duplicate cleanup
- Recursive device-folder imports that link files without copying them

## Ad blocker

- EasyList, EasyPrivacy, and Peter Lowe's list included by default
- Additional filter-list URLs, bulk import, duplicate detection, renaming, individual refresh, and per-list progress
- Custom network and cosmetic rules with an in-app usage guide
- Per-site Standard, Strict, or Off modes
- Blocked-page explanation showing the responsible filter lists

## Backup and privacy

Miowser supports manual and scheduled local backups to a location chosen by the user. Backups can include app settings, Library and completed-download records, bookmarks, speed dials, site choices, ad-blocker configuration, and player preferences. They do not include browsing history, private activity, cookies, logins, open tabs, or the video files themselves.

Miowser does not include a developer-operated account, analytics SDK, or advertising SDK. Websites and user-configured search, media, and filter-list providers can still receive network requests made while using their services.

Read the [privacy policy](https://injarima.github.io/Miowser/privacy-policy.html).

## Build

- Android Studio with Android SDK 37 and JDK 17 or newer
- Minimum Android API 26; target API 37

Open the project in Android Studio, or run:

```shell
./gradlew :app:assembleDebug
```

Release builds use R8 and resource shrinking. This repository does not contain the release signing key.

## Project status

Miowser is under active development. Website compatibility can vary, and some media—especially DRM-protected content—must continue to play through the website's own player.

No source-code license has been selected yet. Publication of this repository does not grant additional reuse rights.
