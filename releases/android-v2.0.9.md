# Arcane Vault Android v2.0.9

## Added
- Set Progress sorting by release date (newest or oldest), with release dates on set tiles and saved sorting preference.
- Pull-request debug APK validation to catch errors before Android releases.

## Changed
- Set release dates are read from the local Scryfall printing catalog; no extra API requests are needed.
- Incomplete Windows QR/USB sync downloads now report a clear error instead of risking an import from truncated data.

## Fixed
- Collection sync detects a response whose received byte count does not match the declared transfer size and rejects empty responses.
- Corrected a JavaScript syntax error introduced while adding release-date sorting, caught by CI before release.

## Removed
- No existing user-facing features removed.
