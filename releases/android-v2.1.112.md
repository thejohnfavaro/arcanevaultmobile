# Arcane Vault Android v2.1.112 (Preview)

## Added
- Direct per-player poison counter **minus** and **plus** controls on the Tabletop Companion board; the current total is shown on the plus control.
- A long-press on the poison plus control opens the existing detailed poison editor.
- A regression test covering consecutive poison adjustments, isolation between players, session recovery, and ordered undo.

## Changed
- Poison adjustments no longer require a modal for routine changes, including four-player Commander tables.
- Existing automatic session persistence and undo history are used for each counter adjustment.

## Fixed
- Faster at-table poison entry without changing commander damage or health calculations.

## Removed
- Nothing. Collection, saved decks, local tabletop history and prior recovery checkpoints are unchanged.

## Verification
- Build Android and run the TabletopSessionTest unit tests.
- In a 4-player Commander session, test +1/-1 poison independently on each panel, undo, app restart recovery, and the long-press detail editor.
- Confirm commander damage source-specific tracking and life adjustments remain intact.
