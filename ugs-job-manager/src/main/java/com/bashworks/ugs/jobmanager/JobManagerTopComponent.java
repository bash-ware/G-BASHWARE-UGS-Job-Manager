package com.bashworks.ugs.jobmanager;

import com.bashworks.ugs.jobmanager.machine.UgsJobManagerGateway;
import com.bashworks.ugs.jobmanager.machine.UgsHeightMapRecovery;
import javax.swing.SwingUtilities;
import org.openide.windows.WindowManager;
import com.bashworks.ugs.jobmanager.ui.JobManagerPanel;
import com.willwinder.ugs.nbp.lib.Mode;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import com.willwinder.universalgcodesender.model.BackendAPI;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;

import java.awt.BorderLayout;

/**
 * NetBeans TopComponent window entrypoint for G-BASHWARE Job Manager.
 * Registers the plugin window into the UGS Platform docking layout and Window menu.
 */
@TopComponent.Description(
        preferredID = "JobManagerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(
        mode = Mode.EDITOR_SECONDARY,
        openAtStartup = true,
        position = 505)
@ActionID(
        category = LocalizingService.CATEGORY_WINDOW,
        id = "com.bashworks.ugs.jobmanager.JobManagerTopComponent")
@ActionReference(path = LocalizingService.MENU_WINDOW_PLUGIN)
@TopComponent.OpenActionRegistration(
        displayName = "G-BASHWARE Job Manager",
        preferredID = "JobManagerTopComponent")
public final class JobManagerTopComponent extends TopComponent {

    private final BackendAPI backend;
    private UgsJobManagerGateway gateway;
    private JobManagerPanel panel;

    public JobManagerTopComponent() {
        this.backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        setLayout(new BorderLayout());
        refreshTitle();
        setToolTipText("Manage incoming CNC jobs and recovery checkpoints");
    }

    @Override
    protected void componentOpened() {
        refreshTitle();
        gateway = new UgsJobManagerGateway(backend);
        panel = new JobManagerPanel(gateway);
        var heightMap = new UgsHeightMapRecovery(backend,
                () -> WindowManager.getDefault().findTopComponent("AutoLevelerTopComponent"));
        panel.setHeightMapRecovery(heightMap::capture,
                encoded -> openAutoLeveler() && heightMap.restore(encoded),
                (file, units) -> openAutoLeveler() && heightMap.restoreXyz(file, units));
        add(panel, BorderLayout.CENTER);
        backend.addUGSEventListener(gateway);
        JobManagerPanel opened = panel;
        SwingUtilities.invokeLater(() -> {
            if (panel == opened) opened.openRecovery();
        });
    }

    private boolean openAutoLeveler() {
        var autoLeveler = WindowManager.getDefault().findTopComponent("AutoLevelerTopComponent");
        if (autoLeveler == null) return false;
        if (!autoLeveler.isOpened()) autoLeveler.open();
        return true;
    }

    private void refreshTitle() {
        setName(PluginInfo.TITLE);
        setDisplayName(PluginInfo.TITLE);
        setHtmlDisplayName(null);
    }

    @Override
    protected void componentClosed() {
        backend.removeUGSEventListener(gateway);
        panel.close();
        remove(panel);
        panel = null;
    }
}
