"""Tests for tools/pdf-to-md.py — Romanian + math glyph fixes."""
import unittest
from importlib.util import spec_from_file_location, module_from_spec
from pathlib import Path

_HERE = Path(__file__).parent
_spec = spec_from_file_location("pdf_to_md", _HERE / "pdf-to-md.py")
_mod = module_from_spec(_spec)
_spec.loader.exec_module(_mod)
normalize_text = _mod.normalize_text


class TestNormalizeText(unittest.TestCase):
    def test_romanian_glyph_combining_marks(self):
        # PDFBox/pypdf output: combining breve + cedilla floating wrong.
        # Expected: composed Romanian letters.
        raw = "Probabilit˘ at ¸i"  # breve + cedilla scattered
        self.assertIn("Probabilități", normalize_text(raw))

    def test_micro_sign_left_as_is(self):
        # µ (U+00B5 micro sign) → leave as-is. Greek mu is U+03BC but
        # the math is readable either way; don't force-convert.
        raw = "µ = 0"
        self.assertIn("µ", normalize_text(raw))

    def test_whitespace_collapse(self):
        # Multi-space + newline runs collapse to single space, preserve
        # paragraph breaks (double newline).
        raw = "foo  bar\n\nbaz   qux"
        out = normalize_text(raw)
        self.assertEqual(out.count("\n\n"), 1)
        self.assertNotIn("  ", out)

    def test_glyph_fix_table_includes_sht(self):
        # ¸ s → ș and ¸ t → ț (cedilla glyph after letter)
        self.assertIn("ș", normalize_text("e¸s antion"))
        self.assertIn("ț", normalize_text("func¸t ie"))


if __name__ == "__main__":
    unittest.main()
