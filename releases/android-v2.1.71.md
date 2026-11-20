# Arcane Vault Android v2.1.71

## Added
- **End Game** flow in Tabletop Companion: explicitly choose a winner, review final life, poison and per-source commander-damage totals, then confirm.
- Optional **Associate saved decks** workflow for each player. Reads the existing Deck Library without editing deck records, ownership or statistics.
- Local archive of up to 30 completed game summaries, accessible through **Table Tools → Recent completed games** even after a new game begins.
- Dedicated **Game Complete** summary screen with winner, final counters and linked decks, plus an explicit option to reopen the match for corrections.
- Regression tests for winner validation, saved deck reference persistence, undo of deck associations, completed-session recovery and compatibility with older saved games.

## Changed
- End Game and saved-deck association live in Table Tools, preserving the tabletop screen for life tracking.
- Completed matches stop accepting counter changes until explicitly reopened.
- Final results and deck links are stored only in the existing isolated local Tabletop Companion preferences; they are not added to cross-device collection sync or deck statistics.
- Closing Table Tools before launching another selection avoids stacked dialogs.

## Fixed
- A complete tabletop game can now be reviewed and retained without inferring a winner from life totals.
- Previously saved Tabletop sessions without any completion or deck-link fields continue to open normally.

## Removed
- Nothing. Existing collections, decks, pricing, Arena analytics, and sync are untouched.

No wins or losses are written to saved deck statistics in this release. Linking a deck is optional and creates a read-only association for future explicit match recording.
