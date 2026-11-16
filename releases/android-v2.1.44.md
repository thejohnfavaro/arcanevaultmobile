# Arcane Vault Android v2.1.44

## Added
- **Smart Mana — Optimize Now** one-tap button in the native Commander/Brawl deck editor.
- Owned-only land optimization prioritizing shock lands, then reliably untapped sources, then conditional untapped lands, with tapped lands only as a fallback.
- Regression checks for one-tap land-only swaps, preserved mainboard quantity, and stale source rejection.

## Changed
- Smart Mana uses only the matching physical or Arena inventory; explicitly marked proxies are excluded.
- Existing nonbasic lands and every nonland card are retained.
- Smart Mana swaps only surplus basics, retains at least one copy of each existing basic name, and leaves the new draft unsaved until Review & Save.
- No separate preview/apply step is necessary for the Smart Mana action.

## Fixed
- Land suggestions never count the same recorded land twice, cannot silently change spell counts, and fail closed without local commander/legality metadata.

## Removed
- None. Manual card editing and the owned-substitution planner remain available.
