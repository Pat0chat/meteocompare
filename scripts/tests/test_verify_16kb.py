"""Self-contained tests of the release 16 KiB gate (no Android SDK required)."""

import contextlib
import importlib.util
import io
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "verify-16kb-page-size.py"
spec = importlib.util.spec_from_file_location("page_size_gate", MODULE_PATH)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def sample_elf(page_alignment=16384):
    data = bytearray(128)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)  # e_phoff
    struct.pack_into("<H", data, 54, 56)  # e_phentsize
    struct.pack_into("<H", data, 56, 1)   # e_phnum
    struct.pack_into("<I", data, 64, 1)   # PT_LOAD
    struct.pack_into("<Q", data, 64 + 48, page_alignment)  # p_align
    return bytes(data)


def add_so(zf, path, contents, align_zip=False):
    info = zipfile.ZipInfo(path)
    info.compress_type = zipfile.ZIP_STORED
    if align_zip:
        name_length = len(path.encode("utf-8"))
        # 30 bytes for ZIP local header; 4 bytes for the extra-field header.
        padding = (gate.PAGE_SIZE - ((30 + name_length + 4) % gate.PAGE_SIZE)) % gate.PAGE_SIZE
        info.extra = struct.pack("<HH", 0xFFFF, padding) + b"\x00" * padding
    zf.writestr(info, contents)


class PageSizeGateTest(unittest.TestCase):
    def test_16k_apk_passes_and_logs_real_elf_and_zip_alignment(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "test.apk"
            with zipfile.ZipFile(path, "w") as zf:
                add_so(zf, "lib/arm64-v8a/libtest.so", sample_elf(), align_zip=True)
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(gate.verify_apk(path), 1)
            self.assertIn("[16KiB][APK] PASS", output.getvalue())
            self.assertIn("ZIP=aligned", output.getvalue())

    def test_apk_rejects_bad_zip_alignment(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "test.apk"
            with zipfile.ZipFile(path, "w") as zf:
                add_so(zf, "lib/arm64-v8a/libtest.so", sample_elf())
            with contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(gate.VerificationError, "not 16 KiB aligned"):
                    gate.verify_apk(path)

    def test_4k_elf_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "test.apk"
            with zipfile.ZipFile(path, "w") as zf:
                add_so(zf, "lib/arm64-v8a/libtest.so", sample_elf(4096), align_zip=True)
            with contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(gate.VerificationError, "PT_LOAD alignment below"):
                    gate.verify_apk(path)

    def test_aab_checks_bundle_alignment_and_elf(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "test.aab"
            # BundleConfig.optimizations.uncompressNativeLibraries:
            # enabled (field 1)=true; pageAlignment (field 2)=PAGE_ALIGNMENT_16K.
            native_config = bytes([0x08, 0x01, 0x10, 0x02])
            optimizations = bytes([0x12, len(native_config)]) + native_config
            config = bytes([0x12, len(optimizations)]) + optimizations
            with zipfile.ZipFile(path, "w") as zf:
                zf.writestr("BundleConfig.pb", config)
                add_so(zf, "base/lib/arm64-v8a/libtest.so", sample_elf())
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(gate.verify_aab(path), 1)
            self.assertIn("[16KiB][AAB] PASS BundleConfig.pb", output.getvalue())
            self.assertIn("[16KiB][AAB] PASS base/lib", output.getvalue())


if __name__ == "__main__":
    unittest.main()
