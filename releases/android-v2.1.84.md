# Arcane Vault Android v2.1.84

## Added
- Android connected-network enumeration as a fallback when the system reports no default active network during ChatGPT OAuth token exchange.
- Clear distinction between no usable network and selected-network DNS/HTTPS failures.

## Changed
- The OAuth token exchange still uses the normal HTTPS connection first. Only a confirmed DNS failure activates a fallback, which selects an internet-capable network, prioritizing a validated network.
- Failed authorization never removes collection, deck or sync data.

## Fixed
- v2.1.83 could report no active Android network even though a usable Wi-Fi or cellular network was available, preventing the fallback from running.

## Removed
- No collection, deck, sync or saved credential data removed.

## Verification
- Signed release workflow must pass JavaScript regression checks, Android unit tests, release compilation, and APK signing verification.
- On device, Home → Options → Continue with ChatGPT should either finish authorization or report the selected-network outcome.
