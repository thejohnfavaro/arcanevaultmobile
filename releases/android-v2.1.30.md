# Arcane Vault Android v2.1.30 — Launcher Widget Hotfix

## Added
- No new widget types; the existing separate Collection Value and Set Progress providers are now correctly named and configured.

## Changed
- Set selection now accepts a valid set code while the local catalog indexes, and the launcher card preview preserves aspect ratio.
- Cached Scryfall valuation is initialized from the authoritative Android physical inventory on app startup.

## Fixed
- **Distinct widget picker labels.** The two providers now register their own "Collection Value" and "Set Progress" names and descriptive text instead of both appearing only as generic Arcane Vault entries.
- **Set Progress selection.** The setup form accepts an official 2–5 character set code (for example BLB) even while the local Scryfall catalog is still loading or has not been synced. A missing catalog remains unverified instead of pretending the set has 0% completion.
- **Set Progress preview sizing.** The setup artwork and launcher image use aspect-preserving FIT_CENTER, with device-width-aware rendering rather than stretching a fixed-size bitmap.
- **Reliable widget creation.** The setup button clearly shows which set will be added, remains enabled for a valid catalog selection or exact set code, and synchronously verifies settings storage before returning success to the Android launcher.
- **Collection Value initialization.** The native app now automatically derives the widget's estimated USD value from its authoritative physical inventory and the **existing Scryfall quote cache** as soon as its WebView origin is ready. Previously, the snapshot was only published when the legacy Physical Collection page rendered.
- **Correct valuation status.** Distinguish no cached price quotes, quotes not matching currently owned printings, no inventory, and real priced coverage. No estimates are invented and unpriced cards are not silently assigned prices.
- **Refresh after changes.** Native printing edits and WebView collection updates now recalculate the cached value projection when possible.

## Removed
- Nothing removed from widgets, inventories, decks or sync records.

## Safety and scope
- The widget is strictly a local read-only view of existing stored physical inventory and the WebView's locally cached Scryfall USD price quotes.
- If Android has never refreshed quotes, it will show a clear "No Scryfall quotes cached" message until prices are fetched in Arcane Vault; Windows prices are not silently assumed to be mirrored to Android.
- Neither widget redraws by querying the network, no new inventory authority was added, and saved sets/collections/decks remain unchanged.
- The official set icon, background art, native set-card parity and native Android Back navigation remain intact.
