package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.service.ToolpathLoader;
import com.bashworks.ugs.jobmanager.service.ToolpathLoader.Toolpath;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.concurrent.CancellationException;

/** Async selected-job thumbnail with a separate 900 x 800 inspection window. */
public final class ToolpathPreviewPanel extends JPanel {
    private final ToolpathCanvas canvas=new ToolpathCanvas(false);
    private final JLabel hint=UITheme.createMutedLabel(" ");
    private SwingWorker<Toolpath,Void> worker;
    private String identity;
    private File selected;
    private Toolpath path;
    private JDialog expanded;

    public ToolpathPreviewPanel() {
        setLayout(new BorderLayout(0,8));setBackground(UITheme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(new UITheme.RoundedBorder(UITheme.BORDER_COLOR,12),BorderFactory.createEmptyBorder(8,10,8,10)));
        JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);
        heading.add(UITheme.createMutedLabel("TOOLPATH"),BorderLayout.WEST);
        heading.add(hint,BorderLayout.EAST);
        add(heading,BorderLayout.NORTH);add(canvas,BorderLayout.CENTER);
        MouseAdapter enlarge=new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if(SwingUtilities.isLeftMouseButton(e)) openExpanded(); } };
        addMouseListener(enlarge);canvas.addMouseListener(enlarge);heading.addMouseListener(enlarge);hint.addMouseListener(enlarge);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"),"enlarge");
        getActionMap().put("enlarge",new AbstractAction(){ public void actionPerformed(ActionEvent e){openExpanded();} });
        canvas.setFocusable(true);
        canvas.getAccessibleContext().setAccessibleName("Selected job toolpath. Click or press Enter to enlarge.");
    }
    public void setJob(File file) {
        String key=file==null?null:file.getAbsolutePath()+":"+file.length()+":"+file.lastModified();
        if(java.util.Objects.equals(identity,key)) return;
        identity=key;selected=file;path=null;
        if(worker!=null) worker.cancel(true);
        if(expanded!=null) { expanded.dispose();expanded=null; }
        canvas.setCursor(Cursor.getDefaultCursor());
        if(file==null) { canvas.showMessage("Select a job to view its toolpath");hint.setText(" ");return; }
        canvas.showMessage("Loading toolpath…");hint.setText(" ");
        worker=new SwingWorker<>() {
            protected Toolpath doInBackground() throws Exception { return new ToolpathLoader().load(file); }
            protected void done() {
                if(isCancelled() || !java.util.Objects.equals(identity,key) || worker!=this) return;
                try {
                    path=get();canvas.setToolpath(path);
                    hint.setText(path.segments.isEmpty()?"No motion":"Click to enlarge ↗");
                    if(!path.note.isEmpty()) { hint.setText("Partial preview · click to enlarge");hint.setToolTipText(path.note); }
                    else hint.setToolTipText(null);
                    canvas.setCursor(path.segments.isEmpty()?Cursor.getDefaultCursor():Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } catch(CancellationException ignored) {
                } catch(Exception ex) {
                    path=null;canvas.showMessage("Toolpath could not be loaded");
                    hint.setText("Preview unavailable");hint.setToolTipText(ex.getCause()!=null?ex.getCause().getMessage():ex.getMessage());
                }
            }
        };
        worker.execute();
    }
    private void openExpanded() {
        if(path==null || selected==null || path.segments.isEmpty()) return;
        if(expanded!=null && expanded.isDisplayable()) { expanded.toFront();return; }
        expanded=new JDialog(SwingUtilities.getWindowAncestor(this),selected.getName()+" — Toolpath preview",Dialog.ModalityType.MODELESS);
        expanded.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        ToolpathCanvas large=new ToolpathCanvas(true);large.setToolpath(path);
        JPanel content=new JPanel(new BorderLayout(0,12));content.setBackground(UITheme.BG_PANEL);content.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));toolbar.setOpaque(false);
        DarkButton iso=new DarkButton("Isometric"),top=new DarkButton("Top / XY"),fit=new DarkButton("Fit view");
        iso.addActionListener(e->large.setIsometric(true));top.addActionListener(e->large.setIsometric(false));fit.addActionListener(e->large.fit());
        toolbar.add(iso);toolbar.add(top);toolbar.add(fit);
        JCheckBox travel=new JCheckBox("Rapid travel");travel.setOpaque(false);travel.setForeground(UITheme.TEXT_SECONDARY);travel.setFont(UITheme.FONT_SMALL);
        travel.addActionListener(e->large.setShowRapids(travel.isSelected()));toolbar.add(travel);
        toolbar.add(UITheme.createMutedLabel("Scroll to zoom · drag to pan"));
        content.add(toolbar,BorderLayout.NORTH);content.add(large,BorderLayout.CENTER);
        JLabel footer=UITheme.createMutedLabel(path.note.isEmpty()?String.format("%,d toolpath segments",path.segments.size()):path.note);
        footer.setToolTipText(footer.getText());content.add(footer,BorderLayout.SOUTH);
        expanded.setContentPane(content);
        expanded.getRootPane().registerKeyboardAction(e->expanded.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        expanded.setSize(900,800);expanded.setMinimumSize(new Dimension(500,400));expanded.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));expanded.setVisible(true);
    }
    public void close() { if(worker!=null) worker.cancel(true);if(expanded!=null)expanded.dispose(); }
}
