#!/usr/bin/env python3
"""Verify Android 16 KiB page-size readiness for release APK/AAB artifacts.

Checks:
- every packaged ELF shared object's PT_LOAD p_align is >= 16 KiB;
- every uncompressed .so in an APK starts at a 16 KiB ZIP data offset;
- an AAB's BundleConfig.pb requests PAGE_ALIGNMENT_16K for uncompressed native libs.

No Android SDK/NDK executable is required, which keeps the release gate reproducible
on CI as well as on developer machines.
"""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path

PAGE_SIZE = 16 * 1024
PT_LOAD = 1
PAGE_ALIGNMENT_16K = 2


class VerificationError(RuntimeError):
    pass


def u16(data: bytes, offset: int, endian: str) -> int:
    return struct.unpack_from(endian + "H", data, offset)[0]


def u32(data: bytes, offset: int, endian: str) -> int:
    return struct.unpack_from(endian + "I", data, offset)[0]


def u64(data: bytes, offset: int, endian: str) -> int:
    return struct.unpack_from(endian + "Q", data, offset)[0]


def elf_load_alignments(data: bytes, name: str) -> list[int]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise VerificationError(f"{name}: not a valid ELF shared object")

    elf_class = data[4]
    byte_order = data[5]
    if byte_order == 1:
        endian = "<"
    elif byte_order == 2:
        endian = ">"
    else:
        raise VerificationError(f"{name}: unsupported ELF byte order {byte_order}")

    if elf_class == 1:  # ELF32
        phoff = u32(data, 28, endian)
        phentsize = u16(data, 42, endian)
        phnum = u16(data, 44, endian)
        align_offset = 28
    elif elf_class == 2:  # ELF64
        phoff = u64(data, 32, endian)
        phentsize = u16(data, 54, endian)
        phnum = u16(data, 56, endian)
        align_offset = 48
    else:
        raise VerificationError(f"{name}: unsupported ELF class {elf_class}")

    alignments: list[int] = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        if offset + phentsize > len(data):
            raise VerificationError(f"{name}: truncated program header table")
        p_type = u32(data, offset, endian)
        if p_type != PT_LOAD:
            continue
        alignment = u32(data, offset + align_offset, endian) if elf_class == 1 else u64(data, offset + align_offset, endian)
        alignments.append(alignment)

    if not alignments:
        raise VerificationError(f"{name}: no PT_LOAD segment found")
    return alignments


def verify_elf(data: bytes, name: str) -> None:
    bad = [align for align in elf_load_alignments(data, name) if align < PAGE_SIZE]
    if bad:
        formatted = ", ".join(hex(value) for value in bad)
        raise VerificationError(f"{name}: PT_LOAD alignment below 16 KiB ({formatted})")


def local_zip_data_offset(archive: Path, info: zipfile.ZipInfo) -> int:
    with archive.open("rb") as raw:
        raw.seek(info.header_offset)
        header = raw.read(30)
    if len(header) != 30 or header[:4] != b"PK\x03\x04":
        raise VerificationError(f"{archive}: invalid local ZIP header for {info.filename}")
    filename_len = struct.unpack_from("<H", header, 26)[0]
    extra_len = struct.unpack_from("<H", header, 28)[0]
    return info.header_offset + 30 + filename_len + extra_len


def native_entries(zf: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    return [
        info
        for info in zf.infolist()
        if info.filename.endswith(".so") and ("/lib/" in f"/{info.filename}" or info.filename.startswith("lib/"))
    ]


def verify_apk(path: Path) -> int:
    count = 0
    print(f"[16KiB] Inspecting APK {path}")
    with zipfile.ZipFile(path) as zf:
        entries = native_entries(zf)
        for info in entries:
            data = zf.read(info)
            verify_elf(data, info.filename)
            count += 1
            alignments = elf_load_alignments(data, info.filename)
            zip_alignment = "compressed (not applicable)"
            if info.compress_type == zipfile.ZIP_STORED:
                offset = local_zip_data_offset(path, info)
                if offset % PAGE_SIZE != 0:
                    raise VerificationError(
                        f"{info.filename}: uncompressed ZIP data offset {offset} is not 16 KiB aligned"
                    )
                zip_alignment = f"aligned (offset={offset})"
            print(
                f"[16KiB][APK] PASS {info.filename} "
                f"PT_LOAD(min)={min(alignments)} ZIP={zip_alignment}"
            )
        if not entries:
            print("[16KiB][APK] No packaged native libraries (ELF/ZIP checks not applicable)")
    return count


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        if offset >= len(data) or shift >= 70:
            raise VerificationError("Malformed protobuf varint in BundleConfig.pb")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7


def protobuf_fields(data: bytes) -> list[tuple[int, int, int | bytes]]:
    fields: list[tuple[int, int, int | bytes]] = []
    offset = 0
    while offset < len(data):
        key, offset = read_varint(data, offset)
        field_number = key >> 3
        wire_type = key & 0x7
        if wire_type == 0:
            value, offset = read_varint(data, offset)
            fields.append((field_number, wire_type, value))
        elif wire_type == 1:
            if offset + 8 > len(data):
                raise VerificationError("Truncated fixed64 field in BundleConfig.pb")
            offset += 8
        elif wire_type == 2:
            length, offset = read_varint(data, offset)
            end = offset + length
            if end > len(data):
                raise VerificationError("Truncated length-delimited field in BundleConfig.pb")
            fields.append((field_number, wire_type, data[offset:end]))
            offset = end
        elif wire_type == 5:
            if offset + 4 > len(data):
                raise VerificationError("Truncated fixed32 field in BundleConfig.pb")
            offset += 4
        else:
            raise VerificationError(f"Unsupported protobuf wire type {wire_type} in BundleConfig.pb")
    return fields


def submessage(data: bytes, field_number: int) -> bytes:
    for number, wire, value in protobuf_fields(data):
        if number == field_number and wire == 2 and isinstance(value, bytes):
            return value
    raise VerificationError(f"BundleConfig.pb missing message field {field_number}")


def varint_field(data: bytes, field_number: int) -> int | None:
    for number, wire, value in protobuf_fields(data):
        if number == field_number and wire == 0 and isinstance(value, int):
            return value
    return None


def verify_aab(path: Path) -> int:
    count = 0
    print(f"[16KiB] Inspecting AAB {path}")
    with zipfile.ZipFile(path) as zf:
        try:
            config = zf.read("BundleConfig.pb")
        except KeyError as exc:
            raise VerificationError(f"{path}: BundleConfig.pb missing") from exc

        optimizations = submessage(config, 2)
        uncompress_native = submessage(optimizations, 2)
        enabled = varint_field(uncompress_native, 1)
        alignment = varint_field(uncompress_native, 2)
        if enabled != 1:
            raise VerificationError(f"{path}: uncompressed native libraries are not enabled in BundleConfig.pb")
        if alignment != PAGE_ALIGNMENT_16K:
            raise VerificationError(
                f"{path}: BundleConfig native page alignment is {alignment!r}, expected PAGE_ALIGNMENT_16K (2)"
            )
        print("[16KiB][AAB] PASS BundleConfig.pb: native PAGE_ALIGNMENT_16K")

        entries = native_entries(zf)
        for info in entries:
            data = zf.read(info)
            verify_elf(data, info.filename)
            count += 1
            print(f"[16KiB][AAB] PASS {info.filename} PT_LOAD(min)={min(elf_load_alignments(data, info.filename))}")
        if not entries:
            print("[16KiB][AAB] No packaged native libraries (ELF checks not applicable)")
    return count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, help="Release APK to verify")
    parser.add_argument("--aab", type=Path, help="Release AAB to verify")
    args = parser.parse_args()

    if not args.apk and not args.aab:
        parser.error("at least one of --apk or --aab is required")

    try:
        if args.apk:
            if not args.apk.is_file():
                raise VerificationError(f"APK not found: {args.apk}")
            count = verify_apk(args.apk)
            print(f"OK: APK 16 KiB packaging/alignment verified ({count} native libraries).")
        if args.aab:
            if not args.aab.is_file():
                raise VerificationError(f"AAB not found: {args.aab}")
            count = verify_aab(args.aab)
            print(f"OK: AAB PAGE_ALIGNMENT_16K + ELF alignment verified ({count} native libraries).")
    except (VerificationError, zipfile.BadZipFile) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
