/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ObjSize.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Electric(tm); see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, Mass 02111-1307, USA.
 */
package com.sun.electric.util.memory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compute size of object in Java heap
 */
public enum ObjSize {

    /**
     * Object count
     */
    COUNT(0, 0) {
        @Override
        public long sizeOf(Object o) {
            return 1;
        }

        @Override
        public long sizeOfClassInstance(Class cls) {
            return 1;
        }

        @Override
        public long sizeOfArray(Class componentCls, int len) {
            return 1;
        }
    },
    /**
     * 32-bit JDK
     */
    JDK32(4, 4),
    /**
     * 64-bit JDK with -XX:+UseCompressedOops
     */
    JDK64Compressed(8, 4),
    /**
     * 64-bit JDK with -XX:-UseCompressedOops
     */
    JDK64Uncompressed(8, 8);
    /**
     * Size of native pointer to VMT table
     */
    private final int vmtSize;
    /**
     * Size of (compressed) pointer
     */
    private final int wordSize;
    /**
     * Size of object header
     */
    private final int headerSize;
    /**
     * Size of array header
     */
    private final int arrayHeaderSize;
    /**
     * Cache of class info.
     * For array classes - negated size of array element in bytes.
     * For other classes - word-align size of class instance.
     */
    private final Map<Class, Integer> classInfoRegistry = new WeakHashMap<Class, Integer>();
    private static final IdentityHashMap<Class, Integer> primitiveSizeRegistry = new IdentityHashMap<Class, Integer>();

    static {
        primitiveSizeRegistry.put(Boolean.TYPE, 1);
        primitiveSizeRegistry.put(Byte.TYPE, Byte.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Character.TYPE, Character.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Short.TYPE, Short.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Integer.TYPE, Integer.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Long.TYPE, Long.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Float.TYPE, Float.SIZE / Byte.SIZE);
        primitiveSizeRegistry.put(Double.TYPE, Double.SIZE / Byte.SIZE);
    }
    private static volatile ObjSize detected = null;

    private ObjSize(int vmtSize, int wordSize) {
        this.vmtSize = vmtSize;
        this.wordSize = wordSize;
        headerSize = vmtSize + wordSize;
        arrayHeaderSize = align(headerSize + 4);
    }

    /**
     * Return aligned size of object in bytes.
     * @param o the Object
     * @return aligned size
     */
    public long sizeOf(Object o) {
        int classInfo = getClassInfo(o.getClass());
        return alignHeap(classInfo > 0 ? classInfo : arrayHeaderSize - classInfo * (long) Array.getLength(o));
    }

    /**
     * Calculate aligned size of objects of specified class
     * @param cls specified class
     * @return aligned size
     * @throws IllegalArgumentException if specified class is array class
     */
    public long sizeOfClassInstance(Class cls) {
        int classInfo = getClassInfo(cls);
        if (classInfo < 0) {
            throw new IllegalArgumentException("Array class " + cls.getSimpleName());
        }
        return alignHeap(classInfo);
    }

    /**
     * Return aligned size of array in bytes
     * @param componentCls type of elements
     * @param len array length
     * @return aligned size
     */
    public long sizeOfArray(Class componentCls, int len) {
        return alignHeap(arrayHeaderSize + typeSize(componentCls) * (long) len);
    }

    /**
     * Get class info from cache or compute it and put it to cache
     * @param cls class
     * @return class info
     */
    private int getClassInfo(Class cls) {
        Integer classInfo;
        synchronized (this) {
            classInfo = classInfoRegistry.get(cls);
        }
        if (classInfo != null) {
            return classInfo;
        } else {
            return computeClassInfo(cls);
        }
    }

    /**
     * Compute class info and put it to cache
     * @param cls class
     * @return class info
     */
    private int computeClassInfo(Class cls) {
        int classInfo;
        if (cls.isArray()) {
            classInfo = -typeSize(cls.getComponentType());
        } else {
            classInfo = calcClsSizeImpl(cls);
        }
        Integer oldInfo;
        synchronized (this) {
            oldInfo = classInfoRegistry.put(cls, classInfo);
        }
        assert oldInfo == null || oldInfo == classInfo;
        return classInfo;
    }

    /**
     * Compute word-aligned size of non-array class instances.
     * @param cls class
     * @return size of class instances
     */
    private int calcClsSizeImpl(Class cls) {
        if (cls == Object.class) {
            return vmtSize + wordSize;
        }
        assert !cls.isArray();
        int size = getClassInfo(cls.getSuperclass());
        assert size > 0;
        for (Field f : cls.getDeclaredFields()) {
            if ((f.getModifiers() & Modifier.STATIC) == 0) {
                size += typeSize(f.getType());
            }
        }
        return align(size);
    }

    /**
     * Compute heap-aligned size (8-byte aligned)
     * @param size size
     * @return heap-aligned size
     */
    private long alignHeap(long size) {
        return -(-size & -8);
    }

    /**
     * Compute word-aligned size
     * @param size size
     * @return word-aligned size
     */
    private int align(int size) {
        return -(-size & -wordSize);
    }

    /**
     * Compute size of array element
     * @param cls type of array element
     * @return size of array element
     */
    private int typeSize(Class cls) {
        Integer primitiveSize = primitiveSizeRegistry.get(cls);
        if (primitiveSize != null) {
            return primitiveSize;
        } else {
            assert !cls.isPrimitive();
            return wordSize;
        }
    }
    /**
     * Static array that is used to detect ObjSize for this JVM
     */
    static boolean[][] detectArray;

    /**
     * Returns ObjSize for this JVM.
     * May take long time because of garbage collection.
     * @return ObjSize for this JVM
     */
    public static ObjSize current() {
        if (detected != null) {
            return detected;
        }
        MemoryUsage memoryUsage = MemoryUsage.getInstance();
        int l = 1000000;
        detectArray = new boolean[l][];
        double sz = Double.NaN;
        try {
            MemoryUsage.collectGarbage();
            MemoryUsage.collectGarbage();
            long mem0 = memoryUsage.getUsedMemory();
            for (int i = 0; i < l; i++) {
                detectArray[i] = new boolean[1];
            }
            long mem1 = memoryUsage.getUsedMemory();
            sz = (mem1 - mem0) / (double) l;
        } finally {
            detectArray = null;
        }
        ObjSize bestObjSize = null;
        double bestDiff = Double.NaN;
        for (ObjSize objSize : ObjSize.values()) {
            long osz = objSize.sizeOf(new boolean[1]);
            double diff = Math.abs(sz - osz);
            if (bestObjSize == null || diff < bestDiff) {
                bestObjSize = objSize;
                bestDiff = diff;
            }
        }
        detected = bestObjSize;
        System.out.println("ObjSize." + ObjSize.current() + " sz=" + sz);
        return bestObjSize;
    }
}
