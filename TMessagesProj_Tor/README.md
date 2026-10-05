# Embedded Tor dependency

Tor 0.4.9.13 native binaries are extracted from Guardian Project's published AAR,
with its SHA-256 checked by the build. TorService.java comes from
the matching tor-android-0.4.9.13-sources.jar. Local lifecycle fixes retry the
control-socket connection, honor shutdown during startup, and serialize native
runs on the worker thread until native teardown finishes. This small module recompiles
that Java service with Java 17 and SDK 36, avoiding the published Java 24 bytecode
and SDK 37.1 metadata. The app loads this feature only on Android API 24+.

Source and binaries: https://github.com/guardianproject/tor-android
Published artifacts: https://github.com/guardianproject/gpmaven/tree/master/info/guardianproject/tor-android/0.4.9.13
License: BSD-3-Clause (see LICENSE). Upstream Tor and bundled dependency notices
remain applicable; see the upstream source distribution for dependency licenses.

When upgrading, update the sources, binary coordinate, checksum, version string
and license notices together; recheck API 24 and 16 KB compatibility.

The module also extracts the native libraries from HevSocks5Tunnel **2.18.0**,
published September 27, 2026. `hev/htproxy/TProxyService.java` is the matching
unmodified JNI contract. The release AAR SHA-256 is checked before extraction.
Source/release: https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.18.0
License: MIT, reproduced in LICENSE-hev.

The VPN passes its own TUN descriptor to Hev; Hev does not own or close this
descriptor. ICMP echo synthesis is disabled. Mapped DNS translates names to
SOCKS requests resolved through Tor. Other UDP requests receive no Tor transport.
