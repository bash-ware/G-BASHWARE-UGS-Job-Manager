package com.bashworks.ugs.jobmanager.ui;

import com.bashworks.ugs.jobmanager.PluginInfo;
import com.bashworks.ugs.jobmanager.machine.SimulatedJobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Standalone Swing runner for G-BASHWARE Job Manager.
 * Allows instant preview, UI development, and testing without launching UGS.
 */
public class JobManagerStandalone {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            File sampleDir = createSampleGcodeFiles();

            SimulatedJobManagerGateway gateway = new SimulatedJobManagerGateway();
            JobManagerPanel panel = new JobManagerPanel(gateway);

            JFrame frame = new JFrame(PluginInfo.TITLE + " — Standalone Preview Harness");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(950, 620));
            frame.setPreferredSize(new Dimension(1050, 680));
            frame.setLayout(new BorderLayout());

            frame.add(panel, BorderLayout.CENTER);

            // Simulation Toolbar at Bottom
            JPanel simToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            simToolbar.setBackground(UITheme.BG_CARD);
            simToolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR));

            JLabel simLabel = new JLabel("⚡ Simulator Controls:");
            simLabel.setFont(UITheme.FONT_SMALL_BOLD);
            simLabel.setForeground(UITheme.ACCENT_CYAN);
            simToolbar.add(simLabel);

            DarkButton btnConn = new DarkButton("Toggle Machine Connect", DarkButton.ButtonStyle.SECONDARY);
            btnConn.setFont(UITheme.FONT_SMALL);
            btnConn.addActionListener(e -> gateway.setSimulatedConnected(!gateway.isConnected()));
            simToolbar.add(btnConn);

            frame.add(simToolbar, BorderLayout.SOUTH);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static File createSampleGcodeFiles() {
        File dir = new File(System.getProperty("user.home"), "Documents/CNC_Samples");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        createSampleFile(new File(dir, "bracket_roughing_120x80.nc"),
                "(G-BASHWARE Sample CNC Job: Bracket Roughing)\n" +
                "G21 ; Metric mm\n" +
                "G90 ; Absolute coordinates\n" +
                "T1 M6 ; 6mm Flat Endmill\n" +
                "S12000 M3 ; Spindle ON\n" +
                "G0 Z5.000\n" +
                "G0 X0.000 Y0.000\n" +
                "G1 Z-3.000 F600\n" +
                "G1 X120.000 Y0.000 F2400\n" +
                "G1 X120.000 Y80.000\n" +
                "G1 X0.000 Y80.000\n" +
                "G1 X0.000 Y0.000\n" +
                "G0 Z10.000\n" +
                "M5 ; Spindle Stop\n" +
                "M30\n");

        createSampleFile(new File(dir, "spoilboard_surfacing_200x200.gcode"),
                "(G-BASHWARE Sample CNC Job: Spoilboard Surfacing)\n" +
                "G21\n" +
                "G90\n" +
                "T2 M6 ; 22mm Surfacing Bit\n" +
                "S15000 M3\n" +
                "G0 Z2.000\n" +
                "G1 Z-0.500 F800\n" +
                "G1 X200.000 Y0.000 F3500\n" +
                "G1 X200.000 Y200.000\n" +
                "G1 X0.000 Y200.000\n" +
                "G1 X0.000 Y0.000\n" +
                "G0 Z15.000\n" +
                "M5\n" +
                "M30\n");

        createSampleFile(new File(dir, "nameplate_laser_engrave.tap"),
                "(G-BASHWARE Sample CNC Job: Laser Nameplate)\n" +
                "G21\n" +
                "G90\n" +
                "S1000 M3 ; Laser ON\n" +
                "G0 X5.000 Y5.000\n" +
                "G1 X95.000 Y5.000 F1800\n" +
                "G1 X95.000 Y45.000\n" +
                "G1 X5.000 Y45.000\n" +
                "G1 X5.000 Y5.000\n" +
                "M5\n" +
                "M30\n");

        return dir;
    }

    private static void createSampleFile(File file, String content) {
        if (!file.exists()) {
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(content);
            } catch (IOException ignored) {
            }
        }
    }
}
