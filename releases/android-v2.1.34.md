# Arcane Vault Android v2.1.34 — Lifetime Collection Value and Set Progress Navigation

## Added
- Each Collection Value homescreen widget can select a card-price check interval of 6, 12, 24, or 48 hours.
- Widget valuations now show a persistent change in collection value since the first successfully priced snapshot, with red/green currency and percentage deltas.
- The widget configuration screen explains that the selected interval controls checks while the app is open; Android launcher refreshes use cached quotes when the app is closed.
- Android Home and Physical Collection now expose the same saved printing-aware price subtotal with USD, EUR, GBP, CAD, AUD, and JPY display preferences and Scryfall/TCGplayer-via-Scryfall reference selection.

## Changed
- Set Progress widget deep links now bypass the all-sets overview queue, so opening a selected set is not blocked by a slow catalog-wide progress query.
- Physical set ownership aggregation uses indexed print-ID, set-code, and collector matches independently rather than a global broad OR join.
- Set details show an immediate loading message and a Back to sets action during slow local queries.
- Widget height accommodates the lifetime change line without clipping the existing coverage and source labels.

## Fixed
- Deep-linked Set Progress could remain stuck at "Opening TRK…" with an empty screen.
- Set lists could appear empty or take too long to show when physical printing reconciliation or image metadata was expensive.
- Collection value was visible on the launcher but not in the main Android Home/Physical Collection views.
- A 48-hour price-check choice no longer defaults silently to 24 hours.

## Removed
- No owned cards, decks, stored printings, snapshots, or saved widget settings were deleted.

## Safety
- Market valuations remain derived exclusively from validated, printing-aware cached Scryfall USD quotes. TCGplayer selection is labelled as Scryfall-backed; direct retailer APIs are not integrated.
- Lifetime tracking starts with the first valid nonzero saved valuation and measures net collection value changes, including inventory changes, rather than claiming market-only investment return.
- Empty or unpriced collections never create a fake baseline.
- New card-price requests are only initiated through the established foreground WebView routine; Android periodic widget redraws do not perform invisible web pricing.
- The signed release workflow verifies the signature and SHA-256 checksum before publishing the APK.
