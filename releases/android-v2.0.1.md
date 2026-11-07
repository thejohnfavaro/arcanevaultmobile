# Arcane Vault Android v2.0.1

## Added
- Automated signed APK release distribution through GitHub Releases.
- SHA-256 checksums for release downloads.

## Changed
- Release builds use the repository's persistent signing identity supplied through GitHub Actions secrets.
- Application update checks identify stable published Android releases using the android-vX.Y.Z tag format.

## Fixed
- Clearer messaging when only debug/test or prerelease APKs are available.
- Versioned release packaging and signing verification.

## Removed
- No user-facing features removed.
