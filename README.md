# G-BASHWARE Job Manager for Universal Gcode Sender (UGS)

[![UGS Platform](https://img.shields.io/badge/UGS%20Platform-2.1.6-blue.svg)](https://winder.github.io/ugs_website/)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2022-orange.svg)](https://adoptium.net/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-green.svg)](https://www.gnu.org/licenses/gpl-3.0)

A high-performance CNC G-code job management, cataloging, and instant activation plugin for **Universal Gcode Sender (UGS) Platform**.

Load and inspect CNC jobs from your local disks, favorite directories, network shares, or direct remote web URLs, and activate them into UGS with one click.

---

## Key Features

* **📁 Local Disk Library & Folder Browser**:
  * Set a watched root folder with recursive subfolder scanning.
  * Real-time search/filter across hundreds of CNC files.
  * Supports `.gcode`, `.nc`, `.ngc`, `.tap`, `.cnc`, `.txt`, `.g`.
  * Recent jobs history automatically persisted across sessions.
  * Drag-and-drop support: drag files directly from Windows Explorer into the UI.

* **🌐 Web & Remote Jobs Downloader**:
  * Download G-code files directly from HTTP/HTTPS URLs.
  * Built-in download progress bar and network error handling.
  * Local caching in `~/.ugs-job-manager/web-cache/` for offline reuse.
  * Extensible architecture for REST / OctoPrint / CNC server integration.

* **⚡ One-Click UGS Activation & Safety**:
  * One-click **"Load into UGS (Activate)"** button loads the file into UGS backend (`backend.setGcodeFile(file)`).
  * Automatically updates the 3D toolpath visualizer and prepares the machine controller.
  * Streaming lockouts prevent accidental job replacement while the CNC machine is running.
  * Quick actions to Start Stream, Reload, or Unload active job.

* **🔍 G-Code Dimensional Inspector**:
  * Fast streaming parser analyzes motion bounds: X, Y, Z spans and ranges.
  * Total line count and actual coordinate motion lines.
  * Tool number detection (T1, T2...).
  * Spindle speeds (RPM) and maximum feed rates.
  * Monospace G-code header viewer with line numbering.

* **💻 Standalone Developer Harness**:
  * Includes a standalone runner (`preview.ps1` / `preview.bat`) allowing rapid UI development and testing with an in-memory simulated machine gateway in under 2 seconds without launching the full UGS platform.

---

## Building and Packaging

### Quick Standalone Preview
```powershell
.\preview.ps1
```
Or double-click `preview.bat`.

### Compile NBM Module
```powershell
.\build.ps1
```

### Full Release Packaging
```powershell
.\package.ps1
```
Creates distribution files in `dist/`:
* `G-BASHWARE-Job-Manager-for-UGS-2.1.6.nbm`
* `G-BASHWARE-Job-Manager-1.0.0_UGS-2.1.6_Windows.zip`
* `SHA256SUMS.txt`
* `update-center/updates.xml`

---

## Installation
See [docs/INSTALL.md](docs/INSTALL.md) for step-by-step instructions.

---

## License
GNU General Public License v3.0 (GPL-3.0) — Developed by **G-BASHWARE** / **BashWorks OU**.
