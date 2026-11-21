import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from porter.cli import main


class LibraryCliTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.payload = self.root / "game-payload"
        self.payload.mkdir()
        self.env = patch.dict(os.environ, {"NX2A_HOME": str(self.root / "home")})
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()

    def invoke(self, *args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main(list(args))
        return code, output.getvalue()

    def test_register_list_manifest_and_remove(self):
        code, out = self.invoke("library", "register", "--title", "CLI Game", "--platform", "switch",
                                "--payload", str(self.payload), "--id", "cli-game", "--json")
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["id"], "cli-game")

        code, out = self.invoke("library", "list", "--json")
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)[0]["title"], "CLI Game")

        code, out = self.invoke("library", "manifest", "cli-game", "--json")
        self.assertEqual(code, 0)
        self.assertFalse(json.loads(out)["runtime"]["shared_runtime_ready"])

        code, out = self.invoke("library", "remove", "cli-game", "--json")
        self.assertEqual(code, 0)
        self.assertFalse(json.loads(out)["payload_deleted"])
        self.assertTrue(self.payload.exists())


if __name__ == "__main__":
    unittest.main()
