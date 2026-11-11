package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.service.ToolpathLoader.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.File;
import javax.swing.*;
import com.bashworks.ugs.jobmanager.ui.ToolpathPreviewPanel;
import com.bashworks.ugs.jobmanager.ui.ToolpathCanvas;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;

public class ToolpathLoaderTest {
    private File file(String text) throws Exception {
        Path path=Files.createTempFile("ugs-toolpath-", ".nc");
        Files.writeString(path,text);return path.toFile();
    }
    private Toolpath parse(String text) throws Exception { return new ToolpathLoader().load(file(text)); }
    @Test public void modalMovesAndUnits() throws Exception {
        Toolpath data=parse("G21 G90\nG0 X10 Y20 Z5\nG1 Z-2 F500\nG91\nX5 Y-4\nG20 G90\nG1 X1 Y1 Z0\nG21\nG1 X30\n");
        assertEquals("",data.note);assertEquals(5,data.segments.size());
        assertTrue(data.segments.get(0).rapid);assertFalse(data.segments.get(1).rapid);
        assertEquals(15,data.segments.get(2).x2,1e-8);assertEquals(16,data.segments.get(2).y2,1e-8);
        assertEquals(25.4,data.segments.get(3).x2,1e-8);assertEquals(25.4,data.segments.get(3).y2,1e-8);
        assertEquals(30,data.segments.get(4).x2,1e-8);
    }
    @Test public void ijArcsAndFullCircle() throws Exception {
        Toolpath data=parse("G21 G90 G17\nG0 X10 Y0 Z0\nG3 X0 Y10 I-10 J0\nG2 X10 Y0 I0 J-10\nG2 I-10 J0\n");
        assertEquals("",data.note);assertTrue("Arc expansion count: " + data.segments.size(),data.segments.size()>200);
        double minY=0;
        for(Segment segment:data.segments) { assertTrue(Double.isFinite(segment.x2));minY=Math.min(minY,segment.y2); }
        assertTrue("Full circle must curve below the chord",minY < -9.9);
        Segment last=data.segments.get(data.segments.size()-1);
        assertEquals(10,last.x2,1e-6);assertEquals(0,last.y2,1e-6);
    }
    @Test public void radiusAndVerticalPlaneHelix() throws Exception {
        Toolpath radius=parse("G21 G90 G17\nG0 X0 Y0 Z0\nG2 X10 Y10 R10\n");
        assertEquals("",radius.note);assertTrue(radius.segments.size()>10);
        Toolpath vertical=parse("G21 G90 G18\nG0 X10 Y0 Z0\nG3 X0 Y5 Z10 I-10 K0\n");
        assertEquals("",vertical.note);assertTrue(vertical.segments.size()>10);
        Segment last=vertical.segments.get(vertical.segments.size()-1);
        assertEquals(0,last.x2,1e-6);assertEquals(5,last.y2,1e-6);assertEquals(10,last.z2,1e-6);
    }
    @Test public void emptyFileAndMissingFile() throws Exception {
        assertTrue(parse("(no motion)\nM5\nM30").segments.isEmpty());
        try { new ToolpathLoader().load(new File("does-not-exist-toolpath.nc"));fail("Missing file must be reported"); }
        catch(java.io.IOException expected) {}
    }
    @Test public void selectionReplacementAndPainting() throws Exception {
        File first=file("G21 G1 X100 Y100 Z5\n");
        File second=file("G21 G1 X7 Y9 Z1\n");
        ToolpathPreviewPanel[] panels=new ToolpathPreviewPanel[1];
        SwingUtilities.invokeAndWait(()->{
            panels[0]=new ToolpathPreviewPanel();
            panels[0].setJob(first);panels[0].setJob(second);
        });
        ToolpathPreviewPanel panel=panels[0];
        var field=ToolpathPreviewPanel.class.getDeclaredField("worker");field.setAccessible(true);
        SwingWorker<?,?> worker=(SwingWorker<?,?>)field.get(panel);
        worker.get(10,TimeUnit.SECONDS);
        var pathField=ToolpathPreviewPanel.class.getDeclaredField("path");pathField.setAccessible(true);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        Toolpath[] result=new Toolpath[1];
        while(result[0]==null && System.nanoTime()<deadline) {
            SwingUtilities.invokeAndWait(()->{try {result[0]=(Toolpath)pathField.get(panel);}catch(Exception ex){throw new RuntimeException(ex);}});
            if(result[0]==null) Thread.sleep(10);
        }
        assertNotNull("Async preview must load",result[0]);
        assertEquals(7,result[0].segments.get(0).x2,1e-8);
        SwingUtilities.invokeAndWait(()->{
            ToolpathCanvas canvas=new ToolpathCanvas(true);canvas.setToolpath(result[0]);canvas.setSize(450,240);
            BufferedImage image=new BufferedImage(450,240,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();canvas.paint(g);g.dispose();
            int colored=0;
            for(int x=0;x<450;x++)for(int y=0;y<240;y++)if((image.getRGB(x,y)&0xffffff)==0x06d6a0)colored++;
            assertTrue("Canvas must render the toolpath",colored>10);
            canvas.setIsometric(false);canvas.fit();
            if(!java.awt.GraphicsEnvironment.isHeadless()) {
                try {
                    var open=ToolpathPreviewPanel.class.getDeclaredMethod("openExpanded");open.setAccessible(true);open.invoke(panel);
                    var popup=ToolpathPreviewPanel.class.getDeclaredField("expanded");popup.setAccessible(true);
                    JDialog dialog=(JDialog)popup.get(panel);
                    assertNotNull(dialog);assertTrue(dialog.isVisible());
                    assertEquals(900,dialog.getWidth());assertEquals(800,dialog.getHeight());
                    dialog.dispose();
                } catch(Exception ex) { throw new RuntimeException(ex); }
            }
            panel.setJob(null);panel.close();
        });
    }
}
