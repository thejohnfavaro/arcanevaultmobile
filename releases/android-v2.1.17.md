# Arcane Vault Android v2.1.17 — Native EDH commander-to-deck browsing

## Added
- Collapsible commander discovery filters for **all commanders**, **any owned copy**, **physical collection**, or **Arena collection**.
- Toggleable exact W/U/B/R/G color-identity combinations and colorless commander search. Filtering combines with name/rules search and Arena availability.
- **View saved decks with this commander** opens the native Deck Library scoped to the selected commander, rather than only copying a name and opening the legacy interface.
- The native Deck Library offers **Show all saved decks** to clear the commander filter.
- Empty-state guidance when a selected commander has no saved decks.

## Changed
- Existing commander art, double-faced flip, card preview, Oracle text, mana value and ownership display remain unchanged.
- Deck results are matched to the existing saved deck's commander field, without fabricating a new deck record.
- Existing full editor remains available via **Copy commander name & open deck editor**.

## Fixed
- Commander filtering now queries the actual local catalog and collection source using parameterized SQL for ownership and exact identity.
- Switching between EDH Lab and saved decks stays within the native workspace instead of requiring a manual navigation chain.

## Removed
- No legacy deck editor, ownership, printing, sync or recovery capabilities removed.

## Safety
- Browsing and deck matching remain read-only; this update does not modify saved decks, Commander selection, collection values, proxies, or sync baselines.
- Checks cover ownership/color filtering and native saved-deck handoff, and the Android debug APK build passed.
- On-device visual and interaction validation is still needed.
