#!/usr/bin/env python3
"""Fast offline validation when Java or an Android SDK is not installed."""

from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    feed = json.loads((ROOT / "backend/src/main/resources/feed.json").read_text())
    playlists = json.loads((ROOT / "backend/src/main/resources/playlists.json").read_text())
    albums = [album for section in feed for album in section["albums"]]

    album_ids = [album["id"] for album in albums]
    playlist_ids = [playlist["id"] for playlist in playlists]
    require(len(albums) >= 5, "Expected at least five fixture albums")
    require(len(album_ids) == len(set(album_ids)), "Album ids must be unique")
    require(len(playlist_ids) == len(set(playlist_ids)), "Playlist ids must be unique")
    require(set(album_ids) == set(playlist_ids), "Every album needs exactly one playlist")

    song_urls: list[str] = []
    for album in albums:
        require(set(album) == {"id", "album", "year", "cover", "artists", "description"}, "Album API shape drifted")
        require(album["cover"] == f"/covers/{album['id']}.svg", "Cover must use the local fixture route")
    for playlist in playlists:
        require(playlist["songs"], f"Playlist {playlist['id']} is empty")
        for song in playlist["songs"]:
            require(set(song) == {"name", "lyric", "src", "length"}, "Song API shape drifted")
            require(re.fullmatch(r"/songs/[a-z0-9-]+\.wav", song["src"]) is not None, "Song must use local WAV route")
            require(song["length"] == "00:05", "Generated fixture duration must match metadata")
            song_urls.append(song["src"])
    require(len(song_urls) == len(set(song_urls)), "Song fixture URLs must be unique")

    android_ns = "{http://schemas.android.com/apk/res/android}"
    main_manifest = ElementTree.parse(ROOT / "android/app/src/main/AndroidManifest.xml")
    debug_manifest = ElementTree.parse(ROOT / "android/app/src/debug/AndroidManifest.xml")
    main_application = main_manifest.getroot().find("application")
    debug_application = debug_manifest.getroot().find("application")
    require(main_application is not None, "Main Android manifest has no application")
    require(debug_application is not None, "Debug Android manifest has no application")
    require(main_application.get(f"{android_ns}usesCleartextTraffic") != "true", "Release manifest must not allow cleartext traffic")
    require(debug_application.get(f"{android_ns}usesCleartextTraffic") == "true", "Debug manifest must allow the documented local HTTP API")

    room_schema = json.loads((ROOT / "android/app/schemas/com.laioffer.spotify.data.local.SpotifyDatabase/1.json").read_text())
    require(room_schema["database"]["version"] == 1, "Room schema version drifted")
    entities = room_schema["database"]["entities"]
    require(len(entities) == 1 and entities[0]["tableName"] == "favorite_albums", "Room favorite table is missing")
    room_fields = {field["columnName"] for field in entities[0]["fields"]}
    require(room_fields == {"id", "name", "year", "cover", "artists", "description"}, "Room schema fields drifted")

    expected_tokens = {
        "android/app/src/main/java/com/laioffer/spotify/di/AppModules.kt": ["@Module", "Retrofit.Builder", "Room.databaseBuilder", "ExoPlayer.Builder"],
        "android/app/src/main/java/com/laioffer/spotify/ui/navigation/SpotifyApp.kt": ["NavHost", "PlayerBar", "NavigationBar"],
        "android/app/src/main/java/com/laioffer/spotify/ui/playlist/PlaylistViewModel.kt": ["@HiltViewModel", "toggleFavorite"],
        "android/app/src/main/java/com/laioffer/spotify/data/local/SpotifyDatabase.kt": ["@Database", "RoomDatabase"],
        "android/app/src/main/java/com/laioffer/spotify/data/model/Models.kt": ["@SerializedName(\"album\")", "@SerializedName(\"section_title\")"],
        "android/app/src/main/java/com/laioffer/spotify/player/PlaybackController.kt": ["Player.STATE_ENDED", "player.seekTo(0)"],
        "backend/src/main/kotlin/com/laioffer/spotify/backend/Application.kt": ["/feed", "/playlists", "/playlist/{id}", "/songs/{file}"],
    }
    for relative_path, tokens in expected_tokens.items():
        source = (ROOT / relative_path).read_text()
        for token in tokens:
            require(token in source, f"{relative_path} is missing required architecture token {token}")

    for build in ("backend", "android"):
        wrapper = ROOT / build / "gradle/wrapper/gradle-wrapper.jar"
        require(wrapper.is_file(), f"{build} Gradle wrapper JAR is missing")
        require(zipfile.is_zipfile(wrapper), f"{build} Gradle wrapper JAR is corrupt")

    print(f"Validated {len(albums)} albums, {len(playlists)} playlists, and {len(song_urls)} local songs.")
    print("Validated Android network manifests, Room schema, architecture seams, and both Gradle wrappers.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, ElementTree.ParseError) as error:
        print(f"validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
