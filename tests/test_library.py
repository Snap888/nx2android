import json
import tempfile
import unittest
from pathlib import Path

from porter import library


class LibraryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.catalog = self.root / "library.json"
        self.payload = self.root / "payload"
        self.payload.mkdir()

    def tearDown(self):
        self.tmp.cleanup()

    def test_register_persists_and_preserves_settings_on_update(self):
        game = library.register("Test Game", "switch", self.payload, game_id="0100abcd", path=self.catalog)
        self.assertEqual(game["launch_state"], "legacy_payload")
        library.update_settings("0100abcd", {"resolution": "720p"}, self.catalog)
        updated = library.register("Renamed", "switch", self.payload, game_id="0100abcd", path=self.catalog)
        self.assertEqual(updated["settings"], {"resolution": "720p"})
        self.assertEqual(len(library.load(self.catalog)["games"]), 1)

    def test_missing_payload_is_rejected(self):
        with self.assertRaises(library.LibraryError):
            library.register("Missing", "switch", self.root / "absent", path=self.catalog)

    def test_remove_does_not_delete_payload(self):
        library.register("Test Game", "switch", self.payload, game_id="test", path=self.catalog)
        result = library.remove("test", self.catalog)
        self.assertFalse(result["payload_deleted"])
        self.assertTrue(self.payload.exists())
        self.assertEqual(library.load(self.catalog)["games"], [])

    def test_manifest_never_claims_shared_runtime_ready(self):
        game = library.register("Test Game", "switch", self.payload, game_id="test", path=self.catalog)
        self.assertFalse(library.manifest(game)["runtime"]["shared_runtime_ready"])

    def test_corrupt_catalog_is_reported(self):
        self.catalog.write_text("{broken", encoding="utf-8")
        with self.assertRaises(library.LibraryError):
            library.load(self.catalog)


if __name__ == "__main__":
    unittest.main()
