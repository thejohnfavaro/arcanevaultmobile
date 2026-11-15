# Arcane Vault Android v2.1.29 — Two Native Home-Screen Widgets

## Added
- **Collection Value** launcher widget: estimated physical collection USD value from the **existing cached Scryfall printing quotes**. Shows priced printing coverage, total physical card copies and the date of last price update.
- **Set Progress** launcher widget: independently configure a Magic set and Physical verified printings or Arena card-name tracking, with name, set code, owned/total and a progress bar.
- Launcher Set Progress setup screen with local set search, separate per-widget source selection and live preview; reconfigure individual widgets via the gear icon.
- Deep-link widget taps to Physical Collection or the matching exact Set Progress detail in native Arcane Vault, retaining native Back behavior.
- Responsive visual tiles shared by native Set Progress and the launcher Set Progress widget, using a consistent dark Arcane Noir surface, gold highlight, purple progress bar, set badge and percentage.
- Independently refreshing widget snapshots when app inventory or local ownership indexes change; scheduled widget updates read only local persisted state.

## Changed
- Android Set Progress rows now use the same designed card renderer as the home-screen Set Progress widget, while keeping Physical/Arena filters, sorting, and tap-to-view details.
- Existing legacy physical price calculation publishes its derived Scryfall-cached market estimate to the launcher. The widget never initiates a price lookup, invents quotes or counts purchase price as market price.
- Verified physical printing counts and Arena name coverage still use existing SQLite catalog semantics; the widget has **no additional ownership ledger**.

## Fixed
- Launcher previously offered no Arcane Vault status widgets or way to watch a single set without opening the full app.

## Removed
- No collections, decks, Shortlist records, printings or sync metadata removed.

## Safety
- Valuation visibly shows only priced inventory coverage; unpriced cards are not silently valued at zero or assumed to have a live quote.
- A missing or syncing set catalog is shown as unavailable, not a misleading 0% completion.
- Widget configuration and snapshots live in isolated small preferences, never in a duplicate collection database.
- Cards, set progress and cached value are derived from existing local ownership, Scryfall catalog and price-quote caches; no network access or extra permission is required by a widget redraw.
- Signed in-app updates, differential/QR/USB sync and saved-deck integrity remain unchanged.

## Future Windows work
- A separate upcoming Windows Card Details layout pass will remove the fixed 230 DIP metadata panel cap and provide responsive legality layout.
