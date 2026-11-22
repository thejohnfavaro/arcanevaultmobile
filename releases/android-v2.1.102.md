# Arcane Vault Android v2.1.102

## Added
- A "Retry missing artwork" control in Art + Details and Grid so cards with temporarily unavailable images can be requested again without leaving Deck Editing.
- A bounded, time-based backoff for failed draft image requests, allowing later recovery during the same editing session.

## Changed
- Art requests now keep at most two images in flight, matching the two artwork workers instead of queueing eight images behind them.
- The loader prioritizes the card thumbnails actually visible when scrolling between or expanding card-type sections, rather than allowing earlier groups to monopolize its work queue.
- Missing draft artwork is no longer permanently excluded after two requests. Temporary failures back off for 6–60 seconds, and the explicit retry action clears the delay.
- Existing local Scryfall exact-name lookup, saved Arena printing annotations, cache, and immediate image repaint behavior are preserved.

## Fixed
- Card artwork in sections after Creatures could stay on a placeholder when queued image requests or temporary failures blocked newly visible entries.
- The visual editor can recover missing artwork in both Art + Details and Grid without resetting the draft or its section layout.

## Removed
- No saved decks, collection or printing data, proxies, AI reviews, synchronization records, or existing List/Grid/Art views removed.

## Verification
- Pre-release JavaScript regression suite, Android unit tests, and debug APK compilation all passed.
- Signed release workflow must independently validate tests, release compilation, and APK signing.
- On device: open a Commander deck -> Edit -> Visual mainboard -> Art + Details or Grid. Scroll to Instants, Sorceries, Artifacts, and Lands, verify their artwork and use Retry missing artwork for any still showing placeholders. Confirm collapse/jump and Review & Save remain intact.
