# G-BASHWARE Job Manager 1.6.0 — Installation

## Requirements

- Universal Gcode Sender (UGS) Platform 2.1.6
- Java 17 or newer

## Install or update

1. Download `G-BASHWARE-Job-Manager-for-UGS-2.1.6.nbm` from the [latest release](https://github.com/bash-ware/G-BASHWARE-UGS-Job-Manager/releases/latest).
2. In UGS, open **Tools → Plugins → Downloaded → Add Plugins** and select the NBM.
3. Install the update and restart UGS when prompted.
4. Open **Window → Plugins → G-BASHWARE Job Manager**.

If you already use the project's NetBeans update center, check for updates in **Tools → Plugins → Updates**.

## Receive and manage jobs

- Send G-code from GcodeMergerSplitter on the other computer over the local network. Discovery uses UDP 9875; the upload receiver uses HTTP 9876. Both computers must be able to reach those ports.
- Received files appear in **Incoming jobs** and are saved in `~/.ugs-job-manager/incoming/`.
- **Load on receive** controls automatic loading into UGS. Double-click a job or choose **Load into UGS** to load manually.
- Use Ctrl/Shift to select several jobs, then **Remove selected**.
- Use **Up / Down**, Alt+Up/Down or drag rows to change the saved order of pending jobs. Running and finished jobs cannot be reordered.
- The first pending job is **Next**. The stream in progress is **Running** (or **Paused**); UGS completion marks it **Finished**. Cancellation/disconnection is treated as stopped work, never as a completed job.
- **Load next** prepares the next pending job; starting each run remains manual.
- **Clear queue** removes every queue entry.
- Queue removals persist across refresh and restart. They preserve files on disk and do not stop or unload an active UGS job. Sending a job again queues it again.
- Select a job to inspect dimensions, tools, feed, spindle and its visual toolpath. Click the thumbnail to open a 900 × 800 preview.
- In the enlarged view, switch between **Isometric** and **Top / XY**, scroll to zoom, drag to pan, or choose **Fit view**. **Rapid travel** shows positioning moves; the default focuses on cutting geometry.
- **Start job** asks for confirmation before streaming.
- The supplied warning alarm plays once when UGS confirms a completed run. Any keypress or mouse activity stops it; on Windows this also works while another app has focus. Pausing, stopping, cancelling or disconnecting does not trigger it. Playback uses your default audio output and existing system volume.

## Recover after an interruption

Leave **Autosave** on to save the queue, loaded and unfinished jobs, height map, and the latest reported positions, controller state, and commands. Choose **Recover** when Job Manager opens to reload the queue and unfinished job.

Job Manager opens AutoLeveler on compatible UGS builds and restores the saved scan for review with compensation off. You can also attach a complete AutoLeveler `.xyz` export and record its units. On UGS 2.1.6, Job Manager preserves the map for export because that version has no AutoLeveler window.

If the tool stayed in place, **Continue…** opens the saved checkpoint for review. Restarting machine motion requires a separately verified restart point.
