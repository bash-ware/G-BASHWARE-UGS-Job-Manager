# G-BASHWARE Job Manager 1.2.0 — Installation

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
- Search by filename. Use Ctrl/Shift to select several jobs, then **Remove selected**.
- **Clear queue** removes every queue entry, including entries hidden by search.
- Queue removals persist across refresh and restart. They preserve files on disk and do not stop or unload an active UGS job. Sending a job again queues it again.
- Select a job to inspect dimensions, tools, feed, spindle and G-code. **Start job** asks for confirmation before streaming.

Version 1.2.0 removes the Web Download tab and folder browser in favor of the incoming LAN queue.
