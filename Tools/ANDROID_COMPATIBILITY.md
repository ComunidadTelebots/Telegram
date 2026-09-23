# Android compatibility and upstream integration

Integrated DrKLO/Telegram `master` at `9552e5541` (12.10.3, version code 7089).
The fork's skins, AMP viewer and Comunidad Telebots channel remain available.
`BuildVars.java` remains ignored and untracked; supply your own credentials locally.

## Supported build configuration

- Production modules: compile/target SDK 36 (Android 16), minimum SDK 21
  (Android 5.0). SDK23 variants require Android 6.0; the test application requires
  Android 8.0. Check the selected variant's merged manifest before distribution.
- Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.0, JDK 17 or later.
- Android SDK platform 36, NDK 27.2.12479018 and CMake 3.22.1.
- Native linker retains upstream's 16 KB page alignment.

Android 1.0–4.4 is **not supported**. Lowering `minSdkVersion` would not backport
Android framework APIs, AndroidX dependencies or native libraries. That range
requires a separate legacy client and a separate compatibility/security review.
Historical Android skin names describe appearance, not supported OS versions.

## Checkout and build

Initialize the pinned submodules before building. On Windows, use Git for Windows
with long paths enabled for this invocation:

```powershell
git -c core.longpaths=true submodule update --init --recursive
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj:compileDebugJavaWithJavac --console=plain
```

Configure local SDK, API credentials, Firebase files and signing as described in
the project README. Never publish builds using upstream placeholder credentials.
The Gradle media namespace configuration handles Windows path separators.

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

Validated on 2026-09-23: `:TMessagesProj:compileDebugJavaWithJavac` and
`:TMessagesProj_App:processAfatDebugMainManifest` completed successfully (168 tasks).
The merged `afatDebug` manifest declares min SDK 21, target SDK 36 and enables
the predictive-back callback. The AMP regression test passes; English and Spanish
string resources parse without duplicate names. APK/native and device testing
have not been completed.

The standalone URL regression test covers malformed URLs, credentials, ports,
case handling, query strings and spoofed cache hosts:

```powershell
New-Item -ItemType Directory -Path .gradle/fork-tests -Force
javac -d .gradle/fork-tests TMessagesProj/src/main/java/org/telegram/ui/web/AmpUrls.java Tools/tests/AmpUrlsTest.java
java -cp .gradle/fork-tests AmpUrlsTest
```

Before release, run the full APK/native build and device tests on API 21, 23, 33,
35 and 36, including 16 KB devices. Exercise login, chat, media/calls, notifications,
back gestures, edge-to-edge insets, skins and AMP loading/fallback/closing.
Passing the URL tests or setting target SDK 36 does not establish device coverage.

Official Android guidance:
- https://developer.android.com/about/versions/16/setup-sdk
- https://developer.android.com/about/versions/16/behavior-changes-16
