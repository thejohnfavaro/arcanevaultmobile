# Arcane Vault Android v2.1.75

## Added
- A verified second-generation backup of the active Tabletop Companion game, saved atomically alongside each life, poison, Commander-damage, turn, name, and color adjustment.
- Checksum-protected tabletop recovery envelope and automatic restore of the last valid checkpoint if the active session cannot be decoded.
- **Restore previous saved game** in Table Tools: preview life, poison, round, and player roster, then explicitly choose whether to restore the previous checkpoint.
- Automated recovery tests verifying life, poison, Commander damage, turn order, panel colors, undo history, prior-generation recovery and rejection of corrupt data.

## Changed
- Existing Tabletop Companion saves now maintain an integrity-checked previous generation without requiring a user action.
- A corrupted active session is recovered independently of collection, deck or sync storage; a clear notice explains that the most recent tabletop change might need to be re-entered.
- Game completion and new-game creation now preserve verified checkpoints alongside the current session and local completed-game archive.
- Existing 2–4-player Commander setup defaults, orientation layout, poison and Commander damage controls, event history, and 60-step undo remain unchanged.

## Fixed
- Previously, one damaged tabletop session could prevent resuming any counters. A valid previous checkpoint now offers a recovery route rather than forcing a fresh game.
- Manual rollback is available with a preview and confirmation, preserving collection and deck data.

## Removed
- No tabletop history, collection, decks, proxy data or sync records intentionally removed. The tabletop session and backup remain outside peer sync.

## Verification
- Full Android Java unit tests, JavaScript regression suite, debug build and signed release checks are required.
- On device: start a 4-player game, change life, poison and Commander damage, confirm Undo; return to Arcane Vault, force-close and reopen the Tabletop Companion and verify counters persist; use Table Tools to preview the previous checkpoint and restore it.
- Android collection and decks must remain unaffected by all tabletop actions and recovery.
