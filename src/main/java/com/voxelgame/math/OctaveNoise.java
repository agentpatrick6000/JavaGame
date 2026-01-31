package com.voxelgame.math;

/**
 * Multi-octave fractal noise (fBm). Stacks multiple Perlin samples
 * with increasing frequency and decreasing amplitude.
 * Thread-safe after construction.
 */
public class OctaveNoise extends Noise {

    private final Perlin[] octaves;
    private final int octaveCount;
    private final double lacunarity;   // frequency multiplier per octave (typically 2.0)
    private final double persistence;  // amplitude multiplier per octave (typically 0.5)

    /**
     * @param seed       Base seed
     * @param octaves    Number of octaves
     * @param lacunarity Frequency multiplier per octave (2.0 is standard)
     * @param persistence Amplitude multiplier per octave (0.5 is standard)
     */
    public OctaveNoise(long seed, int octaves, double lacunarity, double persistence) {
        this.octaveCount = octaves;
        this.lacunarity = lacunarity;
        this.persistence = persistence;
        this.octaves = new Perlin[octaves];
        for (int i = 0; i < octaves; i++) {
            this.octaves[i] = new Perlin(seed + i * 31337L);
        }
    }

    /** Convenience constructor with standard lacunarity=2.0, persistence=0.5 */
    public OctaveNoise(long seed, int octaves) {
        this(seed, octaves, 2.0, 0.5);
    }

    @Override
    public double eval2D(double x, double z) {
        double total = 0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxAmplitude = 0;

        for (int i = 0; i < octaveCount; i++) {
            total += octaves[i].eval2D(x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            frequency *= lacunarity;
            amplitude *= persistence;
        }

        // Normalize to [-1, 1]
        return total / maxAmplitude;
    }

    @Override
    public double eval3D(double x, double y, double z) {
        double total = 0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxAmplitude = 0;

        for (int i = 0; i < octaveCount; i++) {
            total += octaves[i].eval3D(x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            frequency *= lacunarity;
            amplitude *= persistence;
        }

        return total / maxAmplitude;
    }
}
