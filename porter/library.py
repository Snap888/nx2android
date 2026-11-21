"""Persistent NGR game catalog.

This is the data layer for the universal launcher. It deliberately distinguishes a
registered payload from a launchable game: current nx2android output still embeds
its profile in a per-game NativeActivity host.
"""
from __future__ import annotations

import json
import os
import re
import tempfile
import time
import uuid
from pathlib import Path

from porter.paths import user_dir

SCHEMA_VERSION = 1
ID_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}$")


class LibraryError(ValueError):
    pass


def catalog_path() -> Path:
    return user_dir() / "NGR" / "library.json"


def _empty() -> dict:
    return {"schema_version": SCHEMA_VERSION, "games": []}


def _validate_catalog(data: object) -> dict:
    if not isinstance(data, dict) or data.get("schema_version") != SCHEMA_VERSION or not isinstance(data.get("games"), list):
        raise LibraryError("Unsupported or malformed NGR library catalog")
    seen = set()
    for game in data["games"]:
        if not isinstance(game, dict) or not isinstance(game.get("id"), str) or not ID_RE.fullmatch(game["id"]):
            raise LibraryError("Catalog contains a game with an invalid id")
        if game["id"] in seen:
            raise LibraryError(f"Catalog contains duplicate game id: {game['id']}")
        seen.add(game["id"])
        if not isinstance(game.get("settings", {}), dict):
            raise LibraryError(f"Game {game['id']} has invalid settings")
    return data


def load(path: Path | None = None) -> dict:
    p = path or catalog_path()
    if not p.exists():
        return _empty()
    try:
        return _validate_catalog(json.loads(p.read_text(encoding="utf-8")))
    except (OSError, json.JSONDecodeError) as e:
        raise LibraryError(f"Cannot read NGR library {p}: {e}") from e


def save(data: dict, path: Path | None = None) -> Path:
    _validate_catalog(data)
    p = path or catalog_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    # Atomic replacement prevents a crash or low-storage event from truncating the catalog.
    fd, name = tempfile.mkstemp(prefix="library.", suffix=".tmp", dir=p.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(name, p)
    finally:
        try:
            os.unlink(name)
        except FileNotFoundError:
            pass
    return p


def register(title: str, platform: str, payload: str | Path, *, game_id: str = "",
             profile_id: str = "", cover: str = "", runtime: str = "nx2android-legacy",
             source: str = "", path: Path | None = None) -> dict:
    title = title.strip()
    platform = platform.strip().lower()
    payload_path = Path(payload).expanduser().resolve()
    if not title:
        raise LibraryError("Game title cannot be empty")
    if not platform:
        raise LibraryError("Platform cannot be empty")
    if not payload_path.exists():
        raise LibraryError(f"Payload does not exist: {payload_path}")
    gid = game_id.strip() or uuid.uuid4().hex
    if not ID_RE.fullmatch(gid):
        raise LibraryError("Game id must be 1-96 letters, digits, dots, underscores or hyphens")
    data = load(path)
    now = int(time.time())
    existing = next((g for g in data["games"] if g["id"] == gid), None)
    record = {
        "id": gid,
        "title": title,
        "platform": platform,
        "profile_id": profile_id.strip(),
        "payload_path": str(payload_path),
        "cover": str(Path(cover).expanduser().resolve()) if cover else "",
        "runtime": runtime.strip() or "unknown",
        "source": str(Path(source).expanduser().resolve()) if source else "",
        "settings": existing.get("settings", {}) if existing else {},
        "created_at": existing.get("created_at", now) if existing else now,
        "updated_at": now,
        "last_launched_at": existing.get("last_launched_at") if existing else None,
        # Current game-specific host builds are not yet compatible with shared-runtime dispatch.
        "launch_state": "legacy_payload" if runtime == "nx2android-legacy" else "registered",
    }
    if existing:
        data["games"][data["games"].index(existing)] = record
    else:
        if any(g["id"] == gid for g in data["games"]):
            raise LibraryError(f"Game id already exists: {gid}")
        data["games"].append(record)
    save(data, path)
    return record


def update_settings(game_id: str, settings: dict, path: Path | None = None) -> dict:
    if not isinstance(settings, dict):
        raise LibraryError("Settings must be a JSON object")
    data = load(path)
    game = next((g for g in data["games"] if g["id"] == game_id), None)
    if game is None:
        raise LibraryError(f"Game not found: {game_id}")
    game["settings"] = settings
    game["updated_at"] = int(time.time())
    save(data, path)
    return game


def remove(game_id: str, path: Path | None = None) -> dict:
    data = load(path)
    before = len(data["games"])
    data["games"] = [g for g in data["games"] if g["id"] != game_id]
    if len(data["games"]) == before:
        raise LibraryError(f"Game not found: {game_id}")
    save(data, path)
    # Removing a shortcut never deletes game data or payload files.
    return {"id": game_id, "payload_deleted": False}


def manifest(game: dict) -> dict:
    """Versioned launch contract; readiness is explicit, never inferred from a library tile."""
    return {
        "manifest_version": 1,
        "game_id": game["id"],
        "title": game["title"],
        "platform": game["platform"],
        "profile": {"id": game.get("profile_id", "")},
        "payload": {"path": game["payload_path"]},
        "runtime": {"id": game.get("runtime", "unknown"), "shared_runtime_ready": False},
        "launch": {"state": game.get("launch_state", "registered"), "settings": game.get("settings", {})},
    }
