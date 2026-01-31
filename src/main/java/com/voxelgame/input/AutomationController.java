package com.voxelgame.input;

import com.voxelgame.platform.Input;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Automation controller for scripted demos and testing.
 * <p>
 * Accepts commands via:
 * <ul>
 *   <li>TCP socket server on localhost:25565 (for live remote control)</li>
 *   <li>Script file (--script path) with sequential commands</li>
 * </ul>
 * <p>
 * Protocol (newline-separated text commands):
 * <pre>
 *   key:W:press     - press and release key (single frame)
 *   key:W:down      - hold key down
 *   key:W:up        - release key
 *   key:SPACE:press - press spacebar
 *   mouse:move:dx:dy - inject mouse movement
 *   mouse:click:left - inject left mouse click
 *   mouse:click:right - inject right mouse click
 *   sleep:500       - wait 500ms (script mode only)
 *   quit            - request game shutdown
 * </pre>
 * <p>
 * Key names: W, A, S, D, F, SPACE, SHIFT, CTRL, ESCAPE, F3, F4, 1-9,
 *            UP, DOWN, LEFT, RIGHT, TAB, ENTER
 */
public class AutomationController {

    private static final Logger LOG = Logger.getLogger(AutomationController.class.getName());
    private static final int DEFAULT_PORT = 25565;
    private static final int PRESS_DURATION_MS = 80; // how long a "press" holds the key

    // Key state managed by automation
    private final ConcurrentHashMap<Integer, Boolean> keyState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> keyPressedState = new ConcurrentHashMap<>();

    // Mouse state
    private volatile double mouseDX = 0;
    private volatile double mouseDY = 0;
    private volatile boolean leftMouseClick = false;
    private volatile boolean rightMouseClick = false;

    // Lifecycle
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean quitRequested = new AtomicBoolean(false);
    private Thread socketThread;
    private Thread scriptThread;
    private ServerSocket serverSocket;

    // Key name -> GLFW key code mapping
    private static final Map<String, Integer> KEY_MAP = Map.ofEntries(
        Map.entry("W",         GLFW_KEY_W),
        Map.entry("A",         GLFW_KEY_A),
        Map.entry("S",         GLFW_KEY_S),
        Map.entry("D",         GLFW_KEY_D),
        Map.entry("F",         GLFW_KEY_F),
        Map.entry("Q",         GLFW_KEY_Q),
        Map.entry("E",         GLFW_KEY_E),
        Map.entry("R",         GLFW_KEY_R),
        Map.entry("SPACE",     GLFW_KEY_SPACE),
        Map.entry("SHIFT",     GLFW_KEY_LEFT_SHIFT),
        Map.entry("CTRL",      GLFW_KEY_LEFT_CONTROL),
        Map.entry("ESCAPE",    GLFW_KEY_ESCAPE),
        Map.entry("ESC",       GLFW_KEY_ESCAPE),
        Map.entry("TAB",       GLFW_KEY_TAB),
        Map.entry("ENTER",     GLFW_KEY_ENTER),
        Map.entry("F1",        GLFW_KEY_F1),
        Map.entry("F2",        GLFW_KEY_F2),
        Map.entry("F3",        GLFW_KEY_F3),
        Map.entry("F4",        GLFW_KEY_F4),
        Map.entry("F5",        GLFW_KEY_F5),
        Map.entry("UP",        GLFW_KEY_UP),
        Map.entry("DOWN",      GLFW_KEY_DOWN),
        Map.entry("LEFT",      GLFW_KEY_LEFT),
        Map.entry("RIGHT",     GLFW_KEY_RIGHT),
        Map.entry("1",         GLFW_KEY_1),
        Map.entry("2",         GLFW_KEY_2),
        Map.entry("3",         GLFW_KEY_3),
        Map.entry("4",         GLFW_KEY_4),
        Map.entry("5",         GLFW_KEY_5),
        Map.entry("6",         GLFW_KEY_6),
        Map.entry("7",         GLFW_KEY_7),
        Map.entry("8",         GLFW_KEY_8),
        Map.entry("9",         GLFW_KEY_9)
    );

    /**
     * Start the automation controller.
     * Launches socket server and optionally runs a script file.
     *
     * @param scriptPath path to script file, or null for socket-only mode
     */
    public void start(String scriptPath) {
        LOG.info("[Automation] Starting automation controller");

        // Always start socket server for live control
        startSocketServer();

        // Optionally run a script file
        if (scriptPath != null) {
            startScriptRunner(scriptPath);
        }

        // Register with Input system
        Input.setAutomationController(this);

        LOG.info("[Automation] Controller ready (socket=localhost:" + DEFAULT_PORT + 
                 (scriptPath != null ? ", script=" + scriptPath : "") + ")");
    }

    /**
     * Stop the automation controller and clean up threads.
     */
    public void stop() {
        running.set(false);
        Input.setAutomationController(null);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }

        if (socketThread != null) socketThread.interrupt();
        if (scriptThread != null) scriptThread.interrupt();

        LOG.info("[Automation] Controller stopped");
    }

    // ---- State query methods (called by Input.java) ----

    /** Check if automation has a key held down. */
    public boolean isKeyDown(int glfwKey) {
        return keyState.getOrDefault(glfwKey, false);
    }

    /** Check if automation has a single-frame key press. */
    public boolean isKeyPressed(int glfwKey) {
        return keyPressedState.getOrDefault(glfwKey, false);
    }

    /** Get and consume accumulated mouse DX. */
    public double consumeMouseDX() {
        double dx = mouseDX;
        mouseDX = 0;
        return dx;
    }

    /** Get and consume accumulated mouse DY. */
    public double consumeMouseDY() {
        double dy = mouseDY;
        mouseDY = 0;
        return dy;
    }

    /** Get and consume left mouse click state. */
    public boolean consumeLeftMouseClick() {
        boolean clicked = leftMouseClick;
        leftMouseClick = false;
        return clicked;
    }

    /** Get and consume right mouse click state. */
    public boolean consumeRightMouseClick() {
        boolean clicked = rightMouseClick;
        rightMouseClick = false;
        return clicked;
    }

    /** Whether a quit command was received. */
    public boolean isQuitRequested() {
        return quitRequested.get();
    }

    /** Clear single-frame pressed states (called by Input.endFrame). */
    public void endFrame() {
        keyPressedState.clear();
    }

    // ---- Command processing ----

    /**
     * Process a single automation command.
     * Thread-safe — can be called from socket or script threads.
     */
    public void processCommand(String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        cmd = cmd.trim();

        // Skip comments
        if (cmd.startsWith("#") || cmd.startsWith("//")) return;

        LOG.fine("[Automation] Command: " + cmd);

        String[] parts = cmd.split(":");
        String type = parts[0].toLowerCase();

        switch (type) {
            case "key" -> processKeyCommand(parts);
            case "mouse" -> processMouseCommand(parts);
            case "quit" -> {
                LOG.info("[Automation] Quit requested");
                quitRequested.set(true);
            }
            default -> LOG.warning("[Automation] Unknown command type: " + type);
        }
    }

    private void processKeyCommand(String[] parts) {
        if (parts.length < 3) {
            LOG.warning("[Automation] Invalid key command — need key:<name>:<action>");
            return;
        }

        String keyName = parts[1].toUpperCase();
        String action = parts[2].toLowerCase();

        Integer glfwKey = KEY_MAP.get(keyName);
        if (glfwKey == null) {
            // Try single character as direct GLFW key code
            if (keyName.length() == 1) {
                glfwKey = (int) keyName.charAt(0);
            } else {
                LOG.warning("[Automation] Unknown key: " + keyName);
                return;
            }
        }

        switch (action) {
            case "down" -> {
                keyState.put(glfwKey, true);
            }
            case "up" -> {
                keyState.put(glfwKey, false);
            }
            case "press" -> {
                // Set both held and pressed states
                keyState.put(glfwKey, true);
                keyPressedState.put(glfwKey, true);
                // Schedule release after a short delay
                final int key = glfwKey;
                new Thread(() -> {
                    try {
                        Thread.sleep(PRESS_DURATION_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    keyState.put(key, false);
                }, "automation-press-" + keyName).start();
            }
            default -> LOG.warning("[Automation] Unknown key action: " + action);
        }
    }

    private void processMouseCommand(String[] parts) {
        if (parts.length < 3) {
            LOG.warning("[Automation] Invalid mouse command");
            return;
        }

        String action = parts[1].toLowerCase();
        switch (action) {
            case "move" -> {
                if (parts.length >= 4) {
                    try {
                        double dx = Double.parseDouble(parts[2]);
                        double dy = Double.parseDouble(parts[3]);
                        mouseDX += dx;
                        mouseDY += dy;
                    } catch (NumberFormatException e) {
                        LOG.warning("[Automation] Invalid mouse move values");
                    }
                }
            }
            case "click" -> {
                String button = parts[2].toLowerCase();
                if ("left".equals(button)) {
                    leftMouseClick = true;
                } else if ("right".equals(button)) {
                    rightMouseClick = true;
                }
            }
            default -> LOG.warning("[Automation] Unknown mouse action: " + action);
        }
    }

    // ---- Socket server ----

    private void startSocketServer() {
        socketThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(DEFAULT_PORT);
                LOG.info("[Automation] Socket server listening on localhost:" + DEFAULT_PORT);

                while (running.get()) {
                    try {
                        Socket client = serverSocket.accept();
                        LOG.info("[Automation] Client connected from " + client.getRemoteSocketAddress());

                        // Handle client in a new thread
                        new Thread(() -> handleClient(client), "automation-client").start();
                    } catch (IOException e) {
                        if (running.get()) {
                            LOG.warning("[Automation] Socket accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                LOG.severe("[Automation] Failed to start socket server on port " + DEFAULT_PORT + ": " + e.getMessage());
            }
        }, "automation-socket-server");
        socketThread.setDaemon(true);
        socketThread.start();
    }

    private void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {

            writer.println("VOXELGAME AUTOMATION v1.0");
            writer.println("Ready. Send commands (key:W:press, quit, etc.)");

            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Handle sleep in socket mode too
                if (line.toLowerCase().startsWith("sleep:")) {
                    try {
                        int ms = Integer.parseInt(line.substring(6));
                        Thread.sleep(ms);
                        writer.println("OK slept " + ms + "ms");
                    } catch (Exception e) {
                        writer.println("ERR " + e.getMessage());
                    }
                    continue;
                }

                processCommand(line);
                writer.println("OK " + line);
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.info("[Automation] Client disconnected: " + e.getMessage());
            }
        }
    }

    // ---- Script runner ----

    private void startScriptRunner(String scriptPath) {
        scriptThread = new Thread(() -> {
            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                LOG.severe("[Automation] Script file not found: " + scriptPath);
                return;
            }

            LOG.info("[Automation] Running script: " + scriptPath);

            // Wait for game to initialize
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(scriptFile))) {
                String line;
                int lineNum = 0;
                while (running.get() && (line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

                    LOG.info("[Automation] Script line " + lineNum + ": " + line);

                    if (line.toLowerCase().startsWith("sleep:")) {
                        try {
                            int ms = Integer.parseInt(line.substring(6));
                            Thread.sleep(ms);
                        } catch (NumberFormatException e) {
                            LOG.warning("[Automation] Invalid sleep value at line " + lineNum);
                        }
                    } else {
                        processCommand(line);
                        // Small delay between commands to ensure frame processing
                        Thread.sleep(20);
                    }
                }
            } catch (IOException e) {
                LOG.severe("[Automation] Script read error: " + e.getMessage());
            } catch (InterruptedException e) {
                LOG.info("[Automation] Script interrupted");
            }

            LOG.info("[Automation] Script completed");
        }, "automation-script-runner");
        scriptThread.setDaemon(true);
        scriptThread.start();
    }
}
