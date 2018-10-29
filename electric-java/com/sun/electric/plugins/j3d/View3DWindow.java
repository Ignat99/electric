/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: View3DWindow.java
 * Written by Gilda Garreton.
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
package com.sun.electric.plugins.j3d;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.Poly3D;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.Setting;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.plugins.j3d.utils.J3DAppearance;
import com.sun.electric.plugins.j3d.utils.J3DAxis;
import com.sun.electric.plugins.j3d.utils.J3DCanvas3D;
import com.sun.electric.plugins.j3d.utils.J3DUtils;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.*;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.j3d.utils.behaviors.interpolators.KBKeyFrame;
import com.sun.j3d.utils.behaviors.interpolators.RotPosScaleTCBSplinePathInterpolator;
import com.sun.j3d.utils.behaviors.interpolators.TCBKeyFrame;
import com.sun.j3d.utils.behaviors.vp.OrbitBehavior;
import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickIntersection;
import com.sun.j3d.utils.picking.PickResult;
import com.sun.j3d.utils.universe.PlatformGeometry;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;
import com.sun.j3d.utils.geometry.Primitive;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.print.PageFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import javax.media.j3d.*;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.tree.MutableTreeNode;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * This class deals with 3D View using Java3D
 * @author  Gilda Garreton
 * @version 0.1
 */
public class View3DWindow extends JPanel
        implements WindowContent, MouseMotionListener, MouseListener, MouseWheelListener, KeyListener, ActionListener,
        Observer, DatabaseChangeListener
{
	private SimpleUniverse u;
	private J3DCanvas3D canvas;
	protected TransformGroup objTrans;
    private BranchGroup scene;
    private OrbitBehavior orbit;

    // For demo cases
    private Map<TransformGroup,Interpolator> interpolatorMap = new HashMap<TransformGroup,Interpolator>();
    private J3DKeyCollision keyBehavior;

    /** the window frame containing this editwindow */      private WindowFrame wf;
	/** reference to 2D view of the cell */                 private EditWindow view2D;
	/** layer visibility */                                 private LayerVisibility lv;
    /** appearances of used Layers */                       private HashMap<Layer,J3DAppearance> layerAppearance = new HashMap<Layer,J3DAppearance>();
    /** appearances of used Colors */                       private Map<String,J3DAppearance> colorAppearance = new HashMap<String,J3DAppearance>();
	/** the cell that is in the window */					protected Cell cell;
    /** scale3D factor in Z axis */                         private double scale3D = J3DUtils.get3DFactor();
	/** Highlighter for this window */                      private Highlighter highlighter;
	private PickCanvas pickCanvas;
	/** List with all Shape3D drawn per ElectricObject */    private Map<ElectricObject,List<Node>> electricObjectMap = new HashMap<ElectricObject,List<Node>>();
    private boolean oneTransformPerNode = false;
    /** Map with object transformation for individual moves */ private Map<Node,TransformGroup> transformGroupMap = new HashMap<Node,TransformGroup>();
    /** To detect max number of nodes */                    private boolean reachLimit = false;
    /** To ask question only once */                        private boolean alreadyChecked = false;
    /** Job reference */                                    private Job job;
    /** Reference to limit to consider scene graph big */   private int maxNumNodes;
    /** To consider a locos shape if field and gate polys are not aligned */   private boolean locosShape;

    /** Inner class to create 3D view in a job. This should be safer in terms of the number of nodes
     * and be able to stop it
     */
    private static class View3DWindowJob extends Job
    {
        private Cell cell;
        private transient WindowFrame windowFrame;
        private transient WindowContent view2D;
        private boolean transPerNode;
        private List<PolyBase> overridePolygons;

        public View3DWindowJob(Cell cell, List<PolyBase> overridePolygons, WindowFrame wf, WindowContent view2D, boolean transPerNode)
        {
            super("3D View Job", null, Job.Type.CLIENT_EXAMINE, null, null, Job.Priority.USER);
            this.cell = cell;
            this.overridePolygons = overridePolygons;
            this.windowFrame = wf;
            this.view2D = view2D;
            this.transPerNode = transPerNode;
			startJob();
        }

        public boolean doIt() throws JobException
        {
            return true;
        }

        public void terminateOK()
        {
            View3DWindow window = new View3DWindow(cell, overridePolygons, windowFrame, view2D, transPerNode, this);
            windowFrame.finishWindowFrameInformation(window, cell);
            if (!TopLevel.isMDIMode())
            {
                for (Component comp : windowFrame.getFrame().getToolBar().getComponents())
                    comp.setVisible(false);
            }
        }
    }

    /**
     * Method to show a list of polygons in a 3D window.
     * @param polys ArrayList of Poly objects to show.
     * NOTE: Must keep this as an ArrayList, and not make it a List, so that reflection works.
     */
    public static void show3DPolygons(ArrayList<PolyBase> polys)
    {
    	WindowFrame wf = WindowFrame.getCurrentWindowFrame();
    	WindowContent view2D = wf.getContent();
    	Cell cell = view2D.getCell();
        new View3DWindowJob(cell, polys, wf, view2D, false);
    }

    public static void create3DWindow(Cell cell, WindowFrame wf, WindowContent view2D, boolean transPerNode)
    {
        new View3DWindowJob(cell, null, wf, view2D, transPerNode);
    }

    public void getObjTransform(Transform3D trans)
    {
        objTrans.getTransform(trans);
    }

    public void setObjTransform(Transform3D trans)
    {
        objTrans.setTransform(trans);
    }

    /**
     * Method to return if size limit has been reached
     * @param number
     * @return true if number of nodes is still under maximum value
     */
    private boolean isSizeLimitOK(int number)
    {
        if (reachLimit || number > maxNumNodes)
        {
            // Only ask once
            if (!alreadyChecked)
            {
                String[] possibleValues = { "Full", "Limit", "Cancel" };
                int response = Job.getUserInterface().askForChoice("Number of nodes in graph scene reached limit of " + maxNumNodes +
                    " (loaded " + number + " nodes so far).\nClick 'Full' to include all nodes in " +cell +
                    ", 'Limit' to show " + number + " nodes or 'Cancel' to abort process.\nUnexpand cells to reduce the number).",
                    "Warning", possibleValues, possibleValues[2]);
                alreadyChecked = true;
                if (response > 0) // Cancel or limit
                {
                    if (response == 2)
                        job.abort();
                    reachLimit = true;
                }
            }
            if (reachLimit)
                return false;
        }
        return true;
    }

    // constructor
	View3DWindow(Cell cell, List<PolyBase> overridePolygons, WindowFrame wf, WindowContent view2D, boolean transPerNode, Job job)
	{
		this.cell = cell;
        this.wf = wf;
        this.view2D = (EditWindow)view2D;
        lv = this.view2D.getLayerVisibility();
        // Adding observer
        this.view2D.getWindowFrame().addObserver(this);
        this.oneTransformPerNode = transPerNode;
        this.job = job;
        this.maxNumNodes = J3DUtils.get3DMaxNumNodes();

		highlighter = new Highlighter(Highlighter.SELECT_HIGHLIGHTER, wf);

		setLayout(new BorderLayout());
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        if (config == null)
        {
            GraphicsConfigTemplate3D gc3D = new GraphicsConfigTemplate3D( );
            gc3D.setSceneAntialiasing( GraphicsConfigTemplate.PREFERRED );
            GraphicsDevice gd[] = GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( );
            config = gd[0].getBestConfiguration( gc3D );
        }

		canvas = new J3DCanvas3D(config);
		add("Center", canvas);
		canvas.addMouseListener(this);

        // Set global appearances before create the elements
        J3DAppearance.setCellAppearanceValues(this);
        J3DAppearance.setHighlightedAppearanceValues(this);
        J3DAppearance.setAxisAppearanceValues(this);

        // Set global alpha value
        J3DUtils.setAlpha(J3DUtils.get3DAlpha());

		// Create a simple scene and attach it to the virtual universe
		scene = createSceneGraph(cell, overridePolygons);
        if (scene == null) return;

		// Have Java 3D perform optimizations on this scene graph.
	    scene.compile();

		u = new SimpleUniverse(canvas); // viewingPlatform, viewer);

        // lights on ViewPlatform geometry group
        PlatformGeometry pg = new PlatformGeometry();
        J3DUtils.createLights(pg);

        ViewingPlatform viewingPlatform = u.getViewingPlatform();

		// This will move the ViewPlatform back a bit so the
        // objects in the scene can be viewed.
        viewingPlatform.setNominalViewingTransform();

		orbit = new OrbitBehavior(canvas, OrbitBehavior.REVERSE_ALL);
		orbit.setSchedulingBounds(J3DUtils.infiniteBounds);
        orbit.setCapability(OrbitBehavior.ALLOW_LOCAL_TO_VWORLD_READ);

		/** Setting rotation center */
		Point3d center = new Point3d(0, 0, 0);
		BoundingSphere sceneBnd = (BoundingSphere)scene.getBounds();
        sceneBnd.getCenter(center);
		orbit.setRotationCenter(center);
		orbit.setMinRadius(0);
//		orbit.setZoomFactor(10);
//		orbit.setTransFactors(10, 10);
        orbit.setProportionalZoom(true);

        viewingPlatform.setNominalViewingTransform();
    	viewingPlatform.setViewPlatformBehavior(orbit);

		double radius = sceneBnd.getRadius();
		View view = u.getViewer().getView();

		// Too expensive at this point
        if (canvas.getSceneAntialiasingAvailable() && J3DUtils.is3DAntialiasing())
		    view.setSceneAntialiasingEnable(true);

		// Setting the projection policy
		view.setProjectionPolicy(J3DUtils.is3DPerspective()? View.PERSPECTIVE_PROJECTION : View.PARALLEL_PROJECTION);
		if (!J3DUtils.is3DPerspective()) view.setCompatibilityModeEnable(true);

        // Setting transparency sorting
        view.setTransparencySortingPolicy(View.TRANSPARENCY_SORT_GEOMETRY);
        view.setDepthBufferFreezeTransparent(false); // set to true only for transparent layers

        // Setting a good viewpoint for the camera
		Vector3d vCenter = new Vector3d(center);
		double vDist = 1.4 * radius / Math.tan(view.getFieldOfView()/2.0);
		vCenter.z += vDist;
		Transform3D vTrans = new Transform3D();

        //translateB.setView(cellBnd.getWidth(), 0);
        //double[] rotVals = User.transformIntoValues(User.get3DRotation());
        //rotateB.setRotation(rotVals[0], rotVals[1], rotVals[2]);
        //zoomB.setZoom(User.get3DOrigZoom());

		vTrans.setTranslation(vCenter);

		view.setBackClipDistance((vDist+radius)*200.0);
		view.setFrontClipDistance((vDist+radius)/200.0);
//		view.setBackClipPolicy(View.VIRTUAL_EYE);
//		view.setFrontClipPolicy(View.VIRTUAL_EYE);
		if (J3DUtils.is3DPerspective())
		{
            //keyBehavior.setHomeRotation(User.transformIntoValues(J3DUtils.get3DRotation()));

            viewingPlatform.getViewPlatformBehavior().setHomeTransform(vTrans);
            viewingPlatform.getViewPlatformBehavior().goHome();
			//viewingPlatform.getViewPlatformTransform().setTransform(vTrans);
		}
		else
		{
            Transform3D proj = new Transform3D();
            Rectangle2D cellBnd = cell.getBounds();
            proj.ortho(cellBnd.getMinX(), cellBnd.getMinX(), cellBnd.getMinY(), cellBnd.getMaxY(), (vDist+radius)/200.0, (vDist+radius)*2.0);
			view.setVpcToEc(proj);
			//viewingPlatform.getViewPlatformTransform().setTransform(lookAt);
		}

		u.addBranchGraph(scene);

        // Create axis with associated behavior
        BranchGroup axisRoot = new BranchGroup();

        // Position the axis
        Transform3D t = new Transform3D();
//        t.set(new Vector3d(-0.9, -0.5, -2.5)); // set on Linux
        t.set(new Vector3d(-radius/10, -radius/16, -radius/3.5));
        TransformGroup axisTranslation = new TransformGroup(t);
        axisRoot.addChild(axisTranslation);

        // Create transform group to orient the axis and make it
        // readable & writable (this will be the target of the axis
        // behavior)
        final TransformGroup axisTG = new TransformGroup();
        axisTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        axisTG.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        axisTranslation.addChild(axisTG);

        // Create the axis geometry
        J3DAxis axis = new J3DAxis(radius/10, J3DAppearance.axisApps[0], J3DAppearance.axisApps[1],
                J3DAppearance.axisApps[2], User.getDefaultFont());
        axisTG.addChild(axis);

        // Add axis into BG
        pg.addChild(axisRoot);

        // Create the axis behavior
        final TransformGroup viewPlatformTG = viewingPlatform.getViewPlatformTransform();
        pg.addChild(new Behavior() {
            @Override
            public void initialize() {
                setSchedulingInterval(Behavior.getNumSchedulingIntervals() - 1); // the last scheduling interval
                setSchedulingBounds(J3DUtils.infiniteBounds); // everywhere
                wakeupOn(new WakeupOnElapsedFrames(0, true));
            }

            @Override
            public void processStimulus(Enumeration criteria) {
                Transform3D t = new Transform3D();
                viewPlatformTG.getTransform(t);
                // Axis transform is the inverse of view platform transform
                t.setTranslation(new Vector3d());
                t.invert();
                axisTG.setTransform(t);
                wakeupOn(new WakeupOnElapsedFrames(0, true));
            }
        });

        viewingPlatform.setPlatformGeometry(pg) ;

		setWindowTitle();
        UserInterfaceMain.addDatabaseChangeListener(this);
	}

    /**
     * Method to create main transformation group
     * @param cell
     * @return BrachGroup representing the scene graph
     */
    protected BranchGroup createSceneGraph(Cell cell, List<PolyBase> overridePolygons)
	{
		// Create the root of the branch graph
		BranchGroup objRoot = new BranchGroup();
		objRoot.setCapability(BranchGroup.ALLOW_BOUNDS_READ);
        objRoot.setCapability(BranchGroup.ENABLE_PICK_REPORTING);
        objRoot.setCapability(BranchGroup.ALLOW_BOUNDS_WRITE);

        // Create a Transformgroup to scale all objects so they
        // appear in the scene.
        TransformGroup objScale = new TransformGroup();
        Transform3D t3d = new Transform3D();
        t3d.setScale(0.7);
        objScale.setTransform(t3d);
        objRoot.addChild(objScale);

		// Create the TransformGroup node and initialize it to the
		// identity. Enable the TRANSFORM_WRITE capability so that
		// our behavior code can modify it at run time. Add it to
		// the root of the subgraph.
		objTrans = new TransformGroup();
		objTrans.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		objTrans.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		objTrans.setCapability(TransformGroup.ENABLE_PICK_REPORTING);
        objTrans.setCapability(TransformGroup.ALLOW_CHILDREN_EXTEND);
        objTrans.setCapability(TransformGroup.ALLOW_CHILDREN_READ);
        objTrans.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);
        objTrans.setCapability(TransformGroup.ALLOW_BOUNDS_READ);
		//objRoot.addChild(objTrans);
        objScale.addChild(objTrans);

		// Background
        J3DUtils.createBackground(objRoot);

        if (overridePolygons == null)
        {
        	View3DEnumerator view3D = new View3DEnumerator(cell);
        	HierarchyEnumerator.enumerateCell(cell, VarContext.globalContext, view3D);

            if (electricObjectMap.isEmpty())
                System.out.println("No 3D elements added. Check 3D values.");
        } else
        {
    		for(PolyBase poly : overridePolygons)
    		{
    			double distance = 0, thickness = 0;
    			Color color = null;
    			String text = null;
    			float transparency = 0;
    			if (poly instanceof Poly3D)
    			{
    				Poly3D p3d = (Poly3D)poly;
    				distance = p3d.getLowZ() * scale3D;
    				thickness = (p3d.getHighZ() - p3d.getLowZ()) * scale3D;
    				color = p3d.getColor();
    				transparency = p3d.getTransparency();
    				text = p3d.getText();
    			}

    			J3DAppearance ap;
    			if (color == null)
    			{
        			Layer layer = poly.getLayer();
                    if (layer == null || layer.getTechnology() == null) continue; // Non-layout technology. E.g Artwork
        			if (!lv.isVisible(layer)) continue; // Doesn't generate the graph
        			thickness = layer.getThickness() * scale3D;
        			if (thickness == 0) continue; // Skip zero-thickness layers
        			distance = layer.getDistance() * scale3D;
        	        EGraphics graphics = layer.getGraphics();
        	        color = graphics.getColor();
    				ap = getAppearance(color, 0.1f);
    			} else
    			{
    				ap = getAppearance(color, transparency);
    			}

    			// Draw it
                Node node = null;
                Poly.Type type = poly.getStyle();
        		Point2D[] points = poly.getPoints();
                switch (type)
                {
                	case OPENED:
                		Point3d[] pts;
                		if (points.length == 1)
                		{
                			// draw in Z
                    		pts = new Point3d[2];
                    		pts[0] = new Point3d(points[0].getX(), points[0].getY(), distance);
                    		pts[1] = new Point3d(points[0].getX(), points[0].getY(), distance+thickness);
                		} else
                		{
                			// draw in X/Y plane
                    		pts = new Point3d[points.length];
	                		for(int i=0; i<points.length; i++)
	                			pts[i] = new Point3d(points[i].getX(), points[i].getY(), distance);
                		}
                		node = J3DUtils.addLine3D(pts, ap, objTrans);
                		break;
                    case FILLED:
                    case CLOSED:
                        if (poly.getBox() == null)
                        {
                        	node = J3DUtils.addPolyhedron(poly.getPathIterator(null), distance, thickness, ap, objTrans);
                        } else
                        {
                            Rectangle2D bounds = poly.getBounds2D();
                            node = J3DUtils.addPolyhedron(bounds, distance, thickness, ap, objTrans);
                        }
                        break;
                    case CIRCLE:
                    case DISC:
                    	node = J3DUtils.addCylinder(poly.getPoints(), distance, thickness, ap, objTrans);
                        break;
                    case TEXTCENT:
                    	node = J3DUtils.addText(text, points[0].getX(), points[0].getY(), distance, ap, objTrans);
                    	break;
                    default:
                    	if (Job.getDebug())
                    		System.out.println("Case not implemented in View3DDWindow.addPolys type='" + type + "'");
                }
    		}
        }

        if (job.checkAbort()) return null; // Job cancel

		// Picking tools
		pickCanvas = new PickCanvas(canvas, objRoot);
        pickCanvas.setMode(PickCanvas.GEOMETRY_INTERSECT_INFO);
        pickCanvas.setTolerance(4.0f);

        setInterpolator();

        // create the KeyBehavior and attach main transformation group
		keyBehavior = new J3DKeyCollision(objTrans, this);
		keyBehavior.setSchedulingBounds(J3DUtils.infiniteBounds);
		objTrans.addChild(keyBehavior);

		return objRoot;
	}

	/**
	 * Method to set the window title.
	 */
	public void setWindowTitle()
	{
		if (wf == null) return;
		wf.setTitle(wf.composeTitle(cell, "3D View: ", 0));
	}

	/**
	 * Method to return the cell that is shown in this window.
	 * @return the cell that is shown in this window.
	 */
	public Cell getCell() { return cell; }

	/**
	 * Method to get rid of this EditWindow.  Called by WindowFrame when
	 * that windowFrame gets closed.
	 */
	public void finished()
	{
		// remove myself from listener list
		removeKeyListener(this);
		removeMouseListener(this);
		removeMouseMotionListener(this);
		removeMouseWheelListener(this);
		UserInterfaceMain.removeDatabaseChangeListener(this);
        this.view2D.getWindowFrame().deleteObserver(this);
	}

	public void bottomScrollChanged(int e) {}
	public void rightScrollChanged(int e) {}

	/** Dummy functions due to text-oriented functions */
	public void fullRepaint() {
        J3DUtils.setBackgroundColor(null);
        J3DAppearance.setCellAppearanceValues(null);
        J3DAppearance.setHighlightedAppearanceValues(null);
        J3DUtils.setAmbientalColor(null);
        J3DAppearance.setAxisAppearanceValues(null);
        J3DUtils.setDirectionalColor(null);
        for (Map.Entry<Layer,J3DAppearance> e: layerAppearance.entrySet()) {
            Layer layer = e.getKey();
            J3DAppearance app = e.getValue();
            app.setGraphics(layer.getGraphics());
        }
        repaint();
    }
	public boolean findNextText(boolean reverse) { return false; }
	public void replaceText(String replace) {}
	public JPanel getPanel() { return this; }
	public void initTextSearch(String search, boolean caseSensitive, boolean regExp,
		Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr,
		boolean highlightedOnly) {}

	/**
	 * Method to pan along X according to fixed amount of ticks
	 * @param direction
	 * @param panningAmounts
	 * @param ticks
	 */
	public void panXOrY(int direction, double[] panningAmounts, int ticks)
	{
		Cell cell = getCell();
		if (cell == null) return;
		double panningAmount = panningAmounts[User.getPanningDistance()];

		int mult = (int)((double)10 * panningAmount);
		if (mult == 0) mult = 1;

        keyBehavior.moveAlongAxis(direction, mult*ticks);
	}

	/**
	 * Method to shift the panels so that the current cursor location becomes the center.
	 */
	public void centerCursor()
	{
	}

//    public void setViewAndZoom(double x, double y, double zoom)
//    {
////        translateB.setView(x, y);
//    }

	/**
	 * Method to zoom out by a factor of 2 plus mouse pre-defined factor
	 */
	public void zoomOutContents()
    {
        keyBehavior.zoomInOut(false);
//        zoomB.zoomInOut(false);
    }

	/**
	 * Method to zoom in by a factor of 2 plus mouse pre-defined factor
	 */
	public void zoomInContents()
    {
        keyBehavior.zoomInOut(true);
        //zoomB.zoomInOut(true);
    }

	/**
	 * Method to reset zoom/rotation/extraTrans to original place (Fill Window operation)
	 */
	public void fillScreen()
	{
        objTrans.setTransform(new Transform3D());
        u.getViewingPlatform().getViewPlatformBehavior().goHome();
	}

	public void setCell(Cell cell, VarContext context, WindowFrame.DisplayAttributes da) {}
	public void focusOnHighlighted() {}
	public void replaceAllText(String replace) {}
    public Highlighter getHighlighter() { return highlighter; }

	/**
	 *
	 */
	public List<MutableTreeNode> loadExplorerTrees()
	{
        return wf.loadDefaultExplorerTree();
	}

    public void loadTechnologies() {
        lv = LayerVisibility.getLayerVisibility();
    }

	/**
	 * Adds given Arc to scene graph
	 * @param ai
	 * @param objTrans
	 */
	public void addArc(ArcInst ai, FixpTransform transform, TransformGroup objTrans)
	{
		// add the arc
		ArcProto ap = ai.getProto();
		Technology tech = ap.getTechnology();

		List<Node> list = addPolys(tech.getShapeOfArc(ai), transform, objTrans);
        if (list.isEmpty())
            System.out.println("Zero width arc or no layer with non-zero thickness found in arc '" + ai.getName() + "'");
        else
            electricObjectMap.put(ai, list);
	}

	/**
	 * Adds given Node to scene graph
	 * @param no
	 * @param objTrans
	 */
	private void addNode(NodeInst no, FixpTransform transform, TransformGroup objTrans)
	{
		// add the node
		NodeProto nProto = no.getProto();
		Technology tech = nProto.getTechnology();
		int gate = -1;
		int count = 0;
		int poly = -1;

		// Skipping Special nodes
        if (NodeInst.isSpecialNode(no)) return;
        
		List<Node> list = null;
		if (no.isCellInstance())
		{
			// Cell
			Cell cell = (Cell)nProto;
			Rectangle2D rect = no.getBounds();
			double [] values = new double[2];
			values[0] = Double.MAX_VALUE;
			values[1] = Double.MIN_VALUE;
			if (cell.getZValues(values))
			{
				values[0] *= scale3D;
				values[1] *= scale3D;
				Poly pol = new Poly(rect);
	            list = new ArrayList<Node>(1);
	
	            if (transform.getType() != FixpTransform.TYPE_IDENTITY)
				    pol.transform(transform);
				rect = pol.getBounds2D();
				list.add(J3DUtils.addPolyhedron(rect, values[0], values[1] - values[0], J3DAppearance.cellApp, objTrans));
			}
		}
		else
        {
            Poly[] polys = tech.getShapeOfNode(no, true, true, null);
            List<Shape3D> boxList = null;

            // Special case for transistors
            if (nProto.getFunction().isTransistor())
            {
                int[] active = new int[2];
                boolean isSerpentine = no.isSerpentineTransistor();
                boolean isCBTransistor = no.getFunction().isCNTransistor();
                boxList = new ArrayList<Shape3D>(4);

                // Merge active regions
                for (int i = 0; i < polys.length; i++)
                {
                    Layer.Function fun = polys[i].getLayer().getFunction();
                    // count the # of active regions first. Merge only if 2 are founded (assumption of simple transistor)
                    if (!isSerpentine && !isCBTransistor && fun.isDiff())
                    {
                        // The 3D code will merge the active only for simple transistors.
                        // Only 2 active regions are allowed for non-serpentine
                        if (count > 1)
                            System.out.println("More than 2 active regions detected in Transistor '" + no.getName() + "'. Ignoring this layer");
                        else
                            active[count++] = i;
                    }
                    else if (fun.isGatePoly())
                        gate = i;
                    else if (fun.isPoly())
                        poly = i;
                }

                if (count == 2)
                {
                    Rectangle2D rect1 = polys[active[0]].getBounds2D();
                    Rectangle2D rect2 = polys[active[1]].getBounds2D();
                    double minX = Math.min(rect1.getMinX(), rect2.getMinX());
                    double minY = Math.min(rect1.getMinY(), rect2.getMinY());
                    double maxX = Math.max(rect1.getMaxX(), rect2.getMaxX());
                    double maxY = Math.max(rect1.getMaxY(), rect2.getMaxY());
                    Rectangle2D newRect = new Rectangle2D.Double(minX, minY, (maxX-minX), (maxY-minY));
                    Poly tmp = new Poly(newRect);
                    tmp.setLayer(polys[active[0]].getLayer());
                    polys[active[0]] = tmp; // new active with whole area beneath poly gate
                    int last = polys.length - 1;
                    if (active[1] != last)
                        polys[active[1]] = polys[last];
                    polys[last] = null;
                }
            }
			list = addPolys(polys, transform, objTrans);

			// Adding extra layers after polygons are rotated.
            if (locosShape && nProto.getFunction().isTransistor() && gate != -1 && poly != -1)
            {
				Point3d [] pts = new Point3d[8];
	            Point2D[] points = polys[gate].getPoints();
                Point2D p0 = points[0];
                Point2D p1 = points[1];
                Point2D p2 = points[points.length-1];
	            double dist1 = p0.distance(p1);
	            double dist2 = p0.distance(p2);
				Layer layer = polys[gate].getLayer();
				double dist = (layer.getDistance() + layer.getThickness()) * scale3D;
				double distPoly = (polys[poly].getLayer().getDistance()) * scale3D;
                Point2D pointDist, pointClose;
                List<Point3d> topList = new ArrayList<Point3d>();
		        List<Point3d> bottomList = new ArrayList<Point3d>();
                int center, right;

	            if (dist1 > dist2)
	            {
	                pointDist = p1;
		            pointClose = p2;
                    center = 1;
                    right = 2;
	            }
	            else
	            {
	                pointDist = p2;
		            pointClose = p1;
                    center = 2;
                    right = points.length-1;
	            }
                Point2d pDelta = new Point2d(pointDist.getX()-points[0].getX(), pointDist.getY()-points[0].getY());
                pDelta.scale(0.1);
                double[] values = new double[2];
                pDelta.get(values);

                // First extra polyhedron
                topList.add(new Point3d(p0.getX()+values[0], p0.getY()+values[1], dist));
                topList.add(new Point3d(p0.getX(), p0.getY(), distPoly));
                topList.add(new Point3d(p0.getX()-values[0], p0.getY()-values[1], distPoly));
                topList.add(new Point3d(p0.getX(), p0.getY(), dist));
                bottomList.add(new Point3d(pointClose.getX()+values[0], pointClose.getY()+values[1], dist));
                bottomList.add(new Point3d(pointClose.getX(), pointClose.getY(), distPoly));
                bottomList.add(new Point3d(pointClose.getX()-values[0], pointClose.getY()-values[1], distPoly));
                bottomList.add(new Point3d(pointClose.getX(), pointClose.getY(), dist));
                J3DUtils.correctNormals(topList, bottomList);
                System.arraycopy(topList.toArray(), 0, pts, 0, 4);
                System.arraycopy(bottomList.toArray(), 0, pts, 4, 4);
                boxList.add(J3DUtils.addShape3D(pts, 4, getAppearance(layer), objTrans));

                // Second polyhedron
                topList.clear();
                bottomList.clear();
                topList.add(new Point3d(points[center].getX()-values[0], points[center].getY()-values[1], dist));
                topList.add(new Point3d(points[center].getX(), points[center].getY(), distPoly));
                topList.add(new Point3d(points[center].getX()+values[0], points[center].getY()+values[1], distPoly));
                topList.add(new Point3d(points[center].getX(), points[center].getY(), dist));
                bottomList.add(new Point3d(points[right].getX()-values[0], points[right].getY()-values[1], dist));
                bottomList.add(new Point3d(points[right].getX(), points[right].getY(), distPoly));
                bottomList.add(new Point3d(points[right].getX()+values[0], points[right].getY()+values[1], distPoly));
                bottomList.add(new Point3d(points[right].getX(), points[right].getY(), dist));
                J3DUtils.correctNormals(topList, bottomList);
                System.arraycopy(topList.toArray(), 0, pts, 0, 4);
                System.arraycopy(bottomList.toArray(), 0, pts, 4, 4);
                boxList.add(J3DUtils.addShape3D(pts, 4, getAppearance(layer), objTrans));
            }
            if (boxList != null) list.addAll(boxList);
        }
        if (list == null || list.isEmpty())
            System.out.println("Flat node or no layer with non-zero thickness found in node '" + no.getName() + "'");
        else
        {
            electricObjectMap.put(no, list);
            for (int i = 0; i < list.size(); i++)
            {
                transformGroupMap.put(list.get(i), objTrans);
            }
        }
    }

    /**
	 * Adds given list of Polys representing a PrimitiveNode to the transformation group
	 * @param polys
	 * @param transform
	 * @param objTrans
	 */
	private List<Node> addPolys(Poly [] polys, FixpTransform transform, TransformGroup objTrans)
	{
		if (polys == null) return (null);

		List<Node> list = new ArrayList<Node>();
		for(int i = 0; i < polys.length; i++)
		{
			Poly poly = polys[i];
            if (poly == null) continue; // Case for transistors and active regions.
			Layer layer = poly.getLayer();

            if (layer == null || layer.getTechnology() == null) continue; // Non-layout technology. E.g Artwork
			if (!lv.isVisible(layer)) continue; // Doesn't generate the graph

			double thickness = layer.getThickness() * scale3D;
			double distance = layer.getDistance() * scale3D;

			if (thickness == 0) continue; // Skip zero-thickness layers

			if (transform != null)
				poly.transform(transform);

			// Setting appearance
            J3DAppearance ap = getAppearance(layer);
            Poly.Type type = poly.getStyle();

            switch (type)
            {
                case FILLED:
                case CLOSED:
                    // polygon cases
                    if (poly.getBox() == null) // non-manhattan shape
                    {
                        list.add(J3DUtils.addPolyhedron(poly.getPathIterator(null), distance, thickness, ap, objTrans));
                    } else
                    {
                        Rectangle2D bounds = poly.getBounds2D();
                        list.add(J3DUtils.addPolyhedron(bounds, distance, thickness, ap, objTrans));
                    }
                    break;
                case CIRCLE:
                case DISC:
                    list.add(J3DUtils.addCylinder(poly.getPoints(), distance, thickness, ap, objTrans));
                    break;
                default:
                	if (Job.getDebug())	
                		System.out.println("Case not implemented in View3DDWindow.addPolys type='" + type + "'");
            }
		}
		return (list);
	}

    /********************************************************************************************************
     *                  Model-View paradigm to control refresh from 2D
     ********************************************************************************************************/

	/**
	 * Internal method to highlight objects
	 * @param toSelect true if element must be highlighted
	 * @param do2D true if 2D highlighter should be called
	 */
	private void selectObject(boolean toSelect, boolean do2D)
	{
		Highlighter highlighter2D = null;
		// Clean previous selection
		if (view2D != null && do2D)
		{
			highlighter2D = view2D.getHighlighter();
			highlighter2D.clear();
		}
		for (Highlight h : highlighter.getHighlights())
		{
            HighlightShape3D hObj = (HighlightShape3D)(h.getObject());

            if (hObj == null) continue; // 
            
            if (toSelect) // highlight cell, set transparency
			{
				//J3DAppearance app = (J3DAppearance)obj.getAppearance();
				hObj.setAppearance(J3DAppearance.highlightApp);
				//app.getRenderingAttributes().setVisible(false);
				//J3DAppearance.highlightApp.setGraphics(app.getGraphics());
				if (view2D != null && do2D)
				{
					//Geometry geo = obj.getGeometry();
					BoundingBox bb = (BoundingBox)hObj.shape.getBounds();
					Point3d lowerP = new Point3d(), upperP = new Point3d();
					bb.getUpper(upperP);
					bb.getLower(lowerP);
					double[] lowerValues = new double[3];
					double[] upperValues = new double[3];
					lowerP.get(lowerValues);
					upperP.get(upperValues);
					Rectangle2D area = new Rectangle2D.Double(lowerValues[0], lowerValues[1],
							(upperValues[0]-lowerValues[0]), (upperValues[1]-lowerValues[1]));
					highlighter2D.addArea(area, cell);
				}
			}
			else // back to normal
			{
				//EGraphics graphics = J3DAppearance.highlightApp.getGraphics();
                if (hObj.origApp != null)
				//if (graphics != null)
				{
					//J3DAppearance origAp = (J3DAppearance)graphics.get3DAppearance();
					hObj.setAppearance(hObj.origApp);
				}
				else // its a cell
					hObj.setAppearance(J3DAppearance.cellApp);
			}
		}
		if (!toSelect) highlighter.clear();
		if (do2D) view2D.fullRepaint();
	}

    /**
     * Observer method to highlight 3D nodes by clicking 2D objects
     * @param o
     * @param arg
     */
    public void update(Observable o, Object arg)
    {
        if (arg instanceof LayerVisibility) {
            lv = (LayerVisibility)arg;
            for (Map.Entry<Layer,J3DAppearance> e: layerAppearance.entrySet()) {
                Layer layer = e.getKey();
                J3DAppearance app = e.getValue();
                app.set3DVisibility(lv.isVisible(layer));
            }
            repaint();
            return;
        }
        if (o == view2D.getWindowFrame())
        {
            // Undo previous highlight
            selectObject(false, false);

            Highlighter highlighter2D = view2D.getHighlighter();
            List<Geometric> geomList = highlighter2D.getHighlightedEObjs(true, true);

            for (Geometric geom : geomList)
            {
                ElectricObject eobj = geom;

                List<Node> list = electricObjectMap.get(eobj);

                if (list == null || list.size() == 0) continue;

                for (Node shape : list)
                {
                    highlighter.addObject(new HighlightShape3D(shape), cell);
                }
            }
            selectObject(true, false);
            return; // done
        }
    }

    /** This class will help to remember original appearance of the node
     *
     */
    private static class HighlightShape3D
    {
        Node shape;
        Appearance origApp;

        HighlightShape3D(Node n)
        {
            this.shape = n;
            this.origApp = getAppearance(n);
        }

        static Appearance getAppearance(Node n)
        {
            if (n instanceof Shape3D)
                return ((Shape3D)n).getAppearance();
            else if (n instanceof Primitive)
                return ((Primitive)n).getAppearance();
            else
                assert(false); // it should not happen
            return null;
        }

        void setAppearance(Appearance a)
        {
            if (shape instanceof Shape3D)
                ((Shape3D)shape).setAppearance(a);
            else if (shape instanceof Primitive)
                ((Primitive)shape).setAppearance(a);
            else
                assert(false); // it should not happen
        }
    }

    /**
     * Method to change Z values in elements
     * @param value
     */
    public static void setScaleFactor(double value)
    {
	    Transform3D vTrans = new Transform3D();
	    Vector3d vCenter = new Vector3d(1, 1, value);

       	for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof View3DWindow)) continue;
			View3DWindow wnd = (View3DWindow)content;
            wnd.objTrans.getTransform(vTrans);
            vTrans.setScale(vCenter);
            wnd.objTrans.setTransform(vTrans);
		}
    }

	/**
	 * Method to turn on/off antialiasing
	 * @param value true if antialiasing is set to true
	 */
	public static void setAntialiasing(boolean value)
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof View3DWindow)) continue;
			View3DWindow wnd = (View3DWindow)content;
			View view = wnd.u.getViewer().getView();
			view.setSceneAntialiasingEnable(value);
		}
	}

    /**
     * Method to change geometry of all nodes using this particular layer
     * This could be an expensive function!.
     * @param layer
     * @param distance
     * @param thickness
     */
    public void setZValues(Layer layer, Double origDist, Double origThick, Double distance, Double thickness)
    {
//        for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
//        {
//            WindowFrame wf = it.next();
//            WindowContent content = wf.getContent();
//            if (!(content instanceof View3DWindow)) continue;
//            View3DWindow wnd = (View3DWindow)content;
            for (int i = 0; i < /*wnd.*/objTrans.numChildren(); i++)
            {
                Node node = /*wnd.*/objTrans.getChild(i);
                if (node instanceof Shape3D)
                {
                    Shape3D shape = (Shape3D)node;
                    J3DAppearance app = (J3DAppearance)shape.getAppearance();
                    if (app.getLayer() == layer)
                        J3DUtils.updateZValues(shape, origDist.floatValue(), (float)(origDist.floatValue()+origThick.floatValue()),
                                distance.floatValue(), (float)(distance.floatValue()+thickness.floatValue()));
                }
            }
//        }
    }

    /**
     * Method to get 3D appearance for a Layer. It will create
     * object if doesn't exist.
     * @param layer the Layer being drawn.
     * @return the J3DAppearance corresponding to the Layer.
     */
    private J3DAppearance getAppearance(Layer layer) {
        J3DAppearance app = layerAppearance.get(layer);
        if (app == null) {
            app = new J3DAppearance(layer, lv.isVisible(layer));
            layerAppearance.put(layer, app);
        }
        return app;
    }

    /**
     * Method to get 3D appearance for a Color. It will create
     * object if doesn't exist.
     * @param color the color being drawn.
     * @param transparency the transparency of the color (from 0.0 for opaque to 1.0 for transparent).
     * @return the J3DAppearance corresponding to the Color.
     */
    private J3DAppearance getAppearance(Color color, float transparency) {
    	String key = (color.getRGB() & 0xFFFFFF) + "-" + transparency;
        J3DAppearance app = colorAppearance.get(key);
        if (app == null) {
			app = new J3DAppearance(color, transparency);
			app.set3DVisibility(true);
            colorAppearance.put(key, app);
        }
        return app;
    }

    /**
     * Method to export directly PNG file
     * @param ep
     * @param filePath
     */
    public void writeImage(ElectricPrinter ep, String filePath)
    {
        canvas.filePath = filePath;
        saveImage(false);
    }

    public void saveImage(boolean movieMode)
    {
        canvas.movieMode = movieMode;
        canvas.writePNG_ = true;
        canvas.repaint();
    }

	/**
	 * Method to initialize for printing.
	 * @param ep the ElectricPrinter object.
	 * @param pageFormat information about the print job.
     * @return false for now.
	 */
    public boolean initializePrinting(ElectricPrinter ep, PageFormat pageFormat) { return false;}

	/**
	 * Method to print window using offscreen canvas.
	 * @param ep printable object.
	 * @return the image to print (null on error).
	 */
	public BufferedImage getPrintImage(ElectricPrinter ep)
    {
		BufferedImage bImage = ep.getBufferedImage();
        //int OFF_SCREEN_SCALE = 3;

		// might have problems if visibility of some layers is switched off
		if (bImage == null)
		{
            //Forcint the repaint
            canvas.writePNG_ = true;
            canvas.repaint();
            bImage = canvas.img;

//			// Create the off-screen Canvas3D object
//			if (offScreenCanvas3D == null)
//			{
//				offScreenCanvas3D = new J3DUtils.OffScreenCanvas3D(SimpleUniverse.getPreferredConfiguration(), true);
//				// attach the offscreen canvas to the view
//				u.getViewer().getView().addCanvas3D(offScreenCanvas3D);
//				// Set the off-screen size based on a scale3D factor times the
//				// on-screen size
//				Screen3D sOn = canvas.getScreen3D();
//				Screen3D sOff = offScreenCanvas3D.getScreen3D();
//				Dimension dim = sOn.getSize();
//				dim.width *= OFF_SCREEN_SCALE;
//				dim.height *= OFF_SCREEN_SCALE;
//				sOff.setSize(dim);
//				sOff.setPhysicalScreenWidth(sOn.getPhysicalScreenWidth() * OFF_SCREEN_SCALE);
//				sOff.setPhysicalScreenHeight(sOn.getPhysicalScreenHeight() * OFF_SCREEN_SCALE);
//				bImage = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
//				ImageComponent2D buffer = new ImageComponent2D(ImageComponent.FORMAT_RGBA, bImage);
//
//				offScreenCanvas3D.setOffScreenBuffer(buffer);
//			}
//			offScreenCanvas3D.renderOffScreenBuffer();
//			offScreenCanvas3D.waitForOffScreenRendering();
//			bImage = offScreenCanvas3D.getOffScreenBuffer().getImage();
			ep.setBufferedImage(bImage);
			//Need to remove offscreen after that
			//u.getViewer().getView().removeCanvas3D(offScreenCanvas3D);
		}
		Graphics2D g2d = (Graphics2D)ep.getGraphics();
		// In case of printing
		if (g2d != null)
		{
			AffineTransform t2d = new AffineTransform();
			t2d.translate(ep.getPageFormat().getImageableX(), ep.getPageFormat().getImageableY());
			double xscale  = ep.getPageFormat().getImageableWidth() / (double)bImage.getWidth();
			double yscale  = ep.getPageFormat().getImageableHeight() / (double)bImage.getHeight();
			double scale = Math.min(xscale, yscale);
			t2d.scale(scale, scale);

			try {
				ImageObserver obj = ep;
				g2d.drawImage(bImage, t2d, obj);
			}
			catch (Exception ex) {
				ex.printStackTrace();
				return null;
			}
		}
        return bImage;
    }

	// ************************************* EVENT LISTENERS *************************************

	//private int lastXPosition, lastYPosition;

	/**
	 * Respond to an action performed, in this case change the current cell
	 * when the user clicks on an entry in the upHierarchy popup menu.
	 */
	public void actionPerformed(ActionEvent e)
	{
		JMenuItem source = (JMenuItem)e.getSource();
		// extract library and cell from string
		Cell cell = (Cell)Cell.findNodeProto(source.getText());
		if (cell == null) return;
		setCell(cell, VarContext.globalContext, null);
	}

	// the MouseListener events
	public void mousePressed(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();

		/*
		View3DWindow wnd = (View3DWindow)evt.getSource();
		WindowFrame.setCurrentWindowFrame(wnd.wf);

		WindowFrame.curMouseListener.mousePressed(evt);
		*/
	}

	public void mouseReleased(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//WindowFrame.curMouseListener.mouseReleased(evt);
	}

    /**
     * Method to rotate individual groups
     * @param values array of values
     */
    public J3DUtils.ThreeDDemoKnot moveAndRotate(double[] values)
    {
        Vector3f newPos = new Vector3f((float)values[0], (float)values[1], (float)values[2]);
        double factor = 10;
        Quat4f quaf = J3DUtils.createQuaternionFromEuler(factor*values[3], factor*values[4], factor*values[5]);
        Transform3D currXform = new Transform3D();

        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            Variable var = ni.getVar("3D_NODE_DEMO");
            if (var == null) continue;
            List<Node> list = electricObjectMap.get(ni);
            for (int i = 0; i < list.size(); i++)
            {
                Node obj = list.get(i);
                TransformGroup grp = transformGroupMap.get(obj);

                grp.getTransform(currXform);
                currXform.setTranslation(newPos);
//                tmpVec.set(newPos);
//                boolean invert = true;
//                if (invert) {
//                    currXform.mul(currXform, tmpVec);
//                } else {
//                    currXform.mul(tmpVec, currXform);
//                }
                grp.setTransform(currXform);

                grp.getTransform(currXform);

                Matrix4d mat = new Matrix4d();
                // Remember old matrix
                currXform.get(mat);

                //tmpVec.setEuler(rotation);

                // Translate to rotation point
                currXform.setTranslation(new Vector3d(values[6], values[7], values[8]));
                currXform.setRotation(quaf);
//                if (invert) {
//                currXform.mul(currXform, tmpVec);
//                } else {
//                currXform.mul(tmpVec, currXform);
//                }

                // Set old translation back
                Vector3d translation = new
                Vector3d(mat.m03, mat.m13, mat.m23);
                currXform.setTranslation(translation);

                // Update xform
                grp.setTransform(currXform);
            }
        }
        return(new J3DUtils.ThreeDDemoKnot(1, newPos, quaf, null));
    }

	public void mouseClicked(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//WindowFrame.curMouseListener.mouseClicked(evt);
		pickCanvas.setShapeLocation(evt);
//		Transform3D t = new Transform3D();
//		Transform3D t1 = new Transform3D();
//		canvas.getImagePlateToVworld(t);
//		canvas.getVworldToImagePlate(t1);
		PickResult result = pickCanvas.pickClosest();

		// Clean previous selection
		selectObject(false, true);

		if (result != null)
		{
		   Shape3D s = (Shape3D)result.getNode(PickResult.SHAPE3D);

			if (s != null)
			{
				highlighter.addObject(new HighlightShape3D(s), cell);
				selectObject(true, true);
			}
		}
        WindowFrame.getMouseListener().mouseClicked(evt);
	}

	public void mouseEntered(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//showCoordinates(evt);
		//WindowFrame.curMouseListener.mouseEntered(evt);
	}

	public void mouseExited(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//WindowFrame.curMouseListener.mouseExited(evt);
	}

	// the MouseMotionListener events
	public void mouseMoved(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//showCoordinates(evt);
		//WindowFrame.curMouseMotionListener.mouseMoved(evt);
	}

	public void mouseDragged(MouseEvent evt)
	{
		//lastXPosition = evt.getX();   lastYPosition = evt.getY();
		//showCoordinates(evt);
		//WindowFrame.curMouseMotionListener.mouseDragged(evt);
	}

	public void showCoordinates(MouseEvent evt)
	{
		View3DWindow wnd = (View3DWindow)evt.getSource();

		if (wnd.getCell() == null) StatusBar.setCoordinates(null, wnd.wf); else
		{
			Point2D pt = wnd.screenToDatabase(evt.getX(), evt.getY());
            EditingPreferences ep = UserInterfaceMain.getEditingPreferences();
			DBMath.gridAlign(pt, ep.getAlignmentToGrid());

			StatusBar.setCoordinates("(" + TextUtils.formatDouble(pt.getX(), 2) + ", " + TextUtils.formatDouble(pt.getY(), 2) + ")", wnd.wf);
		}
	}

	// the MouseWheelListener events
	public void mouseWheelMoved(MouseWheelEvent evt) { WindowFrame.getMouseWheelListenerListener().mouseWheelMoved(evt); }

	// the KeyListener events
	public void keyPressed(KeyEvent evt) {
		System.out.println("Here keyPressed");WindowFrame.getKeyListenerListener().keyPressed(evt); }

	public void keyReleased(KeyEvent evt) {
		System.out.println("Here keyReleased");WindowFrame.getKeyListenerListener().keyReleased(evt); }

	public void keyTyped(KeyEvent evt) {
		System.out.println("Here keyTyped");WindowFrame.getKeyListenerListener().keyTyped(evt); }

	public Point getLastMousePosition()
	{
		//return new Point(lastXPosition, lastYPosition);
		return new Point(0,0);
	}

	public void databaseChanged(DatabaseChangeEvent e)
	{
        Environment oldEnv = e.oldSnapshot.environment;
        Environment newEnv = e.newSnapshot.environment;
        if (newEnv == oldEnv) return;

        if (newEnv.techPool != oldEnv.techPool) {
            Job.getUserInterface().showInformationMessage("3D Window becomes invalid after technology parameters change", "Closing 3D Window");
            wf.finished();
            return;
        }
        for (Technology tech: newEnv.techPool.values()) {
            for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); ) {
                Layer layer = it.next();
                Setting distanceSetting = layer.getDistanceSetting();
                Setting thicknessSetting = layer.getThicknessSetting();
                Double oldDistance = (Double)oldEnv.getValue(distanceSetting);
                Double oldThickness = (Double)oldEnv.getValue(thicknessSetting);
                Double newDistance = (Double)newEnv.getValue(distanceSetting);
                Double newThickness = (Double)newEnv.getValue(thicknessSetting);
                if (newDistance.equals(oldDistance) && newThickness.equals(oldThickness)) continue;
                setZValues(layer, oldDistance, oldThickness, newDistance, newThickness);
            }
        }
	}

	// ************************************* COORDINATES *************************************

	/**
	 * Method to convert a screen coordinate to database coordinates.
	 * @param screenX the X coordinate (on the screen in this EditWindow).
	 * @param screenY the Y coordinate (on the screen in this EditWindow).
	 * @return the coordinate of that point in database units.
	 */
	public Point2D screenToDatabase(int screenX, int screenY)
	{
		double dbX = 0, dbY = 0;
		/*
		= (screenX - sz.width/2) / scale3D + offx;
		double dbY = (sz.height/2 - screenY) / scale3D + offy;

		*/
		return new Point2D.Double(dbX, dbY);
	}

    /*****************************************************************************
     *          To navigate in tree and create 3D objects                           *
     *****************************************************************************/
	private class View3DEnumerator extends HierarchyEnumerator.Visitor
    {
        public View3DEnumerator(Cell cell)
		{
            // Checking if field poly and gate poly are not aligned.
            // If not, the extra polyhedra are generated to build like a LoCos shape
            Technology tech = cell.getTechnology();
            Layer poly = tech.findLayerFromFunction(Layer.Function.POLY1, -1);
            Layer gate = tech.findLayerFromFunction(Layer.Function.GATE, -1);
            if (poly != null && gate != null && !DBMath.areEquals(poly.getDistance(), gate.getDistance()))
            {
                locosShape = true;
            }
        }

		/**
		 */
		public boolean enterCell(HierarchyEnumerator.CellInfo info)
		{
            if (job != null && job.checkAbort()) return false;
            if (!isSizeLimitOK(info.getCell().getNumArcs() + objTrans.numChildren())) return false;  // limit reached

            FixpTransform rTrans = info.getTransformToRoot();

			for(Iterator<ArcInst> it = info.getCell().getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (ai.getBounds().isEmpty())
				{
					System.out.println("Skipping arc '" + ai.getName() + "' due to its zero area");
					continue;
				}
				addArc(ai, rTrans, objTrans);
			}
			return true;
		}

		/**
		 */
		public void exitCell(HierarchyEnumerator.CellInfo info) {}

		/**
		 */
		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
		{
            if (reachLimit) return false;
            if (job != null && job.checkAbort()) return false;

			NodeInst ni = no.getNodeInst();
			ERectangle rect = ni.getBounds();
			
			if (ni.getBounds().isEmpty())
			{
				System.out.println("Skipping node '" + ni.getName() + "' due to its zero area");
				return false; // error
			}
			
			FixpTransform trans = ni.rotateOutAboutTrueCenter();
			FixpTransform root = info.getTransformToRoot();
			if (root.getType() != FixpTransform.TYPE_IDENTITY)
				trans.preConcatenate(root);

            TransformGroup grp = objTrans;
            if (oneTransformPerNode)
            {
                grp = new TransformGroup();
                grp.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		        grp.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
                grp.setCapability(TransformGroup.ALLOW_CHILDREN_EXTEND);
                grp.setCapability(TransformGroup.ALLOW_CHILDREN_READ);
                grp.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);
                objTrans.addChild(grp);
            }
			addNode(ni, trans, grp);

            if (!isSizeLimitOK(grp.numChildren())) return false;  // limit reached

			// For cells, it should go into the hierarchy
            return ni.isExpanded();
		}
    }

    /*****************************************************************************
     *          Demo Stuff                                                       *
     *****************************************************************************/

    /**
     * Method to create spline interpolator for demo mode
     */
    private void setInterpolator()
    {
        Transform3D yAxis = new Transform3D();
        List<J3DUtils.ThreeDDemoKnot> polys = new ArrayList<J3DUtils.ThreeDDemoKnot>();

        double [] zValues = new double[2];
        cell.getZValues(zValues);
        double zCenter = (zValues[0] + zValues[1])/2;
        Rectangle2D bounding = cell.getBounds();
        Vector3d translation = new Vector3d (bounding.getCenterX(), bounding.getCenterY(), zCenter);
        yAxis.setTranslation(translation);

        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            if (ni.getProto() == Artwork.tech().pinNode)
            {
                Rectangle2D rect = ni.getBounds();
                Variable var = ni.getVar("3D_Z_VALUE");
                double zValue = (var == null) ? zCenter : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_SCALE_VALUE");
                double scale = (var == null) ? 1 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_HEADING_VALUE");
                double heading = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_PITCH_VALUE");
                double pitch = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_BANK_VALUE");
                double bank = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_ROTX_VALUE");
                double rotX = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_ROTY_VALUE");
                double rotY = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                var = ni.getVar("3D_ROTZ_VALUE");
                double rotZ = (var == null) ? 0 : TextUtils.atof(var.getObject().toString());
                J3DUtils.ThreeDDemoKnot knot = new J3DUtils.ThreeDDemoKnot(rect.getCenterX(), rect.getCenterY(),
                        zValue, scale, heading, pitch, bank, rotX, rotY, rotZ);
                polys.add(knot);
            }
        }

        if (polys.size() == 0) return; // nothing to create

        KBKeyFrame[] splineKeyFrames = new KBKeyFrame[polys.size()];
        TCBKeyFrame[] keyFrames = new TCBKeyFrame[polys.size()];
        for (int i = 0; i < polys.size(); i++)
        {
            J3DUtils.ThreeDDemoKnot knot = polys.get(i);
            splineKeyFrames[i] = J3DUtils.getNextKBKeyFrame((float)((float)i/(polys.size()-1)), knot);
            keyFrames[i] = J3DUtils.getNextTCBKeyFrame((float)((float)i/(polys.size()-1)), knot);
        }

        Interpolator tcbSplineInter = new RotPosScaleTCBSplinePathInterpolator(J3DUtils.jAlpha, objTrans,
                                                  yAxis, keyFrames);
        tcbSplineInter.setSchedulingBounds(J3DUtils.infiniteBounds);
        tcbSplineInter.setEnable(false);
        interpolatorMap.put(objTrans, tcbSplineInter);

        objTrans.addChild(tcbSplineInter);
    }

    /**
     * Method to create a path interpolator using knots
     * defined in input list
     * @param knotList list with knot data. If null, search for data attached to nodes
     */
    public Map<TransformGroup,BranchGroup> addInterpolator(List<J3DUtils.ThreeDDemoKnot> knotList)
    {
        if (knotList != null && knotList.size() < 2)
        {
            System.out.println("Needs at least 2 frams for the interpolator");
            return null;
        }

        Map<TransformGroup,BranchGroup> interMap = new HashMap<TransformGroup,BranchGroup>(1);
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
        {
            NodeInst ni = it.next();
            Variable var = ni.getVar("3D_NODE_DEMO");
            if (var == null) continue;
            List<J3DUtils.ThreeDDemoKnot> tmpList = knotList;
            if (tmpList == null)
            {
                tmpList = J3DUtils.readDemoDataFromFile(this);
                if (tmpList == null) continue; // nothing load
            }
            List<Node> list = electricObjectMap.get(ni);
            for (int j = 0; j < list.size(); j++)
            {
                Node obj = list.get(j);
                TransformGroup grp = transformGroupMap.get(obj);
                interMap = addInterpolatorPerGroup(tmpList, grp, interMap, false);
//                BranchGroup behaviorBranch = new BranchGroup();
//                behaviorBranch.setCapability(BranchGroup.ALLOW_DETACH); // to detach this branch from parent group
//                TCBKeyFrame[] keyFrames = new TCBKeyFrame[tmpList.size()];
//                for (int i = 0; i < tmpList.size(); i++)
//                {
//                    J3DUtils.ThreeDDemoKnot knot = tmpList.get(i);
//                    keyFrames[i] = J3DUtils.getNextTCBKeyFrame((float)((float)i/(tmpList.size()-1)), knot);
//                }
//                Transform3D yAxis = new Transform3D();
//                Interpolator tcbSplineInter = new RotPosScaleTCBSplinePathInterpolator(J3DUtils.jAlpha, grp,
//                                                          yAxis, keyFrames);
//                tcbSplineInter.setSchedulingBounds(new BoundingSphere(new Point3d(), Double.MAX_VALUE));
//                behaviorBranch.addChild(tcbSplineInter);
//                interMap.put(grp, behaviorBranch);
//                grp.addChild(behaviorBranch);
//                interpolatorMap.put(grp, tcbSplineInter);
            }
        }
        return interMap;
    }


    /**
     * Method to add interpolator per group
     * @param knotList
     * @param grp
     * @param interMap
     * @return Map with interpolation groups
     */
    public Map<TransformGroup,BranchGroup> addInterpolatorPerGroup(List<J3DUtils.ThreeDDemoKnot> knotList, TransformGroup grp, Map<TransformGroup,BranchGroup> interMap, boolean useView)
    {
        if (knotList == null || knotList.size() < 2)
        {
            System.out.println("Needs at least 2 frams for the interpolator");
            return null;
        }

        if (interMap == null)
            interMap = new HashMap<TransformGroup,BranchGroup>(1);
        if (grp == null)
        {
            if (!useView) grp = objTrans;
            else grp = u.getViewingPlatform().getViewPlatformTransform();
        }
        BranchGroup behaviorBranch = new BranchGroup();
        behaviorBranch.setCapability(BranchGroup.ALLOW_DETACH); // to detach this branch from parent group
        TCBKeyFrame[] keyFrames = new TCBKeyFrame[knotList.size()];
        for (int i = 0; i < knotList.size(); i++)
        {
            J3DUtils.ThreeDDemoKnot knot = knotList.get(i);
            keyFrames[i] = J3DUtils.getNextTCBKeyFrame((float)((float)i/(knotList.size()-1)), knot);
        }
        Transform3D yAxis = new Transform3D();
        Interpolator tcbSplineInter = new J3DRotPosScaleTCBSplinePathInterpolator(J3DUtils.jAlpha, grp,
                                                  yAxis, keyFrames, knotList);
        tcbSplineInter.setSchedulingBounds(new BoundingSphere(new Point3d(), Double.MAX_VALUE));
        behaviorBranch.addChild(tcbSplineInter);
        interMap.put(grp, behaviorBranch);
        grp.addChild(behaviorBranch);
        interpolatorMap.put(grp, tcbSplineInter);
        return interMap;
    }

    /**
     * Method to remove certain interpolators from scene graph
     * @param interMap
     */
    public void removeInterpolator(Map<TransformGroup,BranchGroup> interMap)
    {
        canvas.resetMoveFrames();
        for (TransformGroup grp : interMap.keySet())
        {
            Node node = interMap.get(grp);
            grp.removeChild(node);
        }
    }

    ///////////////////// KEY BEHAVIOR FUNCTION ///////////////////////////////

    private static Vector3d tmpVec = new Vector3d();
    private static Vector3d mapSize = null;

    protected double getScale( )
	{
		return 0.05;
	}

    Vector3d getMapSize( )
    {
        if (mapSize == null)
            mapSize = new Vector3d(2, 0, 2);
        return mapSize;
    }

    Point2d convertToMapCoordinate( Vector3d worldCoord )
	{
		Point2d point2d = new Point2d( );

		Vector3d squareSize = getMapSize();

		point2d.x = (worldCoord.x + getPanel().getWidth())/ squareSize.x;
		point2d.y = (worldCoord.z + getPanel().getHeight())/ squareSize.z;

		return point2d;
	}

    public boolean isCollision(Transform3D t3d)
	{
		// get the translation
		t3d.get(tmpVec);

		// we need to scale up by the scale that was
		// applied to the root TG on the view side of the scenegraph
			tmpVec.scale( 1.0 / getScale( ) );

//        Vector3d mapSquareSize = getMapSize( );

		// first check that we are still inside the "world"
//		if (tmpVec.x < -getPanel().getWidth() + mapSquareSize.x ||
//			tmpVec.x > getPanel().getWidth() - mapSquareSize.x ||
//			tmpVec.y < -getPanel().getHeight() + mapSquareSize.y ||
//			tmpVec.y > getPanel().getHeight() - mapSquareSize.y  )
//			return true;

        // then do a pixel based look up using the map
        return isCollision(tmpVec);
	}

    /**
     * Method to detect if give x, y location in the world collides with geometry
     * @param worldCoord
     * @return true if vector collides
     */
	protected boolean isCollision( Vector3d worldCoord )
	{
		Point2d point = convertToMapCoordinate( worldCoord );

//        PickTool pickTool = new PickTool(scene);
//				pickTool.setMode( PickTool.BOUNDS );
//
//				BoundingSphere bounds = (BoundingSphere) objTrans.getBounds( );
//				PickBounds pickBounds = new PickBounds( new BoundingSphere( new Point3d(keyBehavior.positionVector.x,
//                        keyBehavior.positionVector.y, keyBehavior.positionVector.z), bounds.getRadius( ) ) );
//				pickTool.setShape( pickBounds, new Point3d(0, 0, 0));
//				PickResult[] resultArray = pickTool.pickAll( );
//
//        System.out.println( "Wold Point " + worldCoord + " local " + keyBehavior.positionVector);
//
//        if (resultArray != null)
//        {
//        for( int n = 0; n < resultArray.length; n++ )
//		{
//			Object userData = resultArray[n].getObject( ).getUserData( );
//
//			if ( userData != null && userData instanceof String )
//			{
//					System.out.println( "Collision between: " + objTrans.getUserData( ) + " and: " + userData );
//				// check that we are not colliding with ourselves...
//				if ( ((String) userData).equals( (String) objTrans.getUserData( ) ) == false )
//				{
//					System.out.println( "Collision between: " + objTrans.getUserData( ) + " and: " + userData );
//					return true;
//				}
//			}
//		}
//        }

        pickCanvas.setShapeLocation((int)point.x, (int)point.y);
        PickResult result = pickCanvas.pickClosest();

        if (result != null && result.getNode(PickResult.SHAPE3D) != null)
        {
//             Shape3D shape = (Shape3D)result.getNode(PickResult.SHAPE3D);
             //shape.setAppearance(J3DAppearance.highlightApp);
            for (int i = 0; i < result.numIntersections(); i++)
            {
                PickIntersection inter = result.getIntersection(i);
//            System.out.println("Collision " + inter.getDistance() + " " + inter.getPointCoordinates() + " normal " + inter.getPointNormal());
//                 System.out.println("Point  " + point + " world " + worldCoord);
//                GeometryArray geo = inter.getGeometryArray();

                if (inter.getDistance() < 6)
                    return (true); // collision
            }
        }

        return (false);
	}

    public J3DUtils.ThreeDDemoKnot addFrame(boolean useView)
    {
        Transform3D tmpTrans = new Transform3D();
        if (!useView) objTrans.getTransform(tmpTrans);
        else u.getViewingPlatform().getViewPlatformTransform().getTransform(tmpTrans);
        tmpTrans.get(tmpVec);
        Quat4f rot = new Quat4f();
        tmpTrans.get(rot);
        Shape3D shape = null;


//        for (Highlight h : highlighter.getHighlights())
//		{
//			shape = (Shape3D)h.getObject();
//            break;
//        }
//        repaint();
        return(new J3DUtils.ThreeDDemoKnot(1, new Vector3f(tmpVec), rot, shape));
    }

    public void saveMovie(File file)
    {
        if (file != null)
            canvas.saveMovie(file);
    }

    private static class J3DRotPosScaleTCBSplinePathInterpolator extends com.sun.j3d.utils.behaviors.interpolators.RotPosScaleTCBSplinePathInterpolator
    {
//        List<J3DUtils.ThreeDDemoKnot> knotList;
//        int previousUpper = -1, previousLower = -1;

        public J3DRotPosScaleTCBSplinePathInterpolator(Alpha alpha, TransformGroup target, Transform3D axisOfTransform, TCBKeyFrame[] keys, List<J3DUtils.ThreeDDemoKnot> list)
        {
            super(alpha, target, axisOfTransform, keys);
//            knotList = list;
        }

        public void processStimulus(Enumeration criteria)
        {
            super.processStimulus(criteria);
//
//            if (upperKnot == previousUpper && lowerKnot == previousLower) return;
//            previousUpper = upperKnot;
//            previousLower = lowerKnot;
//            J3DUtils.ThreeDDemoKnot knot = knotList.get(upperKnot-1);
//            if (knot != null && knot.shape != null)
//                target.addChild(knot.shape);
////            knot.shape.getAppearance().getRenderingAttributes().setVisible(true);
//            knot = knotList.get(lowerKnot-1);
//            if (knot != null && knot.shape != null)
////                target.removeChild(knot.shape);
//            knot.shape.getAppearance().getRenderingAttributes().setVisible(false);
////            System.out.println("Criteria " + upperKnot + " " + lowerKnot);
        }
    }
}
