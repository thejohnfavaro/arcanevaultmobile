# Arcane Vault Android v2.1.28 — Native System Back Navigation

## Added
- Android three-button Back and Back swipe gesture navigate one native screen at a time instead of immediately exiting the app.
- Back navigation follows the existing Card Catalog inspector hierarchy: card details, all printings, exact printing details, then catalog.
- Commander previews return to the commander list on phone screens.
- Deck editor returns to deck details, which return to saved Deck Library.
- Set details return to Set Progress; Shortlist full-card previews return to the illustrated Shortlist gallery and restore its scroll position.
- Sync subsections return to their previous selected Sync category; bottom-tab and contextual-tab changes retain bounded navigation history.
- Unsaved deck name/mainboard changes require explicit confirmation before system Back or switching to another workspace discards a draft.
- The native Home screen only exits when its navigation history is exhausted.

## Changed
- The native root view now retains its navigation controller until the user opens legacy tools or leaves the app.
- Android 13+ uses the system predictive Back dispatcher; older devices use the existing onBackPressed fallback.
- Legacy WebView tools retain their original open-dialog dismissal and Back-to-native behavior.

## Fixed
- Android Back previously closed the entire Activity from every native workspace.
- Navigating away from a dirty native deck edit could discard the draft without confirmation.

## Removed
- No saved decks, Shortlist items, physical/Arena inventories, printings or sync data removed.

## Safety
- Navigation state is in-memory and bounded; it does not create a second collection or deck store.
- Saved-deck edits still use recovery-backed compare-and-swap save confirmations.
- Existing Android in-app update/signature validation and wireless/USB/QR sync are unchanged.
