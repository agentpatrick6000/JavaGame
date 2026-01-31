package com.voxelgame.agent;

/**
 * Message schemas for the agent protocol.
 * <p>
 * All messages are JSON. This class provides static builders
 * to construct JSON strings without a dependency on a JSON library.
 * <p>
 * Protocol message types:
 * <ul>
 *   <li><b>Server → Agent:</b> hello, state</li>
 *   <li><b>Agent → Server:</b> action_look, action_move, action_jump,
 *       action_crouch, action_sprint, action_use, action_attack,
 *       action_hotbar_select</li>
 * </ul>
 */
public final class Messages {

    private Messages() {}

    // ---- JSON Utility helpers ----

    /** Escape a string for JSON. */
    static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Format float, strip trailing zeros. */
    static String jsonNum(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String jsonNum(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ---- SimScreen cell classification ----

    /**
     * Block class vocabulary for SimScreen cells.
     * Kept as short strings for bandwidth efficiency.
     */
    public enum CellClass {
        SKY, AIR, SOLID, WATER, LAVA, FOLIAGE, ITEM, ENTITY, UI, UNKNOWN;

        private final String json;

        CellClass() {
            this.json = "\"" + name() + "\"";
        }

        public String toJson() {
            return json;
        }
    }

    /**
     * Depth bucket classification.
     * 6 buckets: 0-2m, 2-5m, 5-10m, 10-20m, 20-50m, 50+m.
     */
    public static int depthBucket(float distance) {
        if (distance < 2.0f) return 0;
        if (distance < 5.0f) return 1;
        if (distance < 10.0f) return 2;
        if (distance < 20.0f) return 3;
        if (distance < 50.0f) return 4;
        return 5;
    }

    /**
     * Light bucket classification.
     * 4 buckets: 0-25%, 25-50%, 50-75%, 75-100%.
     * Input is 0-15 light level.
     */
    public static int lightBucket(int lightLevel) {
        // lightLevel is 0-15, map to 0-3
        if (lightLevel <= 3) return 0;   // 0-25%
        if (lightLevel <= 7) return 1;   // 25-50%
        if (lightLevel <= 11) return 2;  // 50-75%
        return 3;                         // 75-100%
    }

    /**
     * Normal face name from normal vector components.
     */
    public static String normalName(int nx, int ny, int nz) {
        if (ny > 0) return "TOP";
        if (ny < 0) return "BOTTOM";
        if (nz < 0) return "NORTH";
        if (nz > 0) return "SOUTH";
        if (nx > 0) return "EAST";
        if (nx < 0) return "WEST";
        return "NONE";
    }

    // ---- Hello message (handshake) ----

    /**
     * Build the hello message sent on agent connect.
     */
    public static String buildHello() {
        return """
        {"type":"hello","version":"1.0","capabilities":{"simscreen_width":64,"simscreen_height":36,"tick_rate_hz":20,"max_reach":8.0,"actions":["action_look","action_move","action_jump","action_crouch","action_sprint","action_use","action_attack","action_hotbar_select"]},"schema":{"state":{"tick":"int","pose":{"x":"float","y":"float","z":"float","yaw":"float","pitch":"float"},"simscreen":"array[64][36] of {cls:string,depth:int(0-5),light:int(0-3)}","raycast":{"hit_type":"block|entity|miss","hit_class":"CellClass","hit_id":"string","hit_dist":"int(0-5)","hit_normal":"TOP|BOTTOM|NORTH|SOUTH|EAST|WEST|NONE"},"ui_state":{"health":"float","hotbar_selected":"int(0-8)","fly_mode":"bool","on_ground":"bool"},"sound_events":"array (stub, currently empty)"},"actions":{"action_look":{"yaw":"float","pitch":"float"},"action_move":{"forward":"float(-1..1)","strafe":"float(-1..1)","duration":"int(ms)"},"action_jump":{},"action_crouch":{"toggle":"bool"},"action_sprint":{"toggle":"bool"},"action_use":{},"action_attack":{},"action_hotbar_select":{"slot":"int(0-8)"}}},"operator_guide":"You are a player in a voxel world. POV-bound: only what you see/hear exists. No god-mode, no map, no coordinates. CAMERA: Yaw=horizontal (-180 to 180°, 0=north), Pitch=vertical (-90=down,0=horizon,90=up). action_look uses DELTAS, not absolute. To look at (dx,dy,dz) relative to you: dyaw=atan2(dx,dz)*57.3, dpitch=atan2(dy,sqrt(dx²+dz²))*57.3. MOVEMENT: Walk=1.0, sprint=1.5×, fly=creative flight. Jump height~1.2 blocks. Gravity when on_ground=false and fly_mode=false. BLOCKS: action_use places from hotbar[hotbar_selected]. Raycast shows surface+face you're pointing at. New block appears on adjacent face: hit TOP → block goes above, hit NORTH → block goes north side. Break with action_attack. HOTBAR: 9 slots (0-8), change with action_hotbar_select. SURVIVAL (stubs): Health=20 (no damage yet), no hunger, no enemies, no drowning, fall damage disabled. MEMORY: STM=last 30 ticks, MTM=episode summaries, LTM=learned facts. Tag provenance (seen|heard|inferred) + confidence.","memory_contract":{"stm":"last 30 ticks of raw state — ephemeral, overwritten each cycle","mtm":"summarized episodes (e.g. 'walked north 50 blocks, found river') — persist across sessions","ltm":"learned facts and skills (e.g. 'stone is at y<60') — permanent, provenance-tagged","provenance":"each memory entry should note: source (observation|inference|told), confidence (0-1), tick_range"}}""";
    }

    // ---- State message (per-tick) ----

    /**
     * Build state message header (everything except simscreen, which is appended separately).
     */
    public static StringBuilder buildStateStart(long tick, float x, float y, float z, float yaw, float pitch) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"type\":\"state\",\"tick\":").append(tick);
        sb.append(",\"pose\":{\"x\":").append(jsonNum(x));
        sb.append(",\"y\":").append(jsonNum(y));
        sb.append(",\"z\":").append(jsonNum(z));
        sb.append(",\"yaw\":").append(jsonNum(yaw));
        sb.append(",\"pitch\":").append(jsonNum(pitch)).append('}');
        return sb;
    }

    /**
     * Append raycast result to state message.
     */
    public static void appendRaycast(StringBuilder sb, String hitType, CellClass hitClass,
                                      String hitId, int hitDist, String hitNormal) {
        sb.append(",\"raycast\":{\"hit_type\":").append(jsonStr(hitType));
        sb.append(",\"hit_class\":").append(hitClass.toJson());
        sb.append(",\"hit_id\":").append(jsonStr(hitId));
        sb.append(",\"hit_dist\":").append(hitDist);
        sb.append(",\"hit_normal\":").append(jsonStr(hitNormal)).append('}');
    }

    /**
     * Append UI state to state message.
     */
    public static void appendUiState(StringBuilder sb, float health, int hotbarSelected,
                                      boolean flyMode, boolean onGround) {
        sb.append(",\"ui_state\":{\"health\":").append(jsonNum(health));
        sb.append(",\"hotbar_selected\":").append(hotbarSelected);
        sb.append(",\"fly_mode\":").append(flyMode);
        sb.append(",\"on_ground\":").append(onGround).append('}');
    }

    /**
     * Append sound events stub.
     */
    public static void appendSoundEvents(StringBuilder sb) {
        // TODO: Implement when sound system exists
        sb.append(",\"sound_events\":[]");
    }

    /**
     * Close the state message JSON.
     */
    public static String finishState(StringBuilder sb) {
        sb.append('}');
        return sb.toString();
    }

    // ---- Action parsing (Agent → Server) ----

    /**
     * Parse an incoming JSON action message. Minimal hand-parser to avoid
     * adding a JSON library dependency.
     * <p>
     * Returns null if the message is not a valid action.
     */
    public static ActionQueue.AgentAction parseAction(String json, String sourceId) {
        if (json == null || json.isBlank()) return null;
        json = json.trim();

        String type = extractString(json, "type");
        if (type == null) return null;

        return switch (type) {
            case "action_look" -> ActionQueue.AgentAction.look(
                extractFloat(json, "yaw", 0),
                extractFloat(json, "pitch", 0),
                sourceId
            );
            case "action_move" -> ActionQueue.AgentAction.move(
                extractFloat(json, "forward", 0),
                extractFloat(json, "strafe", 0),
                extractLong(json, "duration", 100),
                sourceId
            );
            case "action_jump" -> ActionQueue.AgentAction.jump(sourceId);
            case "action_crouch" -> ActionQueue.AgentAction.crouch(
                extractBool(json, "toggle", true), sourceId
            );
            case "action_sprint" -> ActionQueue.AgentAction.sprint(
                extractBool(json, "toggle", true), sourceId
            );
            case "action_use" -> ActionQueue.AgentAction.use(sourceId);
            case "action_attack" -> ActionQueue.AgentAction.attack(sourceId);
            case "action_hotbar_select" -> ActionQueue.AgentAction.hotbarSelect(
                extractInt(json, "slot", 0), sourceId
            );
            default -> null;
        };
    }

    // ---- Minimal JSON field extractors ----

    static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return null;
        idx++;
        // Skip whitespace
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++; // skip opening quote
        int end = json.indexOf('"', idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    static float extractFloat(String json, String key, float def) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return def;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return def;
        idx++;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-' || json.charAt(end) == 'E' || json.charAt(end) == 'e')) end++;
        if (end == idx) return def;
        try {
            return Float.parseFloat(json.substring(idx, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static long extractLong(String json, String key, long def) {
        return (long) extractFloat(json, key, def);
    }

    static int extractInt(String json, String key, int def) {
        return (int) extractFloat(json, key, def);
    }

    static boolean extractBool(String json, String key, boolean def) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return def;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return def;
        idx++;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (json.startsWith("true", idx)) return true;
        if (json.startsWith("false", idx)) return false;
        return def;
    }
}
