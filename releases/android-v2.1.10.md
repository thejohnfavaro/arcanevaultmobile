# Arcane Vault Android v2.1.10 — USB peer sync

## Added
- Android USB peer sync entry from the Windows native app over ADB reverse, without Wi-Fi or QR codes.
- Explicit USB send/receive approval dialog in Android and native **Data & Sync** instructions.
- Dedicated loopback-only USB URL validation and regression tests.

## Changed
- The USB cable transports the same physical, Arena, deck and preferred artwork peer snapshots as the existing Wi-Fi sync.
- USB-to-Android write-back still requires a genuine shared baseline, preview, user approval and verified full pre-sync backup.
- Windows native v0.9.101 can launch Android automatically over an authorized USB debugging connection.

## Fixed
- USB peer transfers no longer depend on the legacy collection-only QR/USB import path.
- Wi-Fi links remain private-LAN-only; USB loopback addresses cannot be accepted accidentally as Wi-Fi links.

## Removed
- No existing Wi-Fi, QR or file-based sync capability was removed.

## Safety
- Use a USB data cable, install Android SDK Platform-Tools on Windows, enable USB debugging and authorize the computer on the phone.
- Windows peer offers bind only to 127.0.0.1, use expiring one-time links and require explicit Android transfer consent.
- Merges preserve existing conflict rejection, printing-aware deltas, full pre-sync recovery snapshots, and manual approval.
- Device-level cable round-trip testing remains required before considering the workflow fully validated.
