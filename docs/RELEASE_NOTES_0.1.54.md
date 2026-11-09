# Miowser 0.1.54 (code 55)

- The saved Library loads a small window of videos around the viewport. Room handles folder counts, search, and the existing Recently played, Title, and Watched progress sorts.
- Each folder remembers its position separately in grid and list view. Progress and thumbnail updates write and refresh the affected row. When watched-progress sorting changes the order, the Library keeps the visible video in place where possible.
- Playing a Library video builds a playlist from the full filtered order, so Next and Previous work across page boundaries.
- The database upgrade builds the search and sort indexes from existing records. The backup format is unchanged.

## Verify P2

1. In a folder with more than 150 videos, scroll past the first few screens, switch to another folder or Downloads, and return. Try the same with grid and list view.
2. Type and replace search terms quickly. Check matches by title, source name, and media address. Clear the search and verify the folder returns to its saved position.
3. Pause a video partway through, return to the Library, and check progress sorting and scroll position. Verify artwork updates on the affected card.
4. Play a video near a page boundary. Open the playlist and use Next and Previous to reach videos outside the loaded window.

P2 pages the browsing surface. The existing Library edit and backup paths still use full record snapshots when needed. The primary Device Videos tab is P3.
