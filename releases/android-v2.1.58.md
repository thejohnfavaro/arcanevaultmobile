# Arcane Vault Android v2.1.58

## Added
- **Shared full native card inspector from Set Progress.** Tapping a card entry in a set now opens the same Catalog inspector instead of a reduced AlertDialog. This includes official artwork, exact selected printing and back face, Oracle text, type, rarity, owned Arena and Physical totals, shortlist actions, other printings, edit physical ownership, mana value, color identity and cached format legalities.
- **Set Progress overview parity.** Collection-wide owned/missing counts and completion percentage are visible on the main Sets screen. Each set tile also exposes its verified owned count, missing count and release date beneath the existing progress artwork, price information and percent.
- Cached exact mana costs in Android's Scryfall SQLite catalog, including multi-face cards. Existing databases are migrated without clearing cards or ownership. Cards imported before this version show mana cost when Scryfall metadata is next refreshed.
- Exact-name metadata lookup and separate printing-ID lookup so common card names cannot be lost in broad search results.
- Regression checks for full inspector handoff, exact printings, legality information, mana-cost migration, and overview totals.

## Changed
- Format legalities are expandable below Oracle text on mobile, preventing a long list of format statuses from pushing rules text out of view. Their values remain available in the shared Catalog inspector.
- Back from a card opened through Set Progress returns directly to that set without creating a loop through Catalog.
- Existing Set Progress value and printing-identity rules remain intact; no card value or ownership is invented from a name.

## Fixed
- Set Progress's previous metadata-only card popup and subsequent artwork-only replacement omitted the information already accessible in the Catalog inspector.
- Windows-style per-set and collection-wide completion information was hidden until drilling into sets; filtering a cached overview discarded its aggregate totals.
- A fuzzy card-name search could return unrelated cards and fail to find the exact inspected card.

## Removed
- Retired the redundant metadata-only Set Progress card popup.
- No saved inventory, deck, artwork, peer checkpoint, preferred printings or pre-sync recovery backup is removed. No change to peer transfer authorization.
