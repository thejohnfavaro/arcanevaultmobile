# Arcane Vault Android v2.1.56

## Added
- Regression guard verifying native workspace refresh is accessible from MainActivity following a verified peer sync.
- The safe optional Verify & remember baseline action and streamlined baseline picker introduced in v2.1.55.

## Changed
- Allow MainActivity to invoke NativeCompanionScreen's existing native collection refresh method after a verified peer apply.
- Keep all v2.1.55 improvements: compact shared-baseline selection, fewer redundant confirmations, readable merge-review line breaks, optional checkpoint after an exact full-snapshot match, and immediate native workspace refresh.

## Fixed
- Android v2.1.55 Java compilation failed: NativeCompanionScreen.refresh() was private and inaccessible to MainActivity. The method is now package-visible within the app.
- No changes to peer merge authorization, inventory write transactions, or backup semantics.

## Removed
- No user data, inventories, saved decks, artwork, snapshots, checkpoints, or backups removed.
