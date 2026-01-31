package com.voxelgame.save;

/**
 * Save format versioning. Tracks the current format version and provides
 * migration logic for loading saves from older versions.
 */
public class Versioning {

    /** Current save format version. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Check if a save version is compatible with the current version.
     */
    public static boolean isCompatible(int saveVersion) {
        return saveVersion >= 1 && saveVersion <= CURRENT_VERSION;
    }
}
