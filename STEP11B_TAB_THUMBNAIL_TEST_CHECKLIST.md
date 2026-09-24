# Step 11B — tab thumbnail checks

1. Open enough pages to place the current tab several rows down, then open **Tabs**. Confirm its first visible frame already contains the highlighted current tab—there should be no flash at the top followed by a scroll. Close Tabs, select a different deep tab, and repeat. Confirm Browsing or Private matches the current tab.
2. Tap each preview. Confirm switching is immediate, the page retains its scroll/form/playback state, and it does not reload merely because it was selected.
3. Close an inactive tab and the active tab from their preview close buttons. Confirm opener/previous-tab fallback behaves exactly as before.
4. Create normal and private tabs. Confirm they are separated into Browsing and Private, private state uses an icon on cards and beside the address field, and private tabs do not return after fully closing/restarting the app.
5. Background and relaunch Miowser with tab restoration enabled. Confirm regular restored tabs retain their previews when cached and old saved sessions still restore.
6. Rotate the phone and try a wider window. Confirm the grid adapts its column count without clipping cards or actions.
7. Scroll a webpage down by several screens, then open **Tabs**. Confirm its preview shows the currently visible scrolled portion rather than the top of the page. Repeat on a page with a sticky header or a scrollable inner panel. Scroll the tab grid far down and back up; cached previews must remain visible, while a failed/evicted preview uses the skeleton instead of a blank panel. Confirm the Browsing/Private pills remain fixed above the scrolling grid.
8. Select Browsing and tap the app-bar plus; confirm it creates a normal tab. Select Private and tap plus; confirm it creates a private tab (with the private-session confirmation only when starting the first private session).
9. Open more than 30 tabs, move among them, and confirm the app remains responsive; older previews should use the visible skeleton as the bounded cache evicts them.
10. In **Settings → Media grabber**, change **Minimum media length**, return to Detected Media, and confirm filtering still works without a slider on that page.
11. Open a new tab before its first preview capture finishes, or revisit a tab whose old preview has been evicted. Confirm its card shows a clearly visible page-shaped skeleton until a valid preview exists, including after scrolling away and back in Tabs.
