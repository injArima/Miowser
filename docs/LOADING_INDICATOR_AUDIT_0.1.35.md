# Material 3 Expressive loading indicators in 0.1.35

| Surface | Loading state |
| --- | --- |
| App startup | Contained Expressive indicator |
| Browser address bar | Compact Expressive indicator while the page loads |
| Media detector | Compact Expressive indicator while scanning; standard indicator for subtitle preview |
| Ad blocker | Compact Expressive indicator in Refresh all |
| Hub folder import | Compact Expressive indicator in the import dialog |
| Library health | Contained Expressive indicator while checking records |
| Video records sheet | Expressive indicator while records load |
| Backup and restore | Contained Expressive indicator while work is in progress |

The shared component uses one size scale and supplies a static indicator when Reduce motion is enabled. Measured download progress, filter-list byte progress, video watch progress, and privacy dashboard host counts keep their linear bars. The native WebView pull-to-refresh spinner retains its swipe feedback and uses the app's theme color. The rich and WebView players retain their existing loading visuals.

Sources: [Material 3 loading indicator](https://m3.material.io/components/loading-indicator/overview), [Compose LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/LoadingIndicator.composable), [Compose ContainedLoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/ContainedLoadingIndicator.composable), [Compose progress guidance](https://developer.android.com/develop/ui/compose/components/progress).
