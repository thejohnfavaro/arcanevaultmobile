# Arcane Vault Android v2.1.104

## Added
- Optional offline Deck Insights panel in native Android Deck Editing, calculated from the **current unsaved mainboard** and current commander; Refresh Insights recomputes from the latest draft text.
- Mana curve, known/unknown card coverage, nonland average mana value and card-type totals in the editor, reusing the saved Deck Details analysis and chart renderer.
- Nonland spell color-identity bars for W/U/B/R/G/colorless. Multicolor spells count in every applicable identity; unknown metadata is never guessed.
- Approximate quantity-weighted colored casting-cost pip demand using the Windows EDH Lab policy: hybrid symbols share demand, Phyrexian or two-color-choice alternatives are discounted. Separate from land mana production.
- Natural land-drop probabilities for T2–T4, only for a complete, verified 99-card Commander mainboard, assuming a seven-card opening hand, natural draws and no mulligans.
- Unit tests for pip counting, split costs, invalid inputs and 99-card natural land-drop probabilities.
- Android-vs-Windows EDH Lab parity audit at docs/android-windows-edh-parity-2026-11.md, distinguishing implemented analytics from remaining health, synergy, land planning and advanced swap analysis.

## Changed
- Saved Deck Details now displays the additional color-identity, colored-pip and qualified natural land-drop analysis beside its existing mana curve.
- Deck Editing insights calculate on the background executor and reject stale results if the draft changes, is refreshed again or the editor is closed.
- Missing local Scryfall metadata remains explicitly unknown; analysis does not trigger catalog downloads or writes.

## Fixed
- Mana curve and card metadata analysis formerly existed only on saved Deck Details, so unsaved changes could not be reviewed before Review & Save.
- Mobile previously lacked distinct colored pip demand, color-identity distribution and qualified natural land-drop odds that Windows EDH Lab provides.

## Removed
- No collection, deck, printing, proxy, saved AI review, sync data, update path, artwork cache, or existing card view mode was removed. Full Windows EDH Lab parity still requires separate role-health, synergy, advanced mana-source and substitution-impact work.

## Verification
- Pre-release JavaScript regressions, Android unit tests (including new mana tests) and debug APK compilation all passed.
- Signed release must independently pass project/notes validation, regressions, Android unit tests, release compilation and signing.
- On-device: open a saved Commander deck and review Deck Details insights. Select Edit -> Deck Insights and inspect mana curve, color identities, pip demand and natural land odds. Add/remove a draft card, select Refresh Insights, and verify analysis changes without modifying the saved deck until Review & Save. Check List, Art + Details, Grid, card art and collapse/jump functionality are unchanged.
