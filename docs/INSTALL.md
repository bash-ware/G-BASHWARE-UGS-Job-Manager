# G-BASHWARE Job Manager — Installation Guide

## Requirements
* **Universal Gcode Sender (UGS) Platform 2.1.6** (or newer)
* **Java Runtime 17+** (JDK 17 to JDK 22 supported)

---

## Installation via NBM Plugin File

1. In UGS Platform, open the top menu:
   **Tools → Plugins**
2. In the Plugins manager dialog, select the **Downloaded** tab.
3. Click the **Add Plugins...** button.
4. Select the provided `.nbm` file:
   `G-BASHWARE-Job-Manager-for-UGS-2.1.6.nbm`
5. Click **Install** at the bottom left.
6. Follow the installation wizard steps, accept the license, and click **Finish**.
7. Restart UGS Platform when prompted.

---

## Opening the Plugin Window

Once installed and restarted:
* The plugin window automatically docks into your UGS layout (by default docked next to the Visualizer or Editor Secondary pane).
* If closed, you can reopen it anytime from the top menu:
  **Window → Plugins → G-BASHWARE Job Manager**

---

## Quick Usage

### 1. Local Disk Library
* Set your default CNC jobs folder using the **Browse...** button.
* Toggle **Include Subfolders** to scan nested project folders.
* Use the **Filter** field to search through hundreds of files instantly.
* Double-click any job in the table or click **⚡ Load into UGS (Activate)** to immediately send it to UGS, parse toolpaths, and prepare the machine.
* Drag-and-drop any `.gcode`, `.nc`, `.ngc`, or `.tap` file from Windows Explorer straight into the panel.

### 2. Web & Remote Jobs
* Switch to the **🌐 Web Download** tab.
* Enter a direct URL to a G-code file or select one of the presets.
* Click **⚡ Download & Activate in UGS** to fetch, parse dimensions, cache locally, and activate in UGS in one step!
