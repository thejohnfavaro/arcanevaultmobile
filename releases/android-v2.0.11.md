# Arcane Vault Android v2.0.11

## Added
- A clearly labeled **Match History** page for manual results, saved-deck association and CSV import/export.
- An explicit **Import history CSV** button for bringing desktop match records to Android.
- Regression checks confirming Windows-only Arena controls and log polling are absent.

## Changed
- Match statistics now say **recorded** wins, losses and win rate instead of implying automatic verification.
- The welcome guide describes Android's actual manual match-history workflow.
- Match History refreshes local records and imported CSV data only, without attempting to scrape an inaccessible Windows log.

## Fixed
- Android previously displayed an **Arena Connection** page that called Windows-only endpoints and reported connection failures.
- Arena log polling could run during mobile startup even though the Windows log service was unavailable.

## Removed
- Unsupported Arena Connection screen, log path/configuration, wildcard snapshot copy and background log polling from Android.
- Desktop-only Arena connection navigation and onboarding instructions.

Existing card collections, saved decks, and locally recorded/imported match history are retained. The Windows app is unchanged.
