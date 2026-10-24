# Offline Video Assistant fixtures

Only packaged in debug/development builds, through `src/videoPrototype`.
The separate **Miowser Video Lab** launcher opens these local pages. No normal
browser tabs or media repositories are used. All WebView network requests are
restricted to these bundled assets on two local HTTPS origins.

`sample.webm` is an original generated 45-second test pattern with a quiet tone,
320×180 at 12 fps, VP8/Opus, 700,344 bytes. It contains no third-party media.
SHA-256: `956dccf073d5db119ff8c1c4bdee1d6f69dc5bc5838ac9e690305a6237d08725`.

Generated with FFmpeg 7.1 (the tool is local-only and is not shipped):

```text
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=12 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 45 -c:v libvpx -deadline realtime -cpu-used 5 -b:v 100k -g 12 -c:a libopus -b:a 16k -cluster_time_limit 1000 sample.webm
```

The direct fixture uses this file as its source. The cross-origin fixture puts
that video inside `frame.miowser.invalid`. The MediaSource fixture appends the
same bounded asset into one SourceBuffer, creating a real `blob:` MediaSource.
It intentionally does not simulate adaptive quality, DRM, or a network service.
No general-purpose blob-fetch implementation is introduced.
