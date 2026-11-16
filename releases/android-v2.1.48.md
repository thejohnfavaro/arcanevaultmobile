# Arcane Vault Android v2.1.48

## Added
- Build regression check aligned with the native-only commander copy action.

## Changed
- Android signed release validation now checks the current native Commander browser behavior instead of requiring an obsolete WebView deck-editor shortcut.
- Retains native navigation cleanup and signed-release diagnostic improvements from v2.1.47.
- Preserves explicit Arena peer-conflict choices (Keep Android / Use Windows) from v2.1.45.

## Fixed
- Signed Android release regression checks could fail after removal of the legacy deck-editor entry point, because the Commander handoff test still required its old button label.
- The updater can install this release once the signed APK and checksum are successfully published.

## Removed
- No user inventory, deck, artwork, peer baseline, recovery backup, or remaining storage bridge data removed.
