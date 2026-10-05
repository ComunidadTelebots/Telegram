# Android compatibility and upstream integration

Integrated DrKLO/Telegram `master` at
`f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6, version code 7112),
checked on 2026-10-05. This includes the intervening 12.10.4 and 12.10.5 updates.
The fork's skins, AMP viewer and Comunidad Telebots channel remain available.
`BuildVars.java` remains ignored and untracked; supply your own credentials locally.

## Supported build configuration

- Production modules: compile/target SDK 36 (Android 16), minimum SDK 21
  (Android 5.0). SDK23 variants require Android 6.0; the test application requires
  Android 8.0. Check the selected variant's merged manifest before distribution.
- Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.0, JDK 17 or later.
- Android SDK platform 36, NDK 27.2.12479018 and CMake 3.22.1.
- Native linker retains upstream's 16 KB page alignment.
- Native code now uses upstream's C++17 configuration and pinned Abseil/WAMR
  submodules. Initialize all submodules when updating an existing checkout.

Android 1.0–4.4 is **not supported**. Lowering `minSdkVersion` would not backport
Android framework APIs, AndroidX dependencies or native libraries. That range
requires a separate legacy client and a separate compatibility/security review.
Historical Android skin names describe appearance, not supported OS versions.

## Checkout and build

Initialize the pinned submodules before building. On Windows, use Git for Windows
with long paths enabled for this invocation:

```powershell
git -c core.longpaths=true submodule update --init --recursive
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatDebug --console=plain '-Pkotlin.incremental=false' '-Pandroid.injected.build.abi=arm64-v8a'
```

Configure local SDK, API credentials, Firebase files and signing as described in
the project README. Never publish builds using upstream placeholder credentials.
The Gradle media namespace configuration handles Windows path separators.
The command above packages ARM64 only. Omit the injected ABI property to build
the flavor's full ABI set. The debug APK is generated at
`TMessagesProj_App/build/intermediates/apk/afat/debug/app.apk`.

## Fork fixes

- Correct the official-channel avatar and UItem factory API signatures.
- Use `Browser.openUrl` and expose the AMP URL accessor across packages.
- Validate AMP hosts by hostname boundaries, support navigation on API 21–23,
  retain Safe Browsing defaults and disable WebView local-file/content access.
- Try the next AMP cache/original URL after main-frame failures; prevent late
  page callbacks from covering reader mode; release the WebView and animations.
- Keep the user's existing bubble radius when no skin has been selected.
- Unregister the correct back callback when the activity is destroyed on API 33.

## Verification

Validated on 2026-10-05:

- `:TMessagesProj_App:assembleAfatDebug` completed successfully (301 tasks),
  including native ARM64 and Java compilation. Log: `.gradle/upstream-12.10.6-build4.log`.
- APK metadata: 12.10.6, variant version code 71129, min SDK 21, target SDK 36,
  ARM64 only. APK signature verification and `zipalign -c -P 16 4` pass.
- Installed with `adb install -r -t` on the local Pixel_6 AVD (API 37, 16 KB
  pages, x86_64 system with ARM64 translation). LaunchActivity and the debug
  native iOS preview start successfully; the design-history selector opens.
  No crashes were recorded in the emulator crash buffer during these checks.
- AMP URL and client-layout Java regression tests pass, as do all three Python
  design-history tests. English and Spanish resources parse without duplicate names.

Old generated build directories in the OneDrive checkout caused access-denied
errors. They were moved into `.gradle/upstream-12.10.6-backup` and regenerated;
no source changes were required for those failures. Existing compiler warnings
remain. The produced APK uses debug signing and is not a release build.

Authenticated login/chat, Camera2 recording, calls, notifications and the full
supported Android/device matrix have not been validated. Historical designs
remain source references, not complete implementations (see `CLIENT_DESIGNS.md`).

The standalone URL regression test covers malformed URLs, credentials, ports,
case handling, query strings and spoofed cache hosts:

```powershell
New-Item -ItemType Directory -Path .gradle/fork-tests -Force
javac -d .gradle/fork-tests TMessagesProj/src/main/java/org/telegram/ui/web/AmpUrls.java Tools/tests/AmpUrlsTest.java
java -cp .gradle/fork-tests AmpUrlsTest
```

Before release, build and sign the release variants and run device tests on API 21, 23, 33,
35 and 36, including 16 KB devices. Exercise login, chat, media/calls, notifications,
back gestures, edge-to-edge insets, skins and AMP loading/fallback/closing.
Passing the URL tests or setting target SDK 36 does not establish device coverage.

Official Android guidance:
- https://developer.android.com/about/versions/16/setup-sdk
- https://developer.android.com/about/versions/16/behavior-changes-16
