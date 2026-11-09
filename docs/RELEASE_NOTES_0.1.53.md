# Miowser 0.1.53 (code 54)

- The saved Library now uses an on-device Room database. Existing videos, folders, folder rules, selected subtitles, and playback progress migrate automatically.
- Saving a playback position updates one watch-state row. Folder imports, deletions, and Undo save the folder and its membership together.
- Backup files keep the existing format. Miowser exports the current Library from Room and restores older backups through the same restart recovery flow.

## Verify an upgrade

1. Create a backup from an existing populated Library and install the update over it.
2. Check nested folders, their order and rules, pinned entries, saved subtitles, and any relinked videos.
3. Play two saved videos, pause at different positions, restart, and check that each video resumes at its own position.
4. Import a folder, dissolve or delete it, and use Undo. Restart and confirm that the expected folders and videos persist.
5. Export a backup from this build and restore it. Restore the earlier backup too. Check the Library after each restart.

P1 retains the legacy preference snapshot for recovery and moves the existing saved Library to Room. Structural edits still save a Library snapshot. Paging and the primary Device Videos tab belong to later phases.
