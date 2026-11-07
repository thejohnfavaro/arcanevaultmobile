# Arcane Vault Android v2.0.8

## Added
- A shared, conservative 700 ms request interval for all live Scryfall collection lookups.
- Cooldown handling for Scryfall HTTP 429 and HTTP 400/403 access warnings, including supported Retry-After headers.
- Specific status text when metadata enrichment is paused by Scryfall; existing card ownership and synced metadata are retained.

## Changed
- Android's local Scryfall proxy now serializes requests, rejects oversized batches, and avoids hammering Scryfall when temporarily restricted.
- Card metadata synchronization can be resumed after a cooldown, without overwriting successful prior batches.
- The full-printing catalog continues to use Scryfall's official bulk-data download, rather than repeated individual card requests.

## Fixed
- Repeated collection enrichment requests could trigger or worsen Scryfall rate limits and appear to stall partway through.

## Removed
- Treating Scryfall rate-limit and access-denial responses as generic retryable failures.
