#!/usr/bin/env python3
"""Patch a pinned SmartTube source checkout into a hidden, player-only kids TV engine.

The engine is deliberately not a launcher app. It accepts only vnd.youtube://<videoId>
intents from the visible Kids Video TV apps, plays one video with SmartTube's native
Android TV playback stack, then returns to the catalogue.
"""
from pathlib import Path
import re

ROOT = Path("SmartTubeEngine")


def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Give the engine its own package so it can coexist with official YouTube/SmartTube.
replace(
    "smarttubetv/build.gradle",
    'ststable {\n            applicationId "org.smarttube.stable"',
    'ststable {\n            applicationId "com.kidsvideo.tvplayer"'
)

# This private engine does not use SmartTube's Firebase/Crashlytics registration.
# Removing the upstream google-services.json prevents the Google Services plugin from
# rejecting our deliberately different application id during the build.
google_services = ROOT / "smarttubetv/google-services.json"
if google_services.exists():
    google_services.unlink()

# ZIVPN/SSH tunnels are mandatory for this household. SmartTube contains an optional
# Conscrypt network path specifically intended to improve TLS/VPN compatibility;
# enable it by default in this dedicated engine. It remains SmartTube's own code path.
replace(
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/NetworkData.java",
    "return getBoolean(0, false);",
    "return getBoolean(0, true);"
)

# In external/player-only mode, never allow the player to switch from the video that
# the approved catalogue launched to a related/recommended video. If such a switch is
# attempted, close the engine and reveal the safe catalogue underneath.
p = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/PlaybackPresenter.java"
text = p.read_text(encoding="utf-8")
needle = """    public void openVideo(Video video) {\n        if (video == null) {\n            return;\n        }\n"""
insert = """    public void openVideo(Video video) {\n        if (video == null) {\n            return;\n        }\n\n        // Kids TV engine safety: an externally launched video is the only video allowed\n        // during player-only mode. Suggestions/queues may be displayed by upstream code,\n        // but selecting a different id immediately exits instead of playing it.\n        if (getViewManager().isPlayerOnlyModeEnabled()) {\n            Video current = getVideo();\n            if (current != null && current.videoId != null && video.videoId != null\n                    && !current.videoId.equals(video.videoId)) {\n                getViewManager().properlyFinishTheApp(getContext());\n                return;\n            }\n        }\n"""
if needle not in text:
    raise SystemExit("PlaybackPresenter patch point not found")
p.write_text(text.replace(needle, insert, 1), encoding="utf-8")

# Hide the engine from the Android/Google TV launcher and remove normal browser/search
# entry points. Keep only SmartTube's vnd.youtube intent filter, which our catalogue uses
# with an explicit package name. Leave the upstream app label unchanged to avoid manifest
# merge conflicts with SmartTube's leanback-assistant module; there is no launcher entry
# anyway, so children cannot open this engine directly from the TV home screen.
p = ROOT / "smarttubetv/src/main/AndroidManifest.xml"
text = p.read_text(encoding="utf-8")

def strip_filter(match):
    block = match.group(0)
    if "android.intent.action.MAIN" in block:
        return ""
    if "android.intent.action.SEND" in block:
        return ""
    if ("android.intent.action.VIEW" in block
            and "android:scheme=\"vnd.youtube\"" not in block
            and "android:scheme=\"vnd.youtube.launch\"" not in block):
        return ""
    return block

text = re.sub(r"\s*<intent-filter>.*?</intent-filter>", strip_filter, text, flags=re.S)
p.write_text(text, encoding="utf-8")

print("SmartTube source patched as com.kidsvideo.tvplayer")
