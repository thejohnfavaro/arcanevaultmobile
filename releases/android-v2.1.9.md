# Arcane Vault Android v2.1.9 — reviewed peer deck write-back and full recovery

## Added
- Opt-in Windows-to-Android saved deck and preferred artwork write-back alongside printing-aware inventory changes.
- Verified, unique full workspace snapshots saved before every peer write, including physical inventory, Arena inventory, saved decks, and artwork preferences.
- In-app **Restore latest full pre-sync snapshot** action; saves another full snapshot of the current state before restoring.
- Regression coverage for deck printing identity, conflicting changes, and non-replayed peer round trips.

## Changed
- Device Sync review now presents deck/artwork differences as well as card-count deltas and requires explicit approval.
- Android's native physical/Arena inventory recovery journal remains independent of WebView deck and artwork writes.
- Full restore can restore saved decks and preferred artwork as well as the inventories.

## Fixed
- Incoming deck changes no longer require Windows-only write-back when the Android user approves a conflict-free three-way merge.
- WebView write-back verifies stored values before reporting success; a changed workspace since review rejects the operation.

## Removed
- Removed the inventory-only restriction from the opt-in Android peer apply workflow.

## Safety
- Peer apply still requires a genuine previously shared baseline; conflicting concurrent edits and ambiguous deletions are rejected.
- The native inventory journal and a full pre-sync workspace backup are retained. If an interruption occurs between native and WebView writes, restore the full snapshot from Data & Sync.
- Trusted local network only. No automatic unattended merge, no cloud pairing, and no assertion of complete cross-device transaction atomicity.
- Device-level round-trip verification is still needed after updating both apps.
