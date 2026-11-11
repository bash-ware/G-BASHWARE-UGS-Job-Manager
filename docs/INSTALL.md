# G-BASHWARE Job Manager 1.3.1 — Installation

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
- Select a job to inspect dimensions, tools, feed, spindle and G-code. **Start job** asks for confirmation before streaming.

Version 1.3.1 adds persistent run order, drag/arrow reordering and explicit run/completion states. The heading/search/refresh block and preview simulator bar have been removed.
