/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BTree.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database.geometry.btree;

import java.io.*;
import java.util.*;
import com.sun.electric.database.geometry.btree.unboxed.*;
import com.sun.electric.database.geometry.btree.CachingPageStorage.CachedPage;

/**
 *  Page format:
 *
 *    int: pageid of parent
 *    int: isRightMost (1 or 0) 
 *    int: 0
 *    int: pageid of left neighbor (not used)
 *    int: pageid of right neighbor (not used)
 *    int: number of key-value pairs on this page
 *    repeat
 *       key: key
 *       val: val
 */
class LeafNodeCursor
    <K extends Serializable & Comparable,
     V extends Serializable,
     S extends Serializable>
    extends NodeCursor<K,V,S> {

    private        final int LEAF_HEADER_SIZE;
    private        final int LEAF_ENTRY_SIZE;
    private        final int LEAF_MAX_BUCKETS;
    private              int numbuckets = 0;

    public int getMaxBuckets() { return LEAF_MAX_BUCKETS; }

    public LeafNodeCursor(BTree<K,V,S> bt) {
        super(bt);
        this.LEAF_HEADER_SIZE = 6 * SIZEOF_INT;
        this.LEAF_ENTRY_SIZE  = bt.uk.getSize() + bt.uv.getSize();
        this.LEAF_MAX_BUCKETS = (ps.getPageSize() - LEAF_HEADER_SIZE) / LEAF_ENTRY_SIZE;
    }

    public static boolean isLeafNode(CachedPage cp) { return UnboxedInt.instance.deserializeInt(cp.getBuf(), 2*SIZEOF_INT)==0; }

    public void setBuf(CachedPage cp) {
        assert isLeafNode(cp);
        super.setBuf(cp);
        numbuckets = bt.ui.deserializeInt(getBuf(), 5*SIZEOF_INT);
    }
    public void initBuf(CachedPage cp, int parent, boolean isRightMost) {
        super.setBuf(cp);
        bt.ui.serializeInt(0, getBuf(), 2*SIZEOF_INT);
        setRightMost(isRightMost);
        setNumBuckets(0);
        setParent(parent);
    }
    public int  getLeftNeighborPageId() { return bt.ui.deserializeInt(getBuf(), 3*SIZEOF_INT); }
    public int  getRightNeighborPageId() { return bt.ui.deserializeInt(getBuf(), 4*SIZEOF_INT); }
    protected void setNumBuckets(int num) { bt.ui.serializeInt(numbuckets = num, getBuf(), 5*SIZEOF_INT); }
    public int  getNumBuckets() { return numbuckets; }
    public int  compare(byte[] key, int key_ofs, int keynum) {
        if (keynum<0) return 1;
        if (keynum>=getNumBuckets()) return -1;
        return bt.uk.compare(key, key_ofs, getBuf(), LEAF_HEADER_SIZE + keynum*LEAF_ENTRY_SIZE);
    }
    public V getVal(int bucket) {
        return bt.uv.deserialize(getBuf(), LEAF_HEADER_SIZE + bt.uk.getSize() + LEAF_ENTRY_SIZE*bucket);
    }

    /** returns the value previously in the bucket */
    public V setVal(int bucket, V val) {
        assert val!=null;
        int pos = LEAF_HEADER_SIZE + bt.uk.getSize() + LEAF_ENTRY_SIZE*bucket;
        V ret = bt.uv.deserialize(getBuf(), pos);
        bt.uv.serialize(val, getBuf(), pos);
        writeBack();
        return ret;
    }

    /** Insert a key/value pair at the designated bucket. */
    public void insertVal(int bucket, byte[] key, int key_ofs, V val) {
        assert val!=null;
        assert getNumBuckets() < getMaxBuckets();
        if (bucket < getNumBuckets())
            System.arraycopy(getBuf(),
                             LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket,
                             getBuf(),
                             LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*(bucket+1),
                             (getNumBuckets()-bucket)*LEAF_ENTRY_SIZE);
        setNumBuckets(getNumBuckets()+1);
        System.arraycopy(key, key_ofs, getBuf(), LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket, bt.uk.getSize());
        setVal(bucket, val);
        writeBack();
    }

    /** Delete the key/value pair at the designated bucket. */
    public void deleteVal(int bucket) {
        assert bucket < getNumBuckets();
        System.arraycopy(getBuf(),
                         LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*(bucket+1),
                         getBuf(),
                         LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket,
                         (getNumBuckets()-bucket-1)*LEAF_ENTRY_SIZE);
        setNumBuckets(getNumBuckets()-1);
        writeBack();
    }

    protected void scoot(byte[] oldBuf, int endOfBuf, int splitPoint) {
        int len = LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE * splitPoint;
        System.arraycopy(oldBuf, len,
                         getBuf(), LEAF_HEADER_SIZE,
                         endOfBuf - len);
    }

    public boolean isLeafNode() { return true; }
    protected int endOfBuf() { return LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE * getNumBuckets(); }

    public void getKey(int bucket, byte[] key, int key_ofs) {
        System.arraycopy(getBuf(), LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket, key, key_ofs, bt.uk.getSize());
    }

    public int getNumValsBelowBucket(int bucket) { return bucket < getNumBuckets() ? 1 : 0; }

    public void getSummary(int bucket, byte[] buf, int ofs) {
        // This tacitly relies on the fact that Pair<A,B> lays the values out adjacently, and so
        // does the internal layout of a leaf node page.  Probably not such a great idea, but it
        // works for now.
        bt.summary.call(getBuf(), LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket, buf, ofs);
    }

    public K getKey(int bucket) { return bt.uk.deserialize(getBuf(), LEAF_HEADER_SIZE + LEAF_ENTRY_SIZE*bucket); }

    public void getSummary(byte[] buf, int ofs) {
        byte[] buf2 = new byte[bt.summary.getSize()];
        getSummary(0, buf, ofs);
        for(int i=1; i<getNumBuckets(); i++) {
            getSummary(i, buf2, 0);
            bt.summary.multiply(buf, ofs,
                                buf2, 0,
                                buf, ofs);
        }
    }
}
