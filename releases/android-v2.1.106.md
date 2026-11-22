# Arcane Vault Android v2.1.106

## Added
- Native Android Deck Health panel in unsaved Deck Editing. Optional disclosure keeps the existing editor compact and preserves the current draft.
- Strategy focus selector aligned with Windows EDH Lab: Balanced, Faster starts, Card advantage, Interaction & protection, and Commander synergy. Focus affects the display priority, not saved deck or inventory.
- Read-only, quantity-weighted local Oracle-text role coverage for lands, ramp, draw, removal, board wipes, and protection, using Windows' starting targets (36/10/10/8/2/2).
- Explicit counts for early plays with mana value 0–2, unverified cards, missing Oracle metadata, and limited role redundancy by distinct card names.
- Refresh Deck Health action and stale-result safety when the user changes the draft or closes the view.
- Java unit tests for role hints, target states and strategy ordering; JavaScript regression checks for a read-only editor and unknown-card blockers.

## Changed
- Health counts derive from the currently edited draft, not from owned inventory or an already saved deck. They run on the existing background worker.
- Partial decks are labeled Draft building rather than deficient; unknown catalog cards are Provisional and prevent confident replacement advice.
- The report explicitly distinguishes Oracle-text heuristics from verified card functionality, Commander legality, strength ratings, or owned-card recommendations.

## Fixed
- Android Deck Editing previously lacked Windows EDH Lab's basic role-coverage and strategy-prioritized checklist, making it difficult to spot an incomplete deck's broad composition gaps.

## Removed
- No deck, owned printing, proxy, local catalog record, saved AI review, sync data, or artwork cache removed or modified. List, Art + Details, Grid and Deck Insights remain in place.
- Advanced eligibility-validated owned-card replacements and actual commander-synergy matches are not claimed in this first native Android health pass; they remain explicit parity follow-ups.

## Verification
- Pre-release JavaScript regressions and Android debug compilation passed for the feature code; independent signed workflow must validate the current version, regression checks, unit tests, release compilation and APK signing.
- On device: open Borg -> Edit -> Deck Health. Switch between strategy focuses, inspect role counts and unknown metadata warnings, then change a draft quantity and select Refresh Deck Health. Confirm no saved deck or collection changes until Review & Save and that previous visual modes, artwork, collapse controls, mana curve, and colored casting-cost demand still work.
