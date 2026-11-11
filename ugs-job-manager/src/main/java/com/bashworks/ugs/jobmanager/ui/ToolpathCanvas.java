package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.service.ToolpathLoader.Toolpath;
import com.bashworks.ugs.jobmanager.service.ToolpathLoader.Segment;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/** Lightweight orthographic toolpath rendering; path shapes are cached between paints. */
public final class ToolpathCanvas extends JPanel {
    private Toolpath data;
    private boolean isometric=true, showRapids=false, onlyRapids;
    private String message="Select a job to view its toolpath";
    private Path2D cutting=new Path2D.Double(), rapids=new Path2D.Double();
    private double minX, minY, maxX, maxY, zoom=1, panX, panY;
    private Point dragStart;
    private final boolean interactive;

    public ToolpathCanvas(boolean interactive) {
        this.interactive=interactive;
        setBackground(UITheme.BG_INPUT);
        setPreferredSize(new Dimension(450,240));
        setMinimumSize(new Dimension(120,120));
        if (interactive) {
            MouseAdapter navigation=new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragStart=e.getPoint(); }
                @Override public void mouseReleased(MouseEvent e) { dragStart=null; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart!=null) { panX+=e.getX()-dragStart.x; panY+=e.getY()-dragStart.y; dragStart=e.getPoint(); repaint(); }
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    double oldZoom=zoom;
                    zoom=Math.max(0.1,Math.min(50,zoom*Math.pow(1.15,-e.getPreciseWheelRotation())));
                    double ratio=zoom/oldZoom;
                    panX=e.getX()-getWidth()/2.0-(e.getX()-getWidth()/2.0-panX)*ratio;
                    panY=e.getY()-getHeight()/2.0-(e.getY()-getHeight()/2.0-panY)*ratio;
                    repaint();
                }
                @Override public void mouseClicked(MouseEvent e) { if(e.getClickCount()==2) fit(); }
            };
            addMouseListener(navigation); addMouseMotionListener(navigation); addMouseWheelListener(navigation);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }
    public void showMessage(String text) { data=null; message=text; repaint(); }
    public void setToolpath(Toolpath path) { data=path; rebuild(); fit(); }
    public void setIsometric(boolean value) { if(isometric!=value) { isometric=value; rebuild(); fit(); } }
    public void setShowRapids(boolean value) { showRapids=value;rebuild();fit(); }
    public void fit() { zoom=1; panX=0; panY=0; repaint(); }
    public boolean hasToolpath() { return data!=null && !data.segments.isEmpty(); }
    private double px(double x,double y,double z) { return isometric ? (x-y)*0.8660254038 : x; }
    private double py(double x,double y,double z) { return isometric ? (x+y)*0.5-z : -y; }
    private void rebuild() {
        cutting=new Path2D.Double(); rapids=new Path2D.Double();
        minX=minY=Double.POSITIVE_INFINITY; maxX=maxY=Double.NEGATIVE_INFINITY;
        if(data==null) return;
        onlyRapids=data.segments.stream().allMatch(s -> s.rapid);
        for(Segment s:data.segments) {
            double x1=px(s.x1,s.y1,s.z1),y1=py(s.x1,s.y1,s.z1),x2=px(s.x2,s.y2,s.z2),y2=py(s.x2,s.y2,s.z2);
            Path2D path=s.rapid?rapids:cutting;
            path.moveTo(x1,y1); path.lineTo(x2,y2);
            if(!s.rapid || showRapids || onlyRapids) {
                minX=Math.min(minX,Math.min(x1,x2)); maxX=Math.max(maxX,Math.max(x1,x2));
                minY=Math.min(minY,Math.min(y1,y2)); maxY=Math.max(maxY,Math.max(y1,y2));
            }
        }
        message=data.segments.isEmpty()?"No drawable toolpath in this file":"";
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            g.setColor(new Color(0x24,0x2B,0x36));
            for(int x=24;x<w;x+=32) for(int y=24;y<h;y+=32) g.fillRect(x,y,1,1);
            if(!hasToolpath()) {
                g.setFont(UITheme.FONT_SMALL);g.setColor(UITheme.TEXT_MUTED);
                FontMetrics fm=g.getFontMetrics();
                g.drawString(message,Math.max(12,(w-fm.stringWidth(message))/2),h/2);
                return;
            }
            double scale=Math.min(Math.max(1,w-64)/Math.max(1e-6,maxX-minX),Math.max(1,h-80)/Math.max(1e-6,maxY-minY))*zoom;
            Graphics2D pathGraphics=(Graphics2D)g.create();
            pathGraphics.translate(w/2.0+panX,h/2.0+panY);
            pathGraphics.scale(scale,scale);
            pathGraphics.translate(-(minX+maxX)/2,-(minY+maxY)/2);
            pathGraphics.setColor(new Color(0x68,0x79,0x92));
            pathGraphics.setStroke(new BasicStroke((float)(1/scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10,new float[]{(float)(4/scale),(float)(4/scale)},0));
            if(showRapids || onlyRapids) pathGraphics.draw(rapids);
            pathGraphics.setColor(UITheme.ACCENT_TEAL);
            pathGraphics.setStroke(new BasicStroke((float)((interactive?1.6:1.3)/scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            pathGraphics.draw(cutting);
            pathGraphics.dispose();
            g.setFont(UITheme.FONT_SMALL_BOLD);g.setColor(UITheme.TEXT_SECONDARY);
            g.drawString(isometric?"ISOMETRIC · mm":"TOP / XY · mm",14,22);
            g.setColor(UITheme.ACCENT_TEAL);g.fillRoundRect(14,h-22,15,3,3,3);
            g.setFont(UITheme.FONT_SMALL);g.setColor(UITheme.TEXT_MUTED);g.drawString("Cut",35,h-16);
            if(showRapids || onlyRapids) {
                g.setColor(new Color(0x68,0x79,0x92));g.drawLine(75,h-20,91,h-20);
                g.setColor(UITheme.TEXT_MUTED);g.drawString("Travel",98,h-16);
            }
        } finally { g.dispose(); }
    }
}
