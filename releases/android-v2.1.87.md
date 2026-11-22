# Arcane Vault Android v2.1.87

## Added
- Red minus-circle and green plus-circle visual markers for AI Review swap recommendations.

## Changed
- Replaced literal Cut and Add labels in mobile swap rows with colored markers. Kept Why and other explanatory labels.
- Preserved exact card mention markers so verified card names remain tappable.

## Fixed
- Swap recommendations use a more compact visual hierarchy without losing their reasoning.

## Removed
- No collection, deck, saved AI analysis, sync, or ChatGPT authorization data removed.

## Verification
- Signed release workflow must pass regression tests, Android unit tests, APK build and signing.
- Reopen a saved AI review and verify red cut markers, green add markers, clickable cards, and complete Why reasoning.
