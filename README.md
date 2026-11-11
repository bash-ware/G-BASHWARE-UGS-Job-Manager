# G-BASHWARE Job Manager 1.3.1 for UGS

[Download the latest release](https://github.com/bash-ware/G-BASHWARE-UGS-Job-Manager/releases/latest).

Local incoming-job queue for G-code sent from GcodeMergerSplitter on another computer.

- Numbered manual run order with Up/Down arrows, Alt+Up/Down shortcuts and drag reordering.
- Clear Next, Running, Paused, Stopped and Finished states; completed history stays below pending work.
- Order and finished states persist. New jobs append; the running job is locked against reordering.
- Load next prepares the next pending job. Starting remains manual; nothing auto-starts after completion.
- Job details and G-code preview.
- Ctrl/Shift selection and **Remove selected**; **Clear queue** removes every entry.
- Compact interface without the heading/search/refresh block or simulator toolbar.
- Removals persist across refresh and restart. G-code files remain on disk and the active UGS job is unaffected.
- Incoming jobs are stored in `~/.ugs-job-manager/incoming/`; resending a job adds a new uniquely named queue file, preserving earlier jobs.
- LAN discovery on UDP 9875 and receiving on HTTP 9876. The LAN upload endpoint remains; URL downloads have been removed.
- Load into UGS, start, reload and unload actions retain the UGS gateway controls.

## Local preview

Run `./preview.ps1` (or `preview.bat`). The preview uses a simulated machine and an isolated data folder under `.verify/preview-home`, seeded with three example jobs. It uses HTTP 19876 and UDP 19875 so it can run alongside the installed plugin. Queue state in the preview is separate from the installed plugin.

## Build

Run `./build.ps1` to create the NBM module; `./package.ps1` prepares the distribution. See `docs/INSTALL.md` for installation.
