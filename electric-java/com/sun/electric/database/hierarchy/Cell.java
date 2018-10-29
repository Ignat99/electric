/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Cell.java
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
package com.sun.electric.database.hierarchy;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.CellTree;
import com.sun.electric.database.EObjectInputStream;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.IdMapper;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableCell;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.constraint.Constraints;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.network.NetCell;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.Topology;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.tool.user.CircuitChangeJobs;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.collections.ArrayIterator;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.MutableInteger;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * A Cell is a non-primitive NodeProto.
 * Besides the information that it inherits from NodeProto, the Cell holds a
 * set of nodes, arcs, and networks.
 * The exported ports on NodeInsts inside of this cell become the Exports
 * of this Cell.
 * A Cell also has a specific view and version number.
 * <P>
 * It is possible to get all of the versions of the cell.
 * A Cell knows about the most recent version of itself, which may be itself.
 * <P>
 * Cells also belong to CellGroup objects, which gather related cells together.
 * <P>
 * <CENTER><IMG SRC="doc-files/Cell-2.gif"></CENTER>
 * <P>
 * A Cell can have different views and versions, each of which is a cell.
 * The library shown here has two cells ("gate" and "twogate"), each of which has many
 * views (layout, schematics, icon, vhdl) and versions:
 * <P>
 * <CENTER><IMG SRC="doc-files/Cell-1.gif"></CENTER>
 */
public class Cell extends ElectricObject implements NodeProto, Comparable<Cell>
{

    private static final boolean USE_WEAK_REFERENCES = false;
    private static final boolean LAZY_TOPOLOGY = true;
    // ------------------------- private classes -----------------------------

    /**
     * A CellGroup contains a list of cells that are related.
     * This includes different Views of a cell (e.g. the schematic, layout, and icon Views),
     * alternative icons, all the parts of a multi-part icon.
     */
    public static class CellGroup
    {
        // private data

        private final Library lib;
        private TreeSet<Cell> cells;
        private Cell mainSchematic;
        private CellName groupName;

        // ------------------------- public methods -----------------------------
        /**
         * Constructs a CellGroup.
         */
        private CellGroup(Library lib)
        {
            this.lib = lib;
            cells = new TreeSet<Cell>();
        }

        /**
         * Constructor for Undo.
         */
        CellGroup(TreeSet<Cell> cells)
        {
            lib = cells.first().getLibrary();
            this.cells = cells;
            setMainSchematics(true);
            for (Cell cell : cells)
            {
                assert cell.getLibrary() == lib;
                cell.cellGroup = this;
            }
        }

        /**
         * Method to add a Cell to this CellGroup.
         *
         * @param cell the cell to add to this CellGroup.
         */
        private void add(Cell cell)
        {
            lib.checkChanging();
            Cell paramOwner = getParameterOwner();
            synchronized (cells)
            {
                if (!cells.contains(cell))
                {
                    cells.add(cell);
                }
                setMainSchematics(false);
            }
            cell.cellGroup = this;
            if (cell.getD().paramsAllowed() && paramOwner != null)
            {
                cell.setParams(paramOwner);
            }
        }

        /**
         * Method to remove a Cell from this CellGroup.
         *
         * @param f the cell to remove from this CellGroup.
         */
        private void remove(Cell f)
        {
            lib.checkChanging();
            synchronized (cells)
            {
                cells.remove(f);
                setMainSchematics(false);
            }
            f.cellGroup = null;
        }

        /**
         * Method to return an Iterator over all the Cells that are in this CellGroup.
         *
         * @return an Iterator over all the Cells that are in this CellGroup.
         */
        public Iterator<Cell> getCells()
        {
            return cells.iterator();
        }

        /**
         * Method to return the number of Cells that are in this CellGroup.
         *
         * @return the number of Cells that are in this CellGroup.
         */
        public int getNumCells()
        {
            return cells.size();
        }

        /**
         * Method to return a List of all cells in this Group, sorted by View.
         *
         * @return a List of all cells in this Group, sorted by View.
         */
        public List<Cell> getCellsSortedByView()
        {
            synchronized (cells)
            {
                List<Cell> sortedList = new ArrayList<Cell>(cells);
                Collections.sort(sortedList, new TextUtils.CellsByView());
                return sortedList;
            }
        }

        /**
         * Method to return main schematics Cell in this CellGroup.
         * The main schematic is the one that is shown when descending into an icon.
         * Other schematic views may exist in the group, but they are "alternates".
         *
         * @return main schematics Cell in this CellGroup.
         */
        public Cell getMainSchematics()
        {
            return mainSchematic;
        }

        /**
         * Method to return parameter owner Cell in this CellGroup.
         * The parameter owner Cell is icon or schematic Cell whose parameters
         * are used as reference when reconciling parameters of other icon/schematic
         * Cells in the group.
         * Parameter owner Cell is either main schematic Cell or first icon in alphanumeric
         * order in CellGroups without schematic Cells
         *
         * @return parameter owner Cell in this CellGroup.
         */
        public Cell getParameterOwner()
        {
            if (mainSchematic != null)
            {
                return mainSchematic;
            }
            for (Cell cell : cells)
            {
                if (cell.isIcon())
                {
                    return cell;
                }
            }
            return null;
        }

        /**
         * Method to add a parameter on icons/schematics of this CellGroup.
         * It may add repaired copy of this Variable in some cases.
         *
         * @param param parameter to add.
         */
        public void addParam(Variable param)
        {
            for (Cell cell : cells)
            {
                if (!(cell.isIcon() || cell.isSchematic()))
                {
                    continue;
                }

                // find non-conflicting location of this cell attribute
                Point2D offset = cell.newVarOffset();
                cell.addParam(param.withOff(offset.getX(), offset.getY()));
            }
        }

        /**
         * Method to delete a parameter from icons/schematics this CellGroup.
         *
         * @param key the key of the parameter to delete.
         */
        public void delParam(Variable.AttrKey key)
        {
            for (Cell cell : cells)
            {
                if (!(cell.isIcon() || cell.isSchematic()))
                {
                    continue;
                }
                cell.delParam(key);
            }
        }

        /**
         * Rename a parameter. Note that this creates a new variable of
         * the new name and copies all values from the old variable, and
         * then deletes the old variable.
         *
         * @param key the name key of the parameter to rename
         * @param newName the new name of the parameter
         */
        public void renameParam(Variable.AttrKey key, Variable.AttrKey newName)
        {
            if (newName == key)
            {
                return;
            }
            for (Cell cell : cells)
            {
                if (!(cell.isIcon() || cell.isSchematic()))
                {
                    continue;
                }
                cell.renameParam(key, newName);
            }
        }

        /**
         * Method to update a parameter on icons/schematics of this CellGroup with the specified values.
         * If the parameter already exists, only the value is changed; the displayable attributes are preserved.
         *
         * @param key the key of the parameter.
         * @param value the object to store in the parameter.
         * @param unit the unit of the parameter
         */
        public void updateParam(Variable.AttrKey key, Object value, TextDescriptor.Unit unit)
        {
            assert cells.iterator().next().isParam(key);
            for (Cell cell : cells)
            {
                if (!(cell.isIcon() || cell.isSchematic()))
                {
                    continue;
                }
                Variable oldParam = cell.getParameter(key);
                cell.addParam(oldParam.withObject(value).withUnit(unit));
            }
        }

        /**
         * Method to update a text parameter on icons/schematics of this ElectricObject with the specified values.
         * If the Parameter already exists, only the value is changed;
         * the displayable attributes and Code are preserved.
         *
         * @param key the key of the parameter.
         * @param text the text to store in the parameter.
         */
        public void updateParamText(Variable.AttrKey key, String text)
        {
            assert cells.iterator().next().isParam(key);
            for (Cell cell : cells)
            {
                if (!(cell.isIcon() || cell.isSchematic()))
                {
                    continue;
                }
                Variable oldParam = cell.getParameter(key);
                cell.addParam(oldParam.withText(text));
            }
        }

        /**
         * Method to tell whether this CellGroup contains a specified Cell.
         *
         * @param cell the Cell in question.
         * @return true if the Cell is in this CellGroup.
         */
        public boolean containsCell(Cell cell)
        {
            return cell != null && cells.contains(cell);
        }

        /**
         * Returns a printable version of this CellGroup.
         *
         * @return a printable version of this CellGroup.
         */
        @Override
        public String toString()
        {
            return "CellGroup " + getName();
        }

        /**
         * Method to compare two CellGroups.
         * Because CellGroups seem to be ephemeral, and are created dynamically,
         * it is not possible to compare them by equating the object.
         * Therefore, this override compares the group names.
         * Although not accurate, it is better than simple object equality.
         */
        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof CellGroup && groupName != null)
            {
                CellGroup that = (CellGroup)obj;
                return this.lib == that.lib && this.groupName.equals(that.groupName);
            }
            return this == obj;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 31 * hash + (this.groupName != null ? this.groupName.hashCode() : 0);
            return hash;
        }

        /**
         * Returns a string representing the name of the cell group
         */
        public String getName()
        {
            return groupName != null ? groupName.getName() : null;
        }

        public EDatabase getDatabase()
        {
            return lib.getDatabase();
        }

        /**
         * Method to check invariants in this CellGroup.
         *
         * @exception AssertionError if invariants are not valid
         */
        void check()
        {
            for (Cell cell : cells)
            {
                assert lib.cells.get(cell.getCellName()) == cell;
                assert cell.cellGroup == this;
                assert cell.d.groupName.equals(groupName);
            }
            if (mainSchematic != null)
            {
                assert containsCell(mainSchematic);
                assert mainSchematic.getNewestVersion() == mainSchematic;
            }
        }

        /**
         * Method to set the main schematics Cell in this CellGroup.
         * The main schematic is the one that is shown when descending into an icon.
         * Other schematic views may exist in the group, but they are "alternates".
         * Only one schematic view should be in cell group.
         * If many schematic views exists then main schematics is the newest version of first in alphabetical order schematic cell.
         */
        private void setMainSchematics(boolean undo)
        {
            if (cells.isEmpty())
            {
                groupName = null;
                mainSchematic = null;
                return;
            }
            // not set: see if it is obvious
            List<CellName> cellNames = new ArrayList<CellName>();
//            String bestName = null;
            Cell mainSchematic = null;
            for (Cell cell : cells)
            {
                if (cell.isSchematic() && mainSchematic == null)
                {
                    mainSchematic = cell;
                }
                cellNames.add(cell.getCellName());
            }
            groupName = Snapshot.makeCellGroupName(cellNames);
            this.mainSchematic = mainSchematic;
            for (Cell cell : cells)
            {
                if (undo)
                {
                    assert cell.d.groupName.equals(groupName);
                } else
                {
                    cell.setD(cell.d.withGroupName(groupName));
                }
            }
        }
    }
    // -------------------------- private data ---------------------------------
    /**
     * Variable key for characteristic spacing for a cell.
     */
    public static final Variable.Key CHARACTERISTIC_SPACING = Variable.newKey("FACET_characteristic_spacing");
    /**
     * Variable key for text cell contents.
     */
    public static final Variable.Key CELL_TEXT_KEY = Variable.newKey("FACET_message");
    /**
     * Variable key for number of multipage pages.
     */
    public static final Variable.Key MULTIPAGE_COUNT_KEY = Variable.newKey("CELL_page_count");
    /**
     * Variable key for font of text in textual cells.
     */
    public static final Variable.Key TEXT_CELL_FONT_NAME = Variable.newKey("CELL_text_font");
    /**
     * Variable key for size of text in textual cells.
     */
    public static final Variable.Key TEXT_CELL_FONT_SIZE = Variable.newKey("CELL_text_size");
    private static final int[] NULL_INT_ARRAY =
    {
    };
    private static final Export[] NULL_EXPORT_ARRAY =
    {
    };
    /**
     * set if instances should be expanded
     */
    public static final int WANTNEXPAND = 02;
//  /** set if cell is modified */                                  private static final int MODIFIED      =     01000000;
    /**
     * set if everything in cell is locked
     */
    public static final int NPLOCKED = 04000000;
    /**
     * set if instances in cell are locked
     */
    public static final int NPILOCKED = 010000000;
    /**
     * set if cell is part of a "cell library"
     */
    public static final int INCELLLIBRARY = 020000000;
    /**
     * set if cell is from a technology-library
     */
    public static final int TECEDITCELL = 040000000;
    /**
     * set if cell is a multi-page schematic
     */
    private static final int MULTIPAGE = 017600000000;
    /**
     * zero rectangle
     */
    private static final Rectangle2D CENTERRECT = new Rectangle2D.Double(0, 0, 0, 0);
    /**
     * Database to which this Library belongs.
     */
    private final EDatabase database;
    /**
     * Persistent data of this Cell.
     */
    private ImmutableCell d;
    /**
     * The CellGroup this Cell belongs to.
     */
    private CellGroup cellGroup;
    /**
     * The library this Cell belongs to.
     */
    private Library lib;
    /**
     * The technology of this Cell.
     */
    private Technology tech;
    /**
     * The newest version of this Cell.
     */
    Cell newestVersion;
    /**
     * An array of Exports on the Cell by chronological index.
     */
    private Export[] chronExports = new Export[2];
    /**
     * A sorted array of Exports on the Cell.
     */
    private Export[] exports = NULL_EXPORT_ARRAY;
    /**
     * Cell's topology.
     */
    private Reference<Topology> topologyRef;
    /**
     * Cell's topology.
     */
    private Topology strongTopology;
    /**
     * Set containing nodeIds of expanded cells.
     */
    private final BitSet expandedNodes = new BitSet();
    /**
     * Counts of NodeInsts for each CellUsage.
     */
    private int[] cellUsages = NULL_INT_ARRAY;
    /**
     * The temporary integer value.
     */
    private int tempInt;
    /**
     * Set if expanded status of subcell instances is modified.
     */
    private boolean expandStatusModified;
    /**
     * Last CellTree of this Cell
     */
    CellTree tree;
    /**
     * True if cell together with subcells matches cell tree.
     */
    boolean cellTreeFresh;
    /**
     * Last backup of this Cell
     */
    CellBackup backup;
    /**
     * True if cell together with contents matches cell backup.
     */
    boolean cellBackupFresh;
    /**
     * True if cell contents matches cell backup.
     */
    private boolean cellContentsFresh;
    /**
     * True if cell revision date is just set by lowLevelSetRevisionDate
     */
    private boolean revisionDateFresh;
    /**
     * A weak reference to NetCell object with Netlists
     */
    private Reference<NetCell> netCellRef;

    // ------------------ protected and private methods -----------------------
    /**
     * This constructor should not be called.
     * Use the factory "newInstance" to create a Cell.
     */
    Cell(EDatabase database, ImmutableCell d)
    {
        this.database = database;
        this.d = d;
        lib = database.getLib(d.getLibId());
        assert lib != null;
        if (d.techId != null)
        {
            tech = database.getTech(d.techId);
        }
        if (!LAZY_TOPOLOGY)
        {
            strongTopology = new Topology(this, false);
        }
        setTopologyRef(strongTopology);
        setNetCellRef(null);
    }

    /**
     * Don't delete. This method is necessary for serialization
     *
     * @return object serialization.
     */
    private Object writeReplace()
    {
        return new CellKey(this);
    }

    private static class CellKey extends EObjectInputStream.Key<Cell>
    {

        public CellKey()
        {
        }

        private CellKey(Cell cell)
        {
            super(cell);
        }

        @Override
        public void writeExternal(EObjectOutputStream out, Cell cell) throws IOException
        {
            CellId cellId = cell.getId();
            if (cell.getDatabase() != out.getDatabase() || !cell.isLinked())
            {
                throw new NotSerializableException(cell + " not linked");
            }
            out.writeObject(cellId);
        }

        @Override
        public Cell readExternal(EObjectInputStream in) throws IOException, ClassNotFoundException
        {
            CellId cellId = (CellId)in.readObject();
            Cell cell = cellId.inDatabase(in.getDatabase());
            if (cell == null)
            {
                throw new InvalidObjectException(cellId + " not linked");
            }
            return cell;
        }
    }

    /**
     * Factory method to create a new Cell.
     * Also does auxiliary things to create the Cell, such as placing a cell-center if requested.
     *
     * @param lib the Library in which to place this cell.
     * @param name the name of this cell.
     * Cell names may not contain unprintable characters, spaces, tabs, a colon (:), semicolon (;) or curly braces ({}).
     * However, the name can be fully qualified with version and view information.
     * For example, "foo;2{sch}".
     * @return the newly created cell (null on error).
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public static Cell makeInstance(Library lib, String name)
    {
        return makeInstance(EditingPreferences.getInstance(), lib, name);
    }

    /**
     * Factory method to create a new Cell.
     * Also does auxiliary things to create the Cell, such as placing a cell-center if requested.
     *
     * @param ep EditingPreferences with default sizes and text descriptors.
     * @param lib the Library in which to place this cell.
     * @param name the name of this cell.
     * Cell names may not contain unprintable characters, spaces, tabs, a colon (:), semicolon (;) or curly braces ({}).
     * However, the name can be fully qualified with version and view information.
     * For example, "foo;2{sch}".
     * @return the newly created cell (null on error).
     */
    public static Cell makeInstance(EditingPreferences ep, Library lib, String name)
    {
        Cell cell = newInstance(lib, name);

        // add cell-center if requested
        if (ep.isPlaceCellCenter())
        {
            NodeProto cellCenterProto = Generic.tech().cellCenterNode;
            NodeInst cellCenter = NodeInst.newInstance(cellCenterProto, ep, new Point2D.Double(0, 0),
                cellCenterProto.getDefWidth(ep), cellCenterProto.getDefHeight(ep), cell);
            if (cellCenter != null)
            {
                cellCenter.setVisInside();
                cellCenter.setHardSelect();
            }
        }
        return cell;
    }

    /**
     * Factory method to create a new Cell.
     *
     * @param lib the Library in which to place this cell.
     * @param name the name of this cell.
     * Cell names may not contain unprintable characters, spaces, tabs, a colon (:), semicolon (;) or curly braces ({}).
     * However, the name can be fully qualified with version and view information.
     * For example, "foo;2{sch}".
     * @return the newly created cell (null on error).
     */
    public static Cell newInstance(Library lib, String name)
    {
        lib.checkChanging();
        EDatabase database = lib.getDatabase();

        CellName cellName = CellName.parseName(name);
        if (cellName == null)
        {
            return null;
        }

        // check name for legal characters
        String protoName = cellName.getName();
        String original = null;
        for (int i = 0; i < protoName.length(); i++)
        {
            char chr = protoName.charAt(i);
            if (TextUtils.isBadCellNameCharacter(chr))
            {
                if (original == null)
                {
                    original = protoName;
                }
                protoName = protoName.substring(0, i) + '_' + protoName.substring(i + 1);
            }
        }
        if (original != null)
        {
            System.out.println("Cell name changed from '" + original + "' to '" + protoName + "'");
            cellName = CellName.newName(protoName, cellName.getView(), cellName.getVersion());
        }
        cellName = makeUnique(lib, cellName);

        Date creationDate = new Date();
        CellId cellId = lib.getId().newCellId(cellName);
        Cell cell = new Cell(lib.getDatabase(), ImmutableCell.newInstance(cellId, creationDate.getTime()));

        // success
        database.addCell(cell);
        // add ourselves to the library
        lib.addCell(cell);

        // add ourselves to cell group
        cell.lowLevelLinkCellName();

        // handle change control, constraint, and broadcast
        database.unfreshSnapshot();
        Constraints.getCurrent().newObject(cell);
        return cell;
    }

    private void lowLevelLinkCellName()
    {
        // determine the cell group
        for (Iterator<Cell> it = getViewsTail(); it.hasNext();)
        {
            Cell c = it.next();
            if (c.getName().equals(getName()))
            {
                cellGroup = c.cellGroup;
            }
        }
        // still none: make a new one
        if (cellGroup == null)
        {
            cellGroup = new CellGroup(lib);
        }

        // add ourselves to cell group
        cellGroup.add(this);
    }

    /**
     * Method to remove this node from all lists.
     */
    public void kill()
    {
        if (!isLinked())
        {
            System.out.println("Cell already killed");
            return;
        }
        checkChanging();

        List<NodeInst> nodesToKill = new ArrayList<NodeInst>();
        for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
        {
            nodesToKill.add(it.next());
        }
        for (NodeInst ni : nodesToKill)
        {
            ni.kill();
        }
        assert !getUsagesOf().hasNext();

        // remove ourselves from the cellGroup.
        lib.removeCell(this);
        cellGroup.remove(this);
        database.removeCell(getId());

        // handle change control, constraint, and broadcast
        database.unfreshSnapshot();
//        lib.setChanged();
        Constraints.getCurrent().killObject(this);
    }

    /**
     * Method to copy a Cell to any Library.
     *
     * @param fromCell the Cell to copy.
     * @param toLib the Library to copy it to.
     * @param toName the name of the Cell in the destination Library.
     * If the destination library is the same as the original Cell's library and the new name is the same
     * as the old name, a new version is made.
     * @param useExisting true to use existing subcell instances if they exist in the destination Library.
     * @return the new Cell in the destination Library.
     * Note that when copying cells, it is necessary to copy the expanded status of cell instances
     * inside of the copied cell. This must be done during the Job's terminateOK method.
     * See examples that call CellChangeJobs.copyExpandedStatus()
     */
    public static Cell copyNodeProto(Cell fromCell, Library toLib, String toName, boolean useExisting)
    {
        return copyNodeProto(fromCell, toLib, toName, useExisting, null);
    }

    /**
     * Method to copy a Cell to any Library.
     *
     * @param fromCell the Cell to copy.
     * @param toLib the Library to copy it to.
     * @param toName the name of the Cell in the destination Library.
     * If the destination library is the same as the original Cell's library and the new name is the same
     * as the old name, a new version is made.
     * @param useExisting true to use existing subcell instances if they exist in the destination Library.
     * @param cellNamesToUse a map that disambiguates cell names when they clash in different original libraries.
     * The main key is an old cell name, and the value for that key is a map of library names to new cell names.
     * So, for example, if libraries "A" and "B" both have a cell called "X", then existing.get("X").get("A") is "X" but
     * existing.get(X").get("B") is "X_1" which disambiguates the cell names in the destination library. The map may be null.
     * @return the new Cell in the destination Library.
     */
    public static Cell copyNodeProto(Cell fromCell, Library toLib, String toName, boolean useExisting,
        Map<String, Map<String, String>> cellNamesToUse)
    {
        // check for validity
        if (fromCell == null)
        {
            return null;
        }
        if (toLib == null)
        {
            return null;
        }

        // make sure name of new cell is valid
        for (int i = 0; i < toName.length(); i++)
        {
            char ch = toName.charAt(i);
            if (ch <= ' ' || ch == ':' || ch >= 0177)
            {
                System.out.println("invalid name of new cell");
                return null;
            }
        }

        // determine whether this copy is to a different library
        Library destLib = toLib;
        if (toLib == fromCell.getLibrary())
        {
            destLib = null;
        }

        // mark the proper prototype to use for each node
        Map<NodeInst, NodeProto> nodePrototypes = new HashMap<NodeInst, NodeProto>();

        // if doing a cross-library copy and can use existing ones from new library, do it
        if (destLib != null)
        {
            // scan all subcells to see if they are found in the new library
            for (Iterator<NodeInst> it = fromCell.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (!ni.isCellInstance())
                {
                    continue;
                }
                Cell niProto = (Cell)ni.getProto();

                boolean maySubstitute = useExisting;
                if (!maySubstitute)
                {
                    // force substitution for documentation icons
                    if (niProto.isIcon())
                    {
                        if (niProto.isIconOf(fromCell))
                        {
                            maySubstitute = true;
                        }
                    }
                }
                if (!maySubstitute)
                {
                    continue;
                }

                // switch cell names if disambiguating
                String oldCellName = niProto.getName();
                if (cellNamesToUse != null)
                {
                    Map<String, String> libToNameMap = cellNamesToUse.get(oldCellName);
                    if (libToNameMap != null)
                    {
                        String newCellName = libToNameMap.get(niProto.getLibrary().getName());
                        if (newCellName != null)
                        {
                            oldCellName = newCellName;
                        }
                    }
                }

                // search for cell with same name and view in new library
                Cell lnt = null;
                for (Iterator<Cell> cIt = toLib.getCells(); cIt.hasNext();)
                {
                    lnt = cIt.next();
                    if (lnt.getName().equals(oldCellName)
                        && //					if (lnt.getName().equalsIgnoreCase(oldCellName) &&
                        lnt.getView() == niProto.getView())
                    {
                        break;
                    }
                    lnt = null;
                }
                if (lnt == null)
                {
                    continue;
                }

                // make sure all used ports can be found on the uncopied cell
                boolean validPorts = true;
                for (Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext();)
                {
                    PortInst pi = pIt.next();
                    PortProto pp = pi.getPortProto();
                    PortProto ppt = lnt.findPortProto(pp.getName());
//					if (ppt != null)
//					{
//						// the connections must match, too
//						if (pp->connects != ppt->connects) ppt = null;
//					}
                    if (ppt == null)
                    {
                        System.out.println("Cannot use subcell " + lnt.noLibDescribe() + " in " + destLib
                            + ": exports don't match");
                        validPorts = false;
                        break;
                    }
                }
                if (!validPorts)
                {
                    continue;
                }

                // match found: use the prototype from the destination library
                nodePrototypes.put(ni, lnt);
            }
        }
        return copyNodeProtoUsingMapping(fromCell, toLib, toName, nodePrototypes);
    }

    /**
     * Method to copy a Cell to any Library, using a preset mapping of node prototypes.
     *
     * @param fromCell the Cell to copy.
     * @param toLib the Library to copy it to.
     * If the destination library is the same as the original Cell's library, a new version is made.
     * @param toName the name of the Cell in the destination Library.
     * @param nodePrototypes a HashMap from NodeInsts in the source Cell to proper NodeProtos to use in the new Cell.
     * @return the new Cell in the destination Library.
     */
    public static Cell copyNodeProtoUsingMapping(Cell fromCell, Library toLib, String toName,
        Map<NodeInst, NodeProto> nodePrototypes)
    {
        // create the nodeproto
        String cellName = toName;
        if (toName.indexOf('{') < 0 && fromCell.getView() != View.UNKNOWN)
        {
            cellName = toName + fromCell.getView().getAbbreviationExtension();
        }
        Cell newCell = Cell.newInstance(toLib, cellName);
        if (newCell == null)
        {
            return (null);
        }
        newCell.lowLevelSetUserbits(fromCell.lowLevelGetUserbits());

        // copy nodes
        Map<NodeInst, NodeInst> newNodes = new HashMap<NodeInst, NodeInst>();
        for (Iterator<NodeInst> it = fromCell.getNodes(); it.hasNext();)
        {
            // create the new nodeinst
            NodeInst ni = it.next();
            NodeProto lnt = nodePrototypes.get(ni);
            if (lnt == null)
            {
                lnt = ni.getProto();
            }
//            double scaleX = ni.getXSize();
//            double scaleY = ni.getYSize();
            ImmutableNodeInst n = ni.getD();
            NodeInst toNi = NodeInst.newInstance(newCell, lnt, n.name.toString(), n.nameDescriptor,
                n.anchor, n.size, n.orient,
                n.flags, n.techBits, n.protoDescriptor, null);
//            NodeInst toNi = NodeInst.newInstance(lnt, new Point2D.Double(ni.getAnchorCenterX(), ni.getAnchorCenterY()),
//                    scaleX, scaleY, newCell, ni.getOrient(), ni.getName());
            if (toNi == null)
            {
                return null;
            }

            // save the new nodeinst address in the old nodeinst
            newNodes.put(ni, toNi);

            // copy miscellaneous information
            toNi.copyTextDescriptorFrom(ni, NodeInst.NODE_PROTO);
            toNi.copyTextDescriptorFrom(ni, NodeInst.NODE_NAME);
            toNi.copyStateBits(ni);
        }

        // now copy the variables on the nodes
        for (Iterator<NodeInst> it = fromCell.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            NodeInst toNi = newNodes.get(ni);
            toNi.copyVarsFrom(ni);

            // if this is an icon, and this nodeinst is the box with the name of the cell on it,
            // then change the name from the old to the new
            if (newCell.isIcon())
            {
                Variable var = toNi.getVar(Schematics.SCHEM_FUNCTION);
                if (var != null && fromCell.getName().equals(var.getObject()))
                {
                    toNi.addVar(var.withObject(newCell.getName()));
                }
//                String name = toNi.getVarValue(Schematics.SCHEM_FUNCTION, String.class);
//                if (fromCell.getName().equals(name)) {
//                    toNi.updateVar(Schematics.SCHEM_FUNCTION, newCell.getName());
//                }
            }
        }

        // copy arcs
        for (Iterator<ArcInst> it = fromCell.getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();

            // find the nodeinst and portinst connections for this arcinst
            PortInst[] opi = new PortInst[2];
            EPoint[] oLoc = new EPoint[2];
            for (int i = 0; i < 2; i++)
            {
                opi[i] = null;
                NodeInst ono = newNodes.get(ai.getPortInst(i).getNodeInst());
                PortProto pp = ai.getPortInst(i).getPortProto();
                if (!ono.isCellInstance())
                {
                    // primitives associate ports directly
                    opi[i] = ono.findPortInstFromProto(pp);
                } else
                {
                    // cells associate ports by name
                    PortProto ppt = ono.getProto().findPortProto(pp.getName());
                    if (ppt != null)
                    {
                        opi[i] = ono.findPortInstFromProto(ppt);
                    }
                }
                if (opi[i] == null)
                {
                    System.out.println("Error: no port for " + ai.getProto()
                        + " arc on " + ono.getProto());
                }
                oLoc[i] = ai.getLocation(i);
                Poly poly = opi[i].getPoly();
                if (!poly.isInside(oLoc[i]))
                {
                    oLoc[i] = poly.getCenter();
                }
            }
            if (opi[0] == null || opi[1] == null)
            {
                return null;
            }

            // create the arcinst
            ImmutableArcInst a = ai.getD();
            ArcInst toAi = ArcInst.newInstanceNoCheck(newCell, ai.getProto(), a.name.toString(), a.nameDescriptor,
                opi[ArcInst.HEADEND], opi[ArcInst.TAILEND],
                oLoc[ArcInst.HEADEND], oLoc[ArcInst.TAILEND],
                a.getGridExtendOverMin(), a.getAngle(), a.flags);
//            ArcInst toAi = ArcInst.newInstanceBase(ai.getProto(), ai.getLambdaBaseWidth(), opi[ArcInst.HEADEND], opi[ArcInst.TAILEND],
//                    oLoc[ArcInst.HEADEND], oLoc[ArcInst.TAILEND], ai.getName(), ai.getAngle());
            if (toAi == null)
            {
                return null;
            }

            // copy arcinst information
            toAi.copyPropertiesFrom(ai);
        }

        // copy the Exports
        for (Iterator<Export> it = fromCell.getExports(); it.hasNext();)
        {
            Export pp = it.next();

            // match sub-portproto in old nodeinst to sub-portproto in new one
            NodeInst ni = newNodes.get(pp.getOriginalPort().getNodeInst());
            PortInst pi = ni.findPortInst(pp.getOriginalPort().getPortProto().getName());
            if (pi == null)
            {
                System.out.println("Error: no port on " + pp.getOriginalPort().getNodeInst().getProto());
                return null;
            }

            // create the nodeinst portinst
            ImmutableExport e = pp.getD();
            ExportId exportId = newCell.getId().newPortId(e.exportId.getExternalId());
            Export ppt = Export.newInstanceNoIcon(newCell, exportId, e.name.toString(), e.nameDescriptor, pi, e.alwaysDrawn, e.bodyOnly, e.characteristic, null);
//            Export ppt = Export.newInstance(newCell, pi, pp.getName());
            if (ppt == null)
            {
                return null;
            }

            // copy portproto variables
            ppt.copyVarsFrom(pp);

            // copy miscellaneous information
//            ppt.copyStateBits(pp);
//            ppt.copyTextDescriptorFrom(pp, Export.EXPORT_NAME);
        }

        // copy cell variables
        for (Iterator<Variable> it = fromCell.getParametersAndVariables(); it.hasNext();)
        {
            Variable fromVar = it.next();
            if (newCell.isParam(fromVar.getKey()))
            {
                newCell.setTextDescriptor(fromVar.getKey(), fromVar.getTextDescriptor());
            } else if (fromVar.getTextDescriptor().isParam())
            {
//            	if (newCell.getCellGroup() != null)
                newCell.getCellGroup().addParam(fromVar);
                newCell.setTextDescriptor(fromVar.getKey(), fromVar.getTextDescriptor());
            } else
            {
                newCell.addVar(fromVar);
            }
        }

        // reset (copy) date information
        newCell.lowLevelSetCreationDate(fromCell.getCreationDate());
        newCell.lowLevelSetRevisionDate(fromCell.getRevisionDate());

        return newCell;
    }

    /**
     * Method to replace subcells of a Cell by cells with similar name in Cell's
     */
    public void replaceSubcellsByExisting(EditingPreferences ep)
    {
        // scan all subcells to see if they are found in the new library
        Map<NodeInst, Cell> nodePrototypes = new HashMap<NodeInst, Cell>();
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (!ni.isCellInstance())
            {
                continue;
            }
            Cell niProto = (Cell)ni.getProto();
            if (niProto.lib == lib)
            {
                continue;
            }

            // search for cell with same name and view in new library
            Cell lnt = null;
            for (Iterator<Cell> cIt = lib.getCells(); cIt.hasNext();)
            {
                lnt = cIt.next();
                if (lnt.getName().equals(niProto.getName())
                    && //                if (lnt.getName().equalsIgnoreCase(niProto.getName()) &&
                    lnt.getView() == niProto.getView())
                {
                    break;
                }
                lnt = null;
            }
            if (lnt == null)
            {
                continue;
            }

            // make sure all used ports can be found on the uncopied cell
            boolean validPorts = true;
            for (Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext();)
            {
                PortInst pi = pIt.next();
                PortProto pp = pi.getPortProto();
                PortProto ppt = lnt.findPortProto(pp.getName());
//                if (ppt != null) {
//                    // the connections must match, too
//						if (pp->connects != ppt->connects) ppt = null;
//                }
                if (ppt == null)
                {
                    System.out.println("Cannot use subcell " + lnt.noLibDescribe() + " in " + lib
                        + ": exports don't match");
                    validPorts = false;
                    break;
                }
            }
            if (!validPorts)
            {
                continue;
            }

            // match found: use the prototype from the destination library
            nodePrototypes.put(ni, lnt);
        }
        for (Map.Entry<NodeInst, Cell> e : nodePrototypes.entrySet())
        {
            NodeInst ni = e.getKey();
            Cell newProto = e.getValue();
            ni.replace(newProto, ep, false, false, false);
        }
    }

    /**
     * Method to rename this Cell.
     *
     * @param newName the new name of this cell.
     * @param newGroupName the name of cell in a group to put the rename cell to.
     */
    public IdMapper rename(String newName, String newGroupName)
    {
        return rename(CellName.parseName(newName + ";" + getVersion() + getView().getAbbreviationExtension()), newGroupName);
    }

    /**
     * Method to rename this Cell.
     *
     * @param cellName the new name of this cell.
     */
    private IdMapper rename(CellName cellName, String newGroupName)
    {
        checkChanging();
        assert isLinked();
        if (cellName == null)
        {
            return null;
        }
        if (getCellName().equals(cellName))
        {
            return null;
        }

//		// remove temporarily from the library and from cell group
//		lib.removeCell(this);
//		cellGroup.remove(this);
//      // remember the expansion state of instances of the cell
//      Map<CellId, Map<Name, Boolean>> expansionRemap = new HashMap<CellId, Map<Name, Boolean>>();
//      for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();) {
//          NodeInst ni = it.next();
//          Cell cell = ni.getParent();
//          Map<Name, Boolean> cellExpansionRemap = expansionRemap.get(cell.getId());
//          if (cellExpansionRemap == null) {
//              cellExpansionRemap = new HashMap<Name, Boolean>();
//              expansionRemap.put(cell.getId(), cellExpansionRemap);
//          }
//          Boolean isExpanded = new Boolean(ni.isExpanded());
//          cellExpansionRemap.put(ni.getNameKey(), isExpanded);
//      }
        // do the rename
        cellName = makeUnique(lib, cellName);
//      setD(getD().withCellName(cellName));
        Snapshot oldSnapshot = database.backup();
        CellId newCellId = lib.getId().newCellId(cellName);
        IdMapper idMapper = IdMapper.renameCell(oldSnapshot, d.cellId, newCellId);
        Snapshot newSnapshot = oldSnapshot.withRenamedIds(idMapper, d.cellId, newGroupName);

        database.lowLevelSetCanUndoing(true);
        database.undo(newSnapshot);
        database.lowLevelSetCanUndoing(false);
        Constraints.getCurrent().renameIds(idMapper);
        lib.setChanged();

//      // restore the expansion state of instances of the cell
//      for (CellId cid : expansionRemap.keySet()) {
//          Map<Name, Boolean> cellExpansionRemap = expansionRemap.get(cid);
//          Cell cell = cid.inDatabase(database);
//          for (Name name : cellExpansionRemap.keySet()) {
//              NodeInst ni = cell.findNode(name.toString());
//              if (ni != null) {
//                  ni.setExpanded(cellExpansionRemap.get(name).booleanValue());
//              }
//          }
//      }
        return idMapper;
    }

    /**
     * **************************** LOW-LEVEL IMPLEMENTATION *****************************
     */
    private static CellName makeUnique(Library lib, CellName cellName)
    {
        // ensure unique cell name
        String protoName = cellName.getName();
        String canonicProtoName = TextUtils.canonicString(protoName);
        View view = cellName.getView();
        int version = cellName.getVersion();
        if (Snapshot.CELLNAMES_IGNORE_CASE)
        {
            for (Iterator<Cell> it = lib.getCells(); it.hasNext();)
            {
                Cell c = it.next();
                if (TextUtils.canonicString(c.getName()).equals(canonicProtoName))
                {
                    if (!c.getName().equals(protoName))
                    {
                        cellName = CellName.newName(c.getName(), view, version);
                        System.out.println("Change case of cell name from " + protoName + " to " + cellName.getName());
                        protoName = cellName.getName();
                    }
                    break;
                }
            }
        }
        int greatestVersion = 0;
        boolean conflict = version <= 0;
        for (Iterator<Cell> it = lib.getCells(); it.hasNext();)
        {
            Cell c = it.next();
            if (c.getName().equals(protoName) && c.getView() == view) //			if (c.getName().equalsIgnoreCase(protoName) && c.getView() == view)
            {
                if (c.getVersion() == version)
                {
                    conflict = true;
                }
                if (c.getVersion() > greatestVersion)
                {
                    greatestVersion = c.getVersion();
                }
            }
        }
        if (conflict)
        {
            if (version > 0)
            {
                System.out.println("Already have cell " + cellName + " with version " + version + ", generating a new version");
            }
            int newVersion = greatestVersion + 1;
            cellName = CellName.newName(protoName, view, newVersion);
        }
        return cellName;
    }

    /**
     * Low-level method to get the user bits.
     * The "user bits" are a collection of flags that are more sensibly accessed
     * through special methods.
     * This general access to the bits is required because the ELIB
     * file format stores it as a full integer.
     * This should not normally be called by any other part of the system.
     *
     * @return the "user bits".
     */
    public int lowLevelGetUserbits()
    {
        return getD().flags;
    }

    /**
     * Low-level method to set the user bits.
     * The "user bits" are a collection of flags that are more sensibly accessed
     * through special methods.
     * This general access to the bits is required because the ELIB
     * file format stores it as a full integer.
     * This should not normally be called by any other part of the system.
     *
     * @param userBits the new "user bits".
     */
    public void lowLevelSetUserbits(int userBits)
    {
        setD(getD().withFlags(userBits));
    }

    /**
     * Low-level method to backup this Cell to CellTree.
     *
     * @return CellTree which is the backup of this Cell.
     * @throws IllegalStateException if recalculation of Snapshot is required in thread which is not enabled to do it.
     */
    public CellTree tree()
    {
        if (cellTreeFresh)
        {
            return tree;
        }
        checkChanging();
        return doTree();
    }

    /**
     * Low-level method to backup this Cell to CellBackup.
     *
     * @return CellBackup which is the backup of this Cell.
     * @throws IllegalStateException if recalculation of Snapshot is required in thread which is not enabled to do it.
     */
    public CellBackup backup()
    {
        if (cellBackupFresh)
        {
            return backup;
        }
        checkChanging();
        return doBackup();
    }

    public Topology getTopology()
    {
        Topology topology = topologyRef.get();
        if (topology != null)
        {
            return topology;
        }
        return createTopology();
    }

    private synchronized Topology createTopology()
    {
        assert strongTopology == null;
        assert LAZY_TOPOLOGY;
        Topology topology = new Topology(this, backup != null);
        setTopologyRef(topology);
//        System.out.println("Created topology "+database+":"+this);
        return topology;
    }

    private void setTopologyRef(Topology topology)
    {
        topologyRef = USE_WEAK_REFERENCES ? new WeakReference<>(topology) : new SoftReference<>(topology);
    }

    public Topology getTopologyOptional()
    {
        return topologyRef.get();
    }

    private CellTree doTree()
    {
        CellBackup top = backup();
        CellTree[] subTrees = new CellTree[cellUsages.length];
        for (int i = 0; i < cellUsages.length; i++)
        {
            if (cellUsages[i] == 0)
            {
                continue;
            }
            CellId subCellId = getId().getUsageIn(i).protoId;
            subTrees[i] = database.getCell(subCellId).tree();
        }
        tree = tree.with(top, subTrees, getTechPool());
        cellTreeFresh = true;
        return tree;
    }

    private CellBackup doBackup()
    {
        TechPool techPool = getTechPool();
        if (backup == null)
        {
            getTechnology();
            backup = CellBackup.newInstance(getD().withoutVariables(), techPool);
            tree = CellTree.newInstance(backup.cellRevision.d, techPool).with(backup, CellTree.NULL_ARRAY, techPool);
            assert !cellTreeFresh && !cellBackupFresh && !cellContentsFresh && !revisionDateFresh;
        }
        ImmutableNodeInst[] nodes = null;
        ImmutableArcInst[] arcs = null;
        ImmutableExport[] exports = null;
        if (!cellContentsFresh)
        {
//            System.out.println("Refresh contents of " + this);
            Topology topology = getTopologyOptional();
            nodes = topology != null ? topology.backupNodes(backup.cellRevision.nodes) : null;
            arcs = topology != null ? topology.backupArcs(backup.cellRevision.arcs) : null;
            exports = backupExports();
        }
        backup = backup.with(getD(), nodes, arcs, exports, techPool);
        cellBackupFresh = true;
        cellContentsFresh = true;
        if (LAZY_TOPOLOGY)
        {
            strongTopology = null;
        }
        if (backup.modified)
        {
            lib.setChanged();
        }
        return backup;
    }

    private ImmutableExport[] backupExports()
    {
        ImmutableExport[] newExports = new ImmutableExport[exports.length];
        ImmutableExport.Iterable oldExports = backup.cellRevision.exports;
        boolean changed = exports.length != oldExports.size();
        for (int i = 0; i < exports.length; i++)
        {
            Export e = exports[i];
            ImmutableExport d = e.getD();
            changed = changed || oldExports.get(i) != d;
            newExports[i] = d;
        }
        return changed ? newExports : null;
    }

    void recover(CellTree newTree)
    {
        update(true, newTree, null);
    }

    void undo(CellTree newTree, BitSet exportsModified, BitSet boundsModified)
    {
        if (backup == null)
        {
            recover(newTree);
            return;
        }
        assert cellBackupFresh;
        if (backup != newTree.top)
        {
            update(false, newTree, exportsModified);
        } else
        {
            if (exportsModified != null || boundsModified != null)
            {
                checkUndoing();
                Topology topology = getTopologyOptional();
                if (topology != null)
                {
                    topology.updateSubCells(exportsModified, boundsModified);
                }
            }
            cellTreeFresh = true;
            tree = newTree;
        }
    }

    private void update(boolean full, CellTree newTree, BitSet exportsModified)
    {
        checkUndoing();
        unfreshRTree();
        CellBackup newBackup = newTree.top;
        CellRevision newRevision = newBackup.cellRevision;
        this.d = newRevision.d;
        lib = database.getLib(newRevision.d.getLibId());
        tech = database.getTech(newRevision.d.techId);
        cellUsages = newRevision.getInstCounts();
        // Update NodeInsts

        Topology topology = getTopologyOptional();
        if (topology != null)
        {
            if (topology.updateNodes(full, newRevision, exportsModified, expandedNodes))
            {
                expandStatusModified = true;
            }
            topology.updateArcs(newRevision);
        }

        exports = new Export[newRevision.exports.size()];
        for (int i = 0; i < newRevision.exports.size(); i++)
        {
            ImmutableExport d = newRevision.exports.get(i);
            // Add to chronExports
            int chronIndex = d.exportId.getChronIndex();
            if (chronExports.length <= chronIndex)
            {
                Export[] newChronExports = new Export[Math.max(chronIndex + 1, chronExports.length * 2)];
                System.arraycopy(chronExports, 0, newChronExports, 0, chronExports.length);
                chronExports = newChronExports;
            }
            Export e = chronExports[chronIndex];
            if (e != null)
            {
                e.setDInUndo(d);
            } else
            {
                e = new Export(d, this);
                chronExports[chronIndex] = e;
            }
            e.setPortIndex(i);
            exports[i] = e;
        }

        int exportCount = 0;
        for (int i = 0; i < chronExports.length; i++)
        {
            Export e = chronExports[i];
            if (e == null)
            {
                continue;
            }
            int portIndex = e.getPortIndex();
            if (portIndex >= exports.length || e != exports[portIndex])
            {
                e.setPortIndex(-1);
                chronExports[i] = null;
                continue;
            }
            exportCount++;
        }
        assert exportCount == exports.length;

        tree = newTree;
        backup = newBackup;
        cellTreeFresh = true;
        cellBackupFresh = true;
        cellContentsFresh = true;
        if (LAZY_TOPOLOGY)
        {
            strongTopology = null;
        }
        revisionDateFresh = true;
    }

    /**
     * **************************** GRAPHICS *****************************
     */
    /**
     * Method to get the width of this Cell.
     *
     * @return the width of this Cell.
     */
    public double getDefWidth()
    {
        return getBounds().getWidth();
    }

    /**
     * Method to get the width of this Cell.
     *
     * @return the width of this Cell.
     */
    @Override
    public double getDefWidth(EditingPreferences ep)
    {
        return getBounds().getWidth();
    }

    /**
     * Method to the height of this Cell.
     *
     * @return the height of this Cell.
     */
    public double getDefHeight()
    {
        return getBounds().getHeight();
    }

    /**
     * Method to the height of this Cell.
     *
     * @return the height of this Cell.
     */
    @Override
    public double getDefHeight(EditingPreferences ep)
    {
        return getBounds().getHeight();
    }

    /**
     * Method to return the default size of this NodeProto relative to minamal size of this NodeProot.
     * Cells always return zero size.
     *
     * @param ep EditingPreferences with default sizes
     * @return the size to use when creating new NodeInsts of this NodeProto.
     */
    @Override
    public EPoint getDefSize(EditingPreferences ep)
    {
        return EPoint.ORIGIN;
    }

    /**
     * Method to get the size offset of this Cell.
     *
     * @return the size offset of this Cell. It is always zero for cells.
     */
    @Override
    public SizeOffset getProtoSizeOffset()
    {
        return SizeOffset.ZERO_OFFSET;
    }

    /**
     * Method to return an iterator over all RTBounds objects in a given area of this Cell.
     *
     * @param bounds the specified area to search.
     * @return an iterator over all of the RTBounds objects in that area.
     */
    public Iterator<Geometric> searchIterator(Rectangle2D bounds)
    {
        return searchIterator(bounds, true);
    }

    /**
     * Method to return an iterator over all RTBounds objects in a given area of this Cell that allows
     * to ignore elements touching the area.
     *
     * @param bounds the specified area to search.
     * @param includeEdges true if RTBounds objects along edges are considered in.
     * @return an iterator over all of the RTBounds objects in that area.
     */
    public Iterator<Geometric> searchIterator(Rectangle2D bounds, boolean includeEdges)
    {
        return getTopology().searchIterator(bounds, includeEdges);
    }

    /**
     * Method to return the bounds of this Cell.
     *
     * @return an ERectangle with the bounds of this cell's contents
     */
    public ERectangle getBounds()
    {
        return tree().getBounds();
    }

    /**
     * Method to compute the "essential bounds" of this Cell.
     * It looks for NodeInst objects in the cell that are of the type
     * "generic:Essential-Bounds" and builds a rectangle from their locations.
     *
     * @return the bounding area of the essential bounds.
     * Returns null if an essential bounds cannot be determined.
     */
    public Rectangle2D findEssentialBounds()
    {
        return getTopology().findEssentialBounds();
    }

    /**
     * Method to determine whether this Cell has a cell center in it.
     *
     * @return true if this Cell has a Cell-center node in it.
     */
    public boolean alreadyCellCenter()
    {
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (Generic.isCellCenter(ni))
                return true;
        }
        return false;
    }

    /**
     * Method adjust this cell when the reference point moves.
     * This requires renumbering all coordinate values in the Cell.
     *
     * @param cX coordinate X of new center.
     * @param cY coordinate Y of new center.
     */
    public void adjustReferencePoint(double cX, double cY)
    {
        checkChanging();

        // if there is no change, stop now
        if (cX == 0 && cY == 0)
        {
            return;
        }

        // move reference point by (dx,dy)
//		referencePointNode.modifyInstance(-cX, -cY, 0, 0, 0);
        // must adjust all nodes by (dx,dy)
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (Generic.isCellCenter(ni))
                continue;

            // move NodeInst "ni" by (dx,dy)
            ni.move(-cX, -cY);
        }
        for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();

            // move NodeInst "ni" by (dx,dy)
            ai.modify(-cX, -cY, -cX, -cY);
        }

        // adjust all instances of this cell
        for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
        {
            NodeInst ni = it.next();
//			Undo.redrawObject(ni);
            FixpTransform trans = ni.getOrient().pureRotate();
//			AffineTransform trans = NodeInst.pureRotate(ni.getAngle(), ni.isMirroredAboutXAxis(), ni.isMirroredAboutYAxis());
            Point2D in = new Point2D.Double(cX, cY);
            trans.transform(in, in);
            ni.move(in.getX(), in.getY());
        }

        Job.getUserInterface().adjustReferencePoint(this, cX, cY);
//		// adjust all windows showing this cell
//		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
//		{
//			WindowFrame wf = it.next();
//			WindowContent content = wf.getContent();
//			if (!(content instanceof EditWindow_)) continue;
//			Cell cell = content.getCell();
//			if (cell != this) continue;
//			EditWindow_ wnd = (EditWindow_)content;
//			Point2D off = wnd.getOffset();
//			off.setLocation(off.getX()-cX, off.getY()-cY);
//			wnd.setOffset(off);
//		}
    }

    /**
     * Class for creating a description of a frame around a schematic cell.
     */
    public static class FrameDescription
    {

        public static final double MULTIPAGESEPARATION = 1000;
        private static final double FRAMESCALE = 18.0;
        private static final double HASCHXSIZE = (8.5 * FRAMESCALE);
        private static final double HASCHYSIZE = (5.5 * FRAMESCALE);
        private static final double ASCHXSIZE = (11.0 * FRAMESCALE);
        private static final double ASCHYSIZE = (8.5 * FRAMESCALE);
        private static final double BSCHXSIZE = (17.0 * FRAMESCALE);
        private static final double BSCHYSIZE = (11.0 * FRAMESCALE);
        private static final double CSCHXSIZE = (24.0 * FRAMESCALE);
        private static final double CSCHYSIZE = (17.0 * FRAMESCALE);
        private static final double DSCHXSIZE = (36.0 * FRAMESCALE);
        private static final double DSCHYSIZE = (24.0 * FRAMESCALE);
        private static final double ESCHXSIZE = (48.0 * FRAMESCALE);
        private static final double ESCHYSIZE = (36.0 * FRAMESCALE);
        private static final double FRAMEWID = (0.15 * FRAMESCALE);
        private static final double XLOGOBOX = (2.0 * FRAMESCALE);
        private static final double YLOGOBOX = (1.0 * FRAMESCALE);
        private Cell cell;
        private List<Point2D> lineFromEnd;
        private List<Point2D> lineToEnd;
        private List<Point2D> textPoint;
        private List<Double> textSize;
        private List<Point2D> textBox;
        private List<String> textMessage;
        private int pageNo;

        /**
         * Constructor for cell frame descriptions.
         *
         * @param cell the Cell that is having a frame drawn.
         */
        public FrameDescription(Cell cell, int pageNo)
        {
            this.cell = cell;
            this.pageNo = pageNo;
            lineFromEnd = new ArrayList<Point2D>();
            lineToEnd = new ArrayList<Point2D>();
            textPoint = new ArrayList<Point2D>();
            textSize = new ArrayList<Double>();
            textBox = new ArrayList<Point2D>();
            textMessage = new ArrayList<String>();
            loadFrame();
        }

        /**
         * Method to draw a line in a frame.
         * This method is overridden by subclasses that know how to do the function.
         *
         * @param from the starting point of the line (in database units).
         * @param to the ending point of the line (in database units).
         */
        public void showFrameLine(Point2D from, Point2D to)
        {
        }

        /**
         * Method to draw text in a frame.
         * This method is overridden by subclasses that know how to do the function.
         *
         * @param ctr the anchor point of the text.
         * @param size the size of the text (in database units).
         * @param maxWid the maximum width of the text (ignored if zero).
         * @param maxHei the maximum height of the text (ignored if zero).
         * @param string the text to be displayed.
         */
        public void showFrameText(Point2D ctr, double size, double maxWid, double maxHei, String string)
        {
        }

        /**
         * Method called to render the frame information.
         * It makes calls to "renderInit()", "showFrameLine()", and "showFrameText()".
         */
        public void renderFrame()
        {
            double offY = 0;
            if (cell.isMultiPage())
            {
                offY = pageNo * MULTIPAGESEPARATION;
            }
            for (int i = 0; i < lineFromEnd.size(); i++)
            {
                Point2D from = lineFromEnd.get(i);
                Point2D to = lineToEnd.get(i);
                if (offY != 0)
                {
                    from = new Point2D.Double(from.getX(), from.getY() + offY);
                    to = new Point2D.Double(to.getX(), to.getY() + offY);
                }
                showFrameLine(from, to);
            }
            for (int i = 0; i < textPoint.size(); i++)
            {
                Point2D at = textPoint.get(i);
                if (offY != 0)
                {
                    at = new Point2D.Double(at.getX(), at.getY() + offY);
                }
                double size = textSize.get(i).doubleValue();
                Point2D box = textBox.get(i);
                double width = box.getX();
                double height = box.getY();
                String msg = textMessage.get(i);
                showFrameText(at, size, width, height, msg);
            }
        }

        /**
         * Method to determine the size of the schematic frame in the current Cell.
         *
         * @param d a Dimension in which the size (database units) will be placed.
         * @return 0: there should be a frame whose size is absolute;
         * 1: there should be a frame but it combines with other stuff in the cell;
         * 2: there is no frame.
         */
        public static int getCellFrameInfo(Cell cell, Dimension d)
        {
            String frameInfo = cell.getVarValue(User.FRAME_SIZE, String.class);
            if (frameInfo == null)
            {
                return 2;
            }
            if (frameInfo.length() == 0)
            {
                return 2;
            }
            int retval = 0;
            char chr = frameInfo.charAt(0);
            double wid = 0, hei = 0;
            if (chr == 'x')
            {
                wid = XLOGOBOX + FRAMEWID;
                hei = YLOGOBOX + FRAMEWID;
                retval = 1;
            } else
            {
                switch (chr)
                {
                    case 'h':
                        wid = HASCHXSIZE;
                        hei = HASCHYSIZE;
                        break;
                    case 'a':
                        wid = ASCHXSIZE;
                        hei = ASCHYSIZE;
                        break;
                    case 'b':
                        wid = BSCHXSIZE;
                        hei = BSCHYSIZE;
                        break;
                    case 'c':
                        wid = CSCHXSIZE;
                        hei = CSCHYSIZE;
                        break;
                    case 'd':
                        wid = DSCHXSIZE;
                        hei = DSCHYSIZE;
                        break;
                    case 'e':
                        wid = ESCHXSIZE;
                        hei = ESCHYSIZE;
                        break;
                }
            }
            if (frameInfo.indexOf("v") >= 0)
            {
                d.setSize(hei, wid);
            } else
            {
                d.setSize(wid, hei);
            }
            return retval;
        }

        private void loadFrame()
        {
            Dimension d = new Dimension();
            int frameFactor = getCellFrameInfo(cell, d);
            if (frameFactor == 2)
            {
                return;
            }

            String frameInfo = cell.getVarValue(User.FRAME_SIZE, String.class);
            if (frameInfo == null)
            {
                return;
            }
            double schXSize = d.getWidth();
            double schYSize = d.getHeight();

            boolean drawTitleBox = true;
            int xSections = 8;
            int ySections = 4;
            if (frameFactor == 1)
            {
                xSections = ySections = 0;
            } else
            {
                if (frameInfo.indexOf("v") >= 0)
                {
                    xSections = 4;
                    ySections = 8;
                }
                if (frameInfo.indexOf("n") >= 0)
                {
                    drawTitleBox = false;
                }
            }

            double xLogoBox = XLOGOBOX;
            double yLogoBox = YLOGOBOX;
            double frameWid = FRAMEWID;

            // draw the frame
            if (xSections > 0)
            {
                double xSecSize = (schXSize - frameWid * 2) / xSections;
                double ySecSize = (schYSize - frameWid * 2) / ySections;

                // draw the outer frame
                Point2D point0 = new Point2D.Double(-schXSize / 2, -schYSize / 2);
                Point2D point1 = new Point2D.Double(-schXSize / 2, schYSize / 2);
                Point2D point2 = new Point2D.Double(schXSize / 2, schYSize / 2);
                Point2D point3 = new Point2D.Double(schXSize / 2, -schYSize / 2);
                addLine(point0, point1);
                addLine(point1, point2);
                addLine(point2, point3);
                addLine(point3, point0);

                // draw the inner frame
                point0 = new Point2D.Double(-schXSize / 2 + frameWid, -schYSize / 2 + frameWid);
                point1 = new Point2D.Double(-schXSize / 2 + frameWid, schYSize / 2 - frameWid);
                point2 = new Point2D.Double(schXSize / 2 - frameWid, schYSize / 2 - frameWid);
                point3 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid);
                addLine(point0, point1);
                addLine(point1, point2);
                addLine(point2, point3);
                addLine(point3, point0);

                // tick marks along the top and bottom sides
                for (int i = 0; i < xSections; i++)
                {
                    double x = i * xSecSize - (schXSize / 2 - frameWid);
                    if (i > 0)
                    {
                        point0 = new Point2D.Double(x, schYSize / 2 - frameWid);
                        point1 = new Point2D.Double(x, schYSize / 2 - frameWid / 2);
                        addLine(point0, point1);
                        point0 = new Point2D.Double(x, -schYSize / 2 + frameWid);
                        point1 = new Point2D.Double(x, -schYSize / 2 + frameWid / 2);
                        addLine(point0, point1);
                    }

                    char chr = (char)('1' + xSections - i - 1);
                    point0 = new Point2D.Double(x + xSecSize / 2, schYSize / 2 - frameWid / 2);
                    addText(point0, frameWid, 0, 0, String.valueOf(chr));

                    point0 = new Point2D.Double(x + xSecSize / 2, -schYSize / 2 + frameWid / 2);
                    addText(point0, frameWid, 0, 0, String.valueOf(chr));
                }

                // tick marks along the left and right sides
                for (int i = 0; i < ySections; i++)
                {
                    double y = i * ySecSize - (schYSize / 2 - frameWid);
                    if (i > 0)
                    {
                        point0 = new Point2D.Double(schXSize / 2 - frameWid, y);
                        point1 = new Point2D.Double(schXSize / 2 - frameWid / 2, y);
                        addLine(point0, point1);
                        point0 = new Point2D.Double(-schXSize / 2 + frameWid, y);
                        point1 = new Point2D.Double(-schXSize / 2 + frameWid / 2, y);
                        addLine(point0, point1);
                    }
                    char chr = (char)('A' + i);
                    point0 = new Point2D.Double(schXSize / 2 - frameWid / 2, y + ySecSize / 2);
                    addText(point0, frameWid, 0, 0, String.valueOf(chr));

                    point0 = new Point2D.Double(-schXSize / 2 + frameWid / 2, y + ySecSize / 2);
                    addText(point0, frameWid, 0, 0, String.valueOf(chr));
                }
            }
            if (drawTitleBox)
            {
                Point2D point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox);
                Point2D point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox);
                Point2D point2 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid);
                Point2D point3 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid);
                addLine(point0, point1);
                addLine(point1, point2);
                addLine(point2, point3);
                addLine(point3, point0);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 2 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 2 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 4 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 4 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 6 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 6 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 8 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 8 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 10 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 10 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox, -schYSize / 2 + frameWid + yLogoBox * 12 / 15);
                point1 = new Point2D.Double(schXSize / 2 - frameWid, -schYSize / 2 + frameWid + yLogoBox * 12 / 15);
                addLine(point0, point1);

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 13.5 / 15);
                addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 3 / 15, "Cell: " + cell.describe(false) + (cell.isMultiPage() ? " Page " + (pageNo + 1) : ""));

                String projectName = cell.getLibrary().getVarValue(User.FRAME_PROJECT_NAME, String.class, User.getFrameProjectName());
                if (projectName.length() > 0)
                {
                    point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 11 / 15);
                    addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Project: " + projectName);
                }

                String designerName = cell.getVarValue(User.FRAME_DESIGNER_NAME, String.class);
                if (designerName == null)
                {
                    designerName = cell.getLibrary().getVarValue(User.FRAME_DESIGNER_NAME, String.class, User.getFrameDesignerName());
                }
                if (designerName.length() > 0)
                {
                    point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 9 / 15);
                    addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Designer: " + designerName);
                }

                String lastChangeByName = cell.getVarValue(User.FRAME_LAST_CHANGED_BY, String.class);
                if (lastChangeByName != null)
                {
                    point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 7 / 15);
                    addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Last Changed By: " + lastChangeByName);
                }

                String companyName = cell.getLibrary().getVarValue(User.FRAME_COMPANY_NAME, String.class, User.getFrameCompanyName());
                if (companyName.length() > 0)
                {
                    point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 5 / 15);
                    addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Company: " + companyName);
                }

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 3 / 15);
                addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Created: " + TextUtils.formatDate(cell.getCreationDate()));

                point0 = new Point2D.Double(schXSize / 2 - frameWid - xLogoBox / 2, -schYSize / 2 + frameWid + yLogoBox * 1 / 15);
                addText(point0, yLogoBox * 2 / 15, xLogoBox, yLogoBox * 2 / 15, "Revised: " + TextUtils.formatDate(cell.getRevisionDate()));
            }
        }

        private void addLine(Point2D from, Point2D to)
        {
            lineFromEnd.add(from);
            lineToEnd.add(to);
        }

        private void addText(Point2D at, double size, double width, double height, String msg)
        {
            textPoint.add(at);
            textSize.add(new Double(size));
            textBox.add(new Point2D.Double(width, height));
            textMessage.add(msg);
        }
    }

    /**
     * **************************** NODES *****************************
     */
    /**
     * Method to return an Iterator over all NodeInst objects in this Cell.
     *
     * @return an Iterator over all NodeInst objects in this Cell.
     */
    public synchronized Iterator<NodeInst> getNodes()
    {
        return getTopology().getNodes();
    }

    /**
     * Method to return an Iterator over all NodeInst objects in this Cell.
     *
     * @return an Iterator over all NodeInst objects in this Cell.
     */
    public synchronized Iterator<Nodable> getNodables()
    {
        return getTopology().getNodables();
    }

    /**
     * Method to return the number of NodeInst objects in this Cell.
     *
     * @return the number of NodeInst objects in this Cell.
     */
    public int getNumNodes()
    {
        Topology topology = getTopologyOptional();
        return topology != null ? topology.getNumNodes() : backup().cellRevision.nodes.size();
    }

    /**
     * Method to return the NodeInst at specified position.
     *
     * @param nodeIndex specified position of NodeInst.
     * @return the NodeInst at specified position.
     */
    public final NodeInst getNode(int nodeIndex)
    {
        return getTopology().getNode(nodeIndex);
    }

    /**
     * Method to return the NodeInst by its chronological index.
     *
     * @param nodeId chronological index of NodeInst.
     * @return the NodeInst with specified chronological index.
     */
    public NodeInst getNodeById(int nodeId)
    {
        return getTopology().getNodeById(nodeId);
    }

    /**
     * Tells expanded status of NodeInst with specified nodeId.
     *
     * @return true if NodeInst with specified nodeId is expanded.
     */
    public boolean isExpanded(int nodeId)
    {
        return expandedNodes.get(nodeId);
    }

    public BitSet lowLevelExpandedNodes()
    {
        return expandedNodes;
    }

    /**
     * Method to set expanded status of specified NodeInst.
     * Expanded NodeInsts are instances of Cells that show their contents.
     * Unexpanded Cell instances are shown as boxes with the node prototype names in them.
     * The state has no meaning for instances of primitive node prototypes.
     *
     * @param nodeId specified nodeId
     * @param value true if NodeInst is expanded.
     */
    public void setExpanded(int nodeId, boolean value)
    {
        ImmutableNodeInst n = backup().cellRevision.getNodeById(nodeId);
        if (n == null)
        {
            return;
        }
        if (!(n.protoId instanceof CellId) || ((CellId)n.protoId).isIcon())
        {
            return;
        }
        boolean oldValue = expandedNodes.get(nodeId);
        if (oldValue == value)
        {
            return;
        }
        expandedNodes.set(nodeId, value);
        expandStatusModified = true;
    }

    /**
     * Adds to the argument collection this cell and any cell which
     * has an instance at most "depth" levels below this one (or anywhere
     * if depth==-1), and such that the path from this cell to the target
     * does not pass through an instance of a cell already in the
     * collection. This runs in time proportional to the size of the database.
     */
    public void getCellsToDepth(java.util.HashSet<Cell> cells, int depth)
    {
        if (cells.contains(this))
            return;
        cells.add(this);
        if (depth == 0)
            return;
        for (ImmutableNodeInst n : backup().cellRevision.nodes)
        {
            if (!n.isCellInstance())
                continue;

            // ignore recursive references (showing icon in contents)
            Cell subCell = (Cell)n.protoId.inDatabase(database);
            if (subCell.isIconOf(this))
                continue;
            subCell.getCellsToDepth(cells, depth == -1 ? -1 : depth - 1);
        }
    }

    /**
     * Method to set expand specified subcells.
     * Expanded NodeInsts are instances of Cells that show their contents.
     *
     * @param subCells nodeIds of subCells to expand
     */
    void expand(BitSet subCells)
    {
        for (int nodeId = subCells.nextSetBit(0); nodeId >= 0; nodeId = subCells.nextSetBit(nodeId + 1))
        {
            setExpanded(nodeId, true);
        }
    }

    /**
     * Method to return the PortInst by nodeId and PortProtoId.
     *
     * @param nodeId specified NodeId.
     * @param portProtoId
     * @return the PortInst at specified position..
     */
    public PortInst getPortInst(int nodeId, PortProtoId portProtoId)
    {
        return getTopology().getPortInst(nodeId, portProtoId);
    }

    /**
     * Method to return an Iterator over all CellUsage objects in this Cell.
     *
     * @return an Iterator over all CellUsage objects in this Cell.
     */
    public synchronized Iterator<CellUsage> getUsagesIn()
    {
        return new Iterator<CellUsage>()
        {

            private int i = 0;
            CellUsage nextU = findNext();

            @Override
            public boolean hasNext()
            {
                return nextU != null;
            }

            @Override
            public CellUsage next()
            {
                if (nextU == null)
                {
                    throw new NoSuchElementException();
                }
                CellUsage u = nextU;
                nextU = findNext();
                return u;
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            ;

            private CellUsage findNext()
            {
                while (i < cellUsages.length)
                {
                    if (cellUsages[i] != 0)
                    {
                        return getId().getUsageIn(i++);
                    }
                    i++;
                }
                return null;
            }
        };
    }

    /**
     * Method to return the number of NodeUsage objects in this Cell.
     *
     * @return the number of NodeUsage objects in this Cell.
     */
    public int getNumUsagesIn()
    {
        int numUsages = 0;
        for (int i = 0; i < cellUsages.length; i++)
        {
            if (cellUsages[i] != 0)
            {
                numUsages++;
            }
        }
        return numUsages;
    }

    /**
     * Method to find a named NodeInst on this Cell.
     *
     * @param name the name of the NodeInst.
     * @return the NodeInst. Returns null if none with that name are found.
     */
    public NodeInst findNode(String name)
    {
        return getTopology().findNode(name);
    }

    /**
     * Method to unlink a set of these NodeInsts from this Cell.
     *
     * @param killedNodes a set of NodeInsts to kill.
     */
    public void killNodes(Set<NodeInst> killedNodes)
    {
        if (killedNodes.isEmpty())
        {
            return;
        }
        for (NodeInst ni : killedNodes)
        {
            if (ni.getParent() != this)
            {
                throw new IllegalArgumentException("parent");
            }
        }
        Set<ArcInst> arcsToKill = new HashSet<ArcInst>();
        for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();
            if (killedNodes.contains(ai.getTailPortInst().getNodeInst()) || killedNodes.contains(ai.getHeadPortInst().getNodeInst()))
            {
                arcsToKill.add(ai);
            }

        }
        Set<Export> exportsToKill = new HashSet<Export>();
        for (Iterator<Export> it = getExports(); it.hasNext();)
        {
            Export export = it.next();
            if (killedNodes.contains(export.getOriginalPort().getNodeInst()))
            {
                exportsToKill.add(export);
            }
        }

        for (ArcInst ai : arcsToKill)
        {
            ai.kill();
        }
        killExports(exportsToKill);

        for (NodeInst ni : killedNodes)
        {
            if (!ni.isLinked())
            {
                continue;
            }
            // remove this node from the cell
            removeNode(ni);

            // handle change control, constraint, and broadcast
            Constraints.getCurrent().killObject(ni);
        }
    }
    private static boolean allowCirDep = false;

    /**
     * Method to allow temporarily circular library dependences
     * (for example to read legacy libraries).
     * It is called only from synchronized method Input.readLibrary.
     *
     * @param val true allows circular dependencies.
     */
    public static void setAllowCircularLibraryDependences(boolean val)
    {
        allowCirDep = val;
    }

    public void addNodes(Collection<ImmutableNodeInst> nodes)
    {
        checkChanging();

        EPoint newCellCenterAnchor = null;
        BitSet nodeIds = new BitSet();
        HashSet<Name> names = new HashSet<Name>();
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            ImmutableNodeInst n = it.next().getD();
            names.add(n.name);
            if (nodeIds.get(n.nodeId))
            {
                throw new IllegalArgumentException("Duplicated");
            }
            nodeIds.set(n.nodeId);
        }
        HashMap<CellId, MutableInteger> subCellIds = new HashMap<CellId, MutableInteger>();
        ArrayList<NodeInst> newNodes = new ArrayList<NodeInst>();
        for (ImmutableNodeInst n : nodes)
        {
            if (n.protoId instanceof CellId)
            {
                CellId subCellId = (CellId)n.protoId;
                MutableInteger count = subCellIds.get(subCellId);
                if (count == null)
                {
                    count = new MutableInteger(0);
                    subCellIds.put(subCellId, count);
                }
                count.increment();
            }
            if (ImmutableNodeInst.isCellCenter(n.protoId))
            {
                if (alreadyCellCenter() || newCellCenterAnchor != null)
                {
                    System.out.println("Can only be one cell-center in " + this + ": new one ignored");
                    throw new IllegalArgumentException();
                }
                newCellCenterAnchor = n.anchor;
            }
            if (!names.add(n.name))
            {
                System.out.println(this + " already has NodeInst with name \"" + n.name + "\"");
                throw new IllegalArgumentException();
            }

            NodeInst ni = NodeInst.lowLevelNewInstance(getTopology(), n);
            newNodes.add(ni);

            assert ni.checkAndRepair(true, null, null) == 0;

            // check to see if this instantiation would create a circular library dependency
            if (ni.isCellInstance())
            {
                Cell instProto = (Cell)ni.getProto();
                if (instProto.getLibrary() != getLibrary())
                {
                    // a reference will be created, check it
                    Library.LibraryDependency libDep = getLibrary().addReferencedLib(instProto.getLibrary());
                    if (libDep != null)
                    {
                        // addition would create circular dependency
                        if (!allowCirDep)
                        {
                            System.out.println("ERROR: " + libDescribe() + " cannot instantiate "
                                + instProto.libDescribe() + " because it would create a circular library dependence: ");
                            System.out.println(libDep.toString());
                            throw new IllegalArgumentException();
                        }
                        System.out.println("WARNING: " + libDescribe() + " instantiates "
                            + instProto.libDescribe() + " which causes a circular library dependence: ");
                        System.out.println(libDep.toString());
                    }
                }
            }
        }
        for (CellId cellId : subCellIds.keySet())
        {
            Cell subCell = getDatabase().getCell(cellId);
            if (Cell.isInstantiationRecursive(subCell, this))
            {
                System.out.println("Cannot create instance of " + subCell + " in " + this
                    + " because it would be a recursive case");
                throw new IllegalArgumentException();
            }
            subCell.getTechnology();
        }

        Collections.sort(newNodes);
        Topology topology = getTopology();
        int newNumNodes = topology.getNumNodes() + newNodes.size();
        setTopologyModified();
        topology.addNodes(newNodes);
        for (NodeInst ni : newNodes)
        {
            Constraints.getCurrent().newObject(ni);
        }

        for (Map.Entry<CellId, MutableInteger> e : subCellIds.entrySet())
        {
            // count usage
            CellUsage u = getId().getUsageIn(e.getKey());
            if (cellUsages.length <= u.indexInParent)
            {
                int[] newCellUsages = new int[u.indexInParent + 1];
                System.arraycopy(cellUsages, 0, newCellUsages, 0, cellUsages.length);
                cellUsages = newCellUsages;
            }
            cellUsages[u.indexInParent] += e.getValue().intValue();
        }
        if (newCellCenterAnchor != null)
        {
            adjustReferencePoint(newCellCenterAnchor.getX(), newCellCenterAnchor.getY());
        }
        assert !cellContentsFresh && !cellBackupFresh && strongTopology == topology;
        assert topology.getNumNodes() == newNumNodes;
//        check();
    }

    /**
     * Method to add a new NodeInst to the Cell by ImmutableNodeInst.
     *
     * @param n ImmutableNodeInst of new NodeInst
     * @return the newly created NodeInst, or null on error.
     */
    public NodeInst addNode(ImmutableNodeInst n)
    {
        checkChanging();

        if (n.protoId instanceof CellId)
        {
            Cell subCell = getDatabase().getCell((CellId)n.protoId);
            if (Cell.isInstantiationRecursive(subCell, this))
            {
                System.out.println("Cannot create instance of " + subCell + " in " + this
                    + " because it would be a recursive case");
                return null;
            }
            subCell.getTechnology();
        }

        if (ImmutableNodeInst.isCellCenter(n.protoId) && alreadyCellCenter())
        {
            System.out.println("Can only be one cell-center in " + this + ": new one ignored");
            return null;
        }

        if (findNode(n.name.toString()) != null)
        {
            System.out.println(this + " already has NodeInst with name \"" + n.name + "\"");
            return null;
        }

        NodeInst ni = NodeInst.lowLevelNewInstance(getTopology(), n);

        assert ni.checkAndRepair(true, null, null) == 0;

        // check to see if this instantiation would create a circular library dependency
        if (ni.isCellInstance())
        {
            Cell instProto = (Cell)ni.getProto();
            if (instProto.getLibrary() != getLibrary())
            {
                // a reference will be created, check it
                Library.LibraryDependency libDep = getLibrary().addReferencedLib(instProto.getLibrary());
                if (libDep != null)
                {
                    // addition would create circular dependency
                    if (!allowCirDep)
                    {
                        System.out.println("ERROR: " + libDescribe() + " cannot instantiate "
                            + instProto.libDescribe() + " because it would create a circular library dependence: ");
                        System.out.println(libDep.toString());
                        return null;
                    }
                    System.out.println("WARNING: " + libDescribe() + " instantiates "
                        + instProto.libDescribe() + " which causes a circular library dependence: ");
                    System.out.println(libDep.toString());
                }
            }
        }

        setTopologyModified();
        int nodeId = getTopology().addNode(ni);

        // expand status and count usage
        if (ni.isCellInstance())
        {
            Cell subCell = (Cell)ni.getProto();
            expandedNodes.set(nodeId, subCell.isWantExpanded());
            expandStatusModified = true;

            CellUsage u = getId().getUsageIn(subCell.getId());
            if (cellUsages.length <= u.indexInParent)
            {
                int[] newCellUsages = new int[u.indexInParent + 1];
                System.arraycopy(cellUsages, 0, newCellUsages, 0, cellUsages.length);
                cellUsages = newCellUsages;
            }
            cellUsages[u.indexInParent]++;
        }

        // handle change control, constraint, and broadcast
        Constraints.getCurrent().newObject(ni);
        if (ImmutableNodeInst.isCellCenter(n.protoId))
        {
            adjustReferencePoint(n.anchor.getX(), n.anchor.getY());
        }

        return ni;
    }

    /**
     * Method to remove an NodeInst from the cell.
     *
     * @param ni the NodeInst to be removed from the cell.
     */
    private void removeNode(NodeInst ni)
    {
        checkChanging();
        assert ni.isLinked();
        getTopology().removeNode(ni);

//        essenBounds.remove(ni);
        // remove usage count
        if (ni.isCellInstance())
        {
            Cell subCell = (Cell)ni.getProto();
            CellUsage u = getId().getUsageIn(subCell.getId());
            cellUsages[u.indexInParent]--;
            if (cellUsages[u.indexInParent] <= 0)
            {
                assert cellUsages[u.indexInParent] == 0;
                // remove library dependency, if possible
                getLibrary().removeReferencedLib(((Cell)ni.getProto()).getLibrary());
            }
        }

        setTopologyModified();
//        removeNodeName(ni);
//        int nodeId = ni.getD().nodeId;
//        assert chronNodes.get(nodeId) == ni;
//        chronNodes.set(nodeId, null);
//        setDirty();
    }

//    /**
//     * Method to remove an NodeInst from the name index of this cell.
//     * @param ni the NodeInst to be removed from the cell.
//     */
//    public void removeNodeName(NodeInst ni) {
//        int nodeIndex = ni.getNodeIndex();
//        NodeInst removedNi = nodes.remove(nodeIndex);
//        assert removedNi == ni;
//        for (int i = nodeIndex; i < nodes.size(); i++) {
//            NodeInst n = nodes.get(i);
//            n.setNodeIndex(i);
//        }
//        ni.setNodeIndex(-1);
//    }
    /**
     * **************************** ARCS *****************************
     */
    /**
     * Method to return an Iterator over all ArcInst objects in this Cell.
     *
     * @return an Iterator over all ArcInst objects in this Cell.
     */
    public synchronized Iterator<ArcInst> getArcs()
    {
        return getTopology().getArcs();
    }

    /**
     * Method to return the number of ArcInst objects in this Cell.
     *
     * @return the number of ArcInst objects in this Cell.
     */
    public int getNumArcs()
    {
        Topology topology = getTopologyOptional();
        return topology != null ? topology.getNumArcs() : backup().cellRevision.arcs.size();
    }

    /**
     * Method to return the ArcInst at specified position.
     *
     * @param arcIndex specified position of ArcInst.
     * @return the ArcInst at specified position..
     */
    public final ArcInst getArc(int arcIndex)
    {
        return getTopology().getArc(arcIndex);
    }

    /**
     * Method to return the ArcInst by its chronological index.
     *
     * @param arcId chronological index of ArcInst.
     * @return the ArcInst with specified chronological index.
     */
    public ArcInst getArcById(int arcId)
    {
        return getTopology().getArcById(arcId);
    }

    /**
     * Method to find a named ArcInst on this Cell.
     *
     * @param name the name of the ArcInst.
     * @return the ArcInst. Returns null if none with that name are found.
     */
    public ArcInst findArc(String name)
    {
        return getTopology().findArc(name);
    }

    /**
     * Method to unlink a set of these ArcInsts from this Cell.
     *
     * @param killedArcs a set of ArcInsts to kill.
     */
    public void killArcs(Set<ArcInst> killedArcs)
    {
        if (killedArcs.isEmpty())
        {
            return;
        }
        for (ArcInst ai : killedArcs)
        {
            if (ai.getParent() != this)
            {
                throw new IllegalArgumentException("parent");
            }
        }
        for (int arcIndex = getNumArcs() - 1; arcIndex >= 0; arcIndex--)
        {
            ArcInst ai = getArc(arcIndex);
            if (killedArcs.contains(ai))
            {
                ai.kill();
            }
        }
    }

    /**
     * **************************** EXPORTS *****************************
     */
    /**
     * Add Exports to this Cell.
     *
     * @param exports the ImmutableExport to add to this Cell.
     */
    public void addExports(Collection<ImmutableExport> exports)
    {
        ImmutableExport[] a = exports.toArray(new ImmutableExport[exports.size()]);
        Arrays.sort(a, ImmutableExport.NAME_ORDER);
        for (ImmutableExport e : a)
        {
            addExport(e);
        }
    }

    /**
     * Add an Export to this Cell.
     *
     * @param e the ImmutableExport to add to this Cell.
     */
    public Export addExport(ImmutableExport e)
    {
        if (e.exportId.parentId != getId() || getExportChron(e.exportId.chronIndex) != null
            || e.nameDescriptor == null
            || getPortInst(e.originalNodeId, e.originalPortId) == null)
        {
            throw new IllegalArgumentException();
        }
        checkChanging();
        setContentsModified();
        Export export = new Export(e, this);

        int portIndex = -searchExport(export.getName()) - 1;
        if (portIndex < 0)
        {
            throw new IllegalArgumentException("Duplicate Export name " + export.getName());
        }
        assert portIndex >= 0;
        export.setPortIndex(portIndex);

        // Add to chronExprots
        int chronIndex = export.getId().getChronIndex();
        if (chronExports.length <= chronIndex)
        {
            Export[] newChronExports = new Export[Math.max(chronIndex + 1, chronExports.length * 2)];
            System.arraycopy(chronExports, 0, newChronExports, 0, chronExports.length);
            chronExports = newChronExports;
        }
        chronExports[chronIndex] = export;

        Export[] newExports = new Export[exports.length + 1];
        System.arraycopy(exports, 0, newExports, 0, portIndex);
        newExports[portIndex] = export;
        for (int i = portIndex; i < exports.length; i++)
        {
            Export ex = exports[i];
            ex.setPortIndex(i + 1);
            newExports[i + 1] = ex;
        }
        exports = newExports;

        // create a PortInst for every instance of this Cell
        if (getId().numUsagesOf() != 0)
        {
            int[] pattern = new int[exports.length];
            for (int i = 0; i < portIndex; i++)
            {
                pattern[i] = i;
            }
            pattern[portIndex] = -1;
            for (int i = portIndex + 1; i < exports.length; i++)
            {
                pattern[i] = i - 1;
            }
            updatePortInsts(pattern);
        }
        // handle change control, constraint, and broadcast
        export.getOriginalPort().getNodeInst().redoGeometric();
        Constraints.getCurrent().newObject(export);
        return export;
    }

    /**
     * Removes an Export from this Cell.
     *
     * @param export the Export to remove from this Cell.
     */
    void removeExport(Export export)
    {
        checkChanging();
        setContentsModified();
        int portIndex = export.getPortIndex();

        Export[] newExports = exports.length > 1 ? new Export[exports.length - 1] : NULL_EXPORT_ARRAY;
        System.arraycopy(exports, 0, newExports, 0, portIndex);
        for (int i = portIndex; i < newExports.length; i++)
        {
            Export e = exports[i + 1];
            e.setPortIndex(i);
            newExports[i] = e;
        }
        exports = newExports;
        chronExports[export.getId().getChronIndex()] = null;
        export.setPortIndex(-1);

        // remove the PortInst from every instance of this Cell
        if (getId().numUsagesOf() == 0)
        {
            return;
        }
        int[] pattern = new int[exports.length];
        for (int i = 0; i < portIndex; i++)
        {
            pattern[i] = i;
        }
        for (int i = portIndex; i < exports.length; i++)
        {
            pattern[i] = i + 1;
        }
        updatePortInsts(pattern);
//		for(Iterator<NodeInst> it = getInstancesOf(); it.hasNext(); )
//		{
//			NodeInst ni = it.next();
//			ni.removePortInst(export);
//		}
    }

    /**
     * Move renamed Export in sorted exports array.
     *
     * @param oldPortIndex old position of the Export in exports array.
     */
    void moveExport(int oldPortIndex, String newName)
    {
        Export export = exports[oldPortIndex];
        int newPortIndex = -searchExport(newName) - 1;
        if (newPortIndex < 0)
        {
            return;
        }
        if (newPortIndex > oldPortIndex)
        {
            newPortIndex--;
        }
        if (newPortIndex == oldPortIndex)
        {
            return;
        }

        if (newPortIndex > oldPortIndex)
        {
            for (int i = oldPortIndex; i < newPortIndex; i++)
            {
                Export e = exports[i + 1];
                e.setPortIndex(i);
                exports[i] = e;
            }
        } else
        {
            for (int i = oldPortIndex; i > newPortIndex; i--)
            {
                Export e = exports[i - 1];
                e.setPortIndex(i);
                exports[i] = e;
            }
        }
        export.setPortIndex(newPortIndex);
        exports[newPortIndex] = export;

        // move PortInst for every instance of this Cell.
        if (getId().numUsagesOf() == 0)
        {
            return;
        }
        int[] pattern = new int[exports.length];
        for (int i = 0; i < pattern.length; i++)
        {
            pattern[i] = i;
        }
        pattern[newPortIndex] = oldPortIndex;
        if (newPortIndex > oldPortIndex)
        {
            for (int i = oldPortIndex; i < newPortIndex; i++)
            {
                pattern[i] = i + 1;
            }
        } else
        {
            for (int i = oldPortIndex; i > newPortIndex; i--)
            {
                pattern[i] = i - 1;
            }
        }
        updatePortInsts(pattern);
    }

    /**
     * Update PortInsts of all instances of this Cell according to pattern.
     * Pattern contains an element for each Export.
     * If Export was just created, the element contains -1.
     * For old Exports the element contains old index of the Export.
     *
     * @param pattern array with elements describing new PortInsts.
     */
    public void updatePortInsts(int[] pattern)
    {
        for (Iterator<CellUsage> it = getUsagesOf(); it.hasNext();)
        {
            CellUsage cu = it.next();
            Cell parentCell = cu.getParent(database);
            Topology topology = parentCell.getTopologyOptional();
            if (topology != null)
            {
                topology.updatePortInsts(this, pattern);
            }
        }
    }

    /**
     * Method to unlink a set of these Export from this Cell.
     *
     * @param killedExports a set of Exports to kill.
     */
    public void killExports(Set<Export> killedExports)
    {
        checkChanging();
        if (killedExports.isEmpty())
        {
            return;
        }
        for (Export export : killedExports)
        {
            if (export.getParent() != this)
            {
                throw new IllegalArgumentException("parent");
            }
        }

        Export[] killedExportsArray = killedExports.toArray(new Export[killedExports.size()]);
        for (Iterator<CellUsage> uit = getUsagesOf(); uit.hasNext();)
        {
            CellUsage u = uit.next();
            Cell higherCell = database.getCell(u.parentId);

            // collect the arcs attached to the connections to these port instance.
            List<ArcInst> arcsToKill = new ArrayList<ArcInst>();
            for (Iterator<ArcInst> ait = higherCell.getArcs(); ait.hasNext();)
            {
                ArcInst ai = ait.next();
                PortInst tail = ai.getTailPortInst();
                PortInst head = ai.getHeadPortInst();
                if (tail.getNodeInst().getProto() == this && killedExports.contains(tail.getPortProto())
                    || head.getNodeInst().getProto() == this && killedExports.contains(head.getPortProto()))
                {
                    arcsToKill.add(ai);
                }
            }
            // collect re-exports
            Set<Export> higherExportsToKill = null;
            for (Export higherExport : higherCell.exports)
            {
                PortInst pi = higherExport.getOriginalPort();
                if (pi.getNodeInst().getProto() != this)
                {
                    continue;
                }
                Export lowerExport = (Export)pi.getPortProto();
                assert lowerExport.getParent() == this;
                if (!killedExports.contains(lowerExport))
                {
                    continue;
                }
                if (higherExportsToKill == null)
                {
                    higherExportsToKill = new HashSet<Export>();
                }
                higherExportsToKill.add(higherExport);
            }

            // delete variables on port instances
            for (Iterator<NodeInst> it = higherCell.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (ni.getProto() != this)
                {
                    continue;
                }
                for (Export e : killedExportsArray)
                {
                    ni.findPortInstFromProto(e).delVars();
                }
            }
            // delete connected arcs
            for (ArcInst ai : arcsToKill)
            {
                ai.kill();
            }
            // recurse up the hierarchy deleting re-exports
            if (higherExportsToKill != null)
            {
                higherCell.killExports(higherExportsToKill);
            }
        }

        // kill exports themselves
        for (Export e : killedExports)
        {
            assert e.isLinked();
            NodeInst originalNode = e.getOriginalPort().getNodeInst();
            originalNode.redoGeometric();
            removeExport(e);
            // handle change control, constraint, and broadcast
            Constraints.getCurrent().killObject(e);
        }
    }

    /**
     * Method to recursively alter the state bit fields of these Exports.
     *
     * @param changedExports changed exports of this Cell.
     */
    void recursivelyChangeAllPorts(Set<Export> changedExports)
    {
        // look at all usages of this cell
        for (Iterator<CellUsage> cit = getUsagesOf(); cit.hasNext();)
        {
            CellUsage u = cit.next();
            Cell higherCell = database.getCell(u.parentId);
            Set<Export> changedHigherExports = null;
            // see re-exports of these ports
            for (Export higherExport : higherCell.exports)
            {
                PortInst pi = higherExport.getOriginalPort();
                if (pi.getNodeInst().getProto() != this)
                {
                    continue;
                }
                Export lowerExport = (Export)pi.getPortProto();
                assert lowerExport.getParent() == this;
                if (!changedExports.contains(lowerExport))
                {
                    continue;
                }
                if (changedHigherExports == null)
                {
                    changedHigherExports = new HashSet<Export>();
                }
                changedHigherExports.add(higherExport);
                // change this port
                higherExport.copyStateBits(lowerExport);
            }
            // recurse up the hierarchy
            if (changedHigherExports != null)
            {
                higherCell.recursivelyChangeAllPorts(changedHigherExports);
            }
        }
    }

    /**
     * Method to find the PortProto that has a particular name.
     *
     * @return the PortProto, or null if there is no PortProto with that name.
     */
    @Override
    public PortProto findPortProto(String name)
    {
        if (name == null)
        {
            return null;
        }
        return findPortProto(Name.findName(name));
    }

    /**
     * Method to find the PortProto that has a particular Name.
     *
     * @return the PortProto, or null if there is no PortProto with that name.
     */
    @Override
    public PortProto findPortProto(Name name)
    {
        if (name == null)
        {
            return null;
        }
        int portIndex = searchExport(name.toString());
        if (portIndex >= 0)
        {
            return exports[portIndex];
        }
        return null;
    }

    /**
     * Method to return an iterator over all PortProtos of this NodeProto.
     *
     * @return an iterator over all PortProtos of this NodeProto.
     */
    @Override
    public Iterator<PortProto> getPorts()
    {
        return ArrayIterator.iterator((PortProto[])exports);
    }

    /**
     * Method to return an iterator over all Exports of this NodeProto.
     *
     * @return an iterator over all Exports of this NodeProto.
     */
    public Iterator<Export> getExports()
    {
        return ArrayIterator.iterator(exports);
    }

    /**
     * Method to return the number of PortProtos on this NodeProto.
     *
     * @return the number of PortProtos on this NodeProto.
     */
    @Override
    public int getNumPorts()
    {
        return exports.length;
    }

    /**
     * Method to return the PortProto at specified position.
     *
     * @param portIndex specified position of PortProto.
     * @return the PortProto at specified position..
     */
    @Override
    public Export getPort(int portIndex)
    {
        return exports[portIndex];
    }

    /**
     * Method to return the PortProto by thread-independent PortProtoId.
     *
     * @param portProtoId thread-independent PortProtoId.
     * @return the PortProto.
     */
    @Override
    public Export getPort(PortProtoId portProtoId)
    {
        if (portProtoId.getParentId() != getId())
        {
            throw new IllegalArgumentException();
        }
        return getExportChron(portProtoId.getChronIndex());
    }

    /**
     * Method to return the Export at specified chronological index.
     *
     * @param chronIndex specified chronological index of Export.
     * @return the Export at specified chronological index or null.
     */
    public Export getExportChron(int chronIndex)
    {
        return chronIndex < chronExports.length ? chronExports[chronIndex] : null;
    }

    /**
     * Method to find a named Export on this Cell.
     *
     * @param name the name of the export.
     * @return the export. Returns null if that name was not found.
     */
    public Export findExport(String name)
    {
        return (Export)findPortProto(name);
    }

    /**
     * Method to find a named Export on this Cell.
     *
     * @param name the Name of the export.
     * @return the export. Returns null if that name was not found.
     */
    public Export findExport(Name name)
    {
        return (Export)findPortProto(name);
    }

    /**
     * Searches the exports for the specified name using the binary
     * search algorithm.
     *
     * @param name the name to be searched.
     * @return index of the search name, if it is contained in the exports;
     * otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The
     * <i>insertion point</i> is defined as the point at which the
     * Export would be inserted into the list: the index of the first
     * element greater than the name, or <tt>exports.length()</tt>, if all
     * elements in the list are less than the specified name. Note
     * that this guarantees that the return value will be &gt;= 0 if
     * and only if the Export is found.
     */
    private int searchExport(String name)
    {
        int low = 0;
        int high = exports.length - 1;

        while (low <= high)
        {
            int mid = (low + high) >> 1;
            Export e = exports[mid];
            int cmp = TextUtils.STRING_NUMBER_ORDER.compare(e.getName(), name);

            if (cmp < 0)
            {
                low = mid + 1;
            } else if (cmp > 0)
            {
                high = mid - 1;
            } else
            {
                return mid; // Export found
            }
        }
        return -(low + 1);  // Export not found.
    }

    /**
     * **************************** TEXT *****************************
     */
    /**
     * Method to return the CellName object describing this Cell.
     *
     * @return the CellName object describing this Cell.
     */
    public CellName getCellName()
    {
        return getD().cellId.cellName;
    }

    /**
     * Method to return the pure name of this Cell, without
     * any view or version information.
     *
     * @return the pure name of this Cell.
     */
    @Override
    public String getName()
    {
        return getCellName().getName();
    }

    /**
     * Method to describe this cell.
     * The description has the form: cell;version{view}
     * If the cell is not from the current library, prepend the library name.
     *
     * @param withQuotes to wrap description between quotes
     * @return a String that describes this cell.
     */
    @Override
    public String describe(boolean withQuotes)
    {
        String name = "";
        if (lib != Library.getCurrent())
        {
            name += lib.getName() + ":";
        }
        name += noLibDescribe();
        return (withQuotes) ? "'" + name + "'" : name;
    }

    /**
     * Method to describe this cell.
     * The description has the form: Library:cell;version{view}
     *
     * @return a String that describes this cell.
     */
    @Override
    public String libDescribe()
    {
        return (lib.getName() + ":" + noLibDescribe());
    }

    /**
     * Method to describe this cell.
     * The description has the form: cell;version{view}
     * Unlike "describe()", this method never prepends the library name.
     *
     * @return a String that describes this cell.
     */
    @Override
    public String noLibDescribe()
    {
        String name = getName();
        if (getNewestVersion() != this)
        {
            name += ";" + getVersion();
        }
        name += getView().getAbbreviationExtension();
        return name;
    }

    /**
     * Method to find the NodeProto with the given name.
     * This can be a PrimitiveNode (and can be prefixed by a Technology name),
     * or it can be a Cell (and be prefixed by a Library name).
     *
     * @param line the name of the NodeProto.
     * @return the specified NodeProto, or null if none can be found.
     */
    public static NodeProto findNodeProto(String line)
    {
        Technology tech = Technology.getCurrent();
        Library lib = Library.getCurrent();
        boolean saidtech = false;
        boolean saidlib = false;
        int colon = line.indexOf(':');
        String withoutPrefix;
        if (colon == -1)
        {
            withoutPrefix = line;
        } else
        {
            String prefix = line.substring(0, colon);
            Technology t = Technology.findTechnology(prefix);
            if (t != null)
            {
                tech = t;
                saidtech = true;
            }
            Library l = Library.findLibrary(prefix);
            if (l != null)
            {
                lib = l;
                saidlib = true;
            }
            withoutPrefix = line.substring(colon + 1);
        }

        /* try primitives in the technology */
        if (!saidlib)
        {
            PrimitiveNode np = tech.findNodeProto(withoutPrefix);
            if (np != null)
            {
                return np;
            }
        }

        if (!saidtech && lib != null)
        {
            Cell np = lib.findNodeProto(withoutPrefix);
            if (np != null)
            {
                return np;
            }
        }
        return null;
    }

    /**
     * Method to get the strings in this Cell.
     * It is only valid for cells with "text" views (documentation, vhdl, netlist, etc.)
     *
     * @return the strings in this Cell.
     * Returns null if there are no strings.
     */
    public String[] getTextViewContents()
    {
        // look on the cell for its text
        Variable var = getVar(Cell.CELL_TEXT_KEY);
        if (var == null)
        {
            return null;
        }
        Object obj = var.getObject();
        if (!(obj instanceof String[]))
        {
            return null;
        }
        return (String[])obj;
    }

    /**
     * Method to set the strings in this Cell.
     * It is only valid for cells with "text" views (documentation, vhdl, netlist, etc.)
     * The call needs to be wrapped inside of a Job.
     *
     * @param strings an array of Strings that define this Cell.
     * @param ep EditingPreferences with default TextDescriptors
     */
    public void setTextViewContents(String[] strings, EditingPreferences ep)
    {
        checkChanging();
        newVar(Cell.CELL_TEXT_KEY, strings, ep);
    }

    /**
     * Method to return the Variable on this Cell with the given key
     * that is a parameter. Returns null if not found.
     *
     * @param key the key of the variable
     * @return the Variable with that key, that is parameter. Returns null if none found.
     */
    public Variable getParameter(Variable.Key key)
    {
        return key instanceof Variable.AttrKey ? getD().getParameter((Variable.AttrKey)key) : null;
    }

    /**
     * Method to return an Iterator over all Variables marked as parameters on this Cell.
     *
     * @return an Iterator over all Variables on this Cell.
     */
    public Iterator<Variable> getParameters()
    {
        return getD().getParameters();
    }

    /**
     * Tells if this Cell has parameters.
     *
     * @return true if this Cell has parameters.
     */
    public boolean hasParameters()
    {
        return getNumParameters() > 0;
    }

    /**
     * Method to return the number of Parameters on this Cell.
     *
     * @return the number of Parameters on this ImmutableCell.
     */
    public int getNumParameters()
    {
        return getD().getNumParameters();
    }

    /**
     * Method to return the Parameter by its paramIndex.
     *
     * @param paramIndex index of Parameter.
     * @return the Parameter with given paramIndex.
     * @throws ArrayIndexOutOfBoundesException if paramIndex out of bounds.
     */
    public Variable getParameter(int paramIndex)
    {
        return getD().getParameter(paramIndex);
    }

    /**
     * Method to return true if the Variable on this ElectricObject with given key is a parameter.
     * Parameters are those Variables that have values on instances which are
     * passed down the hierarchy into the contents.
     * Parameters can only exist on NodeInst objects.
     *
     * @param varKey key to test
     * @return true if the Variable with given key is a parameter.
     */
    @Override
    public boolean isParam(Variable.Key varKey)
    {
        return varKey instanceof Variable.AttrKey && getD().getParameter((Variable.AttrKey)varKey) != null;
    }

    private void addParam(Variable var)
    {
        assert var.getTextDescriptor().isParam() && var.isInherit();
        if (isIcon())
        {
            // Remove variables with the same name as new parameter
            for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (!ni.isParam(var.getKey()))
                {
                    ni.delVar(var.getKey());
                }
            }
        }
        setD(getD().withoutVariable(var.getKey()).withParam(var));
        if (isIcon())
        {
            // Update units on instance parameters
            for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
            {
                NodeInst ni = it.next();
                Variable instParam = ni.getParameter(var.getKey());
                if (instParam != null)
                {
                    ni.setTextDescriptor(var.getKey(), instParam.getTextDescriptor().withUnit(var.getUnit()));
                }
            }
        }
    }

    private void delParam(Variable.AttrKey key)
    {
        assert isParam(key);
        if (isIcon())
        {
            // Remove instance parameters
            for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
            {
                NodeInst ni = it.next();
                ni.delParameter(key);
            }
        }
        setD(getD().withoutParam(key));
    }

    private void renameParam(Variable.AttrKey key, Variable.AttrKey newName)
    {
        assert isParam(key);
        Variable oldParam = getParameter(key);
        // create new var
        addParam(Variable.newInstance(newName, oldParam.getObject(), oldParam.getTextDescriptor()));
        if (isIcon())
        {
            // Rename instance parameters
            for (Iterator<NodeInst> it = getInstancesOf(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (!ni.isDefinedParameter(key))
                {
                    continue;
                }
                Variable param = ni.getParameter(key);
                ni.addParameter(Variable.newInstance(newName, param.getObject(), param.getTextDescriptor()));
                ni.delParameter(key);
            }
        }
        delParam(key);
    }

    private void setParams(Cell paramOwner)
    {
        for (Iterator<Variable> it = getParameters(); it.hasNext();)
        {
            delParam((Variable.AttrKey)it.next().getKey());
        }
        for (Iterator<Variable> it = paramOwner.getParameters(); it.hasNext();)
        {
            Variable param = it.next();
            addParam(param);
        }
    }

    /**
     * Method to return a list of Polys that describes all text on this Cell.
     *
     * @param hardToSelect is true if considering hard-to-select text.
     * @param wnd the window in which the text will be drawn.
     * @return an array of Polys that describes the text.
     */
    public Poly[] getAllText(boolean hardToSelect, EditWindow0 wnd)
    {
        return getDisplayableVariables(CENTERRECT, wnd, false, false);
    }

    /**
     * Method to return the bounds of all relative text in this Cell.
     * This is used when displaying "full screen" because the text may grow to
     * be larger than the actual cell contents.
     * Only relative (scalable) text is considered, since it is not possible
     * to change the size of absolute text.
     *
     * @param wnd the EditWindow0 in which this Cell is being displayed.
     * @return the bounds of the relative (scalable) text.
     */
    public Rectangle2D getRelativeTextBounds(EditWindow0 wnd)
    {
        Rectangle2D bounds = null;
        for (Iterator<NodeInst> it = this.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            bounds = accumulateTextBoundsOnObject(ni, bounds, wnd);
            for (Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext();)
            {
                PortInst pi = pIt.next();
                bounds = accumulateTextBoundsOnObject(pi, bounds, wnd);
            }
        }
        for (Iterator<ArcInst> it = this.getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();
            bounds = accumulateTextBoundsOnObject(ai, bounds, wnd);
        }
        for (Iterator<Export> it = this.getExports(); it.hasNext();)
        {
            Export pp = it.next();
            bounds = accumulateTextBoundsOnObject(pp, bounds, wnd);
        }
        bounds = accumulateTextBoundsOnObject(this, bounds, wnd);
        return bounds;
    }

    private Rectangle2D accumulateTextBoundsOnObject(ElectricObject eObj, Rectangle2D bounds, EditWindow0 wnd)
    {
        Rectangle2D objBounds = eObj.getTextBounds(wnd);
        if (objBounds == null)
        {
            return bounds;
        }
        if (bounds == null)
        {
            return objBounds;
        }
        Rectangle2D.union(bounds, objBounds, bounds);
        return bounds;
    }

    /**
     * Method to return the basename for autonaming instances of this Cell.
     *
     * @return the basename for autonaming instances of this Cell.
     */
    public Name getBasename()
    {
        return getCellName().getBasename();
    }

    /**
     * Method to determine the index value which, when appended to a given string,
     * will generate a unique name in this Cell.
     *
     * @param prefix the start of the string.
     * @param suffix the end of the string.
     * @param cls the type of object being examined.
     * @param startingIndex the starting value to append to the string.
     * @return a value that, when appended to the prefix, forms a unique name in the cell.
     */
    public int getUniqueNameIndex(String prefix, String suffix, Class<?> cls, int startingIndex)
    {
        int prefixLen = prefix.length();
        int suffixLen = suffix.length();
        int uniqueIndex = startingIndex;
        if (cls == Export.class)
        {
            for (Iterator<Export> it = getExports(); it.hasNext();)
            {
                Export pp = it.next();
                String name = pp.getName();
                if (name.startsWith(prefix) && name.endsWith(suffix)) //				if (TextUtils.startsWithIgnoreCase(pp.getName(), prefix))
                {
                    String restOfName = name.substring(prefixLen, name.length() - suffixLen);
                    if (TextUtils.isANumber(restOfName))
                    {
                        int indexVal = TextUtils.atoi(restOfName);
                        if (indexVal >= uniqueIndex)
                        {
                            uniqueIndex = indexVal + 1;
                        }
                    }
                }
            }
        } else if (cls == NodeInst.class)
        {
            for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                String name = ni.getName();
                if (name.startsWith(prefix) && name.endsWith(suffix)) //				if (TextUtils.startsWithIgnoreCase(ni.getName(), prefix))
                {
                    String restOfName = name.substring(prefixLen, name.length() - suffixLen);
                    if (TextUtils.isANumber(restOfName))
                    {
                        int indexVal = TextUtils.atoi(restOfName);
                        if (indexVal >= uniqueIndex)
                        {
                            uniqueIndex = indexVal + 1;
                        }
                    }
                }
            }
        } else if (cls == ArcInst.class)
        {
            for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
            {
                ArcInst ai = it.next();
                String name = ai.getName();
                if (name.startsWith(prefix) && name.endsWith(suffix)) //				if (TextUtils.startsWithIgnoreCase(ai.getName(), prefix))
                {
                    String restOfName = name.substring(prefixLen, name.length() - suffixLen);
                    if (TextUtils.isANumber(restOfName))
                    {
                        int indexVal = TextUtils.atoi(restOfName);
                        if (indexVal >= uniqueIndex)
                        {
                            uniqueIndex = indexVal + 1;
                        }
                    }
                }
            }
        }
        return uniqueIndex;
    }

    /**
     * Method to determine whether a name is unique in this Cell.
     *
     * @param name the Name being tested to see if it is unique.
     * @param cls the type of object being examined.
     * The only classes that can be examined are PortProto, NodeInst, and ArcInst.
     * @param exclude an object that should not be considered in this test (null to ignore the exclusion).
     * @return true if the name is unique in the Cell. False if it already exists.
     */
    public boolean isUniqueName(String name, Class<?> cls, ElectricObject exclude)
    {
        return isUniqueName(Name.findName(name), cls, exclude);
    }

    /**
     * Method to determine whether a name is unique in this Cell.
     *
     * @param name the Name being tested to see if it is unique.
     * @param cls the type of object being examined.
     * The only classes that can be examined are PortProto, NodeInst, and ArcInst.
     * @param exclude an object that should not be considered in this test (null to ignore the exclusion).
     * @return true if the name is unique in the Cell. False if it already exists.
     */
    public boolean isUniqueName(Name name, Class<?> cls, ElectricObject exclude)
    {
        if (cls == Export.class)
        {
            Export e = findExport(name);
            return (e == null || exclude == e);
        }
        if (cls == NodeInst.class)
        {
            NodeInst ni = findNode(name.toString());
            return (ni == null || exclude == ni);
        }
        if (cls == ArcInst.class)
        {
            ArcInst ai = findArc(name.toString());
            return ai == null || exclude == ai;
        }
        return true;
    }

    /**
     * Method to determine whether a variable key on Cell is deprecated.
     * Deprecated variable keys are those that were used in old versions of Electric,
     * but are no longer valid.
     *
     * @param key the key of the variable.
     * @return true if the variable key is deprecated.
     */
    @Override
    public boolean isDeprecatedVariable(Variable.Key key)
    {
        String name = key.getName();
        if (name.equals("NET_last_good_ncc")
            || name.equals("NET_last_good_ncc_facet")
            || name.equals("SIM_window_signal_order")
            || name.equals("SIM_window_signalorder"))
        {
            return true;
        }
        return super.isDeprecatedVariable(key);
    }

    /**
     * Method to compute the location of a new Variable on this Cell.
     *
     * @return the offset of the new Variable.
     */
    public Point2D newVarOffset()
    {
        // find non-conflicting location of this cell attribute
        int numVars = 0;
        double xPosSum = 0;
        double yPosBot = 0;
        double tallest = 0;
        for (Iterator<Variable> it = getParametersAndVariables(); it.hasNext();)
        {
            Variable eVar = it.next();
            if (!eVar.isDisplay())
            {
                continue;
            }
            xPosSum += eVar.getXOff();
            if (eVar.getYOff() < yPosBot)
            {
                yPosBot = eVar.getYOff();
            }
            if (!eVar.getSize().isAbsolute())
            {
                tallest = Math.max(tallest, eVar.getSize().getSize());
            }
            numVars++;
        }
        if (numVars == 0)
        {
            return new Point2D.Double(0, 0);
        }
        if (tallest == 0)
        {
            tallest = 1;
        }
        return new Point2D.Double(xPosSum / numVars, yPosBot - tallest);
    }

    /**
     * Returns a printable version of this Cell.
     *
     * @return a printable version of this Cell.
     */
    @Override
    public String toString()
    {
        return "cell " + describe(true);
    }

    /**
     * **************************** HIERARCHY *****************************
     */
    /**
     * Returns persistent data of this Cell.
     *
     * @return persistent data of this Cell.
     */
    @Override
    public ImmutableCell getD()
    {
        return d;
    }

    /**
     * Modifies persistent data of this Cell.
     *
     * @param newD new persistent data.
     */
    private void setD(ImmutableCell newD)
    {
        assert isLinked();
        checkChanging();
        ImmutableCell oldD = getD();
        if (newD == oldD)
        {
            return;
        }
        d = newD;
        unfreshBackup();
        Constraints.getCurrent().modifyCell(this, oldD);
    }

    /**
     * Method to add a Variable on this Cell.
     * It may add repaired copy of this Variable in some cases.
     *
     * @param var Variable to add.
     */
    @Override
    public void addVar(Variable var)
    {
        if (var.getTextDescriptor().isParam() || isParam(var.getKey()))
        {
            throw new IllegalArgumentException("Parameters should be added by CellGroup.addParam");
        }
        setD(getD().withVariable(var));
    }

    /**
     * Method to delete a Variable from this Cell.
     *
     * @param key the key of the Variable to delete.
     */
    @Override
    public void delVar(Variable.Key key)
    {
        if (isParam(key))
        {
            throw new IllegalArgumentException("Parameters should be deleted by CellGroup.delParam");
        }
        setD(getD().withoutVariable(key));
    }

    /**
     * Method to return the Parameter or Variable on this Cell with a given key.
     *
     * @param key the key of the Parameter or Variable.
     * @return the Parameter or Variable with that key, or null if there is no such Parameter or Variable Variable.
     * @throws NullPointerException if key is null
     */
    @Override
    public Variable getParameterOrVariable(Variable.Key key)
    {
        checkExamine();
        if (key instanceof Variable.AttrKey)
        {
            Variable param = getParameter(key);
            if (param != null)
            {
                return param;
            }
        }
        return getVar(key);
    }

    /**
     * Method to return an Iterator over all Parameters and Variables on this Cell.
     *
     * @return an Iterator over all Parameters and Variables on this Cell.
     */
    @Override
    public Iterator<Variable> getParametersAndVariables()
    {
        if (getNumParameters() == 0)
        {
            return getVariables();
        }
        List<Variable> allVars = new ArrayList<Variable>();
        for (Iterator<Variable> it = getParameters(); it.hasNext();)
        {
            allVars.add(it.next());
        }
        for (Iterator<Variable> it = getVariables(); it.hasNext();)
        {
            allVars.add(it.next());
        }
        return allVars.iterator();
    }

    /**
     * Updates the TextDescriptor on this Cell selected by varKey.
     * The varKey may be a key of parameter or variable on this Cell.
     * If varKey doesn't select any text descriptor, no action is performed.
     * The TextDescriptor gives information for displaying the Variable.
     *
     * @param varKey key of variable or special key.
     * @param td new value TextDescriptor
     */
    @Override
    public void setTextDescriptor(Variable.Key varKey, TextDescriptor td)
    {
        Variable param = getParameter(varKey);
        if (param != null)
        {
            td = td.withParam(true).withInherit(true).withUnit(param.getUnit());
            addParam(param.withTextDescriptor(td));
            return;
        }
        Variable var = getVar(varKey);
        if (var != null)
        {
            addVar(var.withTextDescriptor(td.withParam(false)));
        }
    }

    /**
     * Method to return NodeProtoId of this NodeProto.
     * NodeProtoId identifies NodeProto independently of threads.
     *
     * @return NodeProtoId of this NodeProto.
     */
    @Override
    public CellId getId()
    {
        return d.cellId;
    }

    /**
     * Returns a Cell by CellId.
     * Returns null if the Cell is not linked to the database.
     *
     * @param cellId CellId to find.
     * @return Cell or null.
     */
    public static Cell inCurrentThread(CellId cellId)
    {
        return EDatabase.currentDatabase().getCell(cellId);
    }

    /**
     * Method to return an iterator over all usages of this NodeProto.
     *
     * @return an iterator over all usages of this NodeProto.
     */
    public Iterator<CellUsage> getUsagesOf()
    {
        return new Iterator<CellUsage>()
        {

            int i;
            CellUsage nextU = findNext();

            @Override
            public boolean hasNext()
            {
                return nextU != null;
            }

            @Override
            public CellUsage next()
            {
                if (nextU == null)
                {
                    throw new NoSuchElementException();
                }
                CellUsage u = nextU;
                nextU = findNext();
                return u;
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            ;

            private CellUsage findNext()
            {
                CellId cellId = getId();
                while (i < cellId.numUsagesOf())
                {
                    CellUsage u = cellId.getUsageOf(i++);
                    Cell parent = u.getParent(database);
                    if (parent == null)
                    {
                        continue;
                    }
                    if (u.indexInParent >= parent.cellUsages.length)
                    {
                        continue;
                    }
                    if (parent.cellUsages[u.indexInParent] > 0)
                    {
                        return u;
                    }
                }
                return null;
            }
        };
    }

    /**
     * Method to return an iterator over all instances of this NodeProto.
     *
     * @return an iterator over all instances of this NodeProto.
     */
    public Iterator<NodeInst> getInstancesOf()
    {
        return new NodeInstsIterator();
    }

    private class NodeInstsIterator implements Iterator<NodeInst>
    {

        private Iterator<CellUsage> uit;
        private Cell cell;
        private int i, n;
        private NodeInst ni;

        NodeInstsIterator()
        {
            uit = getUsagesOf();
            findNext();
        }

        @Override
        public boolean hasNext()
        {
            return ni != null;
        }

        @Override
        public NodeInst next()
        {
            NodeInst ni = this.ni;
            if (ni == null)
            {
                throw new NoSuchElementException();
            }
            findNext();
            return ni;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("NodeInstsIterator.remove()");
        }

        ;

        private void findNext()
        {
            for (;;)
            {
                while (i < n)
                {
                    ni = cell.getNode(i++);
                    if (ni.getProto() == Cell.this)
                    {
                        return;
                    }
                }
                if (!uit.hasNext())
                {
                    ni = null;
                    return;
                }
                CellUsage u = uit.next();
                cell = u.getParent(database);
                if (cell == null)
                {
                    continue;
                }
                i = 0;
                n = cell.getNumNodes();
            }
        }
    }

    /**
     * Determines whether an instantiation of cell <code>toInstantiate</code>
     * into <code>parent</code> would be a recursive operation.
     *
     * @param toInstantiate the cell to instantiate
     * @param parent the cell in which to create the instance
     * @return true if the operation would be recursive, false otherwise
     */
    public static boolean isInstantiationRecursive(Cell toInstantiate, Cell parent)
    {
        // if they are equal, this is recursive
        if (toInstantiate == parent)
        {
            return true;
        }
        // Icons shouldn't contain cell instances
        if (!CellRevision.ALLOW_SUBCELLS_IN_ICON && parent.isIcon())
        {
            return true;
        }

        // special case: allow instance of icon inside of the contents for illustration
        if (toInstantiate.isIconOf(parent))
        {
            assert toInstantiate.isIcon();
            assert parent.isSchematic();
            return false;
        }

        // if the parent is a child of the cell to instantiate, that would be a
        // recursive operation
        if (parent.isAChildOf(toInstantiate))
        {
            return true;
        }

        return false;
    }

    /**
     * Method to determine whether this Cell is a child of a given parent Cell.
     * DO NOT use this method to determine whether an instantiation should be allowed
     * (i.e. it is not a recursive instantiation). Use <code>isInstantiationRecursive</code>
     * instead. This method *only* does what is it says it does: it checks if this cell
     * is currently instantiated as a child of 'parent' cell.
     *
     * @param parent the parent cell being examined.
     * @return true if, somewhere above the hierarchy of this Cell is the parent Cell.
     */
    public boolean isAChildOf(Cell parent)
    {
        return getIsAChildOf(parent, new HashMap<Cell, Cell>());
    }

    private boolean getIsAChildOf(Cell parent, Map<Cell, Cell> checkedParents)
    {
        // if parent is an icon view, also check contents view
        if (parent.isIcon())
        {
            Cell c = parent.contentsView();
            if (c != null && c != parent)
            {
                if (getIsAChildOf(c, checkedParents))
                {
                    return true;
                }
            }
        }

        // see if parent checked already
        if (checkedParents.get(parent) != null)
        {
            return false;
        }
        // mark this parent as being checked so we don't recurse into it again
        checkedParents.put(parent, parent);

        //System.out.println("Checking if this "+describe()+" is a child of "+parent.describe());
        // see if any instances of this have parent 'parent'
        // check both icon and content views
        // Note that contentView and iconView are the same for every recursion
        Cell contentView = contentsView();
        if (contentView == null)
        {
            contentView = this;
        }
        Cell iconView = iconView();

        for (Iterator<NodeInst> it = parent.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (ni.isCellInstance())
            {
                Cell c = (Cell)ni.getProto();
                // ignore instances of icon view inside content view
                if (c.isIconOf(parent))
                {
                    continue;
                }
                if (c == contentView)
                {
                    return true;
                }
                if (c == iconView)
                {
                    return true;
                }
                // recurse
                if (getIsAChildOf(c, checkedParents))
                {
                    return true;
                }
            }
        }
        return false;
    }

//    private boolean getIsAParentOf(Cell child)
//    {
//        if (this == child) return true;
//
//		// look through every instance of the child cell
//		Cell lastParent = null;
//		for(Iterator<NodeInst> it = child.getInstancesOf(); it.hasNext(); )
//		{
//			NodeInst ni = it.next();
//
//			// if two instances in a row have same parent, skip this one
//			if (ni.getParent() == lastParent) continue;
//			lastParent = ni.getParent();
//
//			// recurse to see if the grandparent belongs to the child
//			if (getIsAParentOf(ni.getParent())) return true;
//		}
//
//		// if this has an icon, look at it's instances
//		Cell np = child.iconView();
//		if (np != null)
//		{
//			lastParent = null;
//			for(Iterator<NodeInst> it = np.getInstancesOf(); it.hasNext(); )
//			{
//				NodeInst ni = it.next();
//
//				// if two instances in a row have same parent, skip this one
//				if (ni.getParent() == lastParent) continue;
//				lastParent = ni.getParent();
//
//				// special case: allow an icon to be inside of the contents for illustration
//				if (ni.isCellInstance())
//				{
//					if (((Cell)ni.getProto()).isIconOf(child))
//					{
//						if (!child.isIcon()) continue;
//					}
//				}
//
//				// recurse to see if the grandparent belongs to the child
//				if (getIsAParentOf(ni.getParent())) return true;
//			}
//		}
//		return false;
//	}
    /**
     * Method to determine whether this Cell is in use anywhere.
     * If it is, an error dialog is displayed.
     *
     * @param action a description of the intended action (i.e. "delete").
     * @param quiet true not to warn the user of the cell being used.
     * @param sameCellGroupAlso
     * @return true if this Cell is in use anywhere.
     */
    public boolean isInUse(String action, boolean quiet, boolean sameCellGroupAlso)
    {
        String parents = isInUse(sameCellGroupAlso);
        if (parents != null)
        {
            if (!quiet)
            {
                Job.getUserInterface().showErrorMessage("Cannot " + action + " " + this
                    + " because it is used in " + parents, action + " failed");
            }
            return true;
        }
        if (isSchematic() && this == getNewestVersion())
        {
            for (Cell cell : getCellsInGroup())
            {
                if (!cell.isIcon())
                {
                    continue;
                }
                parents = cell.isInUse(false);
                if (parents != null)
                {
                    if (!quiet)
                    {
                        Job.getUserInterface().showErrorMessage("Cannot " + action + " " + this
                            + " because icon " + cell + " is used in " + parents, action + " failed");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to determine whether this Cell is in use anywhere.
     * If it is, a string with super-cell names is returned.
     *
     * @param sameCellGroupAlso true to check parents in the CellGroup of this Cell also.
     * @return string with super-cell names, or null if cell is not used.
     */
    private String isInUse(boolean sameCellGroupAlso)
    {
        String parents = null;
        for (Iterator<CellUsage> it = getUsagesOf(); it.hasNext();)
        {
            CellUsage u = it.next();
            Cell parent = u.getParent(database);
            if (!sameCellGroupAlso && parent.getCellGroup() == getCellGroup())
            {
                continue;
            }
            if (parents == null)
            {
                parents = parent.describe(true);
            } else
            {
                parents += ", " + parent.describe(true);
            }
        }
        return parents;
    }

    /**
     * **************************** VERSIONS *****************************
     */
    /**
     * Method to create a new version of this Cell.
     *
     * @return a new Cell that is a new version of this Cell.
     */
    public Cell makeNewVersion()
    {
        Cell newVersion = Cell.copyNodeProto(this, lib, noLibDescribe(), false);
        return newVersion;
    }

    /**
     * Method to return the version number of this Cell.
     *
     * @return the version number of this Cell.
     */
    public int getVersion()
    {
        return getCellName().getVersion();
    }

    /**
     * Method to return the number of different versions of this Cell.
     *
     * @return the number of different versions of this Cell.
     */
    public int getNumVersions()
    {
        int count = 0;
        String protoName = getName();
        View view = getView();
        synchronized (lib.cells)
        {
            for (Iterator<Cell> it = getVersionsTail(); it.hasNext();)
            {
                Cell c = it.next();
                if (!c.getName().equals(protoName) || c.getView() != view)
                {
                    break;
                }
                count++;
            }
        }
        return count;
    }

    /**
     * Method to return an Iterator over the different versions of this Cell.
     *
     * @return an Iterator over the different versions of this Cell.
     */
    public Iterator<Cell> getVersions()
    {
        List<Cell> versions = new ArrayList<Cell>();
        String protoName = getName();
        View view = getView();
        synchronized (lib.cells)
        {
            for (Iterator<Cell> it = getVersionsTail(); it.hasNext();)
            {
                Cell c = it.next();
                if (!c.getName().equals(protoName) || c.getView() != view)
                {
                    break;
                }
                versions.add(c);
            }
        }
        return versions.iterator();
    }

    /**
     * Method to return the most recent version of this Cell.
     *
     * @return he most recent version of this Cell.
     */
    public Cell getNewestVersion()
    {
//        synchronized (lib.cells) {
        return newestVersion;
//        }
    }

    /*
     * Return tail submap of library cells which starts from
     * cells with same protoName and view as this Cell.
     * @return tail submap with versions of this Cell.
     */
    private Iterator<Cell> getVersionsTail()
    {
        return lib.getCellsTail(newestVersion.getCellName());
    }

    /*
     * Return tail submap of library cells which starts from
     * cells with same protoName as this Cell.
     * @return tail submap with views of this Cell.
     */
    private Iterator<Cell> getViewsTail()
    {
        CellName cn = CellName.parseName(getName());
        return lib.getCellsTail(cn);
    }

    /**
     * **************************** GROUPS *****************************
     */
    /**
     * Method to get the CellGroup that this Cell is part of.
     *
     * @return the CellGroup that this Cell is part of
     * @throws IllegalStateException if Cell is not linked
     */
    public CellGroup getCellGroup()
    {
        if (!isLinked())
        {
            throw new IllegalStateException();
        }
        assert cellGroup != null;
        return cellGroup;
    }

    /**
     * Method to return a List of Cells in the group with this one.
     * If this Cell is not in a group, the list contains just this Cell.
     *
     * @return a List of Cells in the group with this one.
     * @throws IllegalStateException if Cell is not linked
     */
    public List<Cell> getCellsInGroup()
    {
        if (!isLinked())
        {
            throw new IllegalStateException();
        }
        return new ArrayList<Cell>(cellGroup.cells);
//    	List<Cell> cellsInGroup = new ArrayList<Cell>();
//    	if (cellGroup == null) cellsInGroup.add(this); else
//    	{
//    		for(Cell c : cellGroup.cells) cellsInGroup.add(c);
//    	}
//    	return cellsInGroup;
    }

    /**
     * Method to return the main schematic Cell associated with this Cell.
     * Examines the group for the main schematic.
     *
     * @return the main schematic Cell associated with this Cell.
     * @throws IllegalStateException if Cell is not linked
     */
    public Cell getMainSchematicInGroup()
    {
        if (!isLinked())
        {
            throw new IllegalStateException();
        }
        return cellGroup.getMainSchematics();
//        if (cellGroup != null) return cellGroup.getMainSchematics();
//        if (getView() == View.SCHEMATIC) return this;
//        return null;
    }

    /**
     * Method to move this Cell together with all its versions and views
     * to the group of another Cell.
     *
     * @param otherCell the other cell whose group this Cell should join.
     */
    public void joinGroup(Cell otherCell)
    {
        setCellGroup(otherCell.getCellGroup());
    }

//	/**
//	 * Method to Cell together with all its versions and views into its own CellGroup.
//	 * If there is no already Cells with other names in its CellGroup, nothing is done.
//	 */
//	public void putInOwnCellGroup()
//	{
//		setCellGroup(null);
//	}
    /**
     * Method to put this Cell together with all its versions and views into the given CellGroup.
     *
     * @param cellGroup the CellGroup that this cell belongs to or null to put into own cell group
     */
    public void setCellGroup(CellGroup cellGroup)
    {
        if (!isLinked())
        {
            return;
        }
//		CellGroup oldCellGroup = this.cellGroup;
        if (cellGroup == null)
        {
            cellGroup = new CellGroup(lib);
        }
        checkChanging();
        if (cellGroup == this.cellGroup)
        {
            return;
        }
        database.unfreshSnapshot();
        lib.setChanged();
        String protoName = getName();
        for (Iterator<Cell> it = getViewsTail(); it.hasNext();)
        {
            Cell cell = it.next();
            if (!cell.getName().equals(protoName))
            {
                break;
            }
//            if (cell.cellGroup != null)
            cell.cellGroup.remove(cell);
            cellGroup.add(cell);
        }
//		Undo.modifyCellGroup(this, oldCellGroup);
    }

    /**
     * **************************** VIEWS *****************************
     */
    /**
     * Method to get this Cell's View.
     * Views include "layout", "schematics", "icon", "netlist", etc.
     *
     * @return to get this Cell's View.
     */
    public View getView()
    {
        return getCellName().getView();
    }

    /**
     * Method to change the view of this Cell.
     *
     * @param newView the new View.
     */
    public IdMapper setView(View newView)
    {
        return rename(CellName.newName(getName(), newView, getVersion()), null);
    }

    /**
     * Method to determine whether this Cell is an icon Cell.
     *
     * @return true if this Cell is an icon Cell.
     */
    public boolean isIcon()
    {
        return getId().isIcon();
    }

    /**
     * Method to determine whether this Cell is an icon of another Cell.
     *
     * @param cell the other cell which this may be an icon of.
     * @return true if this Cell is an icon of that other Cell.
     */
    public boolean isIconOf(Cell cell)
    {
        return isIcon() && cellGroup == cell.cellGroup && cell.isSchematic();
    }

    /**
     * Method to return true if this Cell is a schematic Cell.
     *
     * @return true if this Cell is a schematic Cell.
     */
    public boolean isSchematic()
    {
        return getId().isSchematic();
    }

    /**
     * Method to return true if bus names are allowed in this Cell
     *
     * @return true if bus names are allowed in this Cell
     */
    public boolean busNamesAllowed()
    {
        return getD().busNamesAllowed();
    }

    /**
     * Method to return true if this Cell is a layout Cell.
     *
     * @return true if this Cell is a layout Cell
     */
    public boolean isLayout()
    {
        View w = getView();
        return w == View.LAYOUT || w == View.LAYOUTCOMP || w == View.LAYOUTSKEL;
    }

    /**
     * Method to return the number of pages in this multi-page Cell.
     *
     * @return the number of different pages.
     */
    public int getNumMultiPages()
    {
        if (!isMultiPage())
        {
            return 1;
        }
        Rectangle2D bounds = getBounds();
        int numPages = (int)(bounds.getHeight() / FrameDescription.MULTIPAGESEPARATION) + 1;
        Integer storedCount = getVarValue(MULTIPAGE_COUNT_KEY, Integer.class);
        if (storedCount != null)
        {
            if (storedCount.intValue() > numPages)
            {
                numPages = storedCount.intValue();
            }
        }
        return numPages;
    }

    /**
     * Method to find the contents Cell associated with this Cell.
     * This only makes sense if the current Cell is an icon or skeleton Cell.
     *
     * @return the contents Cell associated with this Cell.
     * Returns null if no such Cell can be found.
     */
    public Cell contentsView()
    {
        // can only consider contents if this cell is an icon
        if (!isIcon() && getView() != View.LAYOUTSKEL)
        {
            return null;
        }

        // first check to see if there is a schematics link
        List<Cell> cellsInGroup = getCellsInGroup();
        for (Cell cellInGroup : cellsInGroup)
        {
            if (cellInGroup.isSchematic())
            {
                return cellInGroup;
            }
        }

        // now check to see if there is any layout link
        for (Cell cellInGroup : cellsInGroup)
        {
            if (cellInGroup.getView() == View.LAYOUT)
            {
                return cellInGroup;
            }
        }

        // finally check to see if there is any "unknown" link
        for (Cell cellInGroup : cellsInGroup)
        {
            if (cellInGroup.getView() == View.UNKNOWN)
            {
                return cellInGroup;
            }
        }

        // no contents found
        return null;
    }

    /**
     * Method to find the icon Cell associated with this Cell.
     *
     * @return the icon Cell associated with this Cell.
     * Returns null if no such Cell can be found.
     */
    public Cell iconView()
    {
        // can only get icon view if this is a schematic
        if (!isSchematic())
        {
            return null;
        }

        // now look for views
        for (Cell cellInGroup : getCellsInGroup())
        {
            if (cellInGroup.isIcon())
            {
                return cellInGroup;
            }
        }

        return null;
    }

    /**
     * Method to find the Cell of a given View that is in the same group as this Cell.
     * If there is more than one cell matching the View, it will do a name match.
     *
     * @param view the View of the other Cell.
     * @return the Cell from this group with the specified View.
     * Returns null if no such Cell can be found.
     */
    public Cell otherView(View view)
    {
        Cell otherViewCell = null;
        for (Cell cellInGroup : getCellsInGroup())
        {
            if (cellInGroup.getView() == view)
            {
                // get latest version
                otherViewCell = cellInGroup.getNewestVersion();

                // Perfect match including name
                if (cellInGroup.getName().equals(getName()))
                {
                    return otherViewCell;
                }
            }
        }

        return otherViewCell;
    }

    /**
     * **************************** NETWORKS *****************************
     */
    /**
     * Recompute the Netlist structure for this Cell without shortening resistors.
     *
     * @return the Netlist structure for this cell.
     * @throws NetworkTool.NetlistNotReady if called from GUI thread and change Job hasn't prepared Netlist yet
     */
    public Netlist getNetlist()
    {
        return getNetlist(Netlist.ShortResistors.NO);
    }

    /**
     * Recompute the Netlist structure for this Cell.
     *
     * @param shortResistors short resistors mode of Netlist.
     * @return the Netlist structure for this cell.
     * @throws NetworkTool.NetlistNotReady if called from GUI thread and change Job hasn't prepared Netlist yet
     */
    public Netlist getNetlist(Netlist.ShortResistors shortResistors)
    {
        NetCell netCell = netCellRef.get();
        if (netCell == null)
        {
            netCell = NetCell.newInstance(this);
            setNetCellRef(netCell);
        }
        return netCell.getNetlist(shortResistors);
    }

    private void setNetCellRef(NetCell netCell)
    {
        netCellRef = USE_WEAK_REFERENCES ? new WeakReference<NetCell>(netCell) : new SoftReference<NetCell>(netCell);
    }

//    /** Returns the Netlist structure for this Cell, using current network options.
//     * Waits for completion of change Job when called from GUI thread
//     * @return the Netlist structure for this cell.
//     */
//    public Netlist getUserNetlist() {
//        return getNetlist();
//    }
//
//    /** Returns the Netlist structure for this Cell, using current network options.
//     * Returns null if change Job hasn't prepared GUI Netlist
//     * @return the Netlist structure for this cell.
//     */
//    public Netlist acquireUserNetlist() {
//        return getNetlist();
//    }
    /**
     * **************************** DATES *****************************
     */
    /**
     * Method to get the creation date of this Cell.
     *
     * @return the creation date of this Cell.
     */
    public Date getCreationDate()
    {
        return new Date(getD().creationDate);
    }

    /**
     * Method to set this Cell's creation date.
     * This is a low-level method and should not be called unless you know what you are doing.
     *
     * @param creationDate the date of this Cell's creation.
     */
    public void lowLevelSetCreationDate(Date creationDate)
    {
        setD(getD().withCreationDate(creationDate.getTime()));
    }

    /**
     * Method to return the revision date of this Cell.
     *
     * @return the revision date of this Cell.
     */
    public Date getRevisionDate()
    {
        return new Date(getD().revisionDate);
    }

    /**
     * Method to set this Cell's last revision date.
     * This is a low-level method and should not be called unless you know what you are doing.
     *
     * @param revisionDate the date of this Cell's last revision.
     */
    public void lowLevelSetRevisionDate(Date revisionDate)
    {
        lowLevelSetRevisionDate(revisionDate.getTime());
    }

    /**
     * Method to set this Cell's revision date and user name.
     * Change system is not informed about this.
     * This is a low-level method and should not be called unless you know what you are doing.
     */
    public void lowLevelMadeRevision(long revisionDate, String userName, CellRevision oldRevision)
    {
        backup();
        if (!isModified() || backup.cellRevision == oldRevision || revisionDateFresh)
        {
            return;
        }
        lowLevelSetRevisionDate(revisionDate);
    }

    private void lowLevelSetRevisionDate(long revisionDate)
    {
        backup = backup().withRevisionDate(revisionDate);
        d = backup.cellRevision.d;
        revisionDateFresh = true;
        unfreshCellTree();
        database.unfreshSnapshot();
    }

    /**
     * Method to check the current cell to be sure that no subcells have a more recent date.
     * This is invoked when the "Check cell dates" feature is enabled in the New Nodes tab of
     * the Edit Options dialog.
     */
    public void checkCellDates()
    {
        Set<Cell> cellsChecked = new HashSet<Cell>();
        checkCellDate(getRevisionDate(), cellsChecked);
    }

    /**
     * Recursive method to check sub-cell revision times.
     *
     * @param rev_time the revision date of the top-level cell.
     * Nothing below it can be newer.
     */
    private void checkCellDate(Date rev_time, Set<Cell> cellsChecked)
    {
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (!ni.isCellInstance())
            {
                continue;
            }
            Cell subCell = (Cell)ni.getProto();

            // ignore recursive references (showing icon in contents)
            if (subCell.isIconOf(this))
            {
                continue;
            }
            if (!cellsChecked.contains(subCell))
            {
                subCell.checkCellDate(rev_time, cellsChecked); // recurse
            }

            Cell contentsCell = subCell.contentsView();
            if (contentsCell != null)
            {
                if (!cellsChecked.contains(contentsCell))
                {
                    contentsCell.checkCellDate(rev_time, cellsChecked); // recurse
                }
            }
        }

        // check this cell
        cellsChecked.add(this); // flag that we have seen this one
        if (!getRevisionDate().after(rev_time))
        {
            return;
        }

        // possible error in hierarchy
        System.out.println("WARNING: sub-cell " + this
            + " has been edited since the last revision to the current cell");
    }

    /**
     * **************************** MISCELLANEOUS *****************************
     */
    private int getFlags()
    {
        return d.flags;
    }

    private boolean isFlag(int mask)
    {
        return (getFlags() & mask) != 0;
    }

    private void setFlag(int mask, boolean value)
    {
        lowLevelSetUserbits(value ? getFlags() | mask : getFlags() & ~mask);
    }

    /**
     * Method to set this Cell so that instances of it are "expanded" by when created.
     * Expanded NodeInsts are instances of Cells that show their contents.
     */
    public void setWantExpanded()
    {
        setFlag(WANTNEXPAND, true);
    }

    /**
     * Method to set this Cell so that instances of it are "not expanded" by when created.
     * Expanded NodeInsts are instances of Cells that show their contents.
     */
    public void clearWantExpanded()
    {
        setFlag(WANTNEXPAND, false);
    }

    /**
     * Method to tell if instances of it are "expanded" by when created.
     * Expanded NodeInsts are instances of Cells that show their contents.
     *
     * @return true if instances of it are "expanded" by when created.
     */
    public boolean isWantExpanded()
    {
        return isFlag(WANTNEXPAND) || isIcon();
    }

    /**
     * Method to return the function of this Cell.
     * The Function of CELL is always UNKNOWN.
     *
     * @return the function of this Cell.
     */
    @Override
    public PrimitiveNode.Function getFunction()
    {
        return PrimitiveNode.Function.UNKNOWN;
    }

    /**
     * Method to set this Cell so that everything inside of it is locked.
     * Locked instances cannot be moved or deleted.
     */
    public void setAllLocked()
    {
        setFlag(NPLOCKED, true);
    }

    /**
     * Method to set this Cell so that everything inside of it is not locked.
     * Locked instances cannot be moved or deleted.
     */
    public void clearAllLocked()
    {
        setFlag(NPLOCKED, false);
    }

    /**
     * Method to tell if the contents of this Cell are locked.
     * Locked instances cannot be moved or deleted.
     *
     * @return true if the contents of this Cell are locked.
     */
    public boolean isAllLocked()
    {
        return isFlag(NPLOCKED);
    }

    /**
     * Method to set this Cell so that all instances inside of it are locked.
     * Locked instances cannot be moved or deleted.
     */
    public void setInstancesLocked()
    {
        setFlag(NPILOCKED, true);
    }

    /**
     * Method to set this Cell so that all instances inside of it are not locked.
     * Locked instances cannot be moved or deleted.
     */
    public void clearInstancesLocked()
    {
        setFlag(NPILOCKED, false);
    }

    /**
     * Method to tell if the sub-instances in this Cell are locked.
     * Locked instances cannot be moved or deleted.
     *
     * @return true if the sub-instances in this Cell are locked.
     */
    public boolean isInstancesLocked()
    {
        return isFlag(NPILOCKED);
    }

    /**
     * Method to set this Cell so that it is part of a cell library.
     * Cell libraries are simply libraries that contain standard cells but no hierarchy
     * (as opposed to libraries that define a complete circuit).
     * Certain commands exclude facets from cell libraries, so that the actual circuit hierarchy can be more clearly seen.
     */
    public void setInCellLibrary()
    {
        setFlag(INCELLLIBRARY, true);
    }

//	/**
//	 * Method to set this Cell so that it is not part of a cell library.
//	 * Cell libraries are simply libraries that contain standard cells but no hierarchy
//	 * (as opposed to libraries that define a complete circuit).
//	 * Certain commands exclude facets from cell libraries, so that the actual circuit hierarchy can be more clearly seen.
//	 */
//	public void clearInCellLibrary() { setFlag(INCELLLIBRARY, false); }
//
//	/**
//	 * Method to tell if this Cell is part of a cell library.
//	 * Cell libraries are simply libraries that contain standard cells but no hierarchy
//	 * (as opposed to libraries that define a complete circuit).
//	 * Certain commands exclude facets from cell libraries, so that the actual circuit hierarchy can be more clearly seen.
//	 * @return true if this Cell is part of a cell library.
//	 */
//	public boolean isInCellLibrary() { return isFlag(INCELLLIBRARY); }
    /**
     * Method to set this Cell so that it is part of a technology library.
     * Technology libraries are those libraries that contain Cells with
     * graphical descriptions of the nodes, arcs, and layers of a technology.
     */
    public void setInTechnologyLibrary()
    {
        setFlag(TECEDITCELL, true);
    }

    /**
     * Method to set this Cell so that it is not part of a technology library.
     * Technology libraries are those libraries that contain Cells with
     * graphical descriptions of the nodes, arcs, and layers of a technology.
     */
    public void clearInTechnologyLibrary()
    {
        setFlag(TECEDITCELL, false);
    }

    /**
     * Method to tell if this Cell is part of a Technology Library.
     * Technology libraries are those libraries that contain Cells with
     * graphical descriptions of the nodes, arcs, and layers of a technology.
     *
     * @return true if this Cell is part of a Technology Library.
     */
    public boolean isInTechnologyLibrary()
    {
        return isFlag(TECEDITCELL);
    }

    /**
     * Method to clear this Cell modified bit since last save to disk. No need to call checkChanging().
     * This is done when the library contained this cell is saved to disk.
     */
    void clearModified()
    {
        if (isModified())
        {
            backup = backup().withoutModified();
            unfreshCellTree();
            database.unfreshSnapshot();
        }
        assert cellBackupFresh;
        revisionDateFresh = true;
    }

    /**
     * Method to tell if this Cell has been modified since last save to disk.
     *
     * @return true if cell has been modified.
     */
    public boolean isModified()
    {
        return !cellBackupFresh || backup.modified;
    }

    public void setTopologyModified()
    {
        strongTopology = getTopology();
        setContentsModified();
    }

    /**
     * Method to set if cell has been modified in the batch job.
     */
    public void setContentsModified()
    {
        cellContentsFresh = false;
        unfreshBackup();
    }

    private void unfreshCellTree()
    {
        if (!cellTreeFresh)
        {
            return;
        }
        unfreshRTree();
        Topology topology = getTopologyOptional();
        if (topology != null)
        {
            for (Iterator<NodeInst> it = topology.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (ni.isCellInstance())
                {
                    ni.redoGeometric();
                }
            }
        }
        cellTreeFresh = false;
        for (Iterator<CellUsage> it = getUsagesOf(); it.hasNext();)
        {
            CellUsage cu = it.next();
            Cell cell = database.getCell(cu.parentId);
            cell.unfreshCellTree();
        }
    }

    private void unfreshBackup()
    {
        cellBackupFresh = false;
        revisionDateFresh = false;
        unfreshCellTree();
        database.unfreshSnapshot();
    }

    public void unfreshRTree()
    {
        Topology topology = getTopologyOptional();
        if (topology != null)
        {
            topology.unfreshRTree();
        }
    }

    /**
     * Method to load isExpanded status of subcell instances from Preferences.
     */
    public void loadExpandStatus()
    {
        String cellName = noLibDescribe().replace('/', ':');
        String cellKey = "E" + cellName;
        boolean useWantExpanded = false, mostExpanded = false;
        Preferences libPrefs = Pref.getLibraryPreferences(getId().libId);
        if (libPrefs.get(cellKey, null) == null)
        {
            useWantExpanded = true;
        } else
        {
            mostExpanded = libPrefs.getBoolean(cellKey, false);
        }
        Preferences cellPrefs = null;
        try
        {
            if (libPrefs.nodeExists(cellName))
            {
                cellPrefs = libPrefs.node(cellName);
            }
        } catch (BackingStoreException e)
        {
            ActivityLogger.logException(e);
        }
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (!ni.isCellInstance())
            {
                continue;
            }
            boolean expanded = useWantExpanded ? ((Cell)ni.getProto()).isWantExpanded() : mostExpanded;
            if (cellPrefs != null)
            {
                String nodeName = "E" + ni.getName();
                expanded = cellPrefs.getBoolean(nodeName, expanded);
            }
            setExpanded(ni.getNodeId(), expanded);
        }
        expandStatusModified = false;
    }

    /**
     * Method to save isExpanded status of subcell instances to Preferences.
     */
    void saveExpandStatus() throws BackingStoreException
    {
        if (!expandStatusModified)
        {
            return;
        }
        String cellName = noLibDescribe().replace('/', ':');
        String cellKey = "E" + cellName;
        if (cellKey.length() > Preferences.MAX_KEY_LENGTH)
        {
            if (Job.getDebug())
            {
                System.err.println("WARNING: Cannot save expanded status of cell " + this
                    + " because key exceeds " + Preferences.MAX_KEY_LENGTH + " characters in length.");
            }
            return;
        }
        if (Job.getDebug())
        {
            System.err.println("Save expanded status of " + this);
        }
        int num = 0, expanded = 0, diff = 0;
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (!ni.isCellInstance())
            {
                continue;
            }
            num++;
            boolean isExpanded = isExpanded(ni.getNodeId());
            if (isExpanded)
            {
                expanded++;
            }
            if (isExpanded != ((Cell)ni.getProto()).isWantExpanded())
            {
                diff++;
            }
        }
        boolean useWantExpanded = false, mostExpanded = false;
        Preferences libPrefs = Pref.getLibraryPreferences(getId().libId);
        if (diff <= expanded && diff <= num - expanded)
        {
            useWantExpanded = true;
            libPrefs.remove(cellKey);
        } else
        {
            if (num - expanded < expanded)
            {
                diff = num - expanded;
                mostExpanded = true;
            } else
            {
                diff = expanded;
            }
            libPrefs.putBoolean(cellKey, mostExpanded);
        }
        if (diff == 0)
        {
            if (libPrefs.nodeExists(cellName))
            {
                libPrefs.node(cellName).removeNode();
            }
        } else
        {
            Preferences cellPrefs = libPrefs.node(cellName);
            cellPrefs.clear();
            cellPrefs.put("CELL", cellName);
            for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (!ni.isCellInstance())
                {
                    continue;
                }
                boolean defaultExpanded = useWantExpanded ? ((Cell)ni.getProto()).isWantExpanded() : mostExpanded;
                boolean isExpanded = isExpanded(ni.getNodeId());
                if (isExpanded != defaultExpanded)
                {
                    String nodeName = "E" + ni.getName();
                    if (nodeName.length() < Preferences.MAX_KEY_LENGTH)
                        cellPrefs.putBoolean(nodeName, isExpanded);
                    else
                        System.out.println("Key string too long to store as Preference '" + nodeName + "'");
                }
            }
        }
        expandStatusModified = false;
    }

    /**
     * Method to set the multi-page capability of this Cell.
     * Multipage cells (usually schematics) must have cell frames to isolate the different
     * areas of the cell that are different pages.
     *
     * @param multi true to make this cell multi-page.
     */
    public void setMultiPage(boolean multi)
    {
        setFlag(MULTIPAGE, multi);
    }

    /**
     * Method to tell if this Cell is a multi-page drawing.
     * Multipage cells (usually schematics) must have cell frames to isolate the different
     * areas of the cell that are different pages.
     *
     * @return true if this Cell is a multi-page drawing.
     */
    public boolean isMultiPage()
    {
        return isFlag(MULTIPAGE);
    }

    /**
     * Returns true if this Cell is linked into database.
     *
     * @return true if this Cell is linked into database.
     */
    @Override
    public boolean isLinked()
    {
        database.checkExamine();
        return database.getCell(getId()) == this;
    }

    /**
     * Returns database to which this Cell belongs.
     *
     * @return database to which this ElectricObject belongs.
     */
    @Override
    public EDatabase getDatabase()
    {
        return database;
    }

    /**
     * Method to check and repair data structure errors in this Cell.
     */
    public int checkAndRepair(boolean repair, ErrorLogger errorLogger, EditingPreferences ep)
    {
        int errorCount = 0;
        List<Geometric> list = new ArrayList<Geometric>();

        for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();
            errorCount += ai.checkAndRepair(repair, list, errorLogger);
            checkName(ai.getNameKey(), ai, errorLogger);
        }
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            errorCount += ni.checkAndRepair(repair, list, errorLogger);
            checkName(ni.getNameKey(), ni, errorLogger);
        }
        for (Iterator<Export> it = getExports(); it.hasNext();)
        {
            Export e = it.next();
            checkName(e, errorLogger);
        }
        if (repair && list.size() > 0)
        {
            CircuitChangeJobs.eraseObjectsInList(this, list, false, null, ep);
        }

        if (isSchematic() && getNewestVersion() == this && getMainSchematicInGroup() != this)
        {
            String mainSchemMsg = "Extraneous schematic cell " + describe(false);
//            if (getCellGroup() != null)
            mainSchemMsg += " in cell group " + lib.getName() + ":" + getCellGroup().getName();
            System.out.println(mainSchemMsg);
            if (errorLogger != null)
            {
                errorLogger.logWarning(mainSchemMsg, this, 1);
            }
        }

//        if (cellGroup != null) {
        for (Cell cell : cellGroup.cells)
        {
            Variable var = cell.getParameterOrVariable(NccCellAnnotations.NCC_ANNOTATION_KEY);
            if (var != null && var.isInherit())
            {
                // cleanup NCC cell annotations which were inheritable
                String nccMsg = "Cleaned up NCC annotations in cell " + cell.describe(false);
                if (repair)
                {
                    if (isParam(var.getKey()))
                    {
                        getCellGroup().delParam((Variable.AttrKey)var.getKey());
                    }
                    cell.addVar(var.withInherit(false).withParam(false).withInterior(true));
                    nccMsg += " (REPAIRED)";
                }
                System.out.println(nccMsg);
                if (errorLogger != null)
                {
                    errorLogger.logWarning(nccMsg, cell, 1);
                }
            }
        }
//        }

        return errorCount;
    }

    private void checkName(Name name, Geometric geom, ErrorLogger errorLogger)
    {
        if (name.isTempname())
        {
            return;
        }
        String s = name.toString();
        if (s.indexOf('<') >= 0 && s.indexOf('>') >= 0)
        {
            String msg = "Name " + s + " contains angle brackets. They are not considered as array chars";
            System.out.println(msg);
            if (errorLogger != null)
            {
                errorLogger.logWarning(msg, geom, this, null, 1);
            }
        }
    }

    private void checkName(Export e, ErrorLogger errorLogger)
    {
        String s = e.getName();
        if (s.indexOf('<') >= 0 && s.indexOf('>') >= 0)
        {
            String msg = "Name " + s + " contains angle brackets. They are not considered as array chars";
            System.out.println(msg);
            if (errorLogger != null)
            {
                errorLogger.logWarning(msg, e, this, null, 1);
            }
        }
    }

    /**
     * Method to check invariants in this Cell.
     *
     * @exception AssertionError if invariants are not valid
     */
    @Override
    protected void check()
    {
        boolean originalCellContentsFresh = cellContentsFresh;
        CellId cellId = getD().cellId;
        super.check();
        assert database.getCell(cellId) == this;
        assert database.getLib(getD().getLibId()) == lib;
        if (getD().techId != null)
        {
            assert tech == database.getTech(getD().techId);
        } else
        {
            assert tech == null;
        }
        assert getCellName() != null;
        assert getVersion() > 0;

        if (cellTreeFresh)
        {
            assert cellBackupFresh;
            assert backup == tree.top;
            for (CellTree subCellTree : tree.getSubTrees())
            {
                if (subCellTree == null)
                {
                    continue;
                }
                Cell subCell = database.getCell(subCellTree.top.cellRevision.d.cellId);
                assert subCell.cellTreeFresh;
                assert subCell.tree == subCellTree;
            }
        }
        CellRevision cellRevision = backup != null ? backup.cellRevision : null;
        if (cellBackupFresh)
        {
            assert cellRevision.d == getD();
            assert cellContentsFresh;
        }
        if (cellContentsFresh)
        {
            assert cellRevision.exports.size() == exports.length;
            if (LAZY_TOPOLOGY)
            {
                assert strongTopology == null;
            }
        }

        // check exports
        for (int portIndex = 0; portIndex < exports.length; portIndex++)
        {
            Export e = exports[portIndex];
            assert e.getParent() == this;
            assert e.getPortIndex() == portIndex;
            assert chronExports[e.getId().getChronIndex()] == e;
            if (cellContentsFresh)
            {
                assert cellRevision.exports.get(portIndex) == e.getD();
            }
            if (portIndex > 0)
            {
                assert (TextUtils.STRING_NUMBER_ORDER.compare(exports[portIndex - 1].getName(), e.getName()) < 0);
            }
            assert e.getOriginalPort() == getPortInst(e.getD().originalNodeId, e.getD().originalPortId);
        }
        for (int chronIndex = 0; chronIndex < chronExports.length; chronIndex++)
        {
            Export e = chronExports[chronIndex];
            if (e == null)
            {
                continue;
            }
            assert e.getId() == cellId.getPortId(chronIndex);
            assert e == exports[e.getPortIndex()];
        }

        // check topology
        assert cellContentsFresh == originalCellContentsFresh;
        Topology topology = getTopologyOptional();
        assert cellContentsFresh == originalCellContentsFresh;
        if (topology != null)
        {
            topology.check(cellUsages);
            cellRevision = backup != null ? backup.cellRevision : null;
            if (cellContentsFresh)
            {
                assert cellRevision.arcs.size() == topology.getNumArcs();
                for (int arcIndex = 0; arcIndex < topology.getNumArcs(); arcIndex++)
                {
                    ArcInst ai = topology.getArc(arcIndex);
                    ImmutableArcInst a = ai.getD();
                    assert cellRevision.arcs.get(arcIndex) == a;
                }
                assert cellRevision.nodes.size() == topology.getNumNodes();
                for (int nodeIndex = 0; nodeIndex < topology.getNumNodes(); nodeIndex++)
                {
                    NodeInst ni = topology.getNode(nodeIndex);
                    ImmutableNodeInst n = ni.getD();
                    assert cellRevision.nodes.get(nodeIndex) == n;
                }
            }
        }

        // check group pointers
//        if (cellGroup != null)
        assert cellGroup.containsCell(this);
    }

    /**
     * Method to tell whether an ElectricObject exists in this Cell.
     * Used when saving and restoring highlighting to ensure that the object still
     * exists.
     *
     * @param eObj the ElectricObject in question
     * @return true if that ElectricObject is in this Cell.
     */
    public boolean objInCell(ElectricObject eObj)
    {
        if (eObj instanceof NodeInst)
        {
            for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
            {
                if (it.next() == eObj)
                {
                    return true;
                }
            }
        } else if (eObj instanceof ArcInst)
        {
            for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
            {
                if (it.next() == eObj)
                {
                    return true;
                }
            }
        } else if (eObj instanceof PortInst)
        {
            NodeInst ni = ((PortInst)eObj).getNodeInst();
            for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
            {
                if (it.next() == ni)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to get the 0-based index of this Cell.
     *
     * @return the index of this Cell.
     */
    public final int getCellIndex()
    {
        return getId().cellIndex;
    }

    /**
     * Method to set an arbitrary integer in a temporary location on this Cell.
     *
     * @param tempInt the integer to be set on this Cell.
     */
    public void setTempInt(int tempInt)
    {
        checkChanging();
        this.tempInt = tempInt;
    }

    /**
     * Method to get the temporary integer on this Cell.
     *
     * @return the temporary integer on this Cell.
     */
    public int getTempInt()
    {
        return tempInt;
    }

    /**
     * Method to determine the appropriate Cell associated with this ElectricObject.
     *
     * @return the appropriate Cell associated with this ElectricObject..
     * Returns null if no Cell can be found.
     */
    @Override
    public Cell whichCell()
    {
        return this;
    }

    /**
     * Method to get the library to which this Cell belongs.
     *
     * @return to get the library to which this Cell belongs.
     */
    public Library getLibrary()
    {
        return lib;
    }

    /**
     * Method to return the Technology of this Cell.
     * It can be quite complex to determine which Technology a Cell belongs to.
     * The system examines all of the nodes and arcs in it, and also considers
     * the Cell's view.
     *
     * @return return the Technology of this Cell.
     */
    @Override
    public Technology getTechnology()
    {
        if (tech == null)
        {
            NodeProto[] nodeProtos = null;
            ArcProto[] arcProtos = null;
            if (backup == null && getTopologyOptional() == null)
            {
                nodeProtos = new NodeProto[0];
                arcProtos = new ArcProto[0];
            }
            setTechnology(Technology.whatTechnology(this, nodeProtos, 0, 0, arcProtos));
        }
        return tech;
    }

    /**
     * Method to set the Technology to which this NodeProto belongs
     * It can only be called for Cells because PrimitiveNodes have fixed Technology membership.
     *
     * @param tech the new technology for this NodeProto (Cell).
     */
    public void setTechnology(Technology tech)
    {
        TechId techId = null;
        if (tech != null)
        {
            techId = tech.getId();
            if (database.getTech(techId) != tech)
            {
                throw new IllegalArgumentException("tech");
            }
        }
        setD(getD().withTechId(techId));
        this.tech = tech;
    }

    /**
     * Finds the Schematic Cell associated with this Icon Cell.
     * If this Cell is an Icon View then find the schematic Cell in its
     * CellGroup.
     *
     * @return the Schematic Cell. Returns null if there is no equivalent.
     * If there are multiple versions of the Schematic View then
     * return the latest version.
     */
    public Cell getEquivalent()
    {
        return isIcon() ? getMainSchematicInGroup() : this;
    }

    /**
     * Use to compare cells in Cross Library Check
     *
     * @param obj Object to compare to
     * @param buffer To store comparison messages in case of failure
     * @return True if objects represent same NodeInst
     */
    public boolean compare(Object obj, StringBuffer buffer)
    {
        if (this == obj)
        {
            return (true);
        }

        // Consider already obj==null
        if (obj == null || getClass() != obj.getClass())
        {
            return (false);
        }

        Cell toCompare = (Cell)obj;

        // Traversing nodes
        // @TODO GVG This should be removed if equals is implemented
        Set<Object> noCheckAgain = new HashSet<Object>();
        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            boolean found = false;
            NodeInst node = it.next();

            for (Iterator<NodeInst> i = toCompare.getNodes(); i.hasNext();)
            {
                NodeInst n = i.next();

                if (noCheckAgain.contains(n))
                {
                    continue;
                }

                if (node.compare(n, buffer))
                {
                    found = true;
                    // if node is found, remove element from iterator
                    // because it was found
                    //@TODO GVG Check iterator functionality
                    // Not sure if it could be done with iterators
                    noCheckAgain.add(n);
                    break;
                }
            }
            // No corresponding NodeInst found
            if (!found)
            {
                if (buffer != null)
                {
                    buffer.append("No corresponding node '").append(node).append("' found in '").append(toCompare).append("'\n");
                }
                return (false);
            }
        }
        // other node has more instances
        if (getNumNodes() != toCompare.getNumNodes())
        {
            if (buffer != null)
            {
                buffer.append("Cell '").append(toCompare.libDescribe()).append("' has more nodes than '").append(this).append("'\n");
            }
            return (false);
        }

        // Traversing Arcs
        for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
        {
            boolean found = false;
            ArcInst arc = it.next();

            for (Iterator<ArcInst> i = toCompare.getArcs(); i.hasNext();)
            {
                ArcInst a = i.next();

                if (noCheckAgain.contains(a))
                {
                    continue;
                }

                if (arc.compare(a, buffer))
                {
                    found = true;
                    noCheckAgain.add(a);
                    break;
                }
            }
            // No corresponding ArcInst found
            if (!found)
            {
                if (buffer != null)
                {
                    buffer.append("No corresponding arc '").append(arc).append("' found in other cell" + "\n");
                }
                return (false);
            }
        }
        // other node has more instances
        if (getNumArcs() != toCompare.getNumArcs())
        {
            if (buffer != null)
            {
                buffer.append("Cell '").append(toCompare.libDescribe()).append("' has more arcs than '").append(this).append("'\n");
            }
            return (false);
        }

        // Traversing ports. This includes Exports
        noCheckAgain.clear();
        for (Iterator<Export> it = getExports(); it.hasNext();)
        {
            boolean found = false;
            Export port = it.next();

            for (Iterator<Export> i = toCompare.getExports(); i.hasNext();)
            {
                Export p = i.next();

                if (noCheckAgain.contains(p))
                {
                    continue;
                }

                if (port.compare(p, buffer))
                {
                    found = true;
                    noCheckAgain.add(p);
                    break;
                }
            }
            // No corresponding PortProto found
            if (!found)
            {
                // Message is already added in port.compare()
//                if (buffer != null)
//                    buffer.append("No corresponding port '" + port.getName() + "' found in other cell" + "\n");
                return (false);
            }
        }
        // other node has more instances
        if (getNumPorts() != toCompare.getNumPorts())
        {
            if (buffer != null)
            {
                buffer.append("Cell '").append(toCompare.libDescribe()).append("' has more pors than '").append(this).append("'\n");
            }
            return (false);
        }

        // Checking parameters
        if (getNumParameters() != toCompare.getNumParameters())
        {
            if (buffer != null)
            {
                buffer.append("Cell '").append(toCompare).append("' has more parameters than '").append(this).append("'\n");
            }
            return (false);

        }
        for (Iterator<Variable> it1 = getParameters(), it2 = toCompare.getParameters(); it1.hasNext();)
        {
            Variable param1 = it1.next();
            Variable param2 = it2.next();
            if (!param1.compare(param2, buffer))
            {
                if (buffer != null)
                {
                    buffer.append("No corresponding parameter '").append(param1).append("' found in other cell" + "\n");
                }
                return (false);
            }
        }

        // Checking attributes
        noCheckAgain.clear();
        for (Iterator<Variable> it = getVariables(); it.hasNext();)
        {
            Variable var = it.next();
            boolean found = false;

            for (Iterator<Variable> i = toCompare.getVariables(); i.hasNext();)
            {
                Variable v = i.next();

                if (noCheckAgain.contains(v))
                {
                    continue;
                }

                if (var.compare(v, buffer))
                {
                    found = true;
                    noCheckAgain.add(v);
                    break;
                }
            }
            // No corresponding Variable found
            if (!found)
            {
                if (buffer != null)
                {
                    buffer.append("No corresponding variable '").append(var).append("' found in other cell" + "\n");
                }
                return (false);
            }
        }
        // other node has more instances
        if (getNumVariables() != toCompare.getNumVariables())
        {
            if (buffer != null)
            {
                buffer.append("Cell '").append(toCompare).append("' has more variables than '").append(this).append("'\n");
            }
            return (false);
        }
        return (true);
    }

    /**
     * Compares Cells by their Libraries and CellNames.
     *
     * @param that the other Cell.
     * @return a comparison between the Cells.
     */
    @Override
    public int compareTo(Cell that)
    {
        if (this.lib != that.lib)
        {
            int cmp = this.lib.compareTo(that.lib);
            if (cmp != 0)
            {
                return cmp;
            }
        }
        return this.getCellName().compareTo(that.getCellName());
    }

    /**
     * Method to get MinZ and MaxZ of the cell calculated based on nodes.
     * You must guarantee minZ = Double.MaxValue() and maxZ = Double.MinValue()
     * for initial call.
     *
     * @param array array[0] is minZ and array[1] is max
     * @return true if at least one valid layer was found with data.
     */
    public boolean getZValues(double[] array)
    {
        boolean foundValue = false;

        for (Iterator<NodeInst> it = getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (ni.isCellInstance())
            {
                Cell nCell = (Cell)ni.getProto();
                if (nCell.getZValues(array))
                    foundValue = true;
            } else
            {
                PrimitiveNode np = (PrimitiveNode)ni.getProto();
                if (np.getZValues(array))
                    foundValue = true;
            }
        }
        for (Iterator<ArcInst> it = getArcs(); it.hasNext();)
        {
            ArcInst ai = it.next();
            ArcProto ap = ai.getProto();
            if (ap.getZValues(array))
                foundValue = true;
        }
        return foundValue;
    }

    /**
     * Method to fill a set with any nodes in this Cell that refer to an external library.
     *
     * @param elib the external library being considered.
     * @param set the set being filled.
     * @return true if anything was added to the set.
     */
    public boolean findReferenceInCell(Library elib, Set<Cell> set)
    {
        // Stop recursive search here

        if (lib == elib)
        {
            //set.add(this);
            return (true);
        }
        int initial = set.size();

        for (Iterator<CellUsage> it = getUsagesIn(); it.hasNext();)
        {
            CellUsage cu = it.next();
            Cell nCell = cu.getProto(database);
            if (nCell.getLibrary() == elib)
            {
                set.add(this);
            } else
            {
                nCell.findReferenceInCell(elib, set);
            }
        }
        return (set.size() != initial);
    }
}
