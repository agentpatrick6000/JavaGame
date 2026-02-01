package com.voxelgame.agent;

import com.voxelgame.render.Camera;
import com.voxelgame.sim.Player;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.WorldAccess;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * WebSocket server for AI agent connections.
 * <p>
 * Runs on port 25566 (separate from automation on 25565).
 * Handles agent connect/disconnect, sends handshake + state ticks,
 * receives and routes actions to the ActionQueue.
 * <p>
 * Thread safety: WebSocket callbacks run on netty/java-websocket threads.
 * State broadcasts are called from the game loop thread.
 * ActionQueue is the bridge (lock-free concurrent queue).
 */
public class AgentServer extends WebSocketServer {

    private static final Logger LOG = Logger.getLogger(AgentServer.class.getName());
    public static final int DEFAULT_PORT = 25566;

    /** Throttle: minimum ms between state broadcasts. ~20 Hz = 50ms. */
    private static final long MIN_BROADCAST_INTERVAL_MS = 50;

    private final ActionQueue actionQueue;
    private final Set<WebSocket> agents = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicLong tickCounter = new AtomicLong(0);
    private long lastBroadcastTime = 0;

    // SimScreen generator (created once, reused)
    private final SimScreen simScreen = new SimScreen();

    public AgentServer(int port, ActionQueue actionQueue) {
        super(new InetSocketAddress(port));
        this.actionQueue = actionQueue;
        setReuseAddr(true);
        setDaemon(true);
    }

    public AgentServer(ActionQueue actionQueue) {
        this(DEFAULT_PORT, actionQueue);
    }

    // ---- WebSocket callbacks ----

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String id = connId(conn);
        agents.add(conn);
        LOG.info("[AgentServer] Agent connected: " + id + " (total: " + agents.size() + ")");

        // Send handshake
        try {
            conn.send(Messages.buildHello());
        } catch (Exception e) {
            LOG.warning("[AgentServer] Failed to send hello to " + id + ": " + e.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        agents.remove(conn);
        LOG.info("[AgentServer] Agent disconnected: " + connId(conn) +
                 " (code=" + code + ", reason=" + reason + ", total: " + agents.size() + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String sourceId = connId(conn);
        ActionQueue.AgentAction action = Messages.parseAction(message, sourceId);
        if (action != null) {
            actionQueue.enqueue(action);
        } else {
            LOG.warning("[AgentServer] Unrecognized message from " + sourceId + ": " +
                       (message.length() > 100 ? message.substring(0, 100) + "..." : message));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String id = conn != null ? connId(conn) : "server";
        LOG.warning("[AgentServer] Error (" + id + "): " + ex.getMessage());
    }

    @Override
    public void onStart() {
        LOG.info("[AgentServer] WebSocket server started on port " + getPort());
    }

    // ---- State broadcast (called from game loop) ----

    /**
     * Broadcast the current game state to all connected agents.
     * Throttled to ~20 Hz to avoid flooding.
     * <p>
     * Called from the game loop thread every tick.
     */
    public void broadcastState(Player player, WorldAccess world) {
        if (agents.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastBroadcastTime < MIN_BROADCAST_INTERVAL_MS) return;
        lastBroadcastTime = now;

        long tick = tickCounter.incrementAndGet();
        Camera camera = player.getCamera();

        // Build state message
        StringBuilder sb = Messages.buildStateStart(
            tick,
            camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
            camera.getYaw(), camera.getPitch()
        );

        // Crosshair raycast
        appendCrosshairRaycast(sb, player, world);

        // UI state
        // TODO: health system doesn't exist yet — stub at 20.0 (full health)
        Messages.appendUiState(sb, 20.0f, player.getSelectedSlot(),
                               player.isFlyMode(), player.isOnGround());

        // Hotbar contents
        String[] hotbarContents = new String[Player.HOTBAR_SIZE];
        for (int i = 0; i < Player.HOTBAR_SIZE; i++) {
            int blockId = player.getHotbarBlock(i);
            hotbarContents[i] = blockId == 0 ? null : Blocks.get(blockId).name();
        }
        Messages.appendHotbarContents(sb, hotbarContents);

        // Last action result (consumed once, then null until next action)
        ActionQueue.ActionResult lastResult = actionQueue.consumeLastResult();
        Messages.appendLastActionResult(sb, lastResult);

        // Sound events (stub)
        Messages.appendSoundEvents(sb);

        // SimScreen
        sb.append(",\"simscreen\":");
        simScreen.generate(camera, world, sb);

        String stateJson = Messages.finishState(sb);

        // Broadcast to all connected agents
        for (WebSocket agent : agents) {
            try {
                agent.send(stateJson);
            } catch (Exception e) {
                LOG.fine("[AgentServer] Failed to send state to " + connId(agent));
            }
        }
    }

    /**
     * Append crosshair raycast result to the state message.
     */
    private void appendCrosshairRaycast(StringBuilder sb, Player player, WorldAccess world) {
        Camera camera = player.getCamera();
        var hit = com.voxelgame.world.Raycast.cast(
            world, camera.getPosition(), camera.getFront(), 8.0f
        );

        if (hit != null) {
            int blockId = world.getBlock(hit.x(), hit.y(), hit.z());
            var block = com.voxelgame.world.Blocks.get(blockId);

            Messages.CellClass cls = SimScreen.classifyBlock(blockId, block);
            float dist = (float) Math.sqrt(
                Math.pow(hit.x() + 0.5 - camera.getPosition().x, 2) +
                Math.pow(hit.y() + 0.5 - camera.getPosition().y, 2) +
                Math.pow(hit.z() + 0.5 - camera.getPosition().z, 2)
            );

            Messages.appendRaycast(sb, "block", cls, block.name(),
                Messages.depthBucket(dist),
                Messages.normalName(hit.nx(), hit.ny(), hit.nz()));
        } else {
            Messages.appendRaycast(sb, "miss", Messages.CellClass.SKY, "none", 5, "NONE");
        }
    }

    // ---- Utility ----

    /** Get a short identifier for a connection. */
    private static String connId(WebSocket conn) {
        if (conn == null || conn.getRemoteSocketAddress() == null) return "unknown";
        return conn.getRemoteSocketAddress().toString();
    }

    /** Get the action queue for this server. */
    public ActionQueue getActionQueue() {
        return actionQueue;
    }

    /** Get the number of connected agents. */
    public int getAgentCount() {
        return agents.size();
    }

    /** Graceful shutdown. */
    public void shutdown() {
        LOG.info("[AgentServer] Shutting down...");
        try {
            stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
