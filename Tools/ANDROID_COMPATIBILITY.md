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

## Application packages and channels

Set `APP_CHANNEL` in `gradle.properties` or pass `-PAPP_CHANNEL=beta` to Gradle.
The default is `dev`. Only the following channel names are accepted:

| Channel | Application ID |
| --- | --- |
| dev | org.comunidadtelebots.dev |
| alfa | org.comunidadtelebots.alfa |
| beta | org.comunidadtelebots.beta |
| rc | org.comunidadtelebots.rc |
| stable | org.comunidadtelebots.stable |

Channels are independent of Git branches, ABI flavors and debug/release build
types. All APK modules use the same channel mapping; the old `.beta`/`.web`
build-type suffixes are removed. Java namespaces remain unchanged for JNI and
upstream compatibility. Selecting `stable` alone does not produce a release build.

For example, append `-PAPP_CHANNEL=stable` to an `assembleAfatRelease` invocation.
Different channels can coexist, each with separate app data, provider authorities,
Android account types and contact actions. An APK with a new package does not
update or inherit login/data from the old `org.telegram.messenger.beta` app.
Build outputs use the same variant directory, so copy each APK before building
another channel.

`ENABLE_FIREBASE=auto` enables Google Services only when the module's root
`google-services.json` registers the selected application ID. The user-supplied
configuration for dev, alfa, beta, rc and stable is installed in `TMessagesProj_App/google-services.json`;
all five channel registrations are present. Register each required
package and signing certificate in your own Firebase project and replace the
module's configuration. `-PENABLE_FIREBASE=false` explicitly disables integration;
`true` forces the Google plugin to validate its configuration (including files
in variant source sets). Do not just edit package names in Telegram's Firebase
files. Huawei builds also need matching AppGallery/AG Connect configuration.

Channel verification on 2026-10-05: the dev ARM64 debug APK compiled (301 tasks),
passed signature/16 KB ZIP alignment checks, installed alongside the old beta
package and launched on the API 37 emulator without a recorded crash. All five
channels passed merged-manifest, provider-authority and contact-resource checks;
an invalid channel was rejected. Only dev has been packaged and run for this
change; release signing, FCM delivery and authenticated contact sync remain untested.
The final rebuild includes the user-supplied Firebase dev configuration. Generated
Google Services resources match its app registration and the emulator reports
`FirebaseApp initialization successful`. Google Services resource generation was also verified for alfa, beta, rc and stable using the
subsequently supplied registrations; all five channels select their own Firebase registration.
The retained dev APK is `.gradle/channel-apks/telebots-dev-12.10.6-debug.apk`.

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
