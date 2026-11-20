# Arcane Vault Android v2.1.69

## Added
- A compact **Table Tools** menu inside the offline Tabletop Companion.
- Random starting-player selection from the current 2–4 player roster.
- Coin flips (heads/tails), D6 rolls, D20 rolls and 2D6 rolls showing the individual dice and sum.
- Automated tests for the allowed randomizer inputs and results.

## Changed
- The tabletop toolbar now provides **Vault**, **Undo** and **Table Tools** to preserve room for player panels.
- New Game remains available from Table Tools, with a separate confirmation before returning to player selection.

## Fixed
- Dice and starting-player utilities can be used without changing life totals, poison, commander damage or saved sessions.
- Random tool values are generated locally using Android's secure random source, without a network dependency.

## Removed
- The standalone New Game toolbar button moved into Table Tools; no game setup functionality was removed.

The release is limited to Tabletop Companion. Physical and Arena collections, saved decks, deck statistics, and Windows/Android sync records are unchanged.
