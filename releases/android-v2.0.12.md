# Arcane Vault Android v2.0.12 — UI/UX Refresh

## Added
- Bottom navigation for Cards, Sets, Decks, More and Data, with the original full menu available under More.
- Card details organized into Overview, Printings, Legality and Combos tabs, with large touch-friendly controls and responsive layout.
- Visual Set Progress tiles emphasizing completion percentages and real owned printings.
- Unified Data & Sync screen for collection health and direct catalog, card metadata and pricing actions.
- Physical inventory price refresh using exact Scryfall ID or set and collector number, checkpointed locally in a separate price cache.
- Sort physical inventory by highest/lowest total estimated price or price per copy; unpriced entries last.

## Improved
- Shared visual styles with the Windows edition, accessible focus outlines, larger touch targets, saved detail and price sorting preferences, clearer empty/loading text.
- Metadata, catalog and physical inventory summaries without changing ownership.

## Data safety
- Android's native collection backing and SQLite schema are unchanged. Physical prices are stored separately and never alter quantities.
- Desktop-only Arena log connection remains removed. Manual match history and CSV transfer stay available.
- Scryfall price refresh does not assume a card-name match proves a printing or send frequent unpaced requests.
