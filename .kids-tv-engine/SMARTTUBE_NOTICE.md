# Kids TV Player Engine attribution

The hidden TV playback engine is built from the open-source **SmartTube** project by Yuriy Liskov and contributors:

- Upstream repository: https://github.com/yuliskov/SmartTube
- Pinned source revision for this build: `ffa16426ea3c7c82077e0f00228818b85f373588` (SmartTube 32.68)
- Upstream license: MIT License

The build workflow applies a small patch that gives the engine the package name `com.kidsvideo.tvplayer`, removes launcher/browser entry points, enables the upstream Conscrypt VPN-compatibility path by default, and prevents player-only sessions from switching to a different video.

The visible Kids Video TV apps remain separate and only send an approved 11-character YouTube video id to this hidden engine.
