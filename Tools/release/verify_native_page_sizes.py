#!/usr/bin/env python3
"""Verify native libraries in a release APK for modern 16 KiB Android devices.

This validates ELF PT_LOAD p_align, not only the ZIP entry offset. It is intentionally
a release gate: any unknown or incompatible native library fails the build.
"""
import argparse
import struct
import sys
import zipfile


def elf_load_alignments(data):
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("Not an ELF shared object")
    bits, endian = data[4], data[5]
    if bits not in (1, 2) or endian not in (1, 2):
        raise ValueError("Unsupported ELF header format")
    order = "<" if endian == 1 else ">"
    if bits == 2:
        phoff = struct.unpack_from(order + "Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from(order + "HH", data, 54)
        fmt = order + "IIQQQQQQ"
    else:
        phoff = struct.unpack_from(order + "I", data, 28)[0]
        phentsize, phnum = struct.unpack_from(order + "HH", data, 42)
        fmt = order + "IIIIIIII"
    width = struct.calcsize(fmt)
    if not phnum or phentsize < width or phnum > 1024:
        raise ValueError("Invalid ELF program header table")
    result = []
    for i in range(phnum):
        start = phoff + i * phentsize
        if start + width > len(data):
            raise ValueError("Truncated ELF program headers")
        row = struct.unpack_from(fmt, data, start)
        if row[0] == 1:  # PT_LOAD
            result.append(row[7])
    if not result:
        raise ValueError("ELF does not contain loadable segments")
    return result


def inspect(apk):
    libraries = 0
    failures = []
    abis = set()
    with zipfile.ZipFile(apk) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Corrupt APK member: {bad}")
        for name in archive.namelist():
            if not name.startswith("lib/") or not name.endswith(".so"):
                continue
            libraries += 1
            abis.add(name.split("/")[1])
            try:
                alignments = elf_load_alignments(archive.read(name))
                if any(a < 16384 or a % 16384 for a in alignments):
                    failures.append((name, alignments))
            except (ValueError, struct.error) as exc:
                failures.append((name, str(exc)))
    if not libraries:
        raise ValueError("APK has no native libraries")
    if "arm64-v8a" not in abis:
        raise ValueError("APK is missing ARM64 native libraries for Samsung devices")
    return libraries, abis, failures


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    args = parser.parse_args()
    try:
        libraries, abis, failures = inspect(args.apk)
        print(f"Native libraries checked: {libraries}; ABIs: {', '.join(sorted(abis))}")
        if failures:
            for name, align in failures:
                print(f"16-KB incompatible: {name}: {align}", file=sys.stderr)
            sys.exit(1)
        print("PASS: every loadable ELF segment supports 16-KB memory pages")
    except (OSError, ValueError, zipfile.BadZipFile) as err:
        sys.exit(f"APK native compatibility check failed: {err}")
