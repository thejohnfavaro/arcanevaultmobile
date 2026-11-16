# Arcane Vault Android v2.1.54

## Added
- Regression coverage for independently scoped Arena and saved-deck peer merge choices in the five-argument prepareMerge call.

## Changed
- Update native-wireless-apply JavaScript regression test to match the existing separate Arena and saved-deck choice arguments.
- Retain existing per-record and bulk-choice review, full recovery snapshot, live-state verification, and final approval.

## Fixed
- Android Signed Release v2.1.53 run 43791376538 failed at step 8 due to an outdated four-argument prepareMerge assertion in tests/native-wireless-apply.mjs.
- All 39 release regression test scripts were checked for similar outdated signatures and retired UI labels.

## Removed
- No data, card quantities, saved decks, artwork, or peer baselines removed. No change to peer merge semantics.
