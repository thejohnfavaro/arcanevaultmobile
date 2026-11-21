# Arcane Vault Android v2.1.72 — AI Review + Adaptive Layout Preview

## Added
- Native AI Review for saved decks and unsaved deck drafts, built directly into Android Deck Library rather than a separate window.
- ChatGPT connection settings under Home → Options, with browser-based consent and device-local Android Keystore encrypted renewable credentials. Android authenticates separately from Windows; tokens are not included in collection or peer sync.
- Same read-only deck-health, owned-card substitution, and synergy review modes as Windows. Exact prompt preview, optional unchecked deck-only ownership-shortage context, copy prompt/review, and native formatted AI response text.
- Centralized window-width layout policy and regression tests for compact, medium, and expanded Android windows, including tablet and split-screen sizes.

## Changed
- Native Card Catalog artwork grid now adapts to the actual available app window width: narrower windows can use one column; larger windows grow to additional columns. Rotation, split-screen resizing, and foldable-window width changes reflow the cached page without resetting filters or requerying the card catalog.
- Native EDH Lab Commander Browser switches between single-pane list/detail and side-by-side list/preview based on current window width; it keeps commander selection and search filters on reflow.
- AI recommendations are displayed with native formatted headings, emphasis, and compact phone-friendly proposed-swap text. Raw Markdown can still be copied, but the on-screen result is formatted.

## Fixed
- Initial mobile layout no longer assumes the full physical screen width or locks the Commander Browser to its construction-time orientation.
- Android AI Review can return to the same unsaved editor controls and scroll position without saving, dropping, or modifying the draft.

## Removed
- Nothing from the saved collection, existing decks, proxy data, snapshots, sync or backups. AI Review does not automatically change cards or ownership.

## Validation and limitations
- Combined feature pull request #91 passed JavaScript regression checks, Android Java unit tests, and debug APK compilation.
- Signed production release workflow verifies the same tests, Android release signing and APK checksum. Device-by-device visual and interactive ChatGPT OAuth tests remain necessary.
- This release is a **responsive foundation**, not a claim that all tablets, foldables, Android versions or external windowing environments have been certified. Further device compatibility milestones remain in the roadmap (PR #92).
- ChatGPT permissions may expire or be revoked, so occasional reauthorization can be necessary. Only a user-initiated AI Analyze action submits a deck prompt.
