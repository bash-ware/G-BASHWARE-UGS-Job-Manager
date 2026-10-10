package com.bashworks.ugs.jobmanager.service;

import com.bashworks.ugs.jobmanager.machine.JobManagerGateway;
import com.bashworks.ugs.jobmanager.model.JobAnalysis;
import com.bashworks.ugs.jobmanager.model.JobEntry;
import com.bashworks.ugs.jobmanager.model.JobOrigin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * Embedded LAN receiver service providing zero-config UDP peer discovery and
 * an HTTP REST server for receiving G-code jobs directly from GcodeMergerSplitter.
 */
public final class LanReceiverService {

    public static final int DEFAULT_HTTP_PORT = 9876;
    public static final int DEFAULT_UDP_PORT = 9875;
    private static final String PREF_AUTO_ACTIVATE = "lan_auto_activate";

    public interface LanListener {
        void onClientLinkChanged(boolean linked, String clientIp);
        void onJobReceived(JobEntry job, String clientIp, boolean activated);
    }

    private final JobManagerGateway gateway;
    private final GcodeAnalyzer analyzer;
    private final Preferences preferences;
    private final File incomingDirectory;
    private final List<LanListener> listeners = new CopyOnWriteArrayList<>();

    private HttpServer httpServer;
    private DatagramSocket udpSocket;
    private Thread udpListenerThread;
    private final ScheduledExecutorService heartbeatScheduler;

    private volatile boolean running = false;
    private volatile boolean clientLinked = false;
    private volatile String connectedClientIp = null;
    private volatile long lastClientHeartbeatTime = 0;
    private boolean autoActivate = true;

    public LanReceiverService(JobManagerGateway gateway, GcodeAnalyzer analyzer, Preferences preferences) {
        this.gateway = gateway;
        this.analyzer = analyzer != null ? analyzer : new GcodeAnalyzer();
        this.preferences = preferences != null ? preferences : initPreferences();
        this.autoActivate = this.preferences.getBoolean(PREF_AUTO_ACTIVATE, true);

        File userHome = new File(System.getProperty("user.home"));
        this.incomingDirectory = new File(userHome, ".ugs-job-manager/incoming");
        if (!incomingDirectory.exists()) {
            incomingDirectory.mkdirs();
        }

        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ugs-lan-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    private static Preferences initPreferences() {
        try {
            Class<?> nbPrefClass = Class.forName("org.openide.util.NbPreferences");
            return (Preferences) nbPrefClass.getMethod("forModule", Class.class).invoke(null, LanReceiverService.class);
        } catch (Throwable fallback) {
            return Preferences.userNodeForPackage(LanReceiverService.class);
        }
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        startHttpServer();
        startUdpDiscovery();

        heartbeatScheduler.scheduleAtFixedRate(this::checkClientTimeout, 2, 2, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            } catch (Exception ignored) {}
            httpServer = null;
        }
        if (udpSocket != null) {
            try {
                udpSocket.close();
            } catch (Exception ignored) {}
            udpSocket = null;
        }
        if (udpListenerThread != null) {
            udpListenerThread.interrupt();
            udpListenerThread = null;
        }
        heartbeatScheduler.shutdownNow();
        setClientLinked(false, null);
    }

    private void startHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(DEFAULT_HTTP_PORT), 0);
            httpServer.createContext("/api/status", new StatusHandler());
            httpServer.createContext("/api/jobs/upload", new UploadHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ugs-http-worker");
                t.setDaemon(true);
                return t;
            }));
            httpServer.start();
        } catch (IOException e) {
            System.err.println("Failed to start UGS Job Manager HTTP server on port " + DEFAULT_HTTP_PORT + ": " + e.getMessage());
        }
    }

    private void startUdpDiscovery() {
        try {
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(DEFAULT_UDP_PORT));

            udpListenerThread = new Thread(() -> {
                byte[] buffer = new byte[2048];
                while (running && !udpSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);

                        String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                        if (message.contains("DISCOVERY_PING") || message.contains("GcodeMergerSplitter")) {
                            recordClientPing(packet.getAddress().getHostAddress());

                            // Reply directly to sender with PONG
                            String activeJob = gateway.getActiveGcodeFile() != null ? gateway.getActiveGcodeFile().getName() : "";
                            String jsonResponse = String.format(Locale.US,
                                    "{\"event\":\"DISCOVERY_PONG\",\"server\":\"G-BASHWARE-Job-Manager\",\"httpPort\":%d,\"machineState\":\"%s\",\"activeJob\":\"%s\"}",
                                    DEFAULT_HTTP_PORT, gateway.getControllerStateName(), escapeJson(activeJob));

                            byte[] respBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket responsePacket = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                            udpSocket.send(responsePacket);
                        }
                    } catch (Exception ex) {
                        if (!running) break;
                    }
                }
            }, "ugs-udp-discovery");
            udpListenerThread.setDaemon(true);
            udpListenerThread.start();
        } catch (Exception ex) {
            System.err.println("Failed to bind UDP discovery port " + DEFAULT_UDP_PORT + ": " + ex.getMessage());
        }
    }

    private void recordClientPing(String clientIp) {
        lastClientHeartbeatTime = System.currentTimeMillis();
        setClientLinked(true, clientIp);
    }

    private void checkClientTimeout() {
        if (clientLinked && (System.currentTimeMillis() - lastClientHeartbeatTime > 6000)) {
            setClientLinked(false, null);
        }
    }

    private void setClientLinked(boolean linked, String clientIp) {
        if (this.clientLinked != linked || (linked && !clientIp.equals(this.connectedClientIp))) {
            this.clientLinked = linked;
            this.connectedClientIp = linked ? clientIp : null;
            SwingUtilities.invokeLater(() -> {
                for (LanListener l : listeners) {
                    l.onClientLinkChanged(clientLinked, connectedClientIp);
                }
            });
        }
    }

    public boolean isClientLinked() {
        return clientLinked;
    }

    public String getConnectedClientIp() {
        return connectedClientIp;
    }

    public boolean isAutoActivate() {
        return autoActivate;
    }

    public void setAutoActivate(boolean autoActivate) {
        this.autoActivate = autoActivate;
        preferences.putBoolean(PREF_AUTO_ACTIVATE, autoActivate);
    }

    public void addListener(LanListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(LanListener l) {
        listeners.remove(l);
    }

    public File getIncomingDirectory() {
        return incomingDirectory;
    }

    // === HTTP Handlers ===

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            recordClientPing(exchange.getRemoteAddress().getAddress().getHostAddress());

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            File active = gateway.getActiveGcodeFile();
            String json = String.format(Locale.US,
                    "{\"app\":\"G-BASHWARE Job Manager\",\"version\":\"1.1.0\",\"connected\":true," +
                    "\"machineConnected\":%b,\"machineState\":\"%s\",\"isIdle\":%b,\"isSending\":%b," +
                    "\"activeJob\":\"%s\"}",
                    gateway.isConnected(), gateway.getControllerStateName(), gateway.isIdle(), gateway.isSendingFile(),
                    active != null ? escapeJson(active.getName()) : "");

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            recordClientPing(clientIp);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Filename, X-Activate");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String filename = exchange.getRequestHeaders().getFirst("X-Filename");
            if (filename == null || filename.trim().isEmpty()) {
                filename = queryParams.get("filename");
            }
            if (filename == null || filename.trim().isEmpty()) {
                filename = "job_" + System.currentTimeMillis() + ".nc";
            }
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!filename.toLowerCase().endsWith(".nc") && !filename.toLowerCase().endsWith(".gcode")
                    && !filename.toLowerCase().endsWith(".tap") && !filename.toLowerCase().endsWith(".ngc")) {
                filename += ".nc";
            }

            boolean shouldActivate = autoActivate;
            String activateHeader = exchange.getRequestHeaders().getFirst("X-Activate");
            if (activateHeader != null) {
                shouldActivate = "true".equalsIgnoreCase(activateHeader.trim()) || "1".equals(activateHeader.trim());
            } else if (queryParams.containsKey("activate")) {
                shouldActivate = "true".equalsIgnoreCase(queryParams.get("activate")) || "1".equals(queryParams.get("activate"));
            }

            File targetFile = new File(incomingDirectory, filename);
            try (InputStream in = exchange.getRequestBody();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0) {
                sendError(exchange, 400, "Received empty file");
                return;
            }

            JobAnalysis analysis = analyzer.analyze(targetFile);
            JobEntry entry = new JobEntry(
                    targetFile.getAbsolutePath(),
                    targetFile.getName(),
                    targetFile,
                    "LAN (" + clientIp + ")",
                    JobOrigin.LOCAL_DISK,
                    targetFile.length(),
                    targetFile.lastModified()
            );
            entry.setAnalysis(analysis);

            boolean activated = false;
            if (shouldActivate) {
                try {
                    gateway.loadJobIntoUgs(targetFile);
                    entry.setActiveInUgs(true);
                    activated = true;
                } catch (Exception ex) {
                    System.err.println("Could not auto-activate job in UGS: " + ex.getMessage());
                }
            }

            final boolean isActivated = activated;
            SwingUtilities.invokeLater(() -> {
                for (LanListener l : listeners) {
                    l.onJobReceived(entry, clientIp, isActivated);
                }
            });

            String json = String.format(Locale.US,
                    "{\"success\":true,\"filename\":\"%s\",\"size\":%d,\"lines\":%d,\"dimensions\":\"%s\",\"activated\":%b}",
                    escapeJson(filename), targetFile.length(), analysis.getTotalLines(),
                    escapeJson(analysis.getBounds().toDimensionsString(analysis.getUnits())), activated);

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String json = String.format("{\"success\":false,\"error\":\"%s\"}", escapeJson(message));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
