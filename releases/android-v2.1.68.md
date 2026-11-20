# Arcane Vault Android v2.1.68

## Tabletop Companion UX refinement

- Reworked responsive player surfaces for 2, 3, and 4 players.
- Landscape layout now places the oversized life total alongside stacked +/− controls instead of squeezing everything into a vertical column.
- Portrait mode retains large life totals and accessible adjustment buttons.
- Increased counter touch targets and player label readability.
- Long-press life +/− for adjustments of 5.
- Long-press commander damage +/− for adjustments of 5.
- Poison and commander-damage dialogs now update their own displayed values without closing or rebuilding the full tabletop between taps.
- Long-press the color control to select a specific named panel color; tapping it still cycles colors.
- Counter changes remain separately recorded in undo history and persisted locally.
- Added tests for reversible color selection and independent commander damage accounting.

All changes are isolated to Tabletop Companion. No collection, saved-deck, price, or sync data was migrated or modified.
