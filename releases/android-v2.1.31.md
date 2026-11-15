# Arcane Vault Android v2.1.31 — Reliable Home-Screen Widgets

## Added
- **Real Set Progress search results:** an actual tappable list of Magic set names and official set codes, ranked by exact code, prefix and name matches. Search is no longer an unpopulated dropdown or a guess-based code field.
- **Scryfall set directory fallback:** a single official set-name download, cached for seven days, keeps the picker usable even if the large Android local card-printing catalog has not yet been indexed. The local index is preferred when available.
- **Exact single-set completion:** after selecting a result, Android reads only that set's verified printing or Arena name ownership instead of querying every set for every widget. Missing catalog data remains **unknown**.
- **Foreground collection-price recovery:** when a Collection Value widget exists and the native app opens, Arcane Vault can refresh missing/stale price quotes using its established printing-aware Scryfall batching and saved quote cache. A six-hour retry guard prevents repeated fetches. Launcher redraws never initiate network requests.

## Changed
- The setup screen now displays visible search results, an explicit selected set, genuine completion statistics when indexed, and clear guidance when a local catalog sync is required.
- Set Progress widgets refresh immediately after a successful complete Scryfall catalog index. Existing selected sets, saved snapshots, and ownership remain unchanged.
- The setup screen fills the entire available area with the Arcane Vault background, accommodates Android 15 system insets and status bar, and scrolls its content rather than leaving a large gray area.
- The collection value widget reports a foreground price refresh in progress without presenting zero as a valuation.

## Fixed
- Typing arbitrary short strings such as STK could previously create a widget without resolving a real Magic set, leaving a blank catalog and no useful search result.
- Heavy per-set aggregate SQL queries prevented a responsive set search and left launcher widgets without progress despite indexed data.
- A Collection Value widget could remain blank indefinitely unless the user manually opened legacy tools and refreshed physical printing prices.
- Android widget setup overlapped the status bar and left an unused default-theme gray panel beneath the form.

## Removed
- Removed provisional/free-text unverified set creation. Only an actual local or official Scryfall set directory entry may be selected.
- No existing collections, decks, physical printings, price quotes, Shortlists, or widget configurations were removed.

## Safety
- Set completion is derived from exact verified Scryfall printing IDs or set+collector fallback; card names do not imply physical printing ownership.
- Official set names alone never imply an indexed collection or a 0% completion rate.
- Collection values come from real persisted Scryfall market quotes for owned exact printings, never purchase prices or fabricated per-card estimates.
- Existing WebView quote storage remains the only pricing cache; no parallel valuation or inventory ledger was created.
- Scryfall quote updates are triggered only while the native app is open and a Collection Value widget exists, not from Android periodic widget redraws.
