package com.voxelgame.bench;

/**
 * Global toggles for benchmark fixes.
 * Set via --bench-fix KEY=true|false
 */
public class BenchFixes {
    
    // Fix A: Use primitive float[]/int[] builders instead of ArrayList<Float>/ArrayList<Integer>
    public static boolean FIX_MESH_PRIMITIVE_BUFFERS = false;
    
    // Fix B: Use packed long keys instead of new ChunkPos() per lookup
    public static boolean FIX_CHUNKPOS_PACKED = false;
    
    // Fix C: Async region IO instead of sync writes on main thread
    public static boolean FIX_ASYNC_REGION_IO = false;
    
    /**
     * Parse --bench-fix argument: KEY=true|false
     */
    public static void parse(String arg) {
        String[] parts = arg.split("=", 2);
        if (parts.length != 2) return;
        
        String key = parts[0].trim();
        boolean value = Boolean.parseBoolean(parts[1].trim());
        
        switch (key) {
            case "FIX_MESH_PRIMITIVE_BUFFERS" -> FIX_MESH_PRIMITIVE_BUFFERS = value;
            case "FIX_CHUNKPOS_PACKED" -> FIX_CHUNKPOS_PACKED = value;
            case "FIX_ASYNC_REGION_IO" -> FIX_ASYNC_REGION_IO = value;
        }
    }
    
    public static String status() {
        return String.format(
            "FIX_MESH_PRIMITIVE_BUFFERS=%s, FIX_CHUNKPOS_PACKED=%s, FIX_ASYNC_REGION_IO=%s",
            FIX_MESH_PRIMITIVE_BUFFERS, FIX_CHUNKPOS_PACKED, FIX_ASYNC_REGION_IO);
    }
}
