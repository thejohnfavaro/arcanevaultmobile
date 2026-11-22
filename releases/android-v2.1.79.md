# Arcane Vault Android v2.1.79

## Added
- OAuth failure-stage reporting in Home → Options: local callback, browser return, token exchange, identity verification, plan permission, and encrypted credential save.
- Safe OAuth HTTP error identifiers alongside status codes when returned by the authorization service.

## Changed
- Unsuccessful authorization retains a useful status explanation on the ChatGPT connection screen.
- Browser callback timeouts are distinguished from a network DNS failure.
- Authorization errors avoid exposing tokens, authorization codes, account IDs, or full HTTP response bodies.

## Fixed
- Generic ChatGPT connection failures that did not identify whether a browser callback, token exchange, or secure save failed.

## Removed
- No collection, deck, sync, or authorization data removed.

## Verification
- Signed release workflow must pass Android compile, regression tests, and APK signature verification.
- On device, Home → Options → Continue with ChatGPT should report the precise failure stage. No prompt or collection is sent during diagnosis.
