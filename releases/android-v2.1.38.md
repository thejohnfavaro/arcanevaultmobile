# Arcane Vault Android v2.1.38 — Set Progress Card Scaling

## Added
- Regression checks for uniform set-card bitmap scaling and aspect-ratio-preserving image views.

## Changed
- Set Progress cards use proportional bitmap dimensions when constrained for Android rendering, preserving original set-art, text, and set-symbol proportions.
- Native Set Progress tiles use a consistent 161dp display height and matching render height instead of independently resizing an image rendered for 176dp.
- Native set-card ImageViews preserve the bitmap aspect ratio rather than stretching it nonuniformly.
- The shared widget renderer uses the same uniform downscaling for capped bitmap dimensions.

## Fixed
- Set Progress labels, set symbols, and completion text could appear squashed or stretched on different screen widths and widget sizes.

## Removed
- Independent width/height clamping that distorted text and symbols.
- Stretched FIT_XY treatment for native Set Progress image cards.

## Safety
- Existing set progress ownership, cached set imagery, filtered sets, collection cards, and saved decks remain unchanged.
- All set cache invalidation and price handling remain untouched by this layout-only change.
