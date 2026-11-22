# Arcane Vault Android v2.1.81

## Added
- Android active-network DNS fallback for ChatGPT OAuth token exchange and refresh when the initial request fails with UnknownHostException.

## Changed
- OAuth token exchange uses the existing default HTTPS path first and attempts the active-network connection only after a DNS lookup failure, never after an HTTP error or uncertain transport failure.
- All OAuth network requests retain HTTPS and the original OpenAI hostname; no external proxy or custom DNS provider is introduced.

## Fixed
- Potential mismatch between system DNS and the active Android network resolver during token exchange, despite successful HTTPS diagnostics on the same device.

## Removed
- No app data, decks, collection, sync state, or saved account records removed.

## Verification
- Signed release workflow must pass regression and Android unit tests, compile release, and verify the production APK signature.
- On device: Home → Options → Continue with ChatGPT, then Check ChatGPT connection. If both network resolvers fail, the app reports that condition without revealing tokens or codes.
