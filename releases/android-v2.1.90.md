# Arcane Vault Android v2.1.90

## Added
- Full-height, naturally scrolling card details when opening cards from AI Deck Review.

## Changed
- Embedded AI card inspection now uses the outer deck-workspace scroll instead of a fixed 750dp nested viewport.
- Oracle text, card metadata, format legalities, and printing controls can occupy their full content height.
- Returning to the AI review retains its prior analysis and scroll position.

## Fixed
- Long Oracle text appeared clipped when inspecting cards through an AI review.
- Fixed-height nested inspector prevented reliable scrolling through all card details.

## Removed
- The 750dp fixed-height cap in the AI-linked inspector only; no card text, decks, collection, saved analyses, or authorization data removed.

## Verification
- Signed Android release must pass the existing regression, unit-test, compilation, and signing checks.
- On device, tap a card reference from AI Deck Review, scroll through the full Oracle text and lower details, then return to the saved review.
