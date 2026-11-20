# Arcane Vault Android v2.1.67

## Added
- Tabletop Companion: a dedicated, entirely offline 2–4 player Magic life-tracking workspace, accessible from Home and Decks.
- Four-player Commander preset (40 life each), with 3-player and 2-player setup options.
- Large life +/− controls, independently colored player panels, editable player names (long-press), poison controls, and commander combat damage recorded per opposing commander.
- Commander combat damage also adjusts life; alerts when a single commander deals at least 21 combat damage or poison reaches 10.
- Bounded undo history, saved together with session state after each action; automatically resumes after the app reopens or screen orientation changes.
- Screen-awake mode during play, with automatic Android orientation, and a confirmation when leaving the table.

## Changed
- Tabletop mode dedicates the display to the table, hiding the standard top branding and bottom tabs during a game.
- The preferred setup lists four-player Commander first.
- Sessions are held in a separate private preference file `arcane_tabletop_companion_v1`, not Arcane Vault's physical inventory, Arena inventory, saved decks, or peer sync.

## Fixed
- Android JSON error handling for safe persistence and recovery of tabletop sessions.

## Removed
- No collection, deck, or sync functionality removed.

Notes: The Tabletop Companion is an initial playable MVP. No match stats are written to deck analytics yet. All tabletop controls work without network access, and a new tabletop game replaces only the tabletop session.
