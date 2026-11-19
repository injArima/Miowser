# Miowser 0.1.86 (code 87)

- Corrected color-family usage across the theme system and app components:
  Primary for main actions/highlights/progress, Secondary for tonal actions and
  selected controls, Tertiary for complementary accents, and Neutral for page
  backgrounds, cards, ordinary text, and borders.
- Removed gray mixing from custom-theme neutral generation. Night now derives
  pale-blue surfaces in Light mode and navy surfaces in Dark mode from
  `#091540`; text and borders use contrasting neutral tones. AMOLED keeps its
  black surfaces.
- Corrected selection styling in hub navigation, tab selectors, speed dials,
  theme cards, Downloads toggles, settings switches, checkboxes, and radio
  controls. Selected-control colors are centralized.
- Removed accent fills from ordinary cards, thumbnail placeholders, metadata,
  and history surfaces. Main playback actions retain Primary colors.
- Download connection progress now stays within Primary instead of borrowing
  selection, decorative, and error colors. Delete/error colors remain separate.

See `THEME_COMPONENT_AUDIT_0.1.86.md` for locations to check. Device testing is
left to you.
