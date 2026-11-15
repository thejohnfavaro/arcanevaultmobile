# Arcane Vault Android v2.1.32 — Keyword Autocomplete and Value Widget Preferences

## Added
- Keyword and Oracle rules-text autocomplete in the native Card Catalog. Type a mechanic prefix such as Conn, see matching options, and tap a suggestion or press Space to complete the preferred result.
- Completed mechanics appear as individually removable keyword chips. Additional terms retain the existing comma-separated Match All / Match Any query semantics.
- Collection Value widget setup allows each widget to choose its own market, currency, and price basis, with an on-widget settings control to reopen configuration.
- Scryfall reference valuations can be displayed in USD, EUR, GBP, CAD, AUD, or JPY using a dated, validated, cached reference exchange rate.

## Changed
- Rules-text searches accept common Magic mechanics with keyboard completion and still permit arbitrary Oracle search phrases.
- Collection Value widgets keep individual preferences independent from other homescreen instances; widget refreshes never change owned card records.
- Display prices explicitly identify the source, quote date, and FX date where available.

## Fixed
- Collection Value widget configuration was missing when adding or reconfiguring a widget.
- Unsupported retailer-price selections now display unavailable rather than silently mislabeling Scryfall reference prices.
- Missing quotes and missing currency rates no longer produce misleading zero collection valuations.
- Fixed invalid XML and Java escaped-newline syntax in the widget configuration introduced in the prior debug-only work.

## Removed
- No collection items, saved decks, snapshots, printing identities, or existing user settings were removed.

## Safety
- TCGplayer, Card Kingdom and Cardmarket market rates are not yet integrated. They are selectable for future use but do not produce fabricated market totals.
- Scryfall remains the only connected price feed; quoted USD values remain authoritative and FX conversion is display-only.
- The signed release workflow verifies signing credentials, APK signature, and SHA256 checksum before publication.
