package com.voxelgame.world.mesh;

/**
 * Growable primitive float array builder. No boxing overhead.
 */
public class FloatArrayBuilder {
    private float[] data;
    private int size;
    
    public FloatArrayBuilder() {
        this(1024);
    }
    
    public FloatArrayBuilder(int initialCapacity) {
        this.data = new float[initialCapacity];
        this.size = 0;
    }
    
    public void add(float value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }
    
    public void add(float v0, float v1, float v2) {
        ensureCapacity(size + 3);
        data[size++] = v0;
        data[size++] = v1;
        data[size++] = v2;
    }
    
    public void add(float v0, float v1, float v2, float v3) {
        ensureCapacity(size + 4);
        data[size++] = v0;
        data[size++] = v1;
        data[size++] = v2;
        data[size++] = v3;
    }
    
    public int size() {
        return size;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    public float[] toArray() {
        float[] result = new float[size];
        System.arraycopy(data, 0, result, 0, size);
        return result;
    }
    
    public void clear() {
        size = 0;
    }
    
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length * 2, minCapacity);
            float[] newData = new float[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }
}
