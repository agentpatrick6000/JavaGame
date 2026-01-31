package com.voxelgame.save;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.WorldConstants;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Serializes and deserializes chunk data (blocks + light map) using gzip compression.
 *
 * Format (uncompressed payload, before deflate):
 *   [4 bytes] magic = 0x43484E4B ("CHNK")
 *   [1 byte ] version = 1
 *   [4 bytes] chunkX (int)
 *   [4 bytes] chunkZ (int)
 *   [4 bytes] block data length (= CHUNK_VOLUME)
 *   [N bytes] block data
 *   [4 bytes] light data length (= CHUNK_VOLUME)
 *   [N bytes] light data
 *
 * The entire payload is then DEFLATE compressed.
 */
public class ChunkCodec {

    private static final int MAGIC = 0x43484E4B; // "CHNK"
    private static final byte VERSION = 1;

    /**
     * Encode a chunk to a compressed byte array.
     * Thread-safe: uses snapshot copies of chunk arrays.
     */
    public static byte[] encode(Chunk chunk) throws IOException {
        byte[] blockData = chunk.snapshotBlocks();
        byte[] lightData = chunk.snapshotLightMap();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
            DataOutputStream out = new DataOutputStream(dos);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeInt(chunk.getPos().x());
            out.writeInt(chunk.getPos().z());
            out.writeInt(blockData.length);
            out.write(blockData);
            out.writeInt(lightData.length);
            out.write(lightData);
            out.flush();
        }
        return baos.toByteArray();
    }

    /**
     * Decode a compressed byte array into a Chunk.
     * Creates a new Chunk and populates its block/light data.
     *
     * @return the loaded Chunk with its position set from the saved data
     */
    public static Chunk decode(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (InflaterInputStream iis = new InflaterInputStream(bais)) {
            DataInputStream in = new DataInputStream(iis);

            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid chunk magic: 0x" + Integer.toHexString(magic));
            }

            byte version = in.readByte();
            if (version != VERSION) {
                throw new IOException("Unsupported chunk version: " + version);
            }

            int cx = in.readInt();
            int cz = in.readInt();

            int blockLen = in.readInt();
            if (blockLen != WorldConstants.CHUNK_VOLUME) {
                throw new IOException("Unexpected block data length: " + blockLen +
                    " (expected " + WorldConstants.CHUNK_VOLUME + ")");
            }
            byte[] blockData = in.readNBytes(blockLen);

            int lightLen = in.readInt();
            if (lightLen != WorldConstants.CHUNK_VOLUME) {
                throw new IOException("Unexpected light data length: " + lightLen +
                    " (expected " + WorldConstants.CHUNK_VOLUME + ")");
            }
            byte[] lightData = in.readNBytes(lightLen);

            Chunk chunk = new Chunk(new ChunkPos(cx, cz));
            chunk.loadBlocks(blockData);
            chunk.loadLightMap(lightData);
            chunk.setModified(false); // freshly loaded = not modified
            chunk.setDirty(true);     // needs mesh build
            return chunk;
        }
    }
}
