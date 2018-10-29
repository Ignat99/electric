/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RTNode.java
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database.topology;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.tool.Job;
import com.sun.electric.util.math.AbstractFixpRectangle;

import com.sun.electric.util.math.FixpCoord;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;

/**
 * The RTNode class implements R-Trees.
 * R-trees come from this paper: Guttman, Antonin, "R-Trees: A Dynamic Index Structure for Spatial Searching",
 * ACM SIGMOD, 14:2, 47-57, June 1984.
 * <P>
 * R-trees are height-balanced trees in which all leaves are at the same depth and contain RTBounds objects
 * (generally Geometric objects NodeInst and ArcInst). Entries higher in the tree store boundary information
 * that tightly encloses the leaves below. All nodes hold from M to 2M entries, where M is 4.
 * The bounding boxes of two entries may overlap, which allows arbitrary structures to be represented.
 * A search for a point or an area is a simple recursive walk through the tree to collect appropriate leaf nodes.
 * Insertion and deletion, however, are more complex operations.  The figure below illustrates how R-Trees work:
 * <P>
 * <CENTER><IMG SRC="doc-files/Geometric-1.gif"></CENTER>
 */
public class RTNode<T extends RTBounds> extends AbstractFixpRectangle {

    /** lower bound on R-tree node size */
    private static final int MINRTNODESIZE = 4;
    /** upper bound on R-tree node size */
    private static final int MAXRTNODESIZE = (MINRTNODESIZE * 2);
    /** bounds of this node and its children */
    private long fixpMinX;
    private long fixpMinY;
    private long fixpMaxX;
    private long fixpMaxY;
    /** number of children */
    private int total;
    /** children */
    private final Object[] pointers = new Object[MAXRTNODESIZE];
    /** nonzero if children are terminal */
    private boolean flag;
    /** parent node */
    private RTNode<T> parent;

    @Override
    public long getFixpMinX() {
        return fixpMinX;
    }

    @Override
    public long getFixpMinY() {
        return fixpMinY;
    }

    @Override
    public long getFixpMaxX() {
        return fixpMaxX;
    }

    @Override
    public long getFixpMaxY() {
        return fixpMaxY;
    }

    @Override
    public void setFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        throw new UnsupportedOperationException();
    }

    void setFixpLow(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        this.fixpMinX = fixpMinX;
        this.fixpMinY = fixpMinY;
        this.fixpMaxX = fixpMaxX;
        this.fixpMaxY = fixpMaxY;
    }

    @Override
    public AbstractFixpRectangle createFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        throw new UnsupportedOperationException();
    }

//    public void add(AbstractFixpRectangle r) {
//        fixpMinX = Math.min(fixpMinX, r.getFixpMinX());
//        fixpMinY = Math.min(fixpMinY, r.getFixpMinY());
//        fixpMaxX = Math.max(fixpMaxX, r.getFixpMaxX());
//        fixpMaxY = Math.max(fixpMaxY, r.getFixpMaxY());
//    }

    /** Method to get the number of children of this RTNode. */
    public int getTotal() {
        return total;
    }

    /** Method to set the number of children of this RTNode. */
    void setTotal(int total) {
        this.total = total;
    }

    /** Method to get the parent of this RTNode. */
    private RTNode<T> getParent() {
        return parent;
    }

    /** Method to set the parent of this RTNode. */
    private void setParent(RTNode<T> parent) {
        this.parent = parent;
    }

    /** Method to get the number of children of this RTNode. */
    public Object getChild(int index) {
        return pointers[index];
    }

    @SuppressWarnings("unchecked")
    public T getChildLeaf(int index) {
        return (T)pointers[index];
    }

    @SuppressWarnings("unchecked")
    public RTNode<T> getChildTree(int index) {
        return (RTNode<T>)pointers[index];
    }

    /** Method to set the number of children of this RTNode. */
    void setChild(int index, Object obj) {
        this.pointers[index] = obj;
    }

    /** Method to get the leaf/branch flag of this RTNode. */
    public boolean getFlag() {
        return flag;
    }

    /** Method to set the leaf/branch flag of this RTNode. */
    private void setFlag(boolean flag) {
        this.flag = flag;
    }

    /** Method to set the bounds of this RTNode. */
    private void setBounds(AbstractFixpRectangle bounds) {
        fixpMinX = bounds.getFixpMinX();
        fixpMinY = bounds.getFixpMinY();
        fixpMaxX = bounds.getFixpMaxX();
        fixpMaxY = bounds.getFixpMaxY();
    }

    /** Method to extend the bounds of this RTNode by "bounds". */
    private void unionBounds(AbstractFixpRectangle bounds) {
        fixpMinX = Math.min(fixpMinX, bounds.getFixpMinX());
        fixpMinY = Math.min(fixpMinY, bounds.getFixpMinY());
        fixpMaxX = Math.max(fixpMaxX, bounds.getFixpMaxX());
        fixpMaxY = Math.max(fixpMaxY, bounds.getFixpMaxY());
    }

    /**
     * Method to create the top-level R-Tree structure for a new Cell.
     * @return an RTNode object that is empty.
     */
    public static <T extends RTBounds> RTNode<T> makeTopLevel() {
        RTNode<T> top = new RTNode<T>();
        top.total = 0;
        top.flag = true;
        top.parent = null;
        return top;
    }

    /**
     * Method to link this RTBounds into the R-tree of its parent Cell.
     * This is static, because it may modify the root node, and so it must
     * take a root node and possibly return a different one.
     * @param env the environment of this operation (for messages).
     * @param root root of the RTree.
     * @param geom RTBounds to link.
     * @return new root of RTree.
     */
    public static <T extends RTBounds> RTNode<T> linkGeom(Object env, RTNode<T> root, T geom) {
        // find the bottom-level branch (a RTNode with leafs) that would expand least by adding this RTBounds
        if (root == null) {
            return null;
        }
        AbstractFixpRectangle geomBounds = geom.getBounds();
        long geomMinX = geomBounds.getFixpMinX();
        long geomMinY = geomBounds.getFixpMinY();
        long geomMaxX = geomBounds.getFixpMaxX();
        long geomMaxY = geomBounds.getFixpMaxY();
        RTNode<T> rtn = root;
        for (;;) {
            // if R-tree node contains primitives, exit loop
            if (rtn.getFlag()) {
                break;
            }

            // find sub-node that would expand the least
            double bestExpand = 0;
            int bestSubNode = 0;
            for (int i = 0; i < rtn.getTotal(); i++) {
                // get bounds and area of sub-node
                RTNode<T> subrtn = rtn.getChildTree(i);
                double area = (double) subrtn.getFixpWidth() * (double) subrtn.getFixpHeight();

                // get area of sub-node with new element
                long fixpNewUnionMinX = Math.min(geomMinX, subrtn.fixpMinX);
                long fixpNewUnionMinY = Math.min(geomMinY, subrtn.fixpMinY);
                long fixpNewUnionMaxX = Math.max(geomMaxX, subrtn.fixpMaxX);
                long fixpNewUnionMaxY = Math.max(geomMaxY, subrtn.fixpMaxY);
                double newArea = (double) (fixpNewUnionMaxX - fixpNewUnionMinX) * (double) (fixpNewUnionMaxY - fixpNewUnionMinY);

                // accumulate the least expansion
                double expand = newArea - area;

                // remember the child that expands the least
                if (i != 0 && expand > bestExpand) {
                    continue;
                }
                bestExpand = expand;
                bestSubNode = i;
            }

            // recurse down to sub-node that expanded least
            rtn = rtn.getChildTree(bestSubNode);
        }

        // add this geometry element to the correct leaf R-tree node
        return rtn.addToRTNode(geom, env, root);
    }

    /**
     * Method to remove this geometry from the R-tree its parent cell.
     * This is static, because it may modify the root node, and so it must
     * take a root node and possibly return a different one.
     * @param env the environment of this operation (for messages).
     * @param root root of the RTree.
     * @param geom RTBounds to unlink.
     * @return new root of RTree.
     */
    public static <T extends RTBounds> RTNode<T> unLinkGeom(Object env, RTNode<T> root, T geom) {
    	return unLinkGeom(env, root, geom, true);
    }

    /**
     * Method to remove this geometry from the R-tree its parent cell.
     * This is static, because it may modify the root node, and so it must
     * take a root node and possibly return a different one.
     * @param env the environment of this operation (for messages).
     * @param root root of the RTree.
     * @param geom RTBounds to unlink.
     * @param verbose true to print warnings if the geometry is not found
     * @return new root of RTree.
     */
    public static <T extends RTBounds> RTNode<T> unLinkGeom(Object env, RTNode<T> root, T geom, boolean verbose) {
        // find this node in the tree
        RTNode<T> whichRTN = null;
        int whichInd = 0;
        if (root == null) {
            return null;
        }
        FindResult<T> result = root.findGeom(geom);
        if (result != null) {
            whichRTN = result.rtnode;
            whichInd = result.index;
        } else {
            result = root.findGeomAnywhere(geom);
			if (result == null)
			{
				if (verbose) System.out.println("Internal error: cannot find " + geom + " in R-Tree");
//				root = makeTopLevel();
//				for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
//					root = linkGeom(env, root, (RTBounds)it.next());
//				for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
//					root = linkGeom(env, root, (RTBounds)it.next());
				return root;
			}
            whichRTN = result.rtnode;
            whichInd = result.index;
            if (verbose) System.out.println("Internal warning: " + geom + " not in proper R-Tree location in " + env);
        }

        // delete geom from this R-tree node
        return whichRTN.removeRTNode(whichInd, env, root);
    }
    private static int branchCount;

    /**
     * Debugging method to print this R-Tree.
     * @param indent the level of the tree, for proper indentation.
     */
    public void printRTree(int indent) {
        if (indent == 0) {
            branchCount = 0;
        }

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            line.append(' ');
        }
        line.append("RTNodeFixp");
        if (flag) {
            branchCount++;
            line.append(" NUMBER ").append(branchCount);
        }
        appendRect(line, this);
        line.append(" has ").append(total).append(" children:");
        System.out.println(line);

        for (int j = 0; j < total; j++) {
            if (flag) {
                line.setLength(0);
                for (int i = 0; i < indent + 3; i++) {
                    line.append(' ');
                }
                T child = getChildLeaf(j);
                line.append("Child");
                appendRect(line, child.getBounds());
                line.append(" is ").append(child);
                System.out.println(line);
            } else {
                getChildTree(j).printRTree(indent + 3);
            }
        }
    }

    private static void appendRect(StringBuilder sb, AbstractFixpRectangle r) {
        appendRect(sb, r.getFixpMinX(), r.getFixpMinY(), r.getFixpMaxX(), r.getFixpMaxY());
    }

    private static void appendRect(StringBuilder sb, long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        sb.append(" X(").append(FixpCoord.fixpToLambda(fixpMinX)).append("-").append(FixpCoord.fixpToLambda(fixpMaxX))
            .append(") Y(").append(FixpCoord.fixpToLambda(fixpMinY)).append("-").append(FixpCoord.fixpToLambda(fixpMaxY)).append(")");
    }

    /**
     * Debugging method to display this R-Tree.
     * @param cell the Cell in which to show the R-Tree.
     */
    public void displayRTree(Cell cell) {
        EditWindow_ wnd = Job.getUserInterface().getCurrentEditWindow_();
        wnd.clearHighlighting();
        displaySubRTree(cell);
        wnd.finishedHighlighting();
    }

    private void displaySubRTree(Cell cell) {
        EditWindow_ wnd = Job.getUserInterface().getCurrentEditWindow_();
        for (int j = 0; j < getTotal(); j++) {
            if (getFlag()) {
                wnd.addHighlightArea(getChildLeaf(j).getBounds(), cell);
            } else {
                getChildTree(j).displaySubRTree(cell);
            }
        }
    }

    /**
     * Method to return the number of leaf entries in this RTree.
     * @return the number of leaf entries in this RTree.
     */
    public int tallyRTree() {
        int total = 0;
        if (getFlag()) {
            total += getTotal();
        } else {
            for (int j = 0; j < getTotal(); j++) {
                total += getChildTree(j).tallyRTree();
            }
        }
        return total;
    }

    /**
     * Method to check the validity of an RTree node.
     * @param level the level of the node in the tree (for error reporting purposes).
     * @param env the environment in which this node resides.
     */
    public void checkRTree(int level, Object env) {
        long minX, minY, maxX, maxY;
        if (total == 0) {
            minX = minY = maxX = maxY = 0;
        } else {
            minX = minY = Long.MAX_VALUE;
            maxX = maxY = Long.MIN_VALUE;
            for (int i = 0; i < total; i++) {
                AbstractFixpRectangle r = getBBox(i);
                minX = Math.min(minX, r.getFixpMinX());
                minY = Math.min(minY, r.getFixpMinY());
                maxX = Math.max(maxX, r.getFixpMaxX());
                maxY = Math.max(maxY, r.getFixpMaxY());
            }
        }
        if (minX != fixpMinX || minY != fixpMinY || maxX != fixpMaxX || maxY != fixpMaxY) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tree of ").append(env).append(" at level ").append(level).append(" has bounds");
            appendRect(sb, minX, minY, maxX, maxY);
            sb.append(" but stored bounds are");
            appendRect(sb, this);
            System.out.println(sb);
            for (int i = 0; i < total; i++) {
                sb.setLength(0);
                sb.append("  ---Child ").append(i).append(" is");
                appendRect(sb, getBBox(i));
                System.out.println(sb);
            }
        }

        if (!flag) {
            for (int j = 0; j < total; j++) {
                getChildTree(j).checkRTree(level + 1, env);
            }
        }
    }

    /**
     * Method to get the bounding box of child "child" of this R-tree node.
     */
    private AbstractFixpRectangle getBBox(int child) {
        return flag ? getChildLeaf(child).getBounds() : getChildTree(child);
    }

    /**
     * Method to recompute the bounds of this R-tree node.
     */
    private void figBounds() {
        if (total == 0) {
            fixpMinX = fixpMinY = fixpMaxX = fixpMaxY = 0;
            return;
        }
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;
        if (flag) {
            for (int i = 0; i < total; i++) {
                AbstractFixpRectangle r = getChildLeaf(i).getBounds();
                minX = Math.min(minX, r.getFixpMinX());
                minY = Math.min(minY, r.getFixpMinY());
                maxX = Math.max(maxX, r.getFixpMaxX());
                maxY = Math.max(maxY, r.getFixpMaxY());
            }
        } else {
            for (int i = 0; i < total; i++) {
                RTNode<T> subrt = getChildTree(i);
                minX = Math.min(minX, subrt.fixpMinX);
                minY = Math.min(minY, subrt.fixpMinY);
                maxX = Math.max(maxX, subrt.fixpMaxX);
                maxY = Math.max(maxY, subrt.fixpMaxY);
            }
        }
        this.fixpMinX = minX;
        this.fixpMinY = minY;
        this.fixpMaxX = maxX;
        this.fixpMaxY = maxY;
    }

    /**
     * Method to add object "rtnInsert" to this R-tree node, which is in cell "cell".  Method may have to
     * split the node and recurse up the tree
     */
    private RTNode<T> addToRTNode(Object rtnInsert, Object env, RTNode<T> root) {
        // see if there is room in the R-tree node
        if (getTotal() >= MAXRTNODESIZE) {
            // no room: copy list to temp one
            RTNode<T> temp = new RTNode<T>();
            temp.setTotal(getTotal());
            temp.setFlag(getFlag());
            for (int i = 0; i < getTotal(); i++) {
                temp.setChild(i, getChild(i));
            }

            // find the element farthest from new object
            AbstractFixpRectangle bounds;
            if (rtnInsert instanceof RTBounds) {
                @SuppressWarnings("unchecked")
                T leaf = (T) rtnInsert;
                bounds = leaf.getBounds();
            } else {
                @SuppressWarnings("unchecked")
                RTNode<T> tree = (RTNode<T>) rtnInsert;
                bounds = tree;
            }
            long thisFixpCenterX = bounds.getFixpCenterX();
            long thisFixpCenterY = bounds.getFixpCenterY();
            double newDistSq = 0;
            int newN = 0;
            for (int i = 0; i < temp.getTotal(); i++) {
                AbstractFixpRectangle thisv = temp.getBBox(i);
                double distSq = Point2D.distanceSq(thisFixpCenterX, thisFixpCenterY, thisv.getFixpCenterX(), thisv.getFixpCenterY());
                if (distSq >= newDistSq) {
                    newDistSq = distSq;
                    newN = i;
                }
            }

            // now find element farthest from "newN"
            bounds = temp.getBBox(newN);
            thisFixpCenterX = bounds.getFixpCenterX();
            thisFixpCenterY = bounds.getFixpCenterY();
            double oldDistSq = 0;
            int oldN = 0;
            if (oldN == newN) {
                oldN++;
            }
            for (int i = 0; i < temp.getTotal(); i++) {
                if (i == newN) {
                    continue;
                }
                AbstractFixpRectangle thisv = temp.getBBox(i);
                double distSq = Point2D.distanceSq(thisFixpCenterX, thisFixpCenterY, thisv.getFixpCenterX(), thisv.getFixpCenterY());
                if (distSq >= oldDistSq) {
                    oldDistSq = distSq;
                    oldN = i;
                }
            }

            // allocate a new R-tree node
            RTNode<T> newrtn = new RTNode<T>();
            newrtn.setFlag(getFlag());
            newrtn.setParent(getParent());

            // put the first seed element into the new RTree
            newrtn.setChild(0, temp.getChild(newN));
            newrtn.setTotal(1);
            if (!newrtn.getFlag()) {
                newrtn.getChildTree(0).setParent(newrtn);
            }
            temp.setChild(newN, null);

            AbstractFixpRectangle newBounds = newrtn.getBBox(0);
            newrtn.setBounds(newBounds);
            double newArea = (double) newBounds.getFixpWidth() * (double) newBounds.getFixpHeight();

            // initialize the old R-tree node and put in the other seed element
            setChild(0, temp.getChild(oldN));
            if (!getFlag()) {
                getChildTree(0).setParent(this);
            }
            for (int i = 1; i < getTotal(); i++) {
                setChild(i, null);
            }
            setTotal(1);
            temp.setChild(oldN, null);
            AbstractFixpRectangle oldBounds = getBBox(0);
            setBounds(oldBounds);
            double oldArea = (double) oldBounds.getFixpWidth() * (double) oldBounds.getFixpHeight();

            // cluster the rest of the nodes
            for (;;) {
                // search for a cluster about each new node
                int bestNewNode = -1, bestOldNode = -1;
                double bestNewExpand = 0, bestOldExpand = 0;
                for (int i = 0; i < temp.getTotal(); i++) {
                    if (temp.getChild(i) == null) {
                        continue;
                    }
                    bounds = temp.getBBox(i);

                    long fixpNewUnionMinX = Math.min(newBounds.getFixpMinX(), bounds.getFixpMinX());
                    long fixpNewUnionMinY = Math.min(newBounds.getFixpMinY(), bounds.getFixpMinY());
                    long fixpNewUnionMaxX = Math.max(newBounds.getFixpMaxX(), bounds.getFixpMaxX());
                    long fixpNewUnionMaxY = Math.max(newBounds.getFixpMaxY(), bounds.getFixpMaxY());
                    double newAreaPlus = (double) (fixpNewUnionMaxX - fixpNewUnionMinX) * (double) (fixpNewUnionMaxY - fixpNewUnionMinY);

                    long fixpOldUnionMinX = Math.min(oldBounds.getFixpMinX(), bounds.getFixpMinX());
                    long fixpOldUnionMinY = Math.min(oldBounds.getFixpMinY(), bounds.getFixpMinY());
                    long fixpOldUnionMaxX = Math.max(oldBounds.getFixpMaxX(), bounds.getFixpMaxX());
                    long fixpOldUnionMaxY = Math.max(oldBounds.getFixpMaxY(), bounds.getFixpMaxY());
                    double oldAreaPlus = (double) (fixpOldUnionMaxX - fixpOldUnionMinX) * (double) (fixpOldUnionMaxY - fixpOldUnionMinY);

                    // remember the child that expands the new node the least
                    if (bestNewNode < 0 || newAreaPlus - newArea < bestNewExpand) {
                        bestNewExpand = newAreaPlus - newArea;
                        bestNewNode = i;
                    }

                    // remember the child that expands the old node the least
                    if (bestOldNode < 0 || oldAreaPlus - oldArea < bestOldExpand) {
                        bestOldExpand = oldAreaPlus - oldArea;
                        bestOldNode = i;
                    }
                }

                // if there were no nodes added, all have been clustered
                if (bestNewNode == -1 && bestOldNode == -1) {
                    break;
                }

                // if both selected the same object, select another "old node"
                if (bestNewNode == bestOldNode) {
                    bestOldNode = -1;
                    for (int i = 0; i < temp.getTotal(); i++) {
                        if (i == bestNewNode) {
                            continue;
                        }
                        if (temp.getChild(i) == null) {
                            continue;
                        }
                        bounds = temp.getBBox(i);

                        long fixpOldUnionMinX = Math.min(oldBounds.getFixpMinX(), bounds.getFixpMinX());
                        long fixpOldUnionMinY = Math.min(oldBounds.getFixpMinY(), bounds.getFixpMinY());
                        long fixpOldUnionMaxX = Math.max(oldBounds.getFixpMaxX(), bounds.getFixpMaxX());
                        long fixpOldUnionMaxY = Math.max(oldBounds.getFixpMaxY(), bounds.getFixpMaxY());
                        double oldAreaPlus = (double) (fixpOldUnionMaxX - fixpOldUnionMinX) * (double) (fixpOldUnionMaxY - fixpOldUnionMinY);

                        // remember the child that expands the old node the least
                        if (bestOldNode < 0 || oldAreaPlus - oldArea < bestOldExpand) {
                            bestOldExpand = oldAreaPlus - oldArea;
                            bestOldNode = i;
                        }
                    }
                }

                // add to the proper "old node" to the old node cluster
                if (bestOldNode != -1) {
                    // add this node to "rtn"
                    int curPos = getTotal();
                    setChild(curPos, temp.getChild(bestOldNode));
                    if (!getFlag()) {
                        getChildTree(curPos).setParent(this);
                    }
                    setTotal(curPos + 1);
                    temp.setChild(bestOldNode, null);
                    unionBounds(getBBox(curPos));
                    oldBounds = this;
                    oldArea = (double) oldBounds.getFixpWidth() * (double) oldBounds.getFixpHeight();
                }

                // add to proper "new node" to the new node cluster
                if (bestNewNode != -1) {
                    // add this node to "newrtn"
                    int curPos = newrtn.getTotal();
                    newrtn.setChild(curPos, temp.getChild(bestNewNode));
                    newrtn.setTotal(curPos + 1);
                    if (!newrtn.getFlag()) {
                        newrtn.getChildTree(curPos).setParent(newrtn);
                    }
                    temp.setChild(bestNewNode, null);
                    newrtn.unionBounds(newrtn.getBBox(curPos));
                    newBounds = newrtn;
                    newArea = (double) newBounds.getFixpWidth() * (double) newBounds.getFixpHeight();
                }
            }

            // sensibility check
            if (temp.getTotal() != getTotal() + newrtn.getTotal()) {
                System.out.println("R-trees: " + temp.getTotal() + " nodes split to "
                        + getTotal() + " and " + newrtn.getTotal() + "!");
            }

            // now recursively insert this new element up the tree
            if (getParent() == null) {
                // at top of tree: create a new level
                assert root == this;
                RTNode<T> newroot = new RTNode<T>();
                newroot.setTotal(2);
                newroot.setChild(0, this);
                newroot.setChild(1, newrtn);
                newroot.setFlag(false);
                newroot.setParent(null);
                setParent(newroot);
                newrtn.setParent(newroot);
                newroot.figBounds();
                root = newroot;
            } else {
                // first recompute bounding box of R-tree nodes up the tree
                for (RTNode<T> r = getParent(); r != null; r = r.getParent()) {
                    r.figBounds();
                }

                // now add the new node up the tree
                root = getParent().addToRTNode(newrtn, env, root);
            }
        }

        // now add "rtnInsert" to the R-tree node
        int curPos = getTotal();
        setChild(curPos, rtnInsert);
        setTotal(curPos + 1);

        // compute the new bounds
        AbstractFixpRectangle bounds = getBBox(curPos);
        if (getTotal() == 1 && getParent() == null) {
            // special case when adding the first node in a cell
            setBounds(bounds);
            return root;
        }

        // recursively update node sizes
        RTNode<T> climb = this;
        for (;;) {
            climb.unionBounds(bounds);
            if (climb.getParent() == null) {
                break;
            }
            climb = climb.getParent();
        }

        // now check the RTree
//		checkRTree(0, env);
        return root;
    }

    /**
     * Method to remove entry "ind" from this R-tree node in cell "cell"
     */
    private RTNode<T> removeRTNode(int ind, Object env, RTNode<T> root) {
        // delete entry from this R-tree node
        int j = 0;
        for (int i = 0; i < getTotal(); i++) {
            if (i != ind) {
                setChild(j++, getChild(i));
            }
        }
        setTotal(j);

        // see if node is now too small
        if (getTotal() < MINRTNODESIZE) {
            // if recursed to top, shorten R-tree
            RTNode<T> prtn = getParent();
            if (prtn == null) {
                // if tree has no hierarchy, allow short node
                if (getFlag()) {
                    // compute correct bounds of the top node
                    figBounds();
                    return root;
                }

                // save all top-level entries
                RTNode<T> temp = new RTNode<T>();
                temp.setTotal(getTotal());
                temp.setFlag(true);
                for (int i = 0; i < getTotal(); i++) {
                    temp.setChild(i, getChild(i));
                }

                // erase top level
                setTotal(0);
                setFlag(true);

                // reinsert all data
                for (int i = 0; i < temp.getTotal(); i++) {
                    root = temp.getChildTree(i).reInsert(env, root);
                }
                return root;
            }

            // node has too few entries, must delete it and reinsert members
            int found = -1;
            for (int i = 0; i < prtn.getTotal(); i++) {
                if (prtn.getChild(i) == this) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                System.out.println("R-trees: cannot find entry in parent");
            }

            // remove this entry from its parent
            root = prtn.removeRTNode(found, env, root);

            // reinsert the entries
            return reInsert(env, root);
        }

        // recompute bounding box of this R-tree node and all up the tree
        RTNode<T> climb = this;
        for (;;) {
            climb.figBounds();
            if (climb.getParent() == null) {
                break;
            }
            climb = climb.getParent();
        }
        return root;
    }

    /**
     * Method to reinsert the tree of nodes below this RTNode into cell "cell".
     */
    private RTNode<T> reInsert(Object env, RTNode<T> root) {
        if (getFlag()) {
            for (int i = 0; i < getTotal(); i++) {
                root = linkGeom(env, root, getChildLeaf(i));
            }
        } else {
            for (int i = 0; i < getTotal(); i++) {
                root = getChildTree(i).reInsert(env, root);
            }
        }
        return root;
    }

    private static class FindResult<T extends RTBounds> {
        private final RTNode<T> rtnode;
        private int index;

        private FindResult(RTNode<T> rtnode, int index) {
            this.rtnode = rtnode;
            this.index = index;
        }
    }

    /**
     * Method to find the location of geometry module "geom" in the R-tree
     * below this.  The subnode that contains this module is placed in "subrtn"
     * and the index in that subnode is placed in "subind".  The method returns
     * null if it is unable to find the geometry module.
     */
    private FindResult<T> findGeom(T geom) {
        // if R-tree node contains primitives, search for direct hit
        if (getFlag()) {
            for (int i = 0; i < getTotal(); i++) {
                if (getChildLeaf(i) == geom) {
                    return new FindResult<T>(this, i);
                }
            }
            return null;
        }

        // recurse on all sub-nodes that would contain this geometry module
        AbstractFixpRectangle geomBounds = geom.getBounds();
        for (int i = 0; i < getTotal(); i++) {
            // get bounds and area of sub-node
            AbstractFixpRectangle bounds = getBBox(i);

            if (bounds.getFixpMaxX() < geomBounds.getFixpMinX()) {
                continue;
            }
            if (bounds.getFixpMinX() > geomBounds.getFixpMaxX()) {
                continue;
            }
            if (bounds.getFixpMaxY() < geomBounds.getFixpMinY()) {
                continue;
            }
            if (bounds.getFixpMinY() > geomBounds.getFixpMaxY()) {
                continue;
            }
            FindResult<T> subRet = getChildTree(i).findGeom(geom);
            if (subRet != null) {
                return subRet;
            }
        }
        return null;
    }

    /**
     * Method to find the location of geometry module "geom" anywhere in the R-tree
     * at "rtn".  The subnode that contains this module is placed in "subrtn"
     * and the index in that subnode is placed in "subind".  The method returns
     * false if it is unable to find the geometry module.
     */
    private FindResult<T> findGeomAnywhere(T geom) {
        // if R-tree node contains primitives, search for direct hit
        if (getFlag()) {
            for (int i = 0; i < getTotal(); i++) {
                if (getChildLeaf(i) == geom) {
                    return new FindResult<T>(this, i);
                }
            }
            return null;
        }

        // recurse on all sub-nodes
        for (int i = 0; i < getTotal(); i++) {
            FindResult<T> retVal = getChildTree(i).findGeomAnywhere(geom);
            if (retVal != null) {
                return retVal;
            }
        }
        return null;
    }

    /**
     * Class to search a given area of a Cell.
     * This class acts like an Iterator, returning RTBounds objects that are inside the selected area.
     * <P>
     * For example, here is the code to search cell "myCell" in the area "bounds" (in database coordinates):
     * <P>
     * <PRE>
     * for(RTNode.Search<Geometric> sea = <B>new RTNode.Search(bounds, cell)</B>; sea.hasNext(); )
     * {
     *     Geometric geom = sea.next();
     *     if (geom instanceof NodeInst)
     *     {
     *         NodeInst ni = (NodeInst)geom;
     *         // process NodeInst ni in the selected area
     *     } else
     *     {
     *         ArcInst ai = (ArcInst)geom;
     *         // process ArcInst ai in the selected area
     *     }
     * }
     * </PRE>
     */
    public static class Search<T extends RTBounds> implements Iterator<T> {

        /** maximum depth of search */
        private static final int MAXDEPTH = 100;
        /** current depth of search */
        private int depth;
        /** RTNode stack of search */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        private final RTNode<T>[] rtn = new RTNode[MAXDEPTH];;
        /** index stack of search */
        private int[] position = new int[MAXDEPTH];
        /** desired search bounds */
        private final long searchBoundsMinX;
        private final long searchBoundsMinY;
        private final long searchBoundsMaxX;
        private final long searchBoundsMaxY;
        /** the next object to return */
        private T nextObj;
        /** includes objects on the search area edges */
        private final boolean includeEdges;

        /**
         * Constructor to build an R-Tree search iterator.
         * @param bounds the bounds of the search.
         * @param root the root of the R-Tree.
         * @param includeEdges true to include edges of the bounds in the search.
         */
        public Search(Rectangle2D bounds, RTNode<T> root, boolean includeEdges) {
            this.depth = 0;
            this.rtn[0] = root;
            if (bounds instanceof AbstractFixpRectangle) {
                AbstractFixpRectangle fr = (AbstractFixpRectangle)bounds;
                searchBoundsMinX = fr.getFixpMinX();
                searchBoundsMinY = fr.getFixpMinY();
                searchBoundsMaxX = fr.getFixpMaxX();
                searchBoundsMaxY = fr.getFixpMaxY();
            } else {
                searchBoundsMinX = FixpCoord.lambdaToFixp(bounds.getMinX());
                searchBoundsMinY = FixpCoord.lambdaToFixp(bounds.getMinY());
                searchBoundsMaxX = FixpCoord.lambdaToFixp(bounds.getMaxX());
                searchBoundsMaxY = FixpCoord.lambdaToFixp(bounds.getMaxY());
            }
            this.includeEdges = includeEdges;
            this.nextObj = null;
        }

        /**
         * Constructor to build an R-Tree search iterator that finds everything in the tree.
         * @param root the root of the R-Tree.
         */
        public Search(RTNode<T> root) {
            this.depth = 0;
            this.rtn[0] = root;
            searchBoundsMinX = root.fixpMinX;
            searchBoundsMinY = root.fixpMinY;
            searchBoundsMaxX = root.fixpMaxX;
            searchBoundsMaxY = root.fixpMaxY;
            this.includeEdges = false;
            this.nextObj = null;
        }

        /**
         * Method to return the next object in the bounds of the search.
         * @return the next object found.  Returns null when all objects have been reported.
         */
        private T nextObject() {
//        	synchronized (rtn[depth])
//        	{
            for (;;) {
                RTNode<T> rtnode = rtn[depth];
                int i = position[depth]++;
                if (i < rtnode.getTotal()) {
                    AbstractFixpRectangle nodeBounds = rtnode.getBBox(i);
                    if (includeEdges) {
                        if (nodeBounds.getFixpMaxX() < searchBoundsMinX) {
                            continue;
                        }
                        if (nodeBounds.getFixpMinX() > searchBoundsMaxX) {
                            continue;
                        }
                        if (nodeBounds.getFixpMaxY() < searchBoundsMinY) {
                            continue;
                        }
                        if (nodeBounds.getFixpMinY() > searchBoundsMaxY) {
                            continue;
                        }
                    } else {
                        if (nodeBounds.getFixpMaxX() <= searchBoundsMinX) {
                            continue;
                        }
                        if (nodeBounds.getFixpMinX() >= searchBoundsMaxX) {
                            continue;
                        }
                        if (nodeBounds.getFixpMaxY() <= searchBoundsMinY) {
                            continue;
                        }
                        if (nodeBounds.getFixpMinY() >= searchBoundsMaxY) {
                            continue;
                        }
                    }
                    if (rtnode.getFlag()) {
                        return rtnode.getChildLeaf(i);
                    }

                    // look down the hierarchy
                    if (depth >= MAXDEPTH - 1) {
                        System.out.println("R-trees: search too deep");
                        continue;
                    }
                    depth++;
                    rtn[depth] = rtnode.getChildTree(i);
                    position[depth] = 0;
                } else {
                    // pop up the hierarchy
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                }
            }
            return null;
//        	}
        }

        @Override
        public boolean hasNext() {
            if (nextObj == null) {
                nextObj = nextObject();
            }
            return nextObj != null;
        }

        @Override
        public T next() {
            if (nextObj != null) {
                T ret = nextObj;
                nextObj = null;
                return ret;
            }
            return nextObject();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Search.remove()");
        }
    }
}
