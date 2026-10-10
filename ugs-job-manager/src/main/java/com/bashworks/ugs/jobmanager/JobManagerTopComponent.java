package com.bashworks.ugs.jobmanager;

import com.bashworks.ugs.jobmanager.machine.UgsJobManagerGateway;
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
    private final UgsJobManagerGateway gateway;
    private final JobManagerPanel panel;

    public JobManagerTopComponent() {
        this.backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        this.gateway = new UgsJobManagerGateway(backend);
        this.panel = new JobManagerPanel(gateway);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);

        refreshTitle();
        setToolTipText("Load, catalog, and activate CNC G-code jobs from disk and web");
    }

    @Override
    protected void componentOpened() {
        refreshTitle();
        backend.addUGSEventListener(gateway);
    }

    @Override
    protected void componentShowing() {
        refreshTitle();
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
    }
}
