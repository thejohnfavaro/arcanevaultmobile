# Arcane Vault Android v2.1.83

## Added
- Unit coverage for wrapped Android DNS exceptions and safeguards against retrying non-DNS transport failures.

## Changed
- OAuth token exchange checks the exception cause chain before deciding whether Android's active-network DNS fallback is appropriate.
- Fallback errors explicitly state when an active-network retry was attempted.

## Fixed
- Wrapped `UnknownHostException` errors could previously bypass the token-exchange DNS fallback while still displaying a generic DNS message.

## Removed
- No collection, deck, sync or saved credential data removed.

## Verification
- Signed release workflow must pass JavaScript regressions, Android unit tests, compilation, release signing and signature verification.
- On device, Continue with ChatGPT should either complete the token exchange or identify the active-network fallback result.
