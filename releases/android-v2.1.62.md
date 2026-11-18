# Arcane Vault Android v2.1.62

## Added
- Strict validation of the existing Android device UUID before exporting or reviewing a peer file. A corrupted UUID now produces a recoverable error instead of being silently replaced.
- Saved checkpoint verification for format, schema, required inventory groups, revision and device identity before the Android app identifies a baseline as usable.
- JVM regressions for missing, malformed, noncanonical and empty UUIDs; invalid checkpoint envelopes; isolated optional match analytics; and unchanged peer inventories.

## Changed
- Peer UUID creation is synchronized to avoid competing initializations from simultaneous Android peer actions.
- The entire exported and incoming peer envelope, not just its inner inventory data, must fit the same 30 MiB limit as Windows.
- Checkpoint validation is structural only; it does not imply authenticated device pairing or permission to apply a merge.

## Fixed
- Existing malformed device IDs no longer silently break the sync relationship.
- Damaged or mismatched baseline envelopes are rejected before merge review, while the current collection stays untouched.
- Optional read-only match analytics metadata remains separate from inventory conflict comparison.

## Removed
- No data or features removed.

**Upgrade:** Install with the same production signing certificate as your currently installed Android app. Do not uninstall to update. Pair with Windows v0.9.150 for matching baseline integrity protections.

**1.0 status:** Reliability milestone only. Physical-device upgrade, interrupted sync recovery and complete Windows/Android parity require further verification.
