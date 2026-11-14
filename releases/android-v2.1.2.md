# Arcane Vault Android v2.1.2 — Wireless peer review and system bar layouts

## Added
- **Receive Windows sync over Wi-Fi**: enter the one-time link generated on Windows to download a peer snapshot and preview differences without changing collections or decks.
- **Send phone sync over Wi-Fi**: send an Android peer snapshot to Windows for read-only comparison.
- Android 15+ navigation bar, status bar and display cutout safe-area handling on the shared native and Legacy screens.

## Changed
- Android peer transport accepts only private RFC1918 IPv4 local-network links with correctly formed one-time Arcane Vault tokens.
- Transfer requests are bounded to 30 MiB, reject redirects and avoid internet/cloud servers.
- Navigation bar insets are applied to the app root, so the bottom card rows and sync buttons remain accessible.

## Fixed
- Controls and list content no longer overlap Android's gesture/navigation system bar on Android 15+.
- Manual PC/Android file movement is no longer required for peer review when both devices can communicate over trusted home Wi-Fi.

## Removed
- No existing sync or import methods removed. JSON file export/preview and Legacy tools remain supported.

## Safety
- **This transfers snapshots for read-only comparison only. It does not apply edits or merge collections.**
- The LAN connection uses HTTP, not TLS: use only a trusted home Wi-Fi network, and treat the one-time URL as a secret. A future pairing protocol will authenticate and encrypt the channel.
- Google Play Protect report #36 remains unresolved; signed APKs are not automatically classified safe.
