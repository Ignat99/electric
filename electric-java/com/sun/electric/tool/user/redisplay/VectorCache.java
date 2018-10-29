/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VectorCache.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.redisplay;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableCell;
import com.sun.electric.database.ImmutableElectricObject;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableIconInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.SnapshotAnalyze;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.AbstractFixpRectangle;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.*;
import java.awt.geom.*;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to hold scalable representation of circuit displays.
 */
public class VectorCache
{
	private static final boolean USE_CELL_RTREE = true;
	private static final boolean USE_ELECTRICAL = false;
	private static final boolean WIPE_PINS = true;
	public static final boolean DEBUG = false;

	public static final VectorCache theCache = new VectorCache(EDatabase.clientDatabase());
	/** database to work. */						public final EDatabase database;
	/** list of cell expansions. */					private final ArrayList<VectorCellDef> cachedCells = new ArrayList<VectorCellDef>();
	/** list of polygons to include in cells */		private final Map<CellId, List<VectorBase>> addPolyToCell = new HashMap<CellId, List<VectorBase>>();
	/** list of instances to include in cells */	private final Map<CellId, List<VectorLine>> addInstToCell = new HashMap<CellId, List<VectorLine>>();
	/** local shape builder */						private final ShapeBuilder shapeBuilder = new ShapeBuilder();
	/** List of VectorManhattanBuilders */			private final ArrayList<List<VectorManhattanBox>> boxBuilders = new ArrayList<List<VectorManhattanBox>>();
	/** List of VectorManhattanBuilders for pure nodes. */	private final ArrayList<List<VectorManhattanBox>> pureBoxBuilders = new ArrayList<List<VectorManhattanBox>>();
	/** Current VarContext. */						private VarContext varContext;
	/** Current scale. */							private double curScale;
	/** True to clear fade images. */				private boolean clearFadeImages = false;
	/** True to clear cache. */						private boolean clearCache = false;
	/** counter to know when an update was made */	private long updateStep = 0;
	/** zero rectangle */							private final Rectangle2D CENTERRECT = new Rectangle2D.Double(0, 0, 0, 0);
	private EGraphics instanceGraphics = new EGraphics(false, false, null, 0, 0, 0, 0, 1.0, true,
		new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

	private final EditWindow0 dummyWnd = new EditWindow0()
	{
		double globalScale = User.getGlobalTextScale();
		String defaultFont = User.getDefaultFont();

        public Rectangle2D getGlyphBounds(String text, Font font) { return CENTERRECT; }

		public VarContext getVarContext() { return varContext; }

		public double getScale() { return curScale; }

		public double getGlobalTextScale() { return globalScale; }

		public String getDefaultFont() { return defaultFont; }
	};

	/**
	 * Class which defines the common information for all cached displayable objects
	 */
	public static abstract class VectorBase
	{
		private Layer layer;
		private EGraphics graphicsOverride;
		private ImmutableElectricObject origin;

		VectorBase(ImmutableElectricObject origin, Layer layer, EGraphics graphicsOverride)
		{
			this.origin = origin;
			this.layer = layer;
			this.graphicsOverride = graphicsOverride;
		}

		/**
		 * Return true if this is a filled primitive.
		 */
		boolean isFilled() { return false; }

		public EGraphics getGraphics() { return graphicsOverride; }

		public Layer getLayer() { return layer; }
	}

	/**
	 * Class which defines a cached Manhattan rectangle.
	 */
	static class VectorManhattan extends VectorBase
	{
		/** coordinates of boxes: lX, lY, hX, hY */
		private long[] coords;
		private boolean pureLayer;

		VectorManhattan(ImmutableElectricObject origin, double c1X, double c1Y, double c2X, double c2Y, Layer layer, EGraphics graphicsOverride, boolean pureLayer)
		{
			this(origin, new long[]{databaseToGrid(c1X), databaseToGrid(c1Y), databaseToGrid(c2X), databaseToGrid(c2Y)},
				layer, graphicsOverride, pureLayer);
		}

		VectorManhattan(ImmutableElectricObject origin, long c1X, long c1Y, long c2X, long c2Y, Layer layer, EGraphics graphicsOverride, boolean pureLayer)
		{
			this(origin, new long[]{c1X, c1Y, c2X, c2Y}, layer, graphicsOverride, pureLayer);
		}

		private VectorManhattan(ImmutableElectricObject origin, long[] coords, Layer layer, EGraphics graphicsOverride, boolean pureLayer) {
			super(origin, layer, graphicsOverride);
			this.coords = coords;
			this.pureLayer = pureLayer;
		}

		public long[] getCoords() { return coords; }

		public boolean isPureLayer() { return pureLayer; }

		@Override
		boolean isFilled() { return true; }

		public String toString()
		{
			String msg = "VectorManhattan";
			if (pureLayer) msg += "Pure";
			msg += " "+getLayer().getName();
			for(int i=0; i<coords.length; i+=4) msg += " ("+coords[i]+"<=X<="+coords[i+2]+" and "+coords[i+1]+"<=Y<="+coords[i+3]+")";
			return msg;
		}
	}

	/**
	 * Class which defines a Manhattan box with an origin.
	 * Used by shape builders to accumulate boxes on each layer.
	 */
	static class VectorManhattanBox
	{
		private ImmutableElectricObject origin;
		private long lX, lY, hX, hY;

		public VectorManhattanBox(ImmutableElectricObject origin, long lX, long lY, long hX, long hY)
		{
			this.origin = origin;
			this.lX = lX;
			this.lY = lY;
			this.hX = hX;
			this.hY = hY;
		}
	}

	/**
	 * Class which defines a cached polygon (non-Manhattan).
	 */
	static class VectorPolygon extends VectorBase
	{
		private EPoint[] points;

		VectorPolygon(ImmutableElectricObject origin, Point2D[] points, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.points = new EPoint[points.length];
			for (int i = 0; i < points.length; i++)
			{
				Point2D p = points[i];
				this.points[i] = EPoint.fromGrid(databaseToGrid(p.getX()), databaseToGrid(p.getY()));
			}
		}

		public EPoint[] getPoints() { return points; }

		@Override
		boolean isFilled() { return true; }
	}

	/**
	 * Class which defines a cached line.
	 */
	static class VectorLine extends VectorBase
	{
		private long fX, fY, tX, tY;
		private int texture;

		VectorLine(ImmutableElectricObject origin, long fX, long fY, long tX, long tY, int texture, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.fX = fX;
			this.fY = fY;
			this.tX = tX;
			this.tY = tY;
			this.texture = texture;
		}

		VectorLine(ImmutableElectricObject origin, double fX, double fY, double tX, double tY, int texture, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.fX = databaseToGrid(fX);
			this.fY = databaseToGrid(fY);
			this.tX = databaseToGrid(tX);
			this.tY = databaseToGrid(tY);
			this.texture = texture;
		}

		public long getFromX() { return fX; }

		public long getFromY() { return fY; }

		public long getToX() { return tX; }

		public long getToY() { return tY; }

		public int getTexture() { return texture; }

		public String toString()
		{
			return "VectorLine("+fX+","+fY+")-to-("+tX+","+tY+")";
		}
	}

	/**
	 * Class which defines a cached circle (filled, opened, or thick).
	 */
	static class VectorCircle extends VectorBase
	{
		private long cX, cY, eX, eY;
		private int nature;

		VectorCircle(ImmutableElectricObject origin, double cX, double cY, double eX, double eY, int nature, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.cX = databaseToGrid(cX);
			this.cY = databaseToGrid(cY);
			this.eX = databaseToGrid(eX);
			this.eY = databaseToGrid(eY);
			this.nature = nature;
		}

		public long getCenterX() { return cX; }

		public long getCenterY() { return cY; }

		public long getEdgeX() { return eX; }

		public long getEdgeY() { return eY; }

		/**
		 * Method to describe the type of circle.
		 * @return the type of circle:
		 * 0 for a circle outline;
		 * 1 for a thick circle outline;
		 * 2 for a filled circle
		 */
		public int getNature() { return nature; }

		@Override
		boolean isFilled()
		{
			// true for disc nature
			return nature == 2;
		}
	}

	/**
	 * Class which defines a cached arc of a circle (normal or thick).
	 */
	static class VectorCircleArc extends VectorBase
	{
		private long cX, cY, eX1, eY1, eX2, eY2;
		private boolean thick;
		private boolean bigArc;

		VectorCircleArc(ImmutableElectricObject origin, double cX, double cY, double eX1, double eY1, double eX2, double eY2, boolean thick,
			boolean bigArc, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.cX = databaseToGrid(cX);
			this.cY = databaseToGrid(cY);
			this.eX1 = databaseToGrid(eX1);
			this.eY1 = databaseToGrid(eY1);
			this.eX2 = databaseToGrid(eX2);
			this.eY2 = databaseToGrid(eY2);
			this.thick = thick;
			this.bigArc = bigArc;
		}

		public long getCenterX() { return cX; }

		public long getCenterY() { return cY; }

		public long getEdge1X() { return eX1; }

		public long getEdge1Y() { return eY1; }

		public long getEdge2X() { return eX2; }

		public long getEdge2Y() { return eY2; }

		public boolean isThick() { return thick; }

		public boolean isBigArc() { return bigArc; }
	}

	/**
	 * Class which defines cached text.
	 */
	static class VectorText extends VectorBase
	{
		/** text is on a Cell */						public static final int TEXTTYPECELL = 1;
		/** text is on an Export */						public static final int TEXTTYPEEXPORT = 2;
		/** text is on a Node */						public static final int TEXTTYPENODE = 3;
		/** text is on an Arc */						public static final int TEXTTYPEARC = 4;
		/** text is on an Annotations */				public static final int TEXTTYPEANNOTATION = 5;
		/** text is on an Instances */					public static final int TEXTTYPEINSTANCE = 6;

		/** the text location */						private long cX, cY, wid, hei;
		/** the text style */							private Poly.Type style;
		/** the descriptor of the text */				private TextDescriptor descript;
		/** the text to draw */							private String str;
		/** the text height (in display units) */		private float height;
		/** the type of text (CELL, EXPORT, etc.) */	private int textType;
		/** valid for export text */					private PrimitivePort basePort;
		/** temporary name on node or arc */			private boolean tempName;

		VectorText(ImmutableElectricObject origin, Rectangle2D bounds, Poly.Type style, TextDescriptor descript, String str, int textType, Export e,
			Layer layer, boolean tempName)
		{
			super(origin, layer, null);
			this.cX = databaseToGrid(bounds.getX());
			this.cY = databaseToGrid(bounds.getY());
			this.wid = databaseToGrid(bounds.getWidth());
			this.hei = databaseToGrid(bounds.getHeight());
			this.style = style;
			this.descript = descript;
			this.str = str;
			this.textType = textType;
			if (e != null) basePort = e.getBasePort();
			this.tempName = tempName;

			height = 1;
			if (descript != null)
			{
				TextDescriptor.Size tds = descript.getSize();
				if (!tds.isAbsolute()) height = (float) tds.getSize();
			}
		}

		public float getHeight() { return height; }

		public boolean isTempName() { return tempName; }

		public TextDescriptor getTextDescriptor() { return descript; }

		public int getTextType() { return textType; }

		public long getCX() { return cX; }

		public long getCY() { return cY; }

		public long getWid() { return wid; }

		public long getHei() { return hei; }

		public Poly.Type getStyle() { return style; }

		public PrimitivePort getBasePort() { return basePort; }

		public String getString() { return str; }

		public String toString()
		{
			return "VectorText("+cX+"<=X<="+(cX+wid)+" and"+cY+"<=Y<="+(cY+hei)+"): "+str;
		}
	}

	/**
	 * Class which defines a cached cross (a dot, large or small).
	 */
	static class VectorCross extends VectorBase
	{
		private long x, y;
		private boolean small;

		VectorCross(ImmutableElectricObject origin, double x, double y, boolean small, Layer layer, EGraphics graphicsOverride)
		{
			super(origin, layer, graphicsOverride);
			this.x = databaseToGrid(x);
			this.y = databaseToGrid(y);
			this.small = small;
		}

		public long getCenterX() { return x; }

		public long getCenterY() { return y; }

		public boolean isSmall() { return small; }

		public String toString()
		{
			return "VectorCross "+getLayer().getName()+" ("+x+","+y+")";
		}
	}

	/**
	 * Class which defines a cached subcell reference.
	 */
	static class VectorSubCell implements RTBounds
	{
		private ImmutableNodeInst n;
		private CellId subCellId;
		private long offsetX, offsetY;
		private AbstractFixpRectangle bounds;
		private BitSet shownPorts = new BitSet();

		VectorSubCell(NodeInst ni, Point2D offset)
		{
			n = ni.getD();
			Cell subCell = (Cell) ni.getProto();
			subCellId = subCell.getId();
			offsetX = databaseToGrid(offset.getX());
			offsetY = databaseToGrid(offset.getY());
			bounds = ni.getBounds();
		}

		public long getOffsetX() { return offsetX; }

		public long getOffsetY() { return offsetY; }

		public CellId getCellId() { return subCellId; }

		public AbstractFixpRectangle getBounds() { return bounds; }

		public boolean isPortShown(int index) { return shownPorts.get(index); }

		public ImmutableNodeInst getNode() { return n; }
	}

	/**
	 * Class which holds the cell caches for a given cell.
	 * Since each cell is cached many times, once for every orientation on the screen,
	 * this object can hold many cell caches.
	 */
	class VectorCellDef
	{
		private CellId cellId;
		private ERectangle bounds;
		private float cellArea;
		private float cellMinSize;
        private TechId techId;
		private boolean isParameterized;
		private Map<Orientation, VectorCell> orientations;
		private List<VectorCellExport> exports;

		VectorCellDef(CellId cellId)
		{
			this.cellId = cellId;
			orientations = new HashMap<Orientation, VectorCell>();
			updateBounds(database.backup());
			CellBackup cellBackup = database.backup().getCell(cellId);
			if (cellBackup != null)
			{
				techId = cellBackup.cellRevision.d.techId;
				isParameterized = isCellParameterized(cellBackup.cellRevision);
			}
			clear();
			updateExports();
		}

		public CellId getCellId() { return cellId; }

		public ERectangle getBounds() { return bounds; }

		public float getArea() { return cellArea; }

		public float getMinimumSize() { return cellMinSize; }

		public List<VectorCellExport> getPortShapes()
		{
			if (exports == null) updateExports();
			return exports;
		}

		public VectorCell getAnyCell()
		{
			for (VectorCell vc : orientations.values())
			{
				if (vc.validStep == updateStep) return vc;
			}
			return null;
		}

		private boolean updateBounds(Snapshot snapshot)
		{
			ERectangle newBounds = snapshot.getCellBounds(cellId);
			bounds = newBounds;
			if (bounds != null)
			{
				cellArea = (float)(bounds.getWidth() * bounds.getHeight());
				cellMinSize = (float)Math.min(bounds.getWidth(), bounds.getHeight());
			} else
			{
				cellArea = 0;
				cellMinSize = 0;
			}
			for (VectorCell vc : orientations.values())
				vc.updateBounds();
			return true;
		}

		private void updateExports()
		{
			for (VectorCell vc : orientations.values())
				vc.clearExports();
			Cell cell = getCellFromId(cellId);
			if (cell == null)
			{
				exports = null;
				return;
			}

			// save export centers to detect hierarchical changes later
			exports = new ArrayList<VectorCellExport>();
			for (Iterator<Export> it = cell.getExports(); it.hasNext(); )
			{
				Export e = it.next();
				VectorCellExport vce = new VectorCellExport(e);
				exports.add(vce);
			}
		}

		private void updateVariables()
		{
			for (VectorCell vc : orientations.values())
				vc.updateVariables();
		}

		private void clear()
		{
			for (VectorCell vc : orientations.values())
				vc.clear();
			exports = null;
		}
	}

	VectorCellDef findCellGroup(CellId cellId)
	{
		int cellIndex = cellId.cellIndex;
		while (cellIndex >= cachedCells.size())
			cachedCells.add(null);
		VectorCellDef vcd = cachedCells.get(cellIndex);
		if (vcd == null)
		{
			vcd = new VectorCellDef(cellId);
			cachedCells.set(cellIndex, vcd);
		}
		return vcd;
	}

	/**
	 * Class which defines the exports on a cell (used to tell if they changed)
	 */
	static class VectorCellExport
	{
		private Poly.Point exportCtr;
		private ImmutableExport e;
		/** the text style */						private Poly.Type style;
		/** the descriptor of the text */			private TextDescriptor descript;
		/** the text height (in display units) */	private float height;
		private PrimitivePort basePort;

		VectorCellExport(Export e)
		{
			this.e = e.getD();
			this.descript = this.e.nameDescriptor;
			Poly portPoly = e.getNamePoly();
			assert portPoly.getPoints().length == 1;
			exportCtr = portPoly.getPoints()[0];

			style = Poly.Type.TEXTCENT;
			height = 1;
			if (descript != null)
			{
				style = descript.getPos().getPolyType();
				TextDescriptor.Size tds = descript.getSize();
				if (!tds.isAbsolute())
					height = (float) tds.getSize();
			}
			this.e = e.getD();
			basePort = e.getBasePort();
		}

		public int getChronIndex() { return e.exportId.chronIndex; }

		public String getName(boolean shortName)
		{
			String name = e.name.toString();
			if (shortName)
			{
				int len = name.length();
				for (int i = 0; i < len; i++)
				{
					char ch = name.charAt(i);
					if (TextUtils.isLetterOrDigit(ch)) continue;
					return name.substring(0, i);
				}
			}
			return name;
		}

		public Poly.Type getStyle() { return style; }

		public TextDescriptor getTextDescriptor() { return descript; }

		public float getHeight() { return height; }

		public PrimitivePort getBasePort() { return basePort; }
	}

	/**
	 * Class which defines a cached cell in a single orientation.
	 */
	class VectorCell
	{
		private final VectorCellDef vcd;
		private final Orientation orient;
		private long[] outlinePoints = new long[8];
		private long lX, lY, hX, hY;
		private int[] portCenters;
		private long validStep;
		private Map<Layer,List<VectorBase>> organizedShapes = new HashMap<Layer,List<VectorBase>>();
		private ArrayList<VectorBase> topOnlyShapes;
		private ArrayList<VectorSubCell> subCells = new ArrayList<VectorSubCell>();
		private RTNode<VectorSubCell> subCellTree = null;
		private boolean hasFadeColor;
		private int fadeColor;
		private float maxFeatureSize;
		private boolean fadeImage;
		private int fadeOffsetX, fadeOffsetY;
		private int[] fadeImageColors;
		private int fadeImageWid, fadeImageHei;

		/**
		 * Constructor to build a VectorCell for a given VectorCellDef and Orientation.
		 * Each VectorCellDef is associated with a specific Cell.
		 * Each VectorCell is associated with a particular Orientation of the Cell.
		 * @param vcd the VectorCellDef that this will cache.
		 * @param orient the Orientation of the VectorCellDef that this will cache.
		 */
		VectorCell(VectorCellDef vcd, Orientation orient)
		{
			this.vcd = vcd;
			this.orient = orient;
			if (USE_CELL_RTREE) subCellTree = RTNode.makeTopLevel();
			updateBounds();
		}

		/**
		 * Constructor (used by TechPalette).
		 */
		private VectorCell()
		{
			vcd = null;
			orient = null;
			topOnlyShapes = new ArrayList<VectorBase>();
		}

		/**
		 * Method to return the VectorCellDef that defines the Cell this is caching.
		 * @return the VectorCellDef that defines the Cell this is caching.
		 */
		public VectorCellDef getCellDef() { return vcd; }

		/**
		 * Method to return the Orientation of this cache.
		 * @return the Orientation of this cache.
		 */
		public Orientation getOrientation() { return orient; }

		/**
		 * Method to return the Cell instances in this cache as an R-Tree.
		 * @return the head of an R-Tree of cell instances (VectorSubCell objects).
		 * May be null if R-Trees are not being used.
		 */
		public RTNode<VectorSubCell> getSubCellTree() { return subCellTree; }

		/**
		 * Method to return the Cell instances in this cache as a List.
		 * This method is not valid if R-Trees are being used for cell instances.
		 * @return the List of cell instances (VectorSubCell objects).
		 */
		public List<VectorSubCell> getSubCells() { return subCells; }

		public void setFadeOffset(int oX, int oY)
		{
			fadeOffsetX = oX;
			fadeOffsetY = oY;
		}

		public int getFadeOffsetX() { return fadeOffsetX; }

		public int getFadeOffsetY() { return fadeOffsetY; }

		public boolean isFadeImage() { return fadeImage; }

		public void setFadeImage(boolean f) { fadeImage = f; }

		public void setFadeImageSize(int wid, int hei)
		{
			fadeImageWid = wid;
			fadeImageHei = hei;
		}

		public int getFadeImageWidth() { return fadeImageWid; }

		public int getFadeImageHeight() { return fadeImageHei; }

		public void setFadeColors(int[] colors) { fadeImageColors = colors; }

		public int[] getFadeColors() { return fadeImageColors; }

		public void setFadeColor(int color) { fadeColor = color; }

		public int getFadeColor() { return fadeColor; }

		public void setHasFadeColor(boolean h) { hasFadeColor = h; }

		public boolean hasFadeColor() { return hasFadeColor; }

		/**
		 * Method to return the low X coordinate of the cell bounds.
		 * @return the low X coordinate of the cell bounds.
		 */
		public long getLX() { return lX; }

		/**
		 * Method to return the high X coordinate of the cell bounds.
		 * @return the high X coordinate of the cell bounds.
		 */
		public long getHX() { return hX; }

		/**
		 * Method to return the low Y coordinate of the cell bounds.
		 * @return the low Y coordinate of the cell bounds.
		 */
		public long getLY() { return lY; }

		/**
		 * Method to return the high Y coordinate of the cell bounds.
		 * @return the high Y coordinate of the cell bounds.
		 */
		public long getHY() { return hY; }

		/**
		 * Method to return the coordinates of the Cell outline when drawing a "black box".
		 * @return the coordinates of the Cell outline when drawing a "black box".
		 */
		public long[] getOutlinePoints() { return outlinePoints; }

		public float getMaxFeatureSize() { return maxFeatureSize; }

		/**
		 * Method to return a List of Layers that are in the VectorCache, sorted by rendering order (depth).
		 * @return a List of Layers that are in this VectorCache.
		 */
		public List<Layer> getKnownLayers()
		{
			List<Layer> knownLayers = new ArrayList<Layer>();
			for(Layer lay : organizedShapes.keySet()) knownLayers.add(lay);
			Layer.getLayersSortedByRule(knownLayers, Layer.LayerSortingType.ByFunctionLevel);
			return knownLayers;
		}

		/**
		 * Method to return a List of shapes on a given Layer.
		 * @param layer the Layer to request.
		 * @return a List of VectorBase objects on that Layer.
		 */
		public List<VectorBase> getShapes(Layer layer) { return organizedShapes.get(layer); }

		private void buildCache(Cell cell)
		{
			updateBounds();
			clear();
			maxFeatureSize = 0;
			FixpTransform trans = orient.pureRotate();

			for(List<VectorManhattanBox> b : boxBuilders) b.clear();
			for(List<VectorManhattanBox> b : pureBoxBuilders) b.clear();

			// draw all arcs
			shapeBuilder.setup(cell.backup(), orient, USE_ELECTRICAL, WIPE_PINS, false, null);
			shapeBuilder.vc = this;
			shapeBuilder.hideOnLowLevel = false;
			shapeBuilder.textType = VectorText.TEXTTYPEARC;
			for (Iterator<ArcInst> arcs = cell.getArcs(); arcs.hasNext(); )
			{
				ArcInst ai = arcs.next();
				drawArc(ai, trans, this);
			}

			// draw all primitive nodes
			for (Iterator<NodeInst> nodes = cell.getNodes(); nodes.hasNext(); )
			{
				NodeInst ni = nodes.next();
				if (ni.isCellInstance()) continue;
				boolean hideOnLowLevel = ni.isVisInside() || Generic.isCellCenter(ni);
				if (!hideOnLowLevel) drawPrimitiveNode(ni, trans, this);
			}

			// draw all subcells
			for (Iterator<NodeInst> nodes = cell.getNodes(); nodes.hasNext(); )
			{
				NodeInst ni = nodes.next();
				if (!ni.isCellInstance()) continue;
				drawSubcell(ni, trans, this);
			}

			// add in anything "snuck" onto the cell
			CellId cellId = cell.getId();
			List<VectorBase> addThesePolys = addPolyToCell.get(cellId);
			if (addThesePolys != null)
			{
				for (VectorBase vb : addThesePolys)
					addShape(vb);
			}
			List<VectorLine> addTheseInsts = addInstToCell.get(cellId);
			if (addTheseInsts != null)
			{
				for (VectorLine vl : addTheseInsts)
					addShape(vl);
			}
			addBoxesFromBuilder(this, cell.getTechnology(), boxBuilders, false);
			addBoxesFromBuilder(this, cell.getTechnology(), pureBoxBuilders, true);

			// icon cells should not get greeked because of their contents
			if (cell.isIcon()) maxFeatureSize = 0;

			validStep = updateStep;
		}

		/**
		 * Method to add a shape to the by-layer shape collection.
		 * @param vb the shape to add (a VectorBase object).
		 */
		private void addShape(VectorBase vb)
		{
			Layer layer = vb.getLayer();
			if (layer == null) layer = Generic.tech().glyphLay;
			List<VectorBase> vbList = organizedShapes.get(layer);
			if (vbList == null) organizedShapes.put(layer, vbList = new ArrayList<VectorBase>());
			vbList.add(vb);
		}

		/**
		 * Method to return the List of shapes for a given Layer.
		 * @param layer the Layer in question.
		 * @return a List of VectorBase objects that describe shapes on the given Layer.
		 */
		private List<VectorBase> getShapeList(Layer layer)
		{
			if (layer == null) layer = Generic.tech().glyphLay;
			List<VectorBase> shapes = organizedShapes.get(layer);
			if (shapes == null) organizedShapes.put(layer, shapes = new ArrayList<VectorBase>());
			return shapes;
		}

		/**
		 * Method to update the bounds information when the cell size changes.
		 */
		private void updateBounds()
		{
			lX = lY = Integer.MAX_VALUE;
			hX = hY = Integer.MIN_VALUE;
			ERectangle bounds = vcd.bounds;
			if (bounds == null) return;
			double[] points = new double[8];
			points[0] = points[6] = bounds.getMinX();
			points[1] = points[3] = bounds.getMinY();
			points[2] = points[4] = bounds.getMaxX();
			points[5] = points[7] = bounds.getMaxY();
			orient.pureRotate().transform(points, 0, points, 0, 4);
			for (int i = 0; i < 4; i++)
			{
				long x = databaseToGrid(points[i * 2]);
				long y = databaseToGrid(points[i * 2 + 1]);
				lX = Math.min(lX, x);
				lY = Math.min(lY, y);
				hX = Math.max(hX, x);
				hY = Math.max(hY, y);
				outlinePoints[i * 2] = x;
				outlinePoints[i * 2 + 1] = y;
			}
		}

		private void clear()
		{
			clearExports();
			hasFadeColor = fadeImage = false;
			organizedShapes.clear();
			subCellTree = null;
			if (USE_CELL_RTREE) subCellTree = RTNode.makeTopLevel(); else
				subCells.clear();
			fadeImageColors = null;
		}

		private void addExport(Export e, FixpTransform trans)
		{
			Poly poly = e.getNamePoly();
			Rectangle2D rect = (Rectangle2D) poly.getBounds2D().clone();
			TextDescriptor descript = poly.getTextDescriptor();
			Poly.Type style = descript.getPos().getPolyType();
			style = Poly.rotateType(style, e);
			VectorText vt = new VectorText(e.getOriginalPort().getNodeInst().getD(),
				poly.getBounds2D(), style, descript, e.getName(), VectorText.TEXTTYPEEXPORT, e, null, false);
			topOnlyShapes.add(vt);

			// draw variables on the export
			Poly[] polys = e.getDisplayableVariables(rect, dummyWnd, true, true);
			drawTextPolys(e.getOriginalPort().getNodeInst().getD(), polys, trans, this, true, VectorText.TEXTTYPEEXPORT, false, false);
		}

		private void clearExports()
		{
			portCenters = null;
		}

		/**
		 * Method to update all Cell-level Variables.
		 */
		private void updateVariables()
		{
			Cell cell = getCellFromId(vcd.cellId);
			if (cell == null) return;

			if (topOnlyShapes != null)
			{
				for(int i=0; i<topOnlyShapes.size(); i++)
				{
					VectorBase vb = topOnlyShapes.get(i);
					if (vb.origin instanceof ImmutableCell)
					{
						int lastIndex = topOnlyShapes.size() - 1;
						if (i < lastIndex)
						{
							topOnlyShapes.set(i, topOnlyShapes.get(lastIndex));
							i--;
						}
						topOnlyShapes.remove(lastIndex);
					}
				}
			}
			Poly[] polys = cell.getDisplayableVariables(CENTERRECT, dummyWnd, true, true);
			drawTextPolys(cell.getD(), polys, DBMath.MATID, this, true, VectorText.TEXTTYPECELL, false, false);
		}

		public int[] getPortCenters()
		{
			if (portCenters == null) initPortCenters();
			return portCenters;
		}

		private void initPortCenters()
		{
			List<VectorCellExport> portShapes = vcd.getPortShapes();
			portCenters = new int[portShapes.size() * 2];
			FixpTransform trans = orient.pureRotate();
			Poly.Point tmpPt = Poly.fromFixp(0, 0);
			for (int i = 0, numPorts = portShapes.size(); i < numPorts; i++)
			{
				VectorCellExport vce = portShapes.get(i);
				trans.transform(vce.exportCtr, tmpPt);
				portCenters[i * 2] = (int)(tmpPt.getFixpX() >> FixpCoord.FRACTION_BITS);
				portCenters[i * 2 + 1] = (int)(tmpPt.getFixpY() >> FixpCoord.FRACTION_BITS);
			}
		}

		public ArrayList<VectorBase> getTopOnlyShapes()
		{
			if (topOnlyShapes == null) buildTopOnlyShapes();
			return topOnlyShapes;
		}

		private void buildTopOnlyShapes()
		{
			topOnlyShapes = new ArrayList<VectorBase>();
			Cell cell = getCellFromId(vcd.cellId);
			if (cell == null) return;

			// show cell variables
			Poly[] polys = cell.getDisplayableVariables(CENTERRECT, dummyWnd, true, true);
			drawTextPolys(cell.getD(), polys, DBMath.MATID, this, true, VectorText.TEXTTYPECELL, false, false);

			// draw nodes visible only inside
			FixpTransform trans = orient.pureRotate();
			shapeBuilder.setup(cell.backup(), orient, USE_ELECTRICAL, WIPE_PINS, false, null);
			shapeBuilder.vc = this;
			shapeBuilder.hideOnLowLevel = false;
			shapeBuilder.textType = VectorText.TEXTTYPEARC;
			for (Iterator<NodeInst> nodes = cell.getNodes(); nodes.hasNext(); )
			{
				NodeInst ni = nodes.next();
				if (ni.isCellInstance()) continue;
				boolean hideOnLowLevel = ni.isVisInside() || Generic.isCellCenter(ni);
				if (hideOnLowLevel) drawPrimitiveNode(ni, trans, this);
			}

			// draw exports and their variables
			for (Iterator<Export> it = cell.getExports(); it.hasNext(); )
			{
				Export e = it.next();
				addExport(e, trans);
			}
			Collections.sort(topOnlyShapes, shapeByLayer);
		}
	}

	private class ShapeBuilder extends AbstractShapeBuilder
	{
		private VectorCell vc;
		private boolean hideOnLowLevel;
		private int textType;
		private boolean pureLayer;

		@Override
		public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp)
		{
			switch (style)
			{
				case OPENED:
					addLine(numPoints, 0, layer, graphicsOverride);
					break;
				case OPENEDT1:
					addLine(numPoints, 1, layer, graphicsOverride);
					break;
				case OPENEDT2:
					addLine(numPoints, 2, layer, graphicsOverride);
					break;
				case OPENEDT3:
					addLine(numPoints, 3, layer, graphicsOverride);
					break;
				default:
					Poly.Point[] points = new Poly.Point[numPoints];
					for (int i = 0; i < numPoints; i++)
						points[i] = Poly.fromFixp(coords[i * 2], coords[i * 2 + 1]);
					Poly poly = new Poly(points);
					poly.setStyle(style);
					poly.setLayer(layer);
					poly.setGraphicsOverride(graphicsOverride);
					renderPoly(getCurObj(), poly, vc, hideOnLowLevel, textType, pureLayer, false);
					break;
			}
		}

		@Override
		public void addTextPoly(int numPoints, Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor)
		{
			Poly.Point[] points = new Poly.Point[numPoints];
			for (int i = 0; i < numPoints; i++)
				points[i] = Poly.fromFixp(coords[i * 2], coords[i * 2 + 1]);
			Poly poly = new Poly(points);
			poly.setStyle(style);
			poly.setLayer(layer);
			poly.setString(message);
			poly.setTextDescriptor(descriptor);
			renderPoly(getCurObj(), poly, vc, hideOnLowLevel, textType, pureLayer, false);
		}

		private void addLine(int numPoints, int lineType, Layer layer, EGraphics graphicsOverride)
		{
			List<VectorBase> shapes = hideOnLowLevel ? vc.topOnlyShapes : vc.getShapeList(layer);
			int x1 = (int) (coords[0] >> FixpCoord.FRACTION_BITS);
			int y1 = (int) (coords[1] >> FixpCoord.FRACTION_BITS);
			for (int i = 1; i < numPoints; i++)
			{
				int x2 = (int) (coords[i * 2] >> FixpCoord.FRACTION_BITS);
				int y2 = (int) (coords[i * 2 + 1] >> FixpCoord.FRACTION_BITS);
				VectorLine vl = new VectorLine(getCurObj(), x1, y1, x2, y2, lineType, layer, graphicsOverride);
				shapes.add(vl);
				x1 = x2;
				y1 = y2;
			}
		}

		@Override
		public void addBox(Layer layer)
		{
			List<VectorBase> shapes = hideOnLowLevel ? vc.topOnlyShapes : vc.getShapeList(layer);

			// convert coordinates
			long lX = (int) (coords[0] >> FixpCoord.FRACTION_BITS);
			long lY = (int) (coords[1] >> FixpCoord.FRACTION_BITS);
			long hX = (int) (coords[2] >> FixpCoord.FRACTION_BITS);
			long hY = (int) (coords[3] >> FixpCoord.FRACTION_BITS);
			int layerIndex = -1;
			if (vc.vcd != null && layer.getId().techId == vc.vcd.techId)
				layerIndex = layer.getIndex();
			if (layerIndex >= 0)
			{
				putBox(getCurObj(), layerIndex, pureLayer ? pureBoxBuilders : boxBuilders, lX, lY, hX, hY);
			} else
			{
				shapes.add(new VectorManhattan(getCurObj(), new long[]{lX, lY, hX, hY}, layer, null, pureLayer));
			}

			// ignore implant layers when computing largest feature size
			float minSize = (float) DBMath.gridToLambda(Math.min(hX - lX, hY - lY));
			if (layer != null)
			{
				Layer.Function fun = layer.getFunction();
				if (fun.isSubstrate()) minSize = 0;
			}
			vc.maxFeatureSize = Math.max(vc.maxFeatureSize, minSize);
		}
	}

	/** Creates a new instance of VectorCache */
	public VectorCache(EDatabase database)
	{
		this.database = database;
	}

	public VectorCell findVectorCell(CellId cellId, Orientation orient)
	{
		VectorCellDef vcd = findCellGroup(cellId);
		orient = orient.canonic();
		VectorCell vc = vcd.orientations.get(orient);
		if (vc == null)
		{
			vc = new VectorCell(vcd, orient);
			vcd.orientations.put(orient, vc);
		}
		return vc;
	}

	/**
	 * Method to find the Cell from a CellId.
	 * May have to examine the mutable database if the immutable database cannot find it
	 * (this happens when rendering is done in a change job).
	 * @param cellId the CellId to find.
	 * @return the Cell.
	 */
	public static Cell getCellFromId(CellId cellId)
	{
		Cell cell = VectorCache.theCache.database.getCell(cellId);
		if (cell == null)
		{
			String fullName = cellId.cellName.toString();
			Library lib = Library.getCurrent();
			cell = lib.findNodeProto(fullName);
		}
		return cell;
	}

	public VectorCell drawCell(CellId cellId, Orientation prevTrans, VarContext context, double scale, boolean makeTopLevel)
	{
		curScale = scale; // Fix it later. Multiple Strings positioning shouldn't use scale.
		Cell cell = getCellFromId(cellId);
		VectorCell vc = findVectorCell(cellId, prevTrans);
		VectorCellDef vcd = vc.vcd;
		if (vcd.isParameterized || vc.validStep != updateStep)
		{
			varContext = vcd.isParameterized ? context : null;
			if (cell == null)
			{
				if (Job.getDebug())
					System.out.println("Cell is null in VectorCell.drawCell"); // extra testing
			} else if (cell.isLinked())
			{
long startTime = 0;
if (DEBUG) startTime = System.currentTimeMillis();
				vc.buildCache(cell);
				if (makeTopLevel) vc.buildTopOnlyShapes();
if (DEBUG)
{
	long endTime = System.currentTimeMillis();
	System.out.println("REBUILT VECTOR CACHE FOR CELL " + cell.describe(false) + " Orientation '" + vc.orient + "'" +
		(vcd.isParameterized ? " (PARAMETERIZED)" : "") + " TOOK " + TextUtils.formatDouble((endTime - startTime)/1000.0) + " sec");
//	dumpCache(vc);
}
			}
		}
		return vc;
	}

	public static VectorBase[] drawNode(NodeInst ni)
	{
		VectorCache cache = new VectorCache(EDatabase.clientDatabase());
		VectorCell vc = cache.newDummyVectorCell();
		cache.shapeBuilder.setup(ni.getCellBackup(), null, USE_ELECTRICAL, WIPE_PINS, false, null);
		cache.shapeBuilder.vc = vc;
		cache.drawPrimitiveNode(ni, GenMath.MATID, vc);
		for(Layer layer : vc.organizedShapes.keySet())
		{
			List<VectorBase> vbList = vc.organizedShapes.get(layer);
			if (vbList != null)
				for(VectorBase vb : vbList)
					vc.topOnlyShapes.add(vb);
		}
		Collections.sort(vc.topOnlyShapes, shapeByLayer);
		return vc.topOnlyShapes.toArray(new VectorBase[vc.topOnlyShapes.size()]);
	}

	public static VectorBase[] drawPolys(ImmutableArcInst a, Poly[] polys)
	{
		VectorCache cache = new VectorCache(EDatabase.clientDatabase());
		VectorCell vc = cache.newDummyVectorCell();
		cache.drawPolys(a, polys, GenMath.MATID, vc, false, VectorText.TEXTTYPEARC, false);
		assert vc.topOnlyShapes.isEmpty();
		for(Layer layer : vc.organizedShapes.keySet())
		{
			List<VectorBase> vbList = vc.organizedShapes.get(layer);
			if (vbList != null)
				for(VectorBase vb : vbList)
					vc.topOnlyShapes.add(vb);
		}
		Collections.sort(vc.topOnlyShapes, shapeByLayer);
		return vc.topOnlyShapes.toArray(new VectorBase[vc.topOnlyShapes.size()]);
	}

	private VectorCell newDummyVectorCell()
	{
		return new VectorCell();
	}

	/**
	 * Method to insert a Manhattan rectangle into the vector cache for a Cell.
	 * @param lX the low X of the Manhattan rectangle.
	 * @param lY the low Y of the Manhattan rectangle.
	 * @param hX the high X of the Manhattan rectangle.
	 * @param hY the high Y of the Manhattan rectangle.
	 * @param layer the layer on which to draw the rectangle.
	 * @param cellId the Cell in which to insert the rectangle.
	 */
	public void addBoxToCell(ImmutableElectricObject origin, double lX, double lY, double hX, double hY, Layer layer, CellId cellId)
	{
		List<VectorBase> addToThisCell = addPolyToCell.get(cellId);
		if (addToThisCell == null)
		{
			addToThisCell = new ArrayList<VectorBase>();
			addPolyToCell.put(cellId, addToThisCell);
		}
		VectorManhattan vm = new VectorManhattan(origin, lX, lY, hX, hY, layer, null, false);
		addToThisCell.add(vm);
	}

	/**
	 * Method to insert a Manhattan rectangle into the vector cache for a Cell.
	 * @param lX the low X of the Manhattan rectangle.
	 * @param lY the low Y of the Manhattan rectangle.
	 * @param hX the high X of the Manhattan rectangle.
	 * @param hY the high Y of the Manhattan rectangle.
	 * @param cellId the Cell in which to insert the rectangle.
	 */
	public void addInstanceToCell(ImmutableElectricObject origin, double lX, double lY, double hX, double hY, CellId cellId)
	{
		List<VectorLine> addToThisCell = addInstToCell.get(cellId);
		if (addToThisCell == null)
		{
			addToThisCell = new ArrayList<VectorLine>();
			addInstToCell.put(cellId, addToThisCell);
		}

		// store the subcell
		addToThisCell.add(new VectorLine(origin, lX, lY, hX, lY, 0, null, instanceGraphics));
		addToThisCell.add(new VectorLine(origin, hX, lY, hX, hY, 0, null, instanceGraphics));
		addToThisCell.add(new VectorLine(origin, hX, hY, lX, hY, 0, null, instanceGraphics));
		addToThisCell.add(new VectorLine(origin, lX, hY, lX, lY, 0, null, instanceGraphics));
	}

	private static final PrimitivePortId busPinPortId = Schematics.tech().busPinNode.getPort(0).getId();

	/**
	 * Method to tell whether a Cell is parameterized.
	 * Code is taken from tool.drc.Quick.checkEnumerateProtos
	 * Could also use the code in tool.io.output.Spice.checkIfParameterized
	 * @param cellRevision the Cell to examine
	 * @return true if the cell has parameters
	 */
	private static boolean isCellParameterized(CellRevision cellRevision)
	{
		if (cellRevision.d.getNumParameters() > 0) return true;

		// look for any Java coded stuff (Logical Effort calls)
		for (ImmutableNodeInst n : cellRevision.nodes)
		{
			if (n instanceof ImmutableIconInst)
			{
				for (Iterator<Variable> vIt = ((ImmutableIconInst) n).getDefinedParameters(); vIt.hasNext(); )
				{
					Variable var = vIt.next();
					if (var.isCode()) return true;
				}
			}
			for (Iterator<Variable> vIt = n.getVariables(); vIt.hasNext(); )
			{
				Variable var = vIt.next();
				if (var.isCode()) return true;
			}
		}
		for (ImmutableArcInst a : cellRevision.arcs)
		{
			for (Iterator<Variable> vIt = a.getVariables(); vIt.hasNext(); )
			{
				Variable var = vIt.next();
				if (var.isCode()) return true;
			}
		}

		// bus pin appearance depends on parent Cell
		for (ImmutableExport e : cellRevision.exports)
		{
			if (e.originalPortId == busPinPortId) return true;
		}
		return false;
	}

	private void updateVectorCache(SnapshotAnalyze sa)
	{
		updateStep++;
ElapseTimer timer = null;
if (DEBUG)
{
	timer = (new ElapseTimer()).start();
	System.out.println("UPDATING VECTOR CACHE WITH THESE CHANGES");
	sa.dumpChanges();
}
		// remove caches for any deleted cells
		List<CellId> killedCells = sa.getDeletedCells();
		for(CellId cid : killedCells)
		{
            VectorCellDef vcd = cid.cellIndex < cachedCells.size() ? cachedCells.get(cid.cellIndex) : null;
            if (vcd == null) continue;
			vcd.clear();
			cachedCells.set(cid.cellIndex, null);
		}

		// update cache of exports
		for(CellId cid : sa.getChangedExportCells())
		{
			VectorCellDef vcd = findCellGroup(cid);
			vcd.updateExports();
		}

		// update cache of Cell variables that changed
		for(CellId cid : sa.getChangedVariableCells())
		{
			VectorCellDef vcd = findCellGroup(cid);
			vcd.updateVariables();
		}

		// now update cache for changed cells
		Set<CellId> cellsToUpdate = sa.changedCells();
		for(CellId cid : cellsToUpdate)
		{
			VectorCellDef vcd = findCellGroup(cid);
			if (vcd.isParameterized) continue;

			// see if bounds changes
			ERectangle newBounds = sa.getNewSnapshot().getCellBounds(cid);
			if (newBounds == null || !newBounds.equals(vcd.bounds))
				vcd.updateBounds(sa.getNewSnapshot());

			Cell cell = getCellFromId(cid);
if (DEBUG) System.out.println("  UPDATING ALL ORIENTATIONS OF CELL "+cell.describe(false));
			Set<ImmutableElectricObject> addedToCell = sa.getAdded(cid);
			Set<ImmutableElectricObject> removedFromCell = sa.getRemoved(cid);
			for(Orientation o : vcd.orientations.keySet())
			{
				VectorCell vc = vcd.orientations.get(o);
				if (vc.validStep != updateStep) continue;
if (DEBUG) System.out.println("  UPDATING CELL "+cell.describe(false)+", ORIENTATION '"+o+"'");

				// incremental update: first delete removed object
				if (removedFromCell.size() > 0)
				{
					for(Layer lay : vc.organizedShapes.keySet())
					{
						List<VectorBase> vbList = vc.organizedShapes.get(lay);
						if (vbList == null) continue;
						for(int i=0; i<vbList.size(); i++)
						{
							VectorBase vb = vbList.get(i);
							if (removedFromCell.contains(vb.origin))
							{
								int lastIndex = vbList.size() - 1;
								if (i < lastIndex)
								{
									vbList.set(i, vbList.get(lastIndex));
									i--;
								}
								vbList.remove(lastIndex);
							}
						}
					}
					if (vc.topOnlyShapes != null)
					{
						for(int i=0; i<vc.topOnlyShapes.size(); i++)
						{
							VectorBase vb = vc.topOnlyShapes.get(i);
							if (removedFromCell.contains(vb.origin))
							{
								int lastIndex = vc.topOnlyShapes.size() - 1;
								if (i < lastIndex)
								{
									vc.topOnlyShapes.set(i, vc.topOnlyShapes.get(lastIndex));
									i--;
								}
								vc.topOnlyShapes.remove(lastIndex);
							}
						}
					}

					List<VectorSubCell> removeThese = new ArrayList<VectorSubCell>();
					Iterator<VectorSubCell> sea;
					if (vc.subCellTree == null) sea = vc.subCells.iterator(); else
						sea = new RTNode.Search<VectorSubCell>(vc.subCellTree);
					for ( ; sea.hasNext(); )
					{
						VectorSubCell vsc = sea.next();
						if (removedFromCell.contains(vsc.n))
							removeThese.add(vsc);
					}
					if (vc.subCellTree == null)
					{
						for(VectorSubCell vsc : removeThese)
							vc.subCells.remove(vsc);
					} else
					{
						for(VectorSubCell vsc : removeThese)
							vc.subCellTree = RTNode.unLinkGeom(null, vc.subCellTree, vsc);
					}
if (DEBUG) System.out.println("    REMOVED " + removedFromCell.size() + " SHAPES.  TIME NOW " + timer.end());
				}

				// now include added objects
				for (List<VectorManhattanBox> b : boxBuilders) b.clear();
				for (List<VectorManhattanBox> b : pureBoxBuilders) b.clear();

				// TODO: this next line takes a long time in huge cells
if (DEBUG) System.out.println("    PREPARING SHAPEBUILDER.  TIME NOW " + timer.end());
				shapeBuilder.setup(cell.backup(), o, USE_ELECTRICAL, WIPE_PINS, false, null);
if (DEBUG) System.out.println("    PREPARED SHAPEBUILDER.  TIME NOW " + timer.end());
				shapeBuilder.vc = vc;
				shapeBuilder.hideOnLowLevel = false;
				shapeBuilder.textType = VectorText.TEXTTYPEARC;
				FixpTransform trans = o.pureRotate();
				for(ImmutableElectricObject obj : addedToCell)
				{
					if (obj instanceof ImmutableNodeInst)
					{
						ImmutableNodeInst ini = (ImmutableNodeInst)obj;
						NodeInst ni = cell.getNodeById(ini.nodeId);
						if (ini.isCellInstance())
						{
							drawSubcell(ni, trans, vc);
						} else
						{
							boolean hideOnLowLevel = ni.isVisInside() || Generic.isCellCenter(ni);
							if (!hideOnLowLevel)
							{
								drawPrimitiveNode(ni, trans, vc);
							}
						}

						// draw exports and their variables
						for (Iterator<Export> it = ni.getExports(); it.hasNext(); )
						{
							Export e = it.next();
							if (vc.topOnlyShapes == null)
								vc.topOnlyShapes = new ArrayList<VectorBase>();
							vc.addExport(e, trans);
						}
					} else
					{
						ImmutableArcInst iai = (ImmutableArcInst)obj;
						ArcInst ai = cell.getArcById(iai.arcId);
						drawArc(ai, trans, vc);
					}
				}
				addBoxesFromBuilder(vc, cell.getTechnology(), boxBuilders, false);
				addBoxesFromBuilder(vc, cell.getTechnology(), pureBoxBuilders, true);
				vc.validStep = updateStep;
if (DEBUG) System.out.println("    ADDED SHAPES.  TIME NOW " + timer.end());
			}
		}
if (DEBUG) System.out.println("FINISHED UPDATING CACHE.  TOOK " + timer.end());
	}

	/**
	 * Method to update the VectorCache when a change is made.
	 * @param topCells all cells visible in any window.
	 * @param sa the change that was made.
	 * @return a Set of CellIds that changed.
	 */
	public Set<CellId> updateChange(Set<CellId> topCells, SnapshotAnalyze sa)
	{
		Set<CellId> cellsToRedraw = new HashSet<CellId>();
		if (sa == null) return cellsToRedraw;
		updateVectorCache(sa);

		// explore top cells in EditWindows and mark all potentially visible cells
//		Set<CellId> visibleCells = new HashSet<CellId>();
//		for (CellId cellId : topCells)
//		{
//			if (database.getCell(cellId) == null) continue;
//			markDown(cellId, visibleCells);
//		}

		// make a set of cells that changed in any way
		Set<CellId> cellsThatChanged = sa.changedCells();
		for(CellId cid : sa.getChangedExportCells()) cellsThatChanged.add(cid);

		// make a set of cells that changed size
		Set<CellId> cellsThatChangedSize = sa.sizeChangedCells();

		// initialize the Set of cells that need to be redisplayed
//		for(CellId cid : sa.changedCells()) cellsToRedraw.add(cid);
//		for(CellId cid : sa.getChangedExportCells()) cellsToRedraw.add(cid);
		for(CellId cid : cellsThatChanged) cellsToRedraw.add(cid);

		// the new way to detect subcell changes that require redraw
		Map<CellId,Boolean> contentsState = new HashMap<CellId,Boolean>();
		for(CellId cid : topCells)
		{
			if (cellsToRedraw.contains(cid)) continue;
			CellRevision cellRevision = sa.getNewSnapshot().getCell(cid).cellRevision;
			if (contentsChanged(cid, cellRevision, cellsThatChanged, cellsThatChangedSize, contentsState, sa))
				cellsToRedraw.add(cid);
		}

		// the old way
//		for(CellId cid : visibleCells)
//		{
//			if (cellsToRedraw.contains(cid)) continue;
//			Cell cell = database.getCell(cid);
//			CellRevision cellRevision = sa.getNewSnapshot().getCell(cid).cellRevision;
//			for (ImmutableNodeInst n : cellRevision.nodes)
//			{
//				if (!(n.protoId instanceof CellId)) continue;
//				if (cell.isExpanded(n.nodeId))
//					cellsToRedraw.add(cid);
//			}
//		}

		return cellsToRedraw;
	}

	/**
	 * Method to recursively tell whether the contents of a cell has changed.
	 * @param cellId the cell to examine.
	 * @param cellRevision the CellRevision to use when examining the cell.
	 * @param cellsThatChanged a set if CellIds that changed.
	 * @return true if one of the nodes in this cell (or subcell) is expanded and changed (therefore requiring redraw of the top-level).
	 */
	private boolean contentsChanged(CellId cellId, CellRevision cellRevision, Set<CellId> cellsThatChanged,
		Set<CellId> cellsThatChangedSize, Map<CellId,Boolean> contentsState, SnapshotAnalyze sa)
	{
		Boolean decided = contentsState.get(cellId);
		if (decided != null) return decided.booleanValue();

		boolean changed = false;
		Cell cell = getCellFromId(cellId);
		for (ImmutableNodeInst n : cellRevision.nodes)
		{
			if (!(n.protoId instanceof CellId)) continue;
			if (cell.isExpanded(n.nodeId))
			{
				// expanded: see if subcell changed at all
				try
				{
					if (cellsThatChanged.contains(n.protoId)) { changed = true;   break; }
					if (contentsChanged((CellId)n.protoId, sa.getNewSnapshot().getCell((CellId)n.protoId).cellRevision,
							//cellRevision, 
							cellsThatChanged, cellsThatChangedSize, contentsState, sa)) { changed = true;   break; }
				}
				catch (Error e)
				{
					System.out.println("Error StackOverflowError");
				}
			} else
			{
				// unexpanded: see if subcell changed size
				if (cellsThatChangedSize.contains(n.protoId)) { changed = true;   break; }
			}
		}
		contentsState.put(cellId, Boolean.valueOf(changed));
		return changed;
	}

//	private void markDown(CellId cellId, Set<CellId> visibleCells)
//	{
//		if (visibleCells.contains(cellId)) return;
//
//		visibleCells.add(cellId);
//		Cell cell = database.getCell(cellId);
//		for (Iterator<CellUsage> it = cell.getUsagesIn(); it.hasNext(); )
//		{
//			CellUsage cu = it.next();
//			markDown(cu.protoId, visibleCells);
//		}
//	}

	public void forceRedraw()
	{
		boolean clearCache, clearFadeImages;
		synchronized (this)
		{
			clearCache = this.clearCache;
			this.clearCache = false;
			clearFadeImages = this.clearFadeImages;
			this.clearFadeImages = false;
		}
		if (clearCache || clearFadeImages)
		{
			Snapshot snapshot = database.backup();
			for (int cellIndex = 0, size = cachedCells.size(); cellIndex < size; cellIndex++)
			{
				VectorCellDef vcd = cachedCells.get(cellIndex);
				if (vcd == null) continue;
				if (clearCache)
					vcd.clear();
				if (clearFadeImages)
				{
					for (VectorCell vc : vcd.orientations.values())
					{
						vc.fadeImageColors = null;
						vc.fadeImage = false;
						vc.hasFadeColor = false;
					}
				}
				ERectangle cellBounds = snapshot.getCellBounds(cellIndex);
				if (cellBounds != null && !cellBounds.equals(vcd.bounds))
					vcd.updateBounds(snapshot);
			}
		}
	}

	/**
	 * Method called when it is necessary to clear cache.
	 * This is called when the technology parameters change and everything must be recalculated.
	 */
	public synchronized void clearCache()
	{
		clearCache = true;
		forceRedraw();			// TODO: this fixes Bug #3821, but it might not be right to call here
	}

	/**
	 * Method called when visible layers have changed.
	 * Removes all "greeked images" from cached cells.
	 */
	public synchronized void clearFadeImages()
	{
		clearFadeImages = true;
	}

	private static long databaseToGrid(double lambdaValue)
	{
		return DBMath.lambdaToGrid(lambdaValue);
	}

	private void addBoxesFromBuilder(VectorCell vc, Technology tech, ArrayList<List<VectorManhattanBox>> boxBuilders, boolean pureArray)
	{
		int limit = Math.min(boxBuilders.size(), tech.getNumLayers());
		for (int layerIndex = 0; layerIndex < limit; layerIndex++)
		{
			List<VectorManhattanBox> b = boxBuilders.get(layerIndex);
			if (b.size() == 0) continue;
			Layer layer = tech.getLayer(layerIndex);
			for(VectorManhattanBox vmb : b)
			{
				VectorManhattan vm = new VectorManhattan(vmb.origin, vmb.lX, vmb.lY, vmb.hX, vmb.hY, layer, null, pureArray);
				vc.addShape(vm);
			}
		}
	}

	/**
	 * Comparator class for sorting VectorBase objects by their layer depth.
	 */
	public static Comparator<VectorBase> shapeByLayer = new Comparator<VectorBase>()
	{
		/**
		 * Method to sort VectorBase objects by their layer depth.
		 */
		public int compare(VectorBase vb1, VectorBase vb2)
		{
			if (vb1.isFilled() != vb2.isFilled())
				return vb1.isFilled() ? 1 : -1;
			int level1 = 1000, level2 = 1000;
			boolean isContact1 = false;
			boolean isContact2 = false;
			if (vb1.layer != null)
			{
				Layer.Function fun = vb1.layer.getFunction();
				level1 = fun.getLevel();
				isContact1 = fun.isContact();
			}
			if (vb2.layer != null)
			{
				Layer.Function fun = vb2.layer.getFunction();
				level2 = fun.getLevel();
				isContact2 = fun.isContact();
			}
			if (isContact1 != isContact2)
				return isContact1 ? -1 : 1;
			return level1 - level2;
		}
	};
	
	/**
	 * Method to cache a NodeInst.
	 * @param ni the NodeInst to cache.
	 * @param trans the transformation of the NodeInst to the parent Cell.
	 * @param vc the cached cell in which to place the NodeInst.
	 */
	private void drawSubcell(NodeInst ni, FixpTransform trans, VectorCell vc)
	{
		FixpTransform localTrans = ni.rotateOut(trans);

		// draw the node
		assert ni.isCellInstance();

		// cell instance: record a call to the instance
		PolyBase.Point ctrShift = PolyBase.from(ni.getAnchorCenter());
		localTrans.transform(ctrShift, ctrShift);
		VectorSubCell vsc = new VectorSubCell(ni, ctrShift);
		vsc.shownPorts.clear();
		if (vc.subCellTree == null) vc.subCells.add(vsc); else
			vc.subCellTree = RTNode.linkGeom(null, vc.subCellTree, vsc);

		// show the ports that are not further exported or connected
		for (Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
		{
			Connection con = it.next();
			PortInst pi = con.getPortInst();
			Export e = (Export) pi.getPortProto();
			if (!e.isAlwaysDrawn())
				vsc.shownPorts.set(e.getId().getChronIndex());
		}
		for (Iterator<Export> it = ni.getExports(); it.hasNext(); )
		{
			Export exp = it.next();
			PortInst pi = exp.getOriginalPort();
			Export e = (Export) pi.getPortProto();
			if (!e.isAlwaysDrawn())
				vsc.shownPorts.set(e.getId().getChronIndex());
		}

		// draw any displayable variables on the instance
		Poly[] polys = ni.getDisplayableVariables(dummyWnd, true);
		drawTextPolys(ni.getD(), polys, localTrans, vc, false, VectorText.TEXTTYPENODE, false, !ni.isUsernamed());
	}

	/**
	 * Method to cache a NodeInst.
	 * @param ni the NodeInst to cache.
	 * @param trans the transformation of the NodeInst to the parent Cell.
	 * @param vc the cached cell in which to place the NodeInst.
	 */
	private void drawPrimitiveNode(NodeInst ni, FixpTransform trans, VectorCell vc)
	{
		assert !ni.isCellInstance();
		PrimitiveNode pn = (PrimitiveNode)ni.getProto();
		FixpTransform localTrans = ni.rotateOut(trans);

		// draw the node primitive: save it
		shapeBuilder.textType = pn == Generic.tech().invisiblePinNode ? VectorText.TEXTTYPEANNOTATION : VectorText.TEXTTYPENODE;
		shapeBuilder.pureLayer = (ni.getFunction() == PrimitiveNode.Function.NODE);
		shapeBuilder.hideOnLowLevel = ni.isVisInside() || pn == Generic.tech().cellCenterNode;
		pn.genShape(shapeBuilder, ni.getD());
		drawTextPolys(ni.getD(), ni.getDisplayableVariables(dummyWnd, true), localTrans, vc,
			shapeBuilder.hideOnLowLevel, shapeBuilder.textType, shapeBuilder.pureLayer, !ni.isUsernamed());
	}

	/**
	 * Method to cache an ArcInst.
	 * @param ai the ArcInst to cache.
	 * @param trans the transformation of the ArcInst to the parent cell.
	 * @param vc the cached cell in which to place the ArcInst.
	 */
	private void drawArc(ArcInst ai, FixpTransform trans, VectorCell vc)
	{
		// draw the arc
		ArcProto ap = ai.getProto();
		shapeBuilder.pureLayer = (ap.getNumArcLayers() == 1);
		shapeBuilder.genShapeOfArc(ai.getD());
		drawTextPolys(ai.getD(), ai.getDisplayableVariables(dummyWnd, true), trans, vc, false, VectorText.TEXTTYPEARC, false, !ai.isUsernamed());
	}

	/**
	 * Method to cache an array of polygons.
	 * @param polys the array of polygons to cache.
	 * @param trans the transformation to apply to each polygon.
	 * @param vc the cached cell in which to place the polygons.
	 * @param hideOnLowLevel true if the polygons should be marked such that they are not visible on lower levels of hierarchy.
	 * @param pureLayer true if these polygons come from a pure layer node.
	 */
	private void drawPolys(ImmutableElectricObject origin, Poly[] polys, FixpTransform trans, VectorCell vc, boolean hideOnLowLevel, int textType, boolean pureLayer)
	{
		if (polys == null) return;
		for (int i = 0; i < polys.length; i++)
		{
			// get the polygon and transform it
			Poly poly = polys[i];
			if (poly == null) continue;

			// transform the bounds
			poly.transform(trans);

			// render the polygon
			renderPoly(origin, poly, vc, hideOnLowLevel, textType, pureLayer, false);
		}
	}

	/**
	 * Method to cache an array of polygons.
	 * @param polys the array of polygons to cache.
	 * @param trans the transformation to apply to each polygon.
	 * @param vc the cached cell in which to place the polygons.
	 * @param hideOnLowLevel true if the polygons should be marked such that they are not visible on lower levels of hierarchy.
	 * @param pureLayer true if these polygons come from a pure layer node.
	 * @param tempOwnerName true if owner object has temporary name.
	 */
	private void drawTextPolys(ImmutableElectricObject origin, Poly[] polys, FixpTransform trans, VectorCell vc, boolean hideOnLowLevel, int textType, boolean pureLayer, boolean tempOwnerName)
	{
		if (polys == null) return;
		for (int i = 0; i < polys.length; i++)
		{
			// get the polygon and transform it
			Poly poly = polys[i];
			if (poly == null) continue;

			// transform the bounds
			poly.transform(trans);

			// render the polygon
			renderPoly(origin, poly, vc, hideOnLowLevel, textType, pureLayer, tempOwnerName);
		}
	}

	/**
	 * Method to cache a Poly.
	 * @param poly the polygon to cache.
	 * @param vc the cached cell in which to place the polygon.
	 * @param hideOnLowLevel true if the polygon should be marked such that it is not visible on lower levels of hierarchy.
	 * @param pureLayer true if the polygon comes from a pure layer node.
	 * @param tempOwnerName true if owner object has temporary name.
	 */
	private void renderPoly(ImmutableElectricObject origin, Poly poly, VectorCell vc, boolean hideOnLowLevel, int textType,
		boolean pureLayer, boolean tempOwnerName)
	{
		// now draw it
		Point2D[] points = poly.getPoints();
		Layer layer = poly.getLayer();
		EGraphics graphicsOverride = poly.getGraphicsOverride();
		Poly.Type style = poly.getStyle();
		List<VectorBase> shapes;
		if (hideOnLowLevel)
		{
			if (vc.topOnlyShapes == null)
				vc.topOnlyShapes = new ArrayList<VectorBase>();
			shapes = vc.topOnlyShapes;
		} else
		{
			shapes = vc.getShapeList(layer);
		}
		if (style == Poly.Type.FILLED)
		{
			Rectangle2D bounds = poly.getBox();
			if (bounds != null)
			{
				// convert coordinates
				double lX = bounds.getMinX();
				double hX = bounds.getMaxX();
				double lY = bounds.getMinY();
				double hY = bounds.getMaxY();
				float minSize = (float) Math.min(hX - lX, hY - lY);
				int layerIndex = -1;
				if (layer != null && graphicsOverride == null)
				{
					if (vc.vcd != null && layer.getId().techId == vc.vcd.techId)
						layerIndex = layer.getIndex();
				}
				if (layerIndex >= 0)
				{
					putBox(origin, layerIndex, pureLayer ? pureBoxBuilders : boxBuilders,
						databaseToGrid(lX), databaseToGrid(lY), databaseToGrid(hX), databaseToGrid(hY));
				} else
				{
					VectorManhattan vm = new VectorManhattan(origin, lX, lY, hX, hY, layer, graphicsOverride, pureLayer);
					shapes.add(vm);
				}

				// ignore implant layers when computing largest feature size
				if (layer != null)
				{
					Layer.Function fun = layer.getFunction();
					if (fun.isSubstrate()) minSize = 0;
				}
				vc.maxFeatureSize = Math.max(vc.maxFeatureSize, minSize);
				return;
			}
			VectorPolygon vp = new VectorPolygon(origin, points, layer, graphicsOverride);
			shapes.add(vp);
			return;
		}
		if (style == Poly.Type.CROSSED)
		{
			VectorLine vl1 = new VectorLine(origin, points[0].getX(), points[0].getY(),
				points[1].getX(), points[1].getY(), 0, layer, graphicsOverride);
			VectorLine vl2 = new VectorLine(origin, points[1].getX(), points[1].getY(),
				points[2].getX(), points[2].getY(), 0, layer, graphicsOverride);
			VectorLine vl3 = new VectorLine(origin, points[2].getX(), points[2].getY(),
				points[3].getX(), points[3].getY(), 0, layer, graphicsOverride);
			VectorLine vl4 = new VectorLine(origin, points[3].getX(), points[3].getY(),
				points[0].getX(), points[0].getY(), 0, layer, graphicsOverride);
			VectorLine vl5 = new VectorLine(origin, points[0].getX(), points[0].getY(),
				points[2].getX(), points[2].getY(), 0, layer, graphicsOverride);
			VectorLine vl6 = new VectorLine(origin, points[1].getX(), points[1].getY(),
				points[3].getX(), points[3].getY(), 0, layer, graphicsOverride);
			shapes.add(vl1);
			shapes.add(vl2);
			shapes.add(vl3);
			shapes.add(vl4);
			shapes.add(vl5);
			shapes.add(vl6);
			return;
		}
		if (style.isText())
		{
			Rectangle2D bounds = poly.getBounds2D();
			TextDescriptor descript = poly.getTextDescriptor();
			String str = poly.getString();
			assert graphicsOverride == null;
			DisplayedText dt = poly.getDisplayedText();
			boolean tempName = tempOwnerName && dt != null && (dt.getVariableKey() == NodeInst.NODE_NAME || dt.getVariableKey() == ArcInst.ARC_NAME);
			VectorText vt = new VectorText(origin, bounds, style, descript, str, textType, null, layer, tempName);
			shapes.add(vt);
			vc.maxFeatureSize = Math.max(vc.maxFeatureSize, vt.height);
			return;
		}
		if (style == Poly.Type.CLOSED || style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3)
		{
			int lineType = 0;
			if (style == Poly.Type.OPENEDT1) lineType = 1; else
				if (style == Poly.Type.OPENEDT2) lineType = 2; else
					if (style == Poly.Type.OPENEDT3) lineType = 3;

			for (int j = 1; j < points.length; j++)
			{
				Point2D oldPt = points[j - 1];
				Point2D newPt = points[j];
				VectorLine vl = new VectorLine(origin, oldPt.getX(), oldPt.getY(),
					newPt.getX(), newPt.getY(), lineType, layer, graphicsOverride);
				shapes.add(vl);
			}
			if (style == Poly.Type.CLOSED)
			{
				Point2D oldPt = points[points.length - 1];
				Point2D newPt = points[0];
				VectorLine vl = new VectorLine(origin, oldPt.getX(), oldPt.getY(),
					newPt.getX(), newPt.getY(), lineType, layer, graphicsOverride);
				shapes.add(vl);
			}
			return;
		}
		if (style == Poly.Type.VECTORS)
		{
			for (int j = 0; j < points.length; j += 2)
			{
				Point2D oldPt = points[j];
				Point2D newPt = points[j + 1];
				VectorLine vl = new VectorLine(origin, oldPt.getX(), oldPt.getY(),
					newPt.getX(), newPt.getY(), 0, layer, graphicsOverride);
				shapes.add(vl);
			}
			return;
		}
		if (style == Poly.Type.CIRCLE)
		{
			VectorCircle vci = new VectorCircle(origin, points[0].getX(), points[0].getY(),
				points[1].getX(), points[1].getY(), 0, layer, graphicsOverride);
			shapes.add(vci);
			return;
		}
		if (style == Poly.Type.THICKCIRCLE)
		{
			VectorCircle vci = new VectorCircle(origin, points[0].getX(), points[0].getY(),
				points[1].getX(), points[1].getY(), 1, layer, graphicsOverride);
			shapes.add(vci);
			return;
		}
		if (style == Poly.Type.DISC)
		{
			VectorCircle vci = new VectorCircle(origin, points[0].getX(), points[0].getY(), points[1].getX(),
				points[1].getY(), 2, layer, graphicsOverride);
			shapes.add(vci);
			return;
		}
		if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC)
		{
			int startAngle = GenMath.figureAngle(points[0], points[1]);
			int endAngle = GenMath.figureAngle(points[0], points[2]);
			if (startAngle < endAngle) startAngle += 3600;
			boolean bigArc = startAngle - endAngle > 1800;
			VectorCircleArc vca = new VectorCircleArc(origin, points[0].getX(), points[0].getY(),
				points[1].getX(), points[1].getY(), points[2].getX(), points[2].getY(),
					style == Poly.Type.THICKCIRCLEARC, bigArc, layer, graphicsOverride);
			shapes.add(vca);
			return;
		}
		if (style == Poly.Type.CROSS)
		{
			// draw the cross
			VectorCross vcr = new VectorCross(origin, points[0].getX(), points[0].getY(), true, layer, graphicsOverride);
			shapes.add(vcr);
			return;
		}
		if (style == Poly.Type.BIGCROSS)
		{
			// draw the big cross
			VectorCross vcr = new VectorCross(origin, points[0].getX(), points[0].getY(), false, layer, graphicsOverride);
			shapes.add(vcr);
			return;
		}
	}

	private static void putBox(ImmutableElectricObject origin, int layerIndex, ArrayList<List<VectorManhattanBox>> boxBuilders,
		long lX, long lY, long hX, long hY)
	{
		while (layerIndex >= boxBuilders.size())
		{
			boxBuilders.add(new ArrayList<VectorManhattanBox>());
		}
		List<VectorManhattanBox> b = boxBuilders.get(layerIndex);
		b.add(new VectorManhattanBox(origin, lX, lY, hX, hY));
	}

//	private void dumpCache(VectorCell vc)
//	{
//		Cell cell = database.getCell(vc.vcd.cellId);
//		System.out.println("Cell " + cell.describe(false)+", Orientation '"+vc.orient+"':");
//		if (vc.topOnlyShapes != null)
//		{
//			for(VectorBase vb : vc.topOnlyShapes)
//				System.out.println("   Top-Shape from "+SnapshotAnalyze.describeImmutableObject(cell, vb.origin)+": "+vb);
//		}
//		for(VectorBase vb : vc.shapes)
//		{
//			System.out.println("   Shape from "+SnapshotAnalyze.describeImmutableObject(cell, vb.origin)+": "+vb);
//		}
//		Iterator<VectorSubCell> sea;
//		if (vc.subCellTree == null) sea = vc.subCells.iterator(); else
//			sea = new RTNode.Search<VectorSubCell>(vc.subCellTree);
//		for ( ; sea.hasNext(); )
//		{
//			VectorSubCell vsc = sea.next();
//			System.out.println("   Subcell "+SnapshotAnalyze.describeImmutableObject(cell, vsc.n));
//		}
//	}
}
