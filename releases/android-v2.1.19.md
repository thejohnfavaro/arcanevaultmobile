# Arcane Vault Android v2.1.19 — Peer Sync Safety

## Added
- Android unit tests for anonymous physical printing records in peer preview and merge.

## Changed
- Physical peer sync records must contain a card name or Scryfall printing ID to be matched.

## Fixed
- Reject identity-less physical records whose length-prefixed empty metadata fields previously appeared to form a unique key.

## Removed
- No collection, deck, printing, artwork or saved data removed.

## Safety
- Invalid incoming peer data is rejected without writing to Android inventory. Existing differential-sync review and confirmation safeguards remain in place.
