/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BTreeTest.java
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

import java.util.*;
import com.sun.electric.database.geometry.btree.unboxed.*;

/**
 *  A simple regression test for the BTree.
 */
public class BTreeTest {
    public static void main(String[] s) throws Exception {
        if (s.length != 4) {
            System.err.println("");
            System.err.println("usage: java " + BTree.class.getName() + " <maxsize> <numops> <cachesize> <seed>");
            System.err.println("");
            System.err.println("  Creates a BTree and runs random operations on both it and an in-memory TreeMap.");
            System.err.println("  Reports any disagreements.");
            System.err.println("");
            System.err.println("    <maxsize>   maximum number of entries in the tree, or 0 for no limit");
            System.err.println("    <numops>    number of operations to perform, or 0 for no limit");
            System.err.println("    <cachesize> number of pages to cache in memory, or 0 for no cache");
            System.err.println("    <seed>      seed for random number generator, in hex");
            System.err.println("");
            System.exit(-1);
        }
        if (!BTree.class.desiredAssertionStatus())
            throw new RuntimeException("You need to run this test with assertions enabled!");
        Random rand = new Random(Integer.parseInt(s[3], 16));
        int cachesize = Integer.parseInt(s[2]);
        int numops = Integer.parseInt(s[1]);
        int maxsize = Integer.parseInt(s[0]);
        int size = 0;
        CachingPageStorage ps = new CachingPageStorageWrapper(FilePageStorage.create(), cachesize, false);
        BTree<Integer,Integer,Pair<Integer,Integer>> btree =
            new BTree<Integer,Integer,Pair<Integer,Integer>>(ps, UnboxedInt.instance,
                                                             UnboxedInt.instance,
                                                             null);
        TreeMap<Integer,Integer> tm = 
            new TreeMap<Integer,Integer>();

        int puts=0, gets=0, deletes=0, misses=0, inserts=0;
        long lastprint=0;

        // you can switch one of these off to gather crude performance measurements and compare them to TreeMap
        boolean do_tm = true;
        boolean do_bt = true;

        for(int i=0; numops==0 || i<numops; i++) {
            if (System.currentTimeMillis()-lastprint > 200) {
                lastprint = System.currentTimeMillis();
                System.out.print("\r puts="+puts+" gets="+(gets-misses)+"/"+gets+" deletes="+deletes);
            }
            int key = rand.nextInt() % 1000000;
            switch(rand.nextInt() % 3) {
                case 0: { // get
                    Integer tget = do_tm ? tm.get(key) : null;
                    Integer bget = do_bt ? btree.getValFromKey(key) : null;
                    gets++;
                    if (do_tm && do_bt) {
                        if (tget==null && bget==null) { misses++; break; }
                        if (tget!=null && bget!=null && tget.equals(bget)) break;
                        System.out.print("\r puts="+puts+" gets="+(gets-misses)+"/"+gets+" deletes="+deletes);
                        System.out.println();
                        System.out.println();
                        throw new RuntimeException("  disagreement on key " + key + ": btree="+bget+", treemap="+tget);
                    }
                    break;
                }

                case 1: { // get ordinal
                    int sz = do_bt ? btree.size() : tm.size();
                    int ord = sz==0 ? 0 : Math.abs(rand.nextInt()) % sz;
                    Integer tget = do_tm ? (sz==0 ? null : tm.values().toArray(new Integer[0])[ord]) : null;
                    Integer bget = do_bt ? btree.getValFromOrd(ord) : null;
                    gets++;
                    if (do_tm && do_bt) {
                        if (tget==null && bget==null) break;
                        if (tget!=null && bget!=null && tget.equals(bget)) break;
                        System.out.print("\r puts="+puts+" gets="+(gets-misses)+"/"+gets+" deletes="+deletes);
                        System.out.println();
                        System.out.println();
                        System.out.println("dump:");
                        throw new RuntimeException("  disagreement on ordinal " + ord + ": btree="+bget+", treemap="+tget);
                    }
                    break;
                }

                case 2: { // put
                    int val = rand.nextInt();
                    boolean already_there = false;
                    boolean should_delete = false;
                    if (do_bt) already_there = do_tm ? tm.get(key)!=null : btree.getValFromKey(key)!=null;
                    if (already_there) should_delete = Math.abs(rand.nextInt()) % 10 < 5;
                    if (do_tm) {
                        if (should_delete)
                            tm.remove(key);
                        else
                            tm.put(key, val);
                    }
                    if (do_bt) {
                        if (should_delete)
                            btree.remove(key);
                        else if (already_there)
                            btree.replace(key, val);
                        else
                            btree.insert(key, val);
                    }
                    if (should_delete)
                        deletes++;
                    else
                        puts++;
                    break;
                }
            }
        }
    }
}
