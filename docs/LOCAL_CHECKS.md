# Local checks and build inputs

Use JDK 25, Android SDK Platform 37.0 (`platforms;android-37.0`) and Build Tools 36.0.0. The project compiles app bytecode for Java 17. The Gradle wrapper is 9.7.0; its distribution SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`. The checked-in wrapper JAR must hash to `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` ([Gradle's published checksum](https://gradle.org/release-checksums/)).

On Windows, after installing the SDK and setting `JAVA_HOME` to a compatible JDK:

```powershell
if ((Get-FileHash gradle/wrapper/gradle-wrapper.jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne '7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d') { throw 'Unverified Gradle wrapper' }
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :benchmark:assembleBenchmark --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Local checks failed' }
```

The same tasks run in `.github/workflows/local-checks.yml` on a clean checkout. Unit-test XML, lint reports and R8 mappings are retained as CI artifacts even when a check fails. The release APK is intentionally unsigned; this workflow does not publish or run device tests.

Versions are pinned in `gradle/libs.versions.toml` and the benchmark module. Dependency lockfiles and `gradle/verification-metadata.xml` protect resolved module versions and artifact checksums. Review changes to these files with the corresponding dependency update. To refresh them deliberately, use Gradle's `--write-locks --write-verification-metadata sha256` with the full check command above, review the resulting diff, then run the normal command again without write flags.

Android Studio downloads source and Javadoc JARs incrementally for code navigation. Gradle trusts only these two IDE archive types so their later downloads do not interrupt sync; compiled dependencies, plugins, metadata, and other artifacts still require checksums. Source and Javadoc JARs are therefore not checksum-verified. If another artifact fails verification, inspect its repository and checksum before changing the metadata.

Device acceptance remains separate: browser certificate choices, redirects/authenticated downloads, subtitle attachment, concurrent playback/downloads and release-like behavior need the publisher's physical-device check.

The native request change covers subtitle text, speed-dial metadata/icons and filter-list refreshes. It deliberately leaves download and Media3 transport headers/range behavior intact. Cross-host authenticated-transfer redirect policy still needs dedicated Android device or loopback instrumentation coverage before it can be changed safely.
