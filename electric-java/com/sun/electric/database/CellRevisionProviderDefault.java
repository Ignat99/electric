/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellRevisionProviderDefault.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database;

import com.sun.electric.util.TextUtils;
import com.sun.electric.util.collections.ArrayIterator;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Thi subclass of CellRevisionProvider that provides array-based
 * ImmutableNodeInst/ImmutableArcInst/ImmutableExport that are implemented in
 * Java.
 */
public class CellRevisionProviderDefault extends CellRevisionProvider {

    private static final ImmutableNodeInstIterable emptyNodeList = new ImmutableNodeInstIterable(ImmutableNodeInst.NULL_ARRAY, null);
    private static final ImmutableArcInstIterable emptyArcList = new ImmutableArcInstIterable(ImmutableArcInst.NULL_ARRAY);
    private static final ImmutableExportIterable emptyExportList = new ImmutableExportIterable(ImmutableExport.NULL_ARRAY);

    /**
     * Creates a new instance of CellRevision
     */
    @Override
    public CellRevision createCellRevision(ImmutableCell c) {
        return new CellRevisionJ(c);
    }

    @Override
    protected ImmutableNodeInst.Iterable createNodeList(ImmutableNodeInst[] elems, ImmutableNodeInst.Iterable oldList) {
        if (elems != null) {
            if (elems.length == 0) {
                return emptyNodeList;
            };
            if (oldList == null) {
                oldList = emptyNodeList;
            }
            Iterator<ImmutableNodeInst> it = oldList.iterator();
            boolean changed = false;
            int i = 0;
            while (it.hasNext() && i < elems.length) {
                ImmutableNodeInst on = it.next();
                if (elems[i] == on) {
                    i++;
                } else {
                    changed = true;
                    String ons = on.name.toString();
                    int cmp = TextUtils.STRING_NUMBER_ORDER.compare(elems[i].name.toString(), ons);
                    while (cmp < 0) {
                        if (i > 0 && TextUtils.STRING_NUMBER_ORDER.compare(elems[i - 1].name.toString(), elems[i].name.toString()) >= 0) {
                            throw new IllegalArgumentException("nodes order");
                        }
                        i++;
                        cmp = i == elems.length ? 1 : TextUtils.STRING_NUMBER_ORDER.compare(elems[i].name.toString(), ons);
                    }
                    if (cmp == 0) {
                        i++;
                    }
                }
            }
            while (i < elems.length) {
                changed = true;
                if (i > 0 && TextUtils.STRING_NUMBER_ORDER.compare(elems[i - 1].name.toString(), elems[i].name.toString()) >= 0) {
                    throw new IllegalArgumentException("nodes order");
                }
                i++;
            }
            if (it.hasNext()) {
                changed = true;
            }
            if (!changed) {
                return oldList;
            }
            ImmutableNodeInstIterable old = (ImmutableNodeInstIterable) oldList;
            int[] nodeIndex = old.nodeIndex;
            boolean sameNodeIdAndIndex = true;
            boolean sameNodeIndex = elems.length == old.size();
            int nodeIndexLength = 0;
            int nodeInd = 0;
            Iterator<ImmutableNodeInst> oldNodes = old.iterator();
            for (ImmutableNodeInst n : elems) {
                sameNodeIdAndIndex = sameNodeIdAndIndex && n.nodeId == nodeInd;
                sameNodeIndex = sameNodeIndex && n.nodeId == oldNodes.next().nodeId;
                nodeIndexLength = Math.max(nodeIndexLength, n.nodeId + 1);
                nodeInd++;
            }
            if (sameNodeIdAndIndex) {
                nodeIndex = null;
            } else if (!sameNodeIndex) {
                nodeIndex = new int[nodeIndexLength];
                Arrays.fill(nodeIndex, -1);
                nodeInd = 0;
                for (ImmutableNodeInst n : elems) {
                    int nodeId = n.nodeId;
                    if (nodeIndex[nodeId] >= 0) {
                        throw new IllegalArgumentException("nodeChronIndex");
                    }
                    nodeIndex[nodeId] = nodeInd;
                    nodeInd++;
                }
                assert !Arrays.equals(old.nodeIndex, nodeIndex);
            }
            return new ImmutableNodeInstIterable(elems, nodeIndex);
        } else {
            return oldList != null ? oldList : emptyNodeList;
        }
    }

    @Override
    protected ImmutableArcInst.Iterable createArcList(ImmutableArcInst[] elems, ImmutableArcInst.Iterable oldList) {
        if (elems != null) {
            return elems.length != 0 ? new ImmutableArcInstIterable(elems) : emptyArcList;
        } else {
            return oldList != null ? oldList : emptyArcList;
        }
    }

    @Override
    protected ImmutableExport.Iterable createExportList(ImmutableExport[] elems, ImmutableExport.Iterable oldList) {
        if (elems != null) {
            return elems.length != 0 ? new ImmutableExportIterable(elems) : emptyExportList;
        } else {
            return oldList != null ? oldList : emptyExportList;
        }
    }

    /**
     * Immutable list of ImmutableNodeInsts
     */
    public static class ImmutableNodeInstIterable implements ImmutableNodeInst.Iterable {

        private final ImmutableNodeInst[] elems;
        private final int[] nodeIndex;

        ImmutableNodeInstIterable(ImmutableNodeInst[] elems, int[] nodeIndex) {
            this.elems = elems;
            this.nodeIndex = nodeIndex;
        }

        /**
         * Returns <tt>true</tt> if this list contains no ImmutableNodeInsts.
         *
         * @return <tt>true</tt> if this list contains no ImmutableNodeInsts.
         */
        @Override
        public boolean isEmpty() {
            return elems.length == 0;
        }

        /**
         * Returns the number of ImmutableNodeInsts in this list.
         *
         * @return the number of ImmutableNodeInsts in this list
         */
        @Override
        public int size() {
            return elems.length;
        }

        /**
         * Returns the ImmutableNodeInst at the specified position in this list.
         *
         * @param index index of the element to return
         * @return the element at the specified position in this list
         * @throws IndexOutOfBoundsException if the index is out of range
         * (<tt>index &lt; 0 || index &gt;= size()</tt>)
         */
        @Override
        public ImmutableNodeInst get(int index) {
            return elems[index];
        }

        @Override
        public Iterator<ImmutableNodeInst> iterator() {
            return ArrayIterator.iterator(elems);
        }

        /**
         * Searches the nodes for the specified name using the binary search
         * algorithm.
         *
         * @param name the name to be searched.
         * @return index of the search name, if it is contained in the arcs;
         * otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The <i>insertion
         * point</i> is defined as the point at which the ImmutableNodeInst
         * would be inserted into the list: the index of the first element
         * greater than the name, or <tt>size()</tt>, if all elements in the
         * list are less than the specified name. Note that this guarantees that
         * the return value will be &gt;= 0 if and only if the ImmutableNodeInst
         * is found.
         */
        @Override
        public int searchByName(String name) {
            int low = 0;
            int high = elems.length - 1;
            int pick = high; // initially try the last postition
            while (low <= high) {
                ImmutableNodeInst n = elems[pick];
                int cmp = TextUtils.STRING_NUMBER_ORDER.compare(n.name.toString(), name);
                if (cmp < 0) {
                    low = pick + 1;
                } else if (cmp > 0) {
                    high = pick - 1;
                } else {
                    return pick; // NodeInst found
                }
                pick = (low + high) >> 1; // try in a middle
            }
            return -(low + 1); // NodeInst not found.
        }

        /**
         * Returns ImmutableNodeInst by its nodeId.
         *
         * @param nodeId of ImmutableNodeInst.
         * @return ImmutableNodeInst with given nodeId
         * @throws IndexOutOfBoundsException if nodeId is negative
         */
        @Override
        public ImmutableNodeInst getNodeById(int nodeId) {
            if (nodeIndex == null) {
                return nodeId < this.size() ? this.get(nodeId) : null;
            }
            if (nodeId >= nodeIndex.length) {
                return null;
            }
            int nodeInd = nodeIndex[nodeId];
            return nodeInd >= 0 ? this.get(nodeInd) : null;
        }

        /**
         * Returns sort order index of ImmutableNodeInst by its nodeId.
         *
         * @param nodeId of ImmutableNodeInst.
         * @return sort order index of node
         */
        @Override
        public int getNodeIndexByNodeId(int nodeId) {
            int nodeInd = nodeIndex != null ? nodeIndex[nodeId] : nodeId;
            assert 0 <= nodeInd && nodeInd < this.size();
            return nodeInd;
        }

        /**
         * Returns true an ImmutableNodeInst with specified nodeId is contained in
         * this CellRevision.
         *
         * @param nodeId specified nodeId.
         * @throws IllegalArgumentException if nodeId is negative
         */
        @Override
        public boolean hasNodeWithId(int nodeId) {
            if (nodeId < 0) {
                throw new IllegalArgumentException();
            }
            if (nodeIndex != null) {
                return nodeId < nodeIndex.length && nodeIndex[nodeId] >= 0;
            } else {
                return nodeId < this.size();
            }
        }

        /**
         * Returns maximum nodeId used by nodes of this CellReversion. Returns -1 if
         * CellRevsison doesn't contatin nodes
         *
         * @return maximum nodeId
         */
        @Override
        public int getMaxNodeId() {
            return (nodeIndex != null ? nodeIndex.length : this.size()) - 1;
        }

        /**
         * Checks invariant of this CellRevision.
         *
         * @throws AssertionError if invariant is broken.
         */
        @Override
        public void check() {
            ImmutableNodeInst prevN = null;
            for (ImmutableNodeInst n : this) {
                n.check();
                if (prevN != null) {
                    assert TextUtils.STRING_NUMBER_ORDER.compare(prevN.name.toString(), n.name.toString()) < 0;
                }
                prevN = n;
            }
            if (nodeIndex != null && nodeIndex.length > 0) {
                assert nodeIndex[nodeIndex.length - 1] >= 0;
                for (int nodeId = 0; nodeId < nodeIndex.length; nodeId++) {
                    int nodeInd = nodeIndex[nodeId];
                    if (nodeInd == -1) {
                        continue;
                    }
                    assert this.get(nodeInd).nodeId == nodeId;
                }
            }
            int nodeInd = 0;
            for (ImmutableNodeInst n : this) {
                assert nodeIndex != null ? nodeIndex[n.nodeId] == nodeInd : n.nodeId == nodeInd;
                assert getNodeById(n.nodeId) == n;
                nodeInd++;
            }
        }
    }

    /**
     * Immutable list of ImmutableArcInsts
     */
    public static class ImmutableArcInstIterable implements ImmutableArcInst.Iterable {

        private final ImmutableArcInst[] elems;

        ImmutableArcInstIterable(ImmutableArcInst[] elems) {
            this.elems = elems;
            ImmutableArcInst prevA = null;
            for (ImmutableArcInst a : this.elems) {
                if (prevA != null) {
                    int cmp = TextUtils.STRING_NUMBER_ORDER.compare(prevA.name.toString(), a.name.toString());
                    if (cmp > 0 || cmp == 0 && (a.name.isTempname() || prevA.arcId >= a.arcId)) {
                        throw new IllegalArgumentException("arcs order " + a.name);
                    }
                }
                prevA = a;
            }
        }

        /**
         * Returns <tt>true</tt> if this list contains no ImmutableArcInsts.
         *
         * @return <tt>true</tt> if this list contains no ImmutableArcInsts.
         */
        @Override
        public boolean isEmpty() {
            return elems.length == 0;
        }

        /**
         * Returns the number of ImmutableArcInsts in this list.
         *
         * @return the number of ImmutableArcInsts in this list
         */
        @Override
        public int size() {
            return elems.length;
        }

        /**
         * Returns the ImmutableArcInst at the specified position in this list.
         *
         * @param index index of the ImmutableArcInst to return
         * @return the ImmutableArcInst at the specified position in this list
         * @throws IndexOutOfBoundsException if the index is out of range
         * (<tt>index &lt; 0 || index &gt;= size()</tt>)
         */
        @Override
        public ImmutableArcInst get(int index) {
            return elems[index];
        }

        @Override
        public Iterator<ImmutableArcInst> iterator() {
            return ArrayIterator.iterator(elems);
        }

        /**
         * Searches the arcs for the specified name using the binary search
         * algorithm.
         *
         * @param name the name to be searched.
         * @return index of the search name, if it is contained in the arcs;
         * otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The <i>insertion
         * point</i> is defined as the point at which the ImmutableArcInst would
         * be inserted into the list: the index of the first element greater
         * than the name, or <tt>arcs.size()</tt>, if all elements in the list
         * are less than the specified name. Note that this guarantees that the
         * return value will be &gt;= 0 if and only if the ImmutableArcInst is
         * found.
         */
        @Override
        public int searchByName(String name) {
            int low = 0;
            int high = elems.length - 1;
            int pick = high; // initially try the last postition
            while (low <= high) {
                ImmutableArcInst a = elems[pick];
                int cmp = TextUtils.STRING_NUMBER_ORDER.compare(a.name.toString(), name);
                if (cmp < 0) {
                    low = pick + 1;
                } else if (cmp > 0) {
                    high = pick - 1;
                } else {
                    return pick; // NodeInst found
                }
                pick = (low + high) >> 1; // try in a middle
            }
            return -(low + 1); // NodeInst not found.
        }
    }

    /**
     * Immutable list of ImmutableExports
     */
    public static class ImmutableExportIterable implements ImmutableExport.Iterable {

        private final ImmutableExport[] elems;

        ImmutableExportIterable(ImmutableExport[] elems) {
            this.elems = elems;
            ImmutableExport prevE = null;
            for (ImmutableExport e : this.elems) {
                if (prevE != null && TextUtils.STRING_NUMBER_ORDER.compare(prevE.name.toString(), e.name.toString()) >= 0) {
                    throw new IllegalArgumentException("exports order");
                }
                prevE = e;
            }
        }

        /**
         * Returns <tt>true</tt> if this list contains no ImmutableExports
         *
         * @return <tt>true</tt> if this list contains no ImmutableExports.
         */
        @Override
        public boolean isEmpty() {
            return elems.length == 0;
        }

        /**
         * Returns the number of ImmutableExports in this list.
         *
         * @return the number of ImmutableExports in this list
         */
        @Override
        public int size() {
            return elems.length;
        }

        /**
         * Returns the ImmutableExports at the specified position in this list.
         *
         * @param index index of the ImmutableExport to return
         * @return the ImmutableExport at the specified position in this list
         * @throws IndexOutOfBoundsException if the index is out of range
         * (<tt>index &lt; 0 || index &gt;= size()</tt>)
         */
        @Override
        public ImmutableExport get(int index) {
            return elems[index];
        }

        @Override
        public Iterator<ImmutableExport> iterator() {
            return ArrayIterator.iterator(elems);
        }

        /**
         * Searches the exports for the specified name using the binary search
         * algorithm.
         *
         * @param name the name to be searched.
         * @return index of the search name, if it is contained in the exports;
         * otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The <i>insertion
         * point</i> is defined as the point at which the ImmutableExport would
         * be inserted into the list: the index of the first element greater
         * than the name, or <tt>arcs.size()</tt>, if all elements in the list
         * are less than the specified name. Note that this guarantees that the
         * return value will be &gt;= 0 if and only if the ImmutableExport is
         * found.
         */
        @Override
        public int searchByName(String name) {
            int low = 0;
            int high = elems.length - 1;
            int pick = high; // initially try the last postition
            while (low <= high) {
                ImmutableExport e = elems[pick];
                int cmp = TextUtils.STRING_NUMBER_ORDER.compare(e.name.toString(), name);
                if (cmp < 0) {
                    low = pick + 1;
                } else if (cmp > 0) {
                    high = pick - 1;
                } else {
                    return pick; // NodeInst found
                }
                pick = (low + high) >> 1; // try in a middle
            }
            return -(low + 1); // NodeInst not found.
        }

        @Override
        public ImmutableExport[] toArray() {
            return elems.clone();
        }
    }
}
