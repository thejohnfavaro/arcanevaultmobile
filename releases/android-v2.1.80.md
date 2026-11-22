# Arcane Vault Android v2.1.80

## Added
- OAuth code exchange errors now identify the failed DNS hostname (sanitized to a hostname only).
- Explicit report of token endpoint HTTP redirects and the destination hostname.

## Changed
- Token exchange requests do not silently follow HTTP redirects to a different host.
- The connection screen differentiates an intermittent DNS failure during code exchange from the earlier successful GET/HTTPS diagnostics.

## Fixed
- Generic network error that concealed which destination failed during OpenAI OAuth token exchange.

## Removed
- No collection, saved deck, sync or account data removed.

## Verification
- Signed workflow must pass regression checks, Android unit tests, build and APK signature verification.
- On-device: Home → Options → Continue with ChatGPT; if authorization fails at code exchange, capture the sanitized hostname or redirect report. No authorization codes or tokens are shown.
