#!/usr/bin/env python3
"""Patch a pinned SmartTube source checkout into a hidden, player-only kids TV engine.

This v3.1 engine is for Android/Google TV boxes.  It uses SmartTube's native player stack,
starts with the OkHttp data source, enables Conscrypt, and is built from a revision that
contains SABR media-network failover for the infinite-spinner problem seen on TV boxes/VPNs.
"""
from pathlib import Path
import re

ROOT = Path("SmartTubeEngine")
ENGINE_PACKAGE = "com.kidsvideo.tvplayer31"


def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Separate package so v3.1 can coexist with the first experimental v3.0 engine.
replace(
    "smarttubetv/build.gradle",
    'ststable {\n            applicationId "org.smarttube.stable"',
    f'ststable {{\n            applicationId "{ENGINE_PACKAGE}"'
)

# This private engine does not use SmartTube's Firebase/Crashlytics registration.
google_services = ROOT / "smarttubetv/google-services.json"
if google_services.exists():
    google_services.unlink()

# Prefer the VPN-friendly TLS provider on this dedicated engine.
replace(
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/NetworkData.java",
    "return getBoolean(0, false);",
    "return getBoolean(0, true);"
)

# Start fresh installs on OkHttp rather than device-dependent auto selection.  SmartTube's
# own network error fixer is still free to change engines later if an actual failure occurs.
replace(
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/PlayerTweaksData.java",
    "mPlayerDataSource = Helpers.parseInt(split, 26, Utils.getFasterDataSource());",
    "mPlayerDataSource = Helpers.parseInt(split, 26, PLAYER_DATA_SOURCE_OKHTTP);"
)

# In external/player-only mode, never allow the player to switch from the approved video
# to a related/recommended video.  If such a switch is attempted, close the engine.
p = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/PlaybackPresenter.java"
text = p.read_text(encoding="utf-8")
needle = """    public void openVideo(Video video) {\n        if (video == null) {\n            return;\n        }\n"""
insert = """    public void openVideo(Video video) {\n        if (video == null) {\n            return;\n        }\n\n        // Kids TV safety: only the video selected in the approved catalogue may play.\n        if (getViewManager().isPlayerOnlyModeEnabled()) {\n            Video current = getVideo();\n            if (current != null && current.videoId != null && video.videoId != null\n                    && !current.videoId.equals(video.videoId)) {\n                getViewManager().properlyFinishTheApp(getContext());\n                return;\n            }\n        }\n"""
if needle not in text:
    raise SystemExit("PlaybackPresenter patch point not found")
p.write_text(text.replace(needle, insert, 1), encoding="utf-8")

# Hide normal launcher/browser/search entry points. Keep only SmartTube's vnd.youtube
# intent filter, used privately by our visible catalogue app with an explicit package.
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

print(f"SmartTube source patched as {ENGINE_PACKAGE}")
