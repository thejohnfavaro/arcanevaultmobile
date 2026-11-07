# Arcane Vault Android v2.0.0

## Added
- Android 2.0.0 application baseline, including physical and Arena collection management.
- Manual collection transfer and import tools.
- In-app check for published Android releases.

## Changed
- Android builds use a consistent Gradle 8.13 wrapper and Java 17.
- Versioned Android releases now require signed APKs with checksums.

## Fixed
- Android SDK initialization in CI no longer requests the obsolete tools package.
- Artwork cache JSON status correctly handles JSON exceptions.
- Update checking now distinguishes missing stable releases from an outdated installation.

## Removed
- No user-facing features removed in this release.
