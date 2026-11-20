# Arcane Vault Android v2.1.70

## Added
- Compact Round / Active Player bar in Tabletop Companion with an explicit Next Turn action and visual indication of the active player's panel.
- Round tracking based on the selected starting seat, with wraparound after all 2–4 players have taken a turn.
- Tap the current turn label to choose or reset the starting player without resetting life or counters.
- Game History dialog showing the 30 most recent turn, life, poison, and per-commander damage adjustments.
- Integration of the random starting-player picker with the saved active player / turn order.
- Automated tests for turn rollover, undo, bounded history, backward-compatible session recovery, and persistence.

## Changed
- Turn tracking and recent event history are stored alongside the existing offline Tabletop Companion game session, not in collections or decks.
- Undo restores the previous counters, active turn, round and event log together.
- Dice and coin tools remain display-only; selecting a starting player intentionally updates turn tracking.

## Fixed
- Older tabletop session saves without turn/history fields still reopen with Round 1 and their prior life, poison and commander damage intact.
- Recent event history is bounded, preventing unlimited session growth during extended games.

## Removed
- Nothing. Existing tabletop games, physical and Arena collections, decks, pricing and sync remain intact.

Tabletop Companion remains offline-first. No turn or game history events are sent to the Windows app or Arena match analytics.
