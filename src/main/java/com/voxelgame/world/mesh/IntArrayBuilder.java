package com.voxelgame.world.mesh;

/**
 * Growable primitive int array builder. No boxing overhead.
 */
public class IntArrayBuilder {
    private int[] data;
    private int size;
    
    public IntArrayBuilder() {
        this(1024);
    }
    
    public IntArrayBuilder(int initialCapacity) {
        this.data = new int[initialCapacity];
        this.size = 0;
    }
    
    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }
    
    public void add(int v0, int v1, int v2) {
        ensureCapacity(size + 3);
        data[size++] = v0;
        data[size++] = v1;
        data[size++] = v2;
    }
    
    public void add(int v0, int v1, int v2, int v3, int v4, int v5) {
        ensureCapacity(size + 6);
        data[size++] = v0;
        data[size++] = v1;
        data[size++] = v2;
        data[size++] = v3;
        data[size++] = v4;
        data[size++] = v5;
    }
    
    public int size() {
        return size;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    public int[] toArray() {
        int[] result = new int[size];
        System.arraycopy(data, 0, result, 0, size);
        return result;
    }
    
    public void clear() {
        size = 0;
    }
    
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length * 2, minCapacity);
            int[] newData = new int[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }
}
