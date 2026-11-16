# Arcane Vault Android v2.1.53

## Added
- Separate saved-deck conflict review after choosing Arena inventory authority in Windows → Android peer merge.
- Compact four-choice menu: keep Android deck, use Windows deck, keep Android for all differing shared decks, or use Windows for all differing shared decks.
- Regression coverage for separate Arena vs deck approvals, stale or unrelated deck decisions, and preservation of existing Android data prior to confirmation.

## Changed
- Peer merge preparation accepts separately scoped per-deck decisions; Arena bulk approval alone cannot overwrite saved decks.
- Same-ID deck conflicts require explicit review, and different decks present only on one device continue through the guarded existing addition/tombstone rules.
- Preserve verified full pre-sync backup, final approval, and WebView deck/artwork readback verification.

## Fixed
- Selecting Use Windows for all Arena cards could proceed to an unrelated `Conflicting edits: deckLibrary/<id>` error without an option to review saved-deck differences.
- Kept saved-deck decisions separate from Arena card names to avoid accidental cross-domain authorization.

## Removed
- No existing decks, inventory, printings, artwork, shared baselines, or recovery snapshots removed. No automatic overwrite or deletion.
