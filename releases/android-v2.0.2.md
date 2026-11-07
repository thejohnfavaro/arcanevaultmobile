# Arcane Vault Android v2.0.2

## Added
- Mobile viewport safeguards to keep content within the device screen.
- Regression coverage to prevent stray text following the final HTML tag.

## Changed
- The Android GitHub update checker now defaults to the correct repository: thejohnfavaro/arcanevaultmobile.
- Long notes and code snippets wrap or scroll within the mobile viewport rather than widening the whole page.

## Fixed
- Removed leftover duplicated JavaScript after the end of the Android HTML page that rendered as visible text at the bottom of the app.
- Prevented horizontal page overflow and off-screen content on narrow phones.
- Improved the GitHub 404 message for an incorrect update repository.

## Removed
- Erroneous page content outside the HTML document.
