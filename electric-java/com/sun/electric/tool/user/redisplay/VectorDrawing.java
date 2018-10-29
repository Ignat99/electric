/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VectorDrawing.java
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

import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableDouble;
import com.sun.electric.util.math.Orientation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to do rapid redraw by caching the vector coordinates of all objects.
 */
class VectorDrawing {
	private static final boolean TAKE_STATS = false;
	private static final int MAXGREEKSIZE = 25;
	private static final int SCALE_SH = 20;

	/** the rendering object */										private PixelDrawing offscreen;
	/** the window scale */											private float scale;
	/** the window scale */											private float scale_;
	/** the window scale and pan factor */							private float factorX, factorY;
																	private int factorX_, factorY_;
																	private int scale_int;
	/** true if "peeking" and expanding to the bottom */			private boolean fullInstantiate;
	/** A List of NodeInsts to the cell being in-place edited. */	private List<NodeInst> inPlaceNodePath;
	/** The current cell being in-place edited. */					private Cell inPlaceCurrent;
	/** time that rendering started */								private ElapseTimer timer = ElapseTimer.createInstance();
	/** true if the user has been told of delays */					private boolean takingLongTime;
	/** true to stop rendering */									private boolean stopRendering;
	/** the half-sizes of the window (in pixels) */					private int szHalfWidth, szHalfHeight;
	/** the screen clipping */										private int screenLX, screenHX, screenLY, screenHY;
	/** statistics */												private int boxCount, tinyBoxCount, lineBoxCount, lineCount, polygonCount;
	/** statistics */												private int crossCount, textCount, circleCount, arcCount;
	/** statistics */												private int subCellCount, tinySubCellCount, invisSubCellCount;
	/** object size visibility threshold */							private float objectVisibleThreshold;
	/** object size threshold: above is drawn, below is greeked */	private float objectGreekThreshold;
	/** true to use cell greeking images */							private boolean useCellGreekingImages;
	/** the threshold of text sizes */								private float maxTextSize;
	/** the maximum cell size above which no greeking */			private float maxCellSize;

	/** temporary objects (saves allocation) */						private Point tempPt1 = new Point(), tempPt2 = new Point();
	/** temporary objects (saves allocation) */						private Point tempPt3 = new Point();
	/** temporary object (saves allocation) */						private Rectangle tempRect = new Rectangle();
	/** temp rectangle for transformations */						private FixpRectangle tempFixpRect = FixpRectangle.fromFixpDiagonal(0, 0, 0, 0);

	/** the object that draws the rendered screen */				private static VectorDrawing topVD;
	/** location for debugging icon displays */						private static int debugXP, debugYP;

	// ************************************* TOP LEVEL *************************************

	/**
	 * Constructor creates a VectorDrawing object for a given EditWindow.
	 * @param wnd the EditWindow associated with this VectorDrawing.
	 */
	public VectorDrawing(boolean useCellGreekingImages) {
		this.useCellGreekingImages = useCellGreekingImages;
	}

	/**
	 * Main entry point for drawing a cell.
	 * @param offscreen offscreen buffer
	 * @param scale edit window scale
	 * @param offset the offset factor for this window
	 * @param cell the cell to draw
	 * @param fullInstantiate true to draw all the way to the bottom of the hierarchy.
	 * @param inPlaceNodePath a List of NodeInsts to the cell being in-place edited
	 * @param screenLimit the area in the cell to display (null to show all).
	 */
	public void render(PixelDrawing offscreen, double scale, Point2D offset, Cell cell, boolean fullInstantiate,
			List<NodeInst> inPlaceNodePath, Cell inPlaceCurrent, Rectangle screenLimit, VarContext context,
			double greekSizeLimit, double greekCellSizeLimit, LayerVisibility lv) {
		// see if any layers are being highlighted/dimmed
		this.offscreen = offscreen;
		offscreen.highlightingLayers = false;
		for (Iterator<Layer> it = Technology.getCurrent().getLayers(); it.hasNext();) {
			Layer layer = it.next();
			if (lv.isHighlighted(layer)) {
				offscreen.highlightingLayers = true;
				break;
			}
		}

		// set size limit
		Dimension sz = offscreen.getSize();
		this.scale = (float) scale;
		scale_ = (float) (scale / DBMath.GRID);
		objectGreekThreshold = (float) greekSizeLimit / this.scale;
		objectVisibleThreshold = (float) 1.0 / this.scale;
		maxTextSize = (float) (objectGreekThreshold / PixelDrawing.dp.globalTextScale);
		double screenArea = sz.getWidth() / scale * sz.getHeight() / scale;
		maxCellSize = (float) (greekCellSizeLimit * screenArea);

		// statistics
		timer.start();
		long initialUsed = 0;
		if (TAKE_STATS && Job.getDebug()) initialUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		takingLongTime = false;
		boxCount = tinyBoxCount = lineBoxCount = lineCount = polygonCount = 0;
		crossCount = textCount = circleCount = arcCount = 0;
		subCellCount = tinySubCellCount = invisSubCellCount = 0;

		// draw recursively
		this.fullInstantiate = fullInstantiate;
		this.inPlaceNodePath = inPlaceNodePath;
		this.inPlaceCurrent = inPlaceCurrent;
		szHalfWidth = sz.width / 2;
		szHalfHeight = sz.height / 2;
		screenLX = 0;
		screenHX = sz.width;
		screenLY = 0;
		screenHY = sz.height;
		factorX = (float) (offset.getX() * DBMath.GRID - szHalfWidth / scale_);
		factorY = (float) (offset.getY() * DBMath.GRID + szHalfHeight / scale_);
		factorX_ = (int) factorX;
		factorY_ = (int) factorY;
		scale_int = (int) (scale_ * (1 << SCALE_SH));
		if (screenLimit != null) {
			screenLX = screenLimit.x;
			if (screenLX < 0) screenLX = 0;
			screenHX = screenLimit.x + screenLimit.width;
			if (screenHX >= sz.width) screenHX = sz.width - 1;
			screenLY = screenLimit.y;
			if (screenLY < 0) screenLY = 0;
			screenHY = screenLimit.y + screenLimit.height;
			if (screenHY >= sz.height) screenHY = sz.height - 1;
		}

		// draw the screen, starting with the top cell
		stopRendering = false;
		try {
			VectorCache.VectorCell topVC = drawCell(cell, Orientation.IDENT, context, true);
			topVD = this;

long startTime = 0;
if (VectorCache.DEBUG) startTime = System.currentTimeMillis();
			render(topVC, 0, 0, context, 0, lv);
			drawList(0, 0, topVC.getTopOnlyShapes(), 0, false);
if (VectorCache.DEBUG) System.out.println("REDISPLAY TOOK " + TextUtils.formatDouble((System.currentTimeMillis() - startTime)/1000.0) + " sec");
		} catch (AbortRenderingException e) {}
		topVD = null;

		if (takingLongTime) {
			TopLevel.setBusyCursor(false);
			System.out.println("Done");
		}

		if (TAKE_STATS && Job.getDebug()) {
			long curUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			long memUsed = curUsed - initialUsed;
			timer.end();
			System.out.println("Time to render: " + timer + "    Memory Used: " + memUsed);
			System.out.println("   Rendered " + boxCount + " boxes (" + tinyBoxCount + " tiny, " + lineBoxCount +
				" lines), " + lineCount + " lines, " + polygonCount + " polys, " + crossCount + " crosses, " +
				textCount + " texts, " + circleCount + " circles, " + arcCount + " arcs, " + subCellCount +
				" subcells (" + tinySubCellCount + " tiny, " + invisSubCellCount + " invisible)");
		}
	}

	/**
	 * Main entry point for drawing a tech menu entry.
	 * @param offscreen offscreen buffer
	 * @param scale edit window scale
	 * @param offset the offset factor for this window
	 * @param shapes shapes of tech menu
	 */
	public void render(PixelDrawing offscreen, double scale, Point2D offset, VectorCache.VectorBase[] shapes) {
		// set colors to use
		PixelDrawing.textGraphics = PixelDrawing.textGraphics.withColor(PixelDrawing.gp.getColor(User.ColorPrefType.TEXT));

		// see if any layers are being highlighted/dimmed
		this.offscreen = offscreen;

		// set size limit
		Dimension sz = offscreen.getSize();
		this.scale = (float) scale;
		scale_ = (float) (scale / DBMath.GRID);

		// draw recursively
		szHalfWidth = sz.width / 2;
		szHalfHeight = sz.height / 2;
		screenLX = 0;
		screenHX = sz.width;
		screenLY = 0;
		screenHY = sz.height;
		factorX = (float) (offset.getX() * DBMath.GRID - szHalfWidth / scale_);
		factorY = (float) (offset.getY() * DBMath.GRID + szHalfHeight / scale_);
		factorX_ = (int) factorX;
		factorY_ = (int) factorY;
		scale_int = (int) (scale_ * (1 << SCALE_SH));

		// draw the screen, starting with the top cell
		try {
			List<VectorCache.VectorBase> shapeList = Arrays.asList(shapes);
			drawList(0, 0, shapeList, 0, true);
		} catch (AbortRenderingException e) {}
	}

	/**
	 * Class to define a signal to abort rendering.
	 */
	class AbortRenderingException extends Exception {
	}

	/**
	 * Method to request that the current rendering be aborted because it must
	 * be restarted.
	 *
	 */
	public void abortRendering() {
		stopRendering = true;
	}

	/**
	 * Method to recursively render a cached cell.
	 * @param vc the cached cell to render
	 * @param oX the X offset for rendering the cell (in database grid coordinates).
	 * @param oY the Y offset for rendering the cell (in database grid coordinates).
	 * @param context the VarContext for this point in the rendering.
	 * @param level 0=top-level cell in window; 1=low level cell; -1=greeked cell.
	 * @param lv current layer visibility.
	 */
	private void render(VectorCache.VectorCell vc, long oX, long oY, VarContext context, int level, LayerVisibility lv)
		throws AbortRenderingException
	{
		// render main list of shapes
		List<Layer> knownLayers = vc.getKnownLayers();
		Technology curTech = Technology.getCurrent();

		// first render the other technologies (so they are lowest in opaque buffer)
		for(Layer lay : knownLayers)
		{
			if (lay.getTechnology() == curTech) continue;
			drawList(oX, oY, vc.getShapes(lay), level, false);
		}

		// next render the current technologies (so it is highest in opaque buffer)
		for(Layer lay : knownLayers)
		{
			if (lay.getTechnology() != curTech) continue;
			drawList(oX, oY, vc.getShapes(lay), level, false);
		}

		// now render subcells
		Cell cell = VectorCache.getCellFromId(vc.getCellDef().getCellId());

		Iterator<VectorCache.VectorSubCell> sea;
		if (vc.getSubCellTree() == null) sea = vc.getSubCells().iterator(); else
		{
			// search the R-Tree
			screenToGrid(screenLX, screenLY, tempPt1);
			long dbLX = tempPt1.x - oX;
			long dbLY = tempPt1.y - oY;
			screenToGrid(screenHX, screenHY, tempPt1);
			long dbHX = tempPt1.x - oX;
			long dbHY = tempPt1.y - oY;
			if (dbLX > dbHX) { long swap = dbLX;   dbLX = dbHX;   dbHX = swap; }
			if (dbLY > dbHY) { long swap = dbLY;   dbLY = dbHY;   dbHY = swap; }
			ERectangle bound = ERectangle.fromGrid(dbLX, dbLY, dbHX-dbLX, dbHY-dbLY);
			vc.getOrientation().inverse().rectangleBounds(bound, EPoint.ORIGIN, tempFixpRect);
			sea = new RTNode.Search<VectorCache.VectorSubCell>(tempFixpRect, vc.getSubCellTree(), true);
		}

		for ( ; sea.hasNext(); )
		{
			VectorCache.VectorSubCell vsc = sea.next();
			if (stopRendering)
				throw new AbortRenderingException();
			ImmutableNodeInst ini = vsc.getNode();
			Cell subCell = VectorCache.getCellFromId((CellId) ini.protoId);
			subCellCount++;

			// get instance location
			long soX = vsc.getOffsetX() + oX;
			long soY = vsc.getOffsetY() + oY;
			VectorCache.VectorCell subVC = VectorCache.theCache.findVectorCell(vsc.getCellId(), vc.getOrientation().concatenate(ini.orient));
			gridToScreen(subVC.getLX() + soX, subVC.getHY() + soY, tempPt1);
			gridToScreen(subVC.getHX() + soX, subVC.getLY() + soY, tempPt2);
			long lX = tempPt1.x;
			long lY = tempPt1.y;
			long hX = tempPt2.x;
			long hY = tempPt2.y;

			// see if the subcell is clipped (when not doing R-Tree searches)
			if (vc.getSubCellTree() == null)
			{
				if (hX < screenLX || lX >= screenHX) continue;
				if (hY < screenLY || lY >= screenHY) continue;
			}

			// see if the cell is too tiny to draw
			if (subVC.getCellDef().getMinimumSize() < objectVisibleThreshold)
			{
				invisSubCellCount++;
				continue;
			}
			if (subVC.getCellDef().getMinimumSize() < objectGreekThreshold) {
				Orientation thisOrient = ini.orient;
				Orientation recurseTrans = vc.getOrientation().concatenate(thisOrient);
				VarContext subContext = context.push(cell, ini);
				VectorCache.VectorCell subVC_ = drawCell(subCell, recurseTrans, subContext, false);
				assert subVC_ == subVC;
				makeGreekedImage(subVC, lv);

				int fadeColor = getFadeColor(subVC, subContext, lv);
				drawTinyBox(lX, hX, lY, hY, fadeColor, subVC);
				tinySubCellCount++;
				continue;
			}

			// see if drawing "down in place"
			boolean onPathDown = false;
			if (inPlaceNodePath != null) {
				for (NodeInst niOnPath : inPlaceNodePath) {
					if (niOnPath.getProto().getId() == vsc.getCellId()) {
						onPathDown = true;
						break;
					}
				}
			}

			// see if cell contents should be drawn
			boolean isExpanded = cell.isExpanded(ini.nodeId);
			boolean expanded = isExpanded || fullInstantiate;

			// if not expanded, but viewing this cell in-place, expand it
			if (!expanded && onPathDown) expanded = true;

			if (expanded) {
				Orientation thisOrient = ini.orient;
				Orientation recurseTrans = vc.getOrientation().concatenate(thisOrient);
				VarContext subContext = null;
				if (context != null)
					subContext = context.push(cell, ini);
				VectorCache.VectorCell subVC_ = drawCell(subCell, recurseTrans, subContext, false);
				assert subVC_ == subVC;

				// expanded cells may be replaced with greeked versions (not icons)
				if (!subCell.isIcon()) {
					// may also be "tiny" if all features in the cell are tiny
					boolean allFeaturesTiny = subVC.getMaxFeatureSize() > 0 && subVC.getMaxFeatureSize() < objectGreekThreshold &&
						subVC.getCellDef().getArea() < maxCellSize && isContentsTiny(subCell, subVC, recurseTrans, context);

					// may also be "tiny" if the cell is smaller than the greeked image
					boolean smallerThanGreek = useCellGreekingImages && hX - lX <= MAXGREEKSIZE && hY - lY <= MAXGREEKSIZE;
					if (allFeaturesTiny || smallerThanGreek) {
						makeGreekedImage(subVC, lv);
						int fadeColor = getFadeColor(subVC, context, lv);
						drawTinyBox(lX, hX, lY, hY, fadeColor, subVC);
						tinySubCellCount++;
						continue;
					}
				}

				int subLevel = level;
				if (subLevel == 0) subLevel = 1;
				render(subVC, soX, soY, subContext, subLevel, lv);
			} else {
				// now draw with the proper line type
				long[] op = subVC.getOutlinePoints();
				long p1x = op[0] + soX;
				long p1y = op[1] + soY;
				long p2x = op[2] + soX;
				long p2y = op[3] + soY;
				long p3x = op[4] + soX;
				long p3y = op[5] + soY;
				long p4x = op[6] + soX;
				long p4y = op[7] + soY;
				gridToScreen(p1x, p1y, tempPt1);
				gridToScreen(p2x, p2y, tempPt2);
				offscreen.drawLine(tempPt1, tempPt2, null, PixelDrawing.instanceGraphics, 0, false);
				gridToScreen(p2x, p2y, tempPt1);
				gridToScreen(p3x, p3y, tempPt2);
				offscreen.drawLine(tempPt1, tempPt2, null, PixelDrawing.instanceGraphics, 0, false);
				gridToScreen(p3x, p3y, tempPt1);
				gridToScreen(p4x, p4y, tempPt2);
				offscreen.drawLine(tempPt1, tempPt2, null, PixelDrawing.instanceGraphics, 0, false);
				gridToScreen(p1x, p1y, tempPt1);
				gridToScreen(p4x, p4y, tempPt2);
				offscreen.drawLine(tempPt1, tempPt2, null, PixelDrawing.instanceGraphics, 0, false);

				// draw the instance name
				if (PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.NODE)) {
					tempRect.setBounds((int)lX, (int)lY, (int)(hX - lX), (int)(hY - lY));
					TextDescriptor descript = ini.protoDescriptor;
					offscreen.drawText(tempRect, Poly.Type.TEXTBOX, descript, subCell.describe(false), null,
						PixelDrawing.textGraphics, false);
				}
			}
			if (level == 0 || onPathDown || inPlaceCurrent == cell)
				drawPortList(vsc, subVC, soX, soY, expanded, onPathDown);
		}
	}

	/**
	 * Method to draw a list of cached shapes.
	 * @param oX the X offset to draw the shapes (in database grid coordinates).
	 * @param oY the Y offset to draw the shapes (in database grid coordinates).
	 * @param shapes the List of shapes (VectorBase objects).
	 * @param level 0=top-level cell in window; 1=low level cell; -1=greeked cell.
	 * @param forceVisible true to force all layers to be drawn (regardless of user settings)
	 */
	private void drawList(long oX, long oY, List<VectorCache.VectorBase> shapes, int level, boolean forceVisible)
			throws AbortRenderingException {
		// render all shapes in reverse order (because PixelDrawing don't overwrite opaque layers)
		if (shapes == null) return;
		for (int k = shapes.size() - 1; k >= 0; k--) {
			VectorCache.VectorBase vb = shapes.get(k);
			if (stopRendering)
				throw new AbortRenderingException();

			// get visual characteristics of shape
			Layer layer = vb.getLayer();
			boolean dimmed = false;
			if (layer != null) {
				if (level < 0) {
					// greeked cells ignore cut and implant layers
					Layer.Function fun = layer.getFunction();
					if (fun.isContact() || fun.isWell() || fun.isSubstrate()) continue;
				}
				if (!forceVisible) {
					if (!PixelDrawing.lv.isVisible(layer)) continue;
					dimmed = !PixelDrawing.lv.isHighlighted(layer);
				}
			}
			byte[][] layerBitMap = null;
			EGraphics graphics = vb.getGraphics();
			if (graphics == null && layer != null)
				graphics = PixelDrawing.gp.getGraphics(layer);
			if (graphics != null) {
				int layerNum = graphics.getTransparentLayer() - 1;
				if (layerNum < offscreen.numLayerBitMaps)
					layerBitMap = offscreen.getLayerBitMap(layerNum);
			}

			// handle each shape
			if (vb instanceof VectorCache.VectorManhattan) {
				boxCount++;
				VectorCache.VectorManhattan vm = (VectorCache.VectorManhattan) vb;

				double maxSize = objectGreekThreshold * DBMath.GRID;
				int fadeCol = -1;
				if (layer != null) {
					Layer.Function fun = layer.getFunction();
					if (fun.isImplant() || fun.isSubstrate()) {
						// well and substrate layers are made smaller so that they "greek" sooner
						if (!vm.isPureLayer()) maxSize *= 10;
					} else if (graphics != null) {
						fadeCol = graphics.getRGB();
					}
				}
				long[] coords = vm.getCoords();
				for (int i = 0; i < coords.length; i += 4) {
					long c1X = coords[i];
					long c1Y = coords[i + 1];
					long c2X = coords[i + 2];
					long c2Y = coords[i + 3];
					long dX = c2X - c1X;
					long dY = c2Y - c1Y;
					if (dX < maxSize || dY < maxSize) {
						if (fadeCol < 0)
							continue;
						if (dX < maxSize && dY < maxSize) {
							// both dimensions tiny: just draw a dot
							gridToScreen(c1X + oX, c1Y + oY, tempPt1);
							int x = tempPt1.x;
							int y = tempPt1.y;
							if (x < screenLX || x >= screenHX) continue;
							if (y < screenLY || y >= screenHY) continue;
							offscreen.drawPoint(x, y, null, fadeCol);
							tinyBoxCount++;
						} else {
							// one dimension tiny: draw a line
							gridToScreen(c1X + oX, c2Y + oY, tempPt1);
							gridToScreen(c2X + oX, c1Y + oY, tempPt2);
							assert tempPt1.x <= tempPt2.x && tempPt1.y <= tempPt2.y;
							int lX = tempPt1.x;
							int hX = tempPt2.x;
							int lY = tempPt1.y;
							int hY = tempPt2.y;
							if (hX < screenLX || lX >= screenHX) continue;
							if (hY < screenLY || lY >= screenHY) continue;
							drawTinyBox(lX, hX, lY, hY, fadeCol, null);
							lineBoxCount++;
						}
						continue;
					}

					// determine coordinates of rectangle on the screen
					gridToScreen(c1X + oX, c2Y + oY, tempPt1);
					gridToScreen(c2X + oX, c1Y + oY, tempPt2);
					assert tempPt1.x <= tempPt2.x && tempPt1.y <= tempPt2.y;
					int lX = tempPt1.x;
					int hX = tempPt2.x;
					int lY = tempPt1.y;
					int hY = tempPt2.y;

					// reject if completely off the screen
					if (hX < screenLX || lX >= screenHX) continue;
					if (hY < screenLY || lY >= screenHY) continue;

					// clip to screen
					if (lX < screenLX) lX = screenLX;
					if (hX >= screenHX) hX = screenHX - 1;
					if (lY < screenLY) lY = screenLY;
					if (hY >= screenHY) hY = screenHY - 1;

					// draw the box
					offscreen.drawBox(lX, hX, lY, hY, layerBitMap, graphics, dimmed);
				}
			} else if (vb instanceof VectorCache.VectorLine) {
				lineCount++;
				VectorCache.VectorLine vl = (VectorCache.VectorLine) vb;

				// determine coordinates of line on the screen
				gridToScreen(vl.getFromX() + oX, vl.getFromY() + oY, tempPt1);
				gridToScreen(vl.getToX() + oX, vl.getToY() + oY, tempPt2);

				// clip and draw the line
				offscreen.drawLine(tempPt1, tempPt2, layerBitMap, graphics, vl.getTexture(), dimmed);
			} else if (vb instanceof VectorCache.VectorPolygon) {
				polygonCount++;
				VectorCache.VectorPolygon vp = (VectorCache.VectorPolygon) vb;
				EPoint[] oldPoints = vp.getPoints();
				Point[] intPoints = new Point[oldPoints.length];
				for (int i = 0; i < oldPoints.length; i++) {
					intPoints[i] = new Point();
					gridToScreen(oldPoints[i].getGridX() + oX, oldPoints[i].getGridY() + oY, intPoints[i]);
				}
				Point[] clippedPoints = GenMath.clipPoly(intPoints, screenLX, screenHX - 1, screenLY, screenHY - 1);
				if (clippedPoints.length >= 2)
					offscreen.drawPolygon(clippedPoints, layerBitMap, graphics, dimmed);
			} else if (vb instanceof VectorCache.VectorCross) {
				crossCount++;
				VectorCache.VectorCross vcr = (VectorCache.VectorCross) vb;
				gridToScreen(vcr.getCenterX() + oX, vcr.getCenterY() + oY, tempPt1);
				int size = 5;
				if (vcr.isSmall()) size = 3;
				offscreen.drawLine(new Point(tempPt1.x - size, tempPt1.y), new Point(tempPt1.x + size, tempPt1.y),
						null, graphics, 0, dimmed);
				offscreen.drawLine(new Point(tempPt1.x, tempPt1.y - size), new Point(tempPt1.x, tempPt1.y + size),
						null, graphics, 0, dimmed);
			} else if (vb instanceof VectorCache.VectorText) {
				VectorCache.VectorText vt = (VectorCache.VectorText) vb;
				switch (vt.getTextType()) {
					case VectorCache.VectorText.TEXTTYPEARC:
						if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.ARC)) continue;
						break;
					case VectorCache.VectorText.TEXTTYPENODE:
						if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.NODE)) continue;
						break;
					case VectorCache.VectorText.TEXTTYPECELL:
						if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.CELL)) continue;
						break;
					case VectorCache.VectorText.TEXTTYPEEXPORT:
						if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.EXPORT)) continue;
						break;
					case VectorCache.VectorText.TEXTTYPEANNOTATION:
						if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.ANNOTATION)) continue;
						break;
				}
				if (vt.getHeight() < maxTextSize) continue;
                if (vt.isTempName() && !PixelDrawing.gp.isShowTempNames()) continue;

				String drawString = vt.getString();
                long lX = vt.getCX();
                long lY = vt.getCY();
                long hX = lX + vt.getWid();
                long hY = lY + vt.getHei();
				gridToScreen(lX + oX, hY + oY, tempPt1);
				gridToScreen(hX + oX, lY + oY, tempPt2);
				lX = tempPt1.x;
				lY = tempPt1.y;
				hX = tempPt2.x;
				hY = tempPt2.y;
				if (vt.getTextType() == VectorCache.VectorText.TEXTTYPEEXPORT && vt.getBasePort() != null) {
					if (!PixelDrawing.lv.isVisible(vt.getBasePort().getParent()))
						continue;
					graphics = PixelDrawing.textGraphics;
					int exportDisplayLevel = PixelDrawing.gp.exportDisplayLevel;
					if (exportDisplayLevel == 2) {
						// draw export as a cross
						int cX = (int)((lX + hX) / 2);
						int cY = (int)((lY + hY) / 2);
						int size = 3;
						offscreen.drawLine(new Point(cX - size, cY), new Point(cX + size, cY), null, graphics, 0, false);
						offscreen.drawLine(new Point(cX, cY - size), new Point(cX, cY + size), null, graphics, 0, false);
						crossCount++;
						continue;
					}

					// draw export as text
					if (exportDisplayLevel == 1)
						drawString = Export.getShortName(drawString);
					layerBitMap = null;
				}

				textCount++;
				tempRect.setBounds((int)lX, (int)lY, (int)(hX - lX), (int)(hY - lY));
				offscreen.drawText(tempRect, vt.getStyle(), vt.getTextDescriptor(), drawString, layerBitMap, graphics, dimmed);
			} else if (vb instanceof VectorCache.VectorCircle) {
				circleCount++;
				VectorCache.VectorCircle vci = (VectorCache.VectorCircle) vb;
				gridToScreen(vci.getCenterX() + oX, vci.getCenterY() + oY, tempPt1);
				gridToScreen(vci.getEdgeX() + oX, vci.getEdgeY() + oY, tempPt2);
				switch (vci.getNature()) {
					case 0:
						offscreen.drawCircle(tempPt1, tempPt2, layerBitMap, graphics, dimmed);
						break;
					case 1:
						offscreen.drawThickCircle(tempPt1, tempPt2, layerBitMap, graphics, dimmed);
						break;
					case 2:
						offscreen.drawDisc(tempPt1, tempPt2, layerBitMap, graphics, dimmed);
						break;
				}
			} else if (vb instanceof VectorCache.VectorCircleArc) {
				arcCount++;
				VectorCache.VectorCircleArc vca = (VectorCache.VectorCircleArc) vb;
				gridToScreen(vca.getCenterX() + oX, vca.getCenterY() + oY, tempPt1);
				gridToScreen(vca.getEdge1X() + oX, vca.getEdge1Y() + oY, tempPt2);
				gridToScreen(vca.getEdge2X() + oX, vca.getEdge2Y() + oY, tempPt3);
				offscreen.drawCircleArc(tempPt1, tempPt2, tempPt3, vca.isThick(), vca.isBigArc(), layerBitMap, graphics, dimmed);
			}
		}
	}

	/**
	 * Method to draw a list of cached port shapes.
	 * @param oX the X offset to draw the shapes (in database grid coordinates).
	 * @param oY the Y offset to draw the shapes (in database grid coordinates).
	 * @param expanded true to draw a list on expanded instance.
	 * @param onPathDown true if this level of hierarchy is the current one in "down-in-place" editing.
	 */
	private void drawPortList(VectorCache.VectorSubCell vsc, VectorCache.VectorCell subVC_, long oX, long oY,
			boolean expanded, boolean onPathDown) throws AbortRenderingException {
		if (!PixelDrawing.gp.isTextVisibilityOn(TextDescriptor.TextType.PORT))
			return;

		// render all shapes
		List<VectorCache.VectorCellExport> portShapes = subVC_.getCellDef().getPortShapes();
		int[] portCenters = subVC_.getPortCenters();
		assert portShapes.size() * 2 == portCenters.length;
		for (int i = 0; i < portShapes.size(); i++) {
			VectorCache.VectorCellExport vce = portShapes.get(i);
			if (stopRendering)
				throw new AbortRenderingException();

			// get visual characteristics of shape
			if (!onPathDown && vsc.isPortShown(vce.getChronIndex())) continue;
			if (vce.getHeight() < maxTextSize) continue;

			int cX = portCenters[i * 2];
			int cY = portCenters[i * 2 + 1];
			gridToScreen(cX + oX, cY + oY, tempPt1);
			cX = tempPt1.x;
			cY = tempPt1.y;

			int portDisplayLevel = PixelDrawing.gp.portDisplayLevel;
			EGraphics portGraphics = expanded ? PixelDrawing.textGraphics : offscreen
					.getPortGraphics(vce.getBasePort());
			if (portDisplayLevel == 2) {
				// draw port as a cross
				int size = 3;
				offscreen.drawLine(new Point(cX - size, cY), new Point(cX + size, cY), null, portGraphics, 0, false);
				offscreen.drawLine(new Point(cX, cY - size), new Point(cX, cY + size), null, portGraphics, 0, false);
				crossCount++;
				continue;
			}

			// draw port as text
			boolean shortName = portDisplayLevel == 1;
			String drawString = vce.getName(shortName);

			textCount++;
			tempRect.setBounds(cX, cY, 0, 0);
			offscreen.drawText(tempRect, vce.getStyle().transformAnchorOfType(subVC_.getOrientation()),
                               vce.getTextDescriptor(), drawString, null, portGraphics, false);
		}
	}

	/**
	 * Method to convert a database grid coordinate to screen coordinates.
	 * @param dbX the X coordinate (in database grid units).
	 * @param dbY the Y coordinate (in database grid units).
	 * @param result the Point in which to store the screen coordinates.
	 */
	private void gridToScreen(long dbX, long dbY, Point result) {
		if (false) {
			result.x = (int)(((dbX - factorX_) * scale_int) >> SCALE_SH);
			result.y = (int)(((factorY_ - dbY) * scale_int) >> SCALE_SH);
		} else {
			double scrX = (dbX - factorX) * scale_;
			double scrY = (factorY - dbY) * scale_;
			result.x = (int) (scrX >= 0 ? scrX + 0.5 : scrX - 0.5);
			result.y = (int) (scrY >= 0 ? scrY + 0.5 : scrY - 0.5);
		}
	}

	/**
	 * Method to convert a screen coordinates to a database grid coordinates.
	 * @param dbX the X coordinate (in screen units).
	 * @param dbY the Y coordinate (in screen units).
	 * @param result the Point in which to store the database grid coordinates.
	 */
    void screenToGrid(long scrX, long scrY, Point result) {
        double dbX = scrX / scale_ + factorX;
        double dbY = factorY - scrY / scale_;
        result.x = (int)(dbX >= 0 ? dbX + 0.5 : dbX - 0.5);
        result.y = (int)(dbY >= 0 ? dbY + 0.5 : dbY - 0.5);
    }

	/**
	 * Method to draw a tiny box on the screen in a given color. Done when the
	 * object is too small to draw in full detail.
	 * @param lX the low X coordinate of the box.
	 * @param hX the high X coordinate of the box.
	 * @param lY the low Y coordinate of the box.
	 * @param hY the high Y coordinate of the box.
	 * @param col the color to draw.
	 */
	private void drawTinyBox(long lX, long hX, long lY, long hY, int col, VectorCache.VectorCell greekedCell) {
		if (lX < screenLX) lX = screenLX;
		if (hX >= screenHX) hX = screenHX - 1;
		if (lY < screenLY) lY = screenLY;
		if (hY >= screenHY) hY = screenHY - 1;
		if (useCellGreekingImages) {
			if (greekedCell != null && greekedCell.getFadeColors() != null) {
				int backgroundColor = PixelDrawing.gp.getColor(User.ColorPrefType.BACKGROUND).getRGB();
				int backgroundRed = (backgroundColor >> 16) & 0xFF;
				int backgroundGreen = (backgroundColor >> 8) & 0xFF;
				int backgroundBlue = backgroundColor & 0xFF;

				// render the icon properly with scale
				int greekWid = greekedCell.getFadeImageWidth();
				int greekHei = greekedCell.getFadeImageHeight();
				long wid = hX - lX;
				long hei = hY - lY;
				float xInc = greekWid / (float) wid;
				float yInc = greekHei / (float) hei;
				float yPos = 0;
				for (int y = 0; y < hei; y++) {
					float yEndPos = yPos + yInc;
					int yS = (int) yPos;
					int yE = (int) yEndPos;

					float xPos = 0;
					for (int x = 0; x < wid; x++) {
						float xEndPos = xPos + xInc;
						int xS = (int) xPos;
						int xE = (int) xEndPos;

						float r = 0, g = 0, b = 0;
						float totalArea = 0;
						for (int yGrab = yS; yGrab <= yE; yGrab++) {
							if (yGrab >= greekHei) continue;
							float yArea = 1;
							if (yGrab == yS) yArea = (1 - (yPos - yS));
							if (yGrab == yE) yArea *= (yEndPos - yE);

							for (int xGrab = xS; xGrab <= xE; xGrab++) {
								if (xGrab >= greekWid) continue;
								int index = xGrab + yGrab * greekedCell.getFadeImageWidth();
								int[] colors = greekedCell.getFadeColors();
								if (colors == null || index >= colors.length)
									continue;
								int value = colors[index];
								int red = (value >> 16) & 0xFF;
								int green = (value >> 8) & 0xFF;
								int blue = value & 0xFF;
								float area = yArea;
								if (xGrab == xS) area *= (1 - (xPos - xS));
								if (xGrab == xE) area *= (xEndPos - xE);
								if (area <= 0) continue;
								r += red * area;
								g += green * area;
								b += blue * area;
								totalArea += area;
							}
						}
						if (totalArea > 0) {
							int red = (int) (r / totalArea);
							if (red > 255) red = 255;
							int green = (int) (g / totalArea);
							if (green > 255) green = 255;
							int blue = (int) (b / totalArea);
							if (blue > 255) blue = 255;
							if (Math.abs(backgroundRed - red) > 2 || Math.abs(backgroundGreen - green) > 2
									|| Math.abs(backgroundBlue - blue) > 2) {
								offscreen.drawPoint((int)(lX + x), (int)(lY + y), null, (red << 16) | (green << 8) | blue);
							}
						}
						xPos = xEndPos;
					}
					yPos = yEndPos;
				}
				return;
			}
		}

		// no greeked image: just use the greeked color
		for (int y = (int)lY; y <= hY; y++) {
			for (int x = (int)lX; x <= hX; x++)
				offscreen.drawPoint(x, y, null, col);
		}
	}

	/**
	 * Method to determine whether a cell has tiny contents. Recursively
	 * examines the cache of this and all subcells to see if the maximum feature
	 * sizes are all below the global threshold "objectGreekThreshold".
	 * @param cell the Cell in question.
	 * @param vc the cached representation of the cell.
	 * @param trans the Orientation of the cell.
	 * @return true if the cell has all tiny contents.
	 */
	private boolean isContentsTiny(Cell cell, VectorCache.VectorCell vc, Orientation trans, VarContext context)
			throws AbortRenderingException {
		if (vc.getMaxFeatureSize() > objectGreekThreshold) return false;

		Iterator<VectorCache.VectorSubCell> sea;
		if (vc.getSubCellTree() == null) sea = vc.getSubCells().iterator(); else
			sea = new RTNode.Search<VectorCache.VectorSubCell>(vc.getSubCellTree());
		for ( ; sea.hasNext(); )
		{
			VectorCache.VectorSubCell vsc = sea.next();
			ImmutableNodeInst ini = vsc.getNode();
			boolean isExpanded = cell.isExpanded(ini.nodeId);
			VectorCache.VectorCell subVC = VectorCache.theCache.findVectorCell(vsc.getCellId(), vc.getOrientation().concatenate(ini.orient));
			if (isExpanded || fullInstantiate) {
				Orientation thisOrient = ini.orient;
				Orientation recurseTrans = trans.concatenate(thisOrient);
				VarContext subContext = context.push(cell, ini);
				Cell subCell = VectorCache.getCellFromId(vsc.getCellId());
				VectorCache.VectorCell subVC_ = drawCell(subCell, recurseTrans, subContext, false);
				assert subVC_ == subVC;
				boolean subCellTiny = isContentsTiny(subCell, subVC, recurseTrans, subContext);
				if (!subCellTiny) return false;
				continue;
			}
			if (subVC.getCellDef().getMinimumSize() > objectGreekThreshold) return false;
		}
		return true;
	}

	private void makeGreekedImage(VectorCache.VectorCell subVC, LayerVisibility lv) throws AbortRenderingException {
		if (subVC.isFadeImage()) return;
		if (!useCellGreekingImages) return;

		// determine size and scale of greeked cell image
		Rectangle2D cellBounds = subVC.getCellDef().getBounds();
		Rectangle2D ownBounds = new Rectangle2D.Double(cellBounds.getMinX(), cellBounds.getMinY(), cellBounds
				.getWidth(), cellBounds.getHeight());
		FixpTransform trans = subVC.getOrientation().rotateAbout(0, 0);
		DBMath.transformRect(ownBounds, trans);
		double greekScale = MAXGREEKSIZE / ownBounds.getHeight();
		if (ownBounds.getWidth() > ownBounds.getHeight())
			greekScale = MAXGREEKSIZE / ownBounds.getWidth();
		int lX = (int) Math.floor(cellBounds.getMinX() * greekScale);
		int hX = (int) Math.ceil(cellBounds.getMaxX() * greekScale);
		int lY = (int) Math.floor(cellBounds.getMinY() * greekScale);
		int hY = (int) Math.ceil(cellBounds.getMaxY() * greekScale);
		if (hX <= lX) hX = lX + 1;
		int greekWid = hX - lX;
		if (hY <= lY) hY = lY + 1;
		int greekHei = hY - lY;
		Rectangle screenBounds = new Rectangle(lX, lY, greekWid, greekHei);

		// construct the offscreen buffers for the greeked cell image
		PixelDrawing offscreen = new PixelDrawing(greekScale, screenBounds);
		Point2D cellCtr = new Point2D.Double(ownBounds.getCenterX(), ownBounds.getCenterY());
		VectorDrawing subVD = new VectorDrawing(useCellGreekingImages);

		subVC.setFadeOffset(debugXP, debugYP);
		debugXP += MAXGREEKSIZE + 5;
		if (topVD != null) {
			if (debugXP + MAXGREEKSIZE + 2 >= topVD.offscreen.getSize().width) {
				debugXP = 0;
				debugYP += MAXGREEKSIZE + 5;
			}
		}

		// set rendering information for the greeked cell image
		subVD.offscreen = offscreen;
		subVD.screenLX = 0;
		subVD.screenHX = greekWid;
		subVD.screenLY = 0;
		subVD.screenHY = greekHei;
		subVD.szHalfWidth = greekWid / 2;
		subVD.szHalfHeight = greekHei / 2;
		subVD.objectGreekThreshold = 0;
		subVD.objectVisibleThreshold = 0;
		subVD.maxTextSize = 0;
		subVD.scale = (float) greekScale;
		subVD.scale_ = (float) (greekScale / DBMath.GRID);
		subVD.factorX = (float) (cellCtr.getX() * DBMath.GRID - subVD.szHalfWidth / subVD.scale_);
		subVD.factorY = (float) (cellCtr.getY() * DBMath.GRID + subVD.szHalfHeight / subVD.scale_);
		subVD.factorX_ = (int) subVD.factorX;
		subVD.factorY_ = (int) subVD.factorY;
		subVD.scale_int = (int) (subVD.scale_ * (1 << SCALE_SH));
		subVD.fullInstantiate = true;
		subVD.takingLongTime = true;

		// render the greeked cell
		subVD.offscreen.clearImage(null, null);
		subVD.render(subVC, 0, 0, VarContext.globalContext, -1, lv);
		subVD.offscreen.composite(null);

		// remember the greeked cell image
		int[] img = offscreen.getOpaqueData();
		subVC.setFadeImageSize(greekWid, greekHei);
		int[] colors = new int[greekWid * greekHei];
		int i = 0;
		for (int y = 0; y < greekHei; y++) {
			for (int x = 0; x < greekWid; x++) {
				int value = img[i];
				colors[i++] = value & 0xFFFFFF;
			}
		}
		subVC.setFadeColors(colors);
		subVC.setFadeImage(true);
	}

	/**
	 * Method to determine the "fade" color for a cached cell. Fading is done
	 * when the cell is too tiny to draw (or all of its contents are too tiny).
	 * Instead of drawing the cell contents, the entire cell is painted with the
	 * "fade" color.
	 * @param vc the cached cell.
	 * @return the fade color (an integer with red/green/blue).
	 */
	private int getFadeColor(VectorCache.VectorCell vc, VarContext context, LayerVisibility lv)
			throws AbortRenderingException {
		if (vc.hasFadeColor())
			return vc.getFadeColor();

		// examine all shapes
		Map<Layer, MutableDouble> layerAreas = new HashMap<Layer, MutableDouble>();
		gatherContents(vc, layerAreas, context);

		// now compute the color
		Set<Layer> keys = layerAreas.keySet();
		double totalArea = 0;
		for (Layer layer : keys) {
			if (!lv.isVisible(layer)) continue;
			MutableDouble md = layerAreas.get(layer);
			totalArea += md.doubleValue();
		}
		double r = 0, g = 0, b = 0;
		if (totalArea == 0) {
			// no fade color, make it the background color
			vc.setFadeColor(PixelDrawing.gp.getColor(User.ColorPrefType.BACKGROUND).getRGB());
		} else {
			for (Layer layer : keys) {
				if (!lv.isVisible(layer)) continue;
				MutableDouble md = layerAreas.get(layer);
				double portion = md.doubleValue() / totalArea;
				EGraphics desc = PixelDrawing.gp.getGraphics(layer);
				Color col = desc.getColor();
				r += col.getRed() * portion;
				g += col.getGreen() * portion;
				b += col.getBlue() * portion;
			}
			if (r < 0) r = 0;
			if (r > 255) r = 255;
			if (g < 0) g = 0;
			if (g > 255) g = 255;
			if (b < 0) b = 0;
			if (b > 255) b = 255;
			vc.setFadeColor((((int)r) << 16) | (((int)g) << 8) | (int)b);
		}
		vc.setHasFadeColor(true);
		return vc.getFadeColor();
	}

	/**
	 * Helper method to recursively examine a cached cell and its subcells and
	 * compute the coverage of each layer.
	 *
	 * @param vc the cached cell to examine.
	 * @param layerAreas a HashMap of all layers and the areas they cover.
	 */
	private void gatherContents(VectorCache.VectorCell vc, Map<Layer, MutableDouble> layerAreas, VarContext context)
		throws AbortRenderingException
	{
		List<Layer> knownLayers = vc.getKnownLayers();
		for(Layer layer : knownLayers)
		{
			if (layer == null) continue;
			Layer.Function fun = layer.getFunction();
			if (fun.isImplant() || fun.isSubstrate()) continue;

			List<VectorCache.VectorBase> shapesOnLayer = vc.getShapes(layer);
			for (VectorCache.VectorBase vb : shapesOnLayer)
			{
				// handle each shape
				double area = 0;
				if (vb instanceof VectorCache.VectorManhattan) {
					VectorCache.VectorManhattan vm = (VectorCache.VectorManhattan) vb;
					long[] coords = vm.getCoords();
					for (int i = 0; i < coords.length; i += 4) {
						double c1X = coords[i];
						double c1Y = coords[i + 1];
						double c2X = coords[i + 2];
						double c2Y = coords[i + 3];
						area += (c1X - c2X) * (c1Y - c2Y);
					}
				} else if (vb instanceof VectorCache.VectorPolygon) {
					VectorCache.VectorPolygon vp = (VectorCache.VectorPolygon) vb;
					area = GenMath.getAreaOfPoints(vp.getPoints());
				} else if (vb instanceof VectorCache.VectorCircle) {
					VectorCache.VectorCircle vci = (VectorCache.VectorCircle) vb;
					double radius = new Point2D.Double(vci.getCenterX(), vci.getCenterY()).distance(new Point2D.Double(vci.getEdgeX(), vci.getEdgeY()));
					area = radius * radius * Math.PI;
				}
				if (area == 0) continue;
				MutableDouble md = layerAreas.get(layer);
				if (md == null) {
					md = new MutableDouble(0);
					layerAreas.put(layer, md);
				}
				md.setValue(md.doubleValue() + area);
			}
		}

		Cell cell = VectorCache.getCellFromId(vc.getCellDef().getCellId());
		Iterator<VectorCache.VectorSubCell> sea;
		if (vc.getSubCellTree() == null) sea = vc.getSubCells().iterator(); else
			sea = new RTNode.Search<VectorCache.VectorSubCell>(vc.getSubCellTree());
		for ( ; sea.hasNext(); )
		{
			VectorCache.VectorSubCell vsc = sea.next();
			VectorCache.VectorCellDef vcd = VectorCache.theCache.findCellGroup(vsc.getCellId());
			VectorCache.VectorCell subVC = vcd.getAnyCell();
			ImmutableNodeInst ini = vsc.getNode();
			VarContext subContext = context.push(cell, ini);
			if (subVC == null) {
				Cell nodeProto = VectorCache.getCellFromId((CellId) ini.protoId);
				subVC = drawCell(nodeProto, Orientation.IDENT, subContext, false);
			}
			gatherContents(subVC, layerAreas, subContext);
		}
	}

	// ************************************* CACHE CREATION *************************************

	/**
	 * Method to cache the contents of a cell.
	 * @param cell the Cell to cache
	 * @param prevTrans the orientation of the cell (just a rotation, no offsets here).
	 * @return a cached cell object for the given Cell.
	 */
	private VectorCache.VectorCell drawCell(Cell cell, Orientation prevTrans, VarContext context, boolean makeTopLevel)
			throws AbortRenderingException {
		// caching the cell: check for abort and delay reporting
		if (stopRendering)
			throw new AbortRenderingException();
		if (!takingLongTime) {
			if (timer.currentTimeLong() > 1000) {
				System.out.print("Display caching, please wait...");
				TopLevel.setBusyCursor(true);
				takingLongTime = true;
			}
		}

		return VectorCache.theCache.drawCell(cell.getId(), prevTrans, context, scale, makeTopLevel);
	}
}
