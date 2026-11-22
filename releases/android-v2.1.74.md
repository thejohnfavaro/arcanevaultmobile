# Arcane Vault Android v2.1.74

## Added
- Persistent AI Deck Reviews now use the same per-deck `aiReview` metadata as Windows, including response Markdown, model, focus, UTC save date, and Windows-compatible SHA-256 deck-content fingerprint.
- **Save review with deck** action in Android's native AI Review, using the existing authoritative deck store and verified compare-and-save with private recovery. Review saves do not create spurious deck-card revisions.
- Saved and **Outdated** AI Review status on deck library cards, deck details, and in the AI Review screen.
- Offline reopening of saved analyses with verified clickable card references and full card preview when the local catalog is present.
- Headless tests for exact Windows fingerprint compatibility, stale changes and preserved unrelated deck fields, plus reviewed Windows↔Android peer sync and explicit conflicts when both devices edit one deck.

## Changed
- Analysis saving is explicit. New AI responses remain read-only until the user selects **Save review with deck**.
- Review results are tied to the exact deck snapshot and review focus analyzed; a changed or unsaved draft cannot silently overwrite a stored review.
- Existing deck editing and peer synchronization retain the complete `aiReview` object as part of the full deck record. Sync retains conflict review instead of auto-merging divergent versions of a deck.
- Existing collection, printing selections, proxy counts, deck history, and data/sync recovery behavior are unchanged.

## Fixed
- AI Deck Reviews saved on Windows are no longer missing from Android's native saved-deck view after a reviewed sync.
- Android AI responses need not disappear on reopening once saved with their deck; reviews of changed decks display an outdated warning.

## Removed
- No collection, sync, deck, review, or history data removed. No Commander bracket rating is added to Arena Brawl.

## Verification
- Android debug build, Java unit and JavaScript regression suites, signed APK creation and signature verification are required.
- Test locally: open a Windows-reviewed deck on Android, inspect a linked card, generate and save a review on Android, sync back to Windows, then edit a card quantity and confirm the old review becomes outdated.
- Android still requires an explicit peer-sync review and conflict approval before applying changes. Stored AI output is advisory and never makes deck edits automatically.
