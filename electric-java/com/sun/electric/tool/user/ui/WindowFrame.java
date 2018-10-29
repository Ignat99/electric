/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WindowFrame.java
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
package com.sun.electric.tool.user.ui;

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.Topology;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.simulation.Stimuli;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.user.menus.WindowMenu;
import com.sun.electric.tool.user.waveform.WaveformWindow;

import com.sun.electric.util.ClientOS;
import com.sun.electric.util.math.FixpTransform;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyVetoException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;

import java.util.Set;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.tree.MutableTreeNode;

/**
 * This class defines an edit window, with a cell explorer on the left side.
 */
public class WindowFrame extends Observable
{
	/** offset between newly created windows */			private static final int WINDOW_OFFSET = 0;		// was 150

	/** the nature of the main window part (from above) */	private WindowContent content;
	/** the split pane that shows explorer and edit. */	private JSplitPane js;
    /** the internal frame (if MDI). */					private JInternalFrame jif = null;
    /** the top-level frame (if SDI). */				private TopLevel jf = null;
    /** the internalframe listener */                   private InternalWindowsEvents internalWindowsEvents;
    /** the window event listener */                    private WindowsEvents windowsEvents;
	/** the side bar (explorer, components, etc) */		private JTabbedPane sideBar;
	/** true if sidebar is on the left */				private boolean sideBarOnLeft;
	/** the explorer tab */								private ExplorerTree explorerTab;
	/** the component tab */							private PaletteFrame paletteTab;
	/** the layers tab */								private LayerTab layersTab;
    /** true if this window is finished */              private boolean finished = false;
    /** the index of this window */						private int index;
	/** indicator of the most recent used window */		private int usageClock;
    /** the dynamic menu to hold WindowMenus */			private JMenu dynamicMenu;

	/** the overall usage counter */					private static int usageCounter = 0;
    /** the unique index number of all windows */		private static int windowIndexCounter = 0;
	/** the offset of each new windows from the last */	private static int windowOffset = 0;
	/** the list of all windows on the screen */		private static List<WindowFrame> windowList = new ArrayList<WindowFrame>();
	/** the current windows. */							private static WindowFrame curWindowFrame = null;

    /** current ElectricEventListener */                private static ElectricEventListener curListener = null;
//	/** current mouse listener */						private static MouseListener curMouseListener = null;
//    /** current mouse motion listener */				private static MouseMotionListener curMouseMotionListener = null;
//    /** current mouse wheel listener */					private static MouseWheelListener curMouseWheelListener = null;
//    /** current key listener */							private static KeyListener curKeyListener = null;

    /**
     * Use by highlight tools to bring the frame to the front.
     * @param wf
     */
    public static void showFrame(WindowFrame wf) {
        if (TopLevel.isMDIMode()) {
            JInternalFrame jif = wf.getInternalFrame();
            try {
                jif.setIcon(false);
                jif.setSelected(true);
            } catch (PropertyVetoException e) {}
            if (!jif.isVisible()) {
                jif.toFront();
                TopLevel.addToDesktop(jif);
            } else
                jif.toFront();
        } else {
            JFrame jf = wf.getFrame();
            jf.setState(Frame.NORMAL);
            if (!jf.isVisible()) {
                jf.toFront();
                jf.setVisible(true);
            } else
                jf.toFront();
        }
    }

    public static void repaintAllWindows()
    {
		for(Iterator<WindowFrame> it = getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			content.fullRepaint();
		}
        for(Iterator<WindowFrame> it = getWindows(); it.hasNext(); )
        {
        	WindowFrame wf = it.next();
        	wf.loadComponentMenuForTechnology();
        }
        PreferencesFrame.updateLayerPreferencesColors();
    }

    /** library tree updater */                         static { new LibraryTreeUpdater(); }

    public static class DisplayAttributes
    {
		/** the window scale */									public final double scale;
		/** the window offset */								public final double offX, offY;
		/** path to cell being edited (down-in-place) */		public final List<NodeInst> inPlaceDescent;

		public DisplayAttributes() {
            this(1, 0, 0, Collections.<NodeInst>emptyList());
        }

		public DisplayAttributes(EditWindow wnd) {
            this(wnd.getScale(), wnd.getOffset().getX(), wnd.getOffset().getY(), wnd.getInPlaceEditNodePath());
        }

		public DisplayAttributes(double scale, double offX, double offY, List<NodeInst> inPlaceDescent) {
            this.scale = scale;
            this.offX = offX;
            this.offY = offY;
            this.inPlaceDescent = new ArrayList<NodeInst>(inPlaceDescent);
        }

        public FixpTransform getIntoCellTransform() {
            FixpTransform intoCell = new FixpTransform();
            for (NodeInst ni : inPlaceDescent)
                intoCell.preConcatenate(ni.rotateIn(ni.translateIn()));
            return intoCell;
        }

        public FixpTransform getOutofCellTransform() {
            FixpTransform outofCell = new FixpTransform();
            for (NodeInst ni : inPlaceDescent)
                outofCell.concatenate(ni.translateOut(ni.rotateOut()));
            return outofCell;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DisplayAttributes) {
                DisplayAttributes that = (DisplayAttributes)o;
                return this.scale == that.scale && this.offX == that.offX && this.offY == that.offY &&
                        this.inPlaceDescent.equals(that.inPlaceDescent);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 53 * hash + (int) (Double.doubleToLongBits(this.scale) ^ (Double.doubleToLongBits(this.scale) >>> 32));
            hash = 53 * hash + (int) (Double.doubleToLongBits(this.offX) ^ (Double.doubleToLongBits(this.offX) >>> 32));
            hash = 53 * hash + (int) (Double.doubleToLongBits(this.offY) ^ (Double.doubleToLongBits(this.offY) >>> 32));
            return hash;
        }
    }

    //******************************** CONSTRUCTION ********************************

	// constructor
	public WindowFrame()
	{
		index = windowIndexCounter++;
        cellHistory = new ArrayList<CellHistory>();
        cellHistoryLocation = -1;
	}

    /**
     * Method to set the dynamic menu where
     * @param menu
     */
    public void setDynamicMenu(JMenu menu) {dynamicMenu = menu;}
    public JMenu getDynamicMenu() { return dynamicMenu; }

    /**
	 * Method to create a new circuit-editing window on the screen that displays a Cell.
	 * @param cell the cell to display.
	 * @return the WindowFrame that shows the Cell.
	 */
	public static WindowFrame createEditWindow(final Cell cell)
	{
		final WindowFrame frame = new WindowFrame();
		if (cell != null && cell.getView().isTextView())
		{
			TextWindow tWnd = new TextWindow(cell, frame);
            frame.finishWindowFrameInformation(tWnd, cell);
			tWnd.fillScreen();
		} else
		{
			EditWindow eWnd = EditWindow.CreateElectricDoc(cell, frame, null);
			frame.finishWindowFrameInformation(eWnd, cell);
			eWnd.fillScreen();
			eWnd.fullRepaint();
		}
        removeUIBinding(frame.js, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        removeUIBinding(frame.js, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0));

        if (cell != null)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    frame.explorerTab.openLibraryInExplorerTree(cell.getLibrary(), cell, true);
                }
            });
        }
		return frame;
	}

	/**
	 * Method to get the unique index number of this WindowFrame.
	 * @return the unique index number of this WindowFrame.
	 */
	public int getIndex() { return index; }

	/**
	 * Method to find the WindowFrame associated with an index number.
	 * @param index the index number.
	 * @return the associated WindowFrame (null if none found).
	 */
	public static WindowFrame findFromIndex(int index)
	{
        for (WindowFrame wf: windowList)
        	if (wf.index == index) return wf;
		return null;
	}

	/**
     * Method to finish with frame pointers.
     */
    public void finishWindowFrameInformation(WindowContent wnd, Cell cell)
    {
        buildWindowStructure(wnd, cell, null);
        setCurrentWindowFrame(this, true);
        populateJFrame();
        // Adding into WindowMenu
        WindowMenu.setDynamicMenus();
    }

    /*****************************************************************************
     *          3D Stuff                                                         *
     *****************************************************************************/

	/**
	 * Method to access 3D view and highligh elements if view is available
	 */
	public static void show3DHighlight()
	{
        for(Iterator<WindowFrame> it = getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
            wf.setChanged();
            wf.notifyObservers(wf.getContent());
            wf.clearChanged();
        }
	}

	/**
	 * Method to notify 3D view about change of Layer visibility
	 */
	public void set3DLayerVisibility(LayerVisibility lv)
	{
        setChanged();
        notifyObservers(lv);
        clearChanged();
	}

    /*****************************************************************************
     *          END OF 3D Stuff                                                  *
     *****************************************************************************/

	/**
	 * Method to create a new waveform window on the screen given the simulation data.
	 * @param sd the simulation data to use in the waveform window.
	 * @return the WindowFrame that shows the waveforms.
	 */
	public static WindowFrame createWaveformWindow(Stimuli sd)
	{
		WindowFrame frame = new WindowFrame();
		WaveformWindow wWnd = new WaveformWindow(sd, frame);
        frame.finishWindowFrameInformation(wWnd, sd.getCell());
		wWnd.fillScreen();

		// open the "SIGNALS" part of the explorer panel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() { WindowFrame.wantToOpenCurrentLibrary(false, null); }});
		return frame;
	}

	/**
	 * Method to update all technology popup selectors.
	 * Called when a new technology has been created.
	 */
	public static void updateTechnologyLists()
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			wf.layersTab.loadTechnologies(false);
			wf.paletteTab.loadTechnologies(false);
            wf.getContent().loadTechnologies();
		}
	}

    /**
     * Class to handle changes the "Technology" selection of either the Layer tab or the Component tab of the side bar.
     */
	public static class CurTechControlListener implements ActionListener
    {
        private WindowFrame wf;

		CurTechControlListener(WindowFrame wf)
        {
        	this.wf = wf;
        }

        public void actionPerformed(ActionEvent evt)
		{
			JComboBox source = (JComboBox)evt.getSource();
            String techName = (String)source.getSelectedItem();
            Technology tech = Technology.findTechnology(techName);
            if (tech != null)
			{
	            // change the technology
                Job.getExtendedUserInterface().setCurrentTechnology(tech);
				wf.getPaletteTab().loadForTechnology(tech, wf);
				wf.getLayersTab().updateLayersTab();
				WindowContent content = wf.getContent();
				if (content != null) content.fullRepaint();
            }
        }
    }

    public List<MutableTreeNode> loadDefaultExplorerTree()
    {
        MutableTreeNode libraryExplorerNode = ExplorerTreeModel.makeLibraryTree();
        return Collections.singletonList(libraryExplorerNode);
    }

	private Dimension buildWindowStructure(WindowContent content, Cell cell, GraphicsConfiguration gc)
	{
		this.content = content;

		// the left half: an explorer tree in a scroll pane
		explorerTab = new ExplorerTree(content.loadExplorerTrees());
		JScrollPane scrolledTree = new JScrollPane(explorerTab);

		// make a tabbed list of panes on the left
		sideBar = new JTabbedPane();

		// Only Mac version will align tabs on the left. The text orientation is vertical by default on Mac
        if (ClientOS.isOSMac())
			sideBar.setTabPlacement(JTabbedPane.LEFT);
		paletteTab = PaletteFrame.newInstance(this);
		layersTab = new LayerTab(this);
		loadComponentMenuForTechnology();

		sideBar.add("Components", paletteTab.getMainPanel());
		sideBar.add("Explorer", scrolledTree);
		sideBar.add("Layers", layersTab);

		sideBar.setSelectedIndex(User.getDefaultWindowTab());
		sideBar.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
            	JTabbedPane tp = (JTabbedPane)evt.getSource();
            	User.setDefaultWindowTab(tp.getSelectedIndex());
            }
        });

		// initialize the frame
		String cellDescription = (cell == null) ? "no cell" : cell.describe(false);
		Dimension sz = createJFrame(cellDescription, gc);
		windowOffset += 70;
		if (windowOffset > 300) windowOffset = 0;

		// put them together into the split pane
		js = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		sideBarOnLeft = !User.isSideBarOnRight();

        JComponent panel = content.getPanel();
        if (User.isDockMessagesWindow()) {
            JSplitPane js2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            js2.setTopComponent(panel);
            js2.setBottomComponent(new MessagesWindow().getContent());
            js2.setDividerLocation(js2.getMaximumDividerLocation());
            panel = js2;
        }

		if (sideBarOnLeft)
		{
			js.setLeftComponent(sideBar);
			js.setRightComponent(panel);
			js.setDividerLocation(200);
		} else
		{
			js.setLeftComponent(panel);
			js.setRightComponent(sideBar);
			js.setDividerLocation(sz.width - 200);
		}

		// accumulate a list of current windows
		synchronized(windowList) {
			windowList.add(this);
		}

		return sz;
	}

	/**
	 * Method to create a JFrame that will hold all the Components in this WindowFrame.
	 * @param title the window title.
	 * @param gc the GraphicsConfiguration (which screen the window is on).
	 * @return the size of the frame.
	 */
	private Dimension createJFrame(String title, GraphicsConfiguration gc)
	{
		Dimension scrnSize = TopLevel.getScreenSize();
		Dimension frameSize = new Dimension(scrnSize.width * 4 / 5, scrnSize.height * 6 / 8);
		if (TopLevel.isMDIMode())
		{
			jif = new JInternalFrame(title, true, true, true, true);
			jif.setSize(frameSize);
			jif.setLocation(windowOffset+WINDOW_OFFSET, windowOffset);
			jif.setAutoscrolls(true);
			jif.setFrameIcon(TopLevel.getFrameIcon());
		} else
		{
			jf = new TopLevel("Electric - " + title, new Rectangle(frameSize), this, gc, true);
			Dimension preferredSize = User.getDefaultWindowSize();
			if (preferredSize != null) frameSize = preferredSize;
			jf.setSize(frameSize);
			Point preferredLoc = User.getDefaultWindowPos();
			jf.setLocation(windowOffset+WINDOW_OFFSET+preferredLoc.x, windowOffset+preferredLoc.y);
		}
		return frameSize;
	}

    /**
     * Set the Technology popup in the component tab and the layers tab to the current technology.
     */
	public void loadComponentMenuForTechnology()
	{
		Technology tech = Technology.getCurrent();
		if (content.getCell() != null)
		{
			tech = content.getCell().getTechnology();
		}

		//Technology tech = Technology.findTechnology(User.getDefaultTechnology());
		paletteTab.loadForTechnology(tech, this);
		layersTab.updateLayersTab();
	}

	/**
	 * Populate the JFrame with the Components
	 */
	private void populateJFrame()
	{
		if (TopLevel.isMDIMode())
		{
			jif.getContentPane().add(js);
			internalWindowsEvents = new InternalWindowsEvents(this);
			jif.addInternalFrameListener(internalWindowsEvents);
			// add tool bar as listener so it can find out state of cell history in EditWindow
//			jif.addInternalFrameListener(TopLevel.getCurrentJFrame().getToolBar());
			//content.getPanel().addPropertyChangeListener(TopLevel.getTopLevel().getToolBar());
//			content.getPanel().addPropertyChangeListener(EditWindow.propGoBackEnabled, TopLevel.getCurrentJFrame().getToolBar());
//			content.getPanel().addPropertyChangeListener(EditWindow.propGoForwardEnabled, TopLevel.getCurrentJFrame().getToolBar());
//			frame.jif.moveToFront();
            TopLevel.addToDesktop(jif);
		} else
		{
			jf.getContentPane().add(js);
			windowsEvents = new WindowsEvents(this);
			jf.addWindowListener(windowsEvents);
			jf.addWindowFocusListener(windowsEvents);
			// add tool bar as listener so it can find out state of cell history in EditWindow
//			content.getPanel().addPropertyChangeListener(EditWindow.propGoBackEnabled, ((TopLevel)jf).getToolBar());
//			content.getPanel().addPropertyChangeListener(EditWindow.propGoForwardEnabled, ((TopLevel)jf).getToolBar());
			/*if (!Job.BATCHMODE)*/ jf.setVisible(true);
		}
	}

	public Component getComponent()
	{
		if (TopLevel.isMDIMode()) return jif;
		return jf;
	}

	/**
	 * Method to return the usage clock of this WindowFrame.
	 * Each activation of the WindowFrame increments the value, so the WindowFrame
	 * with the highest value is the one most recently activated.
	 * @return the usage clock of this WindowFrame.
	 */
	public int getUsageClock()
	{
		return usageClock;
	}

	/**
	 * Depopulate the JFrame.  Currently this is only used in SDI mode when
	 * moving a WindowFrame from one display to another.  A new JFrame on the
	 * new display must be created and populated with the WindowFrame Components.
	 * To do so, those components must first be removed from the old frame.
	 */
	private void depopulateJFrame()
	{
        assert SwingUtilities.isEventDispatchThread();
		if (TopLevel.isMDIMode()) {
			jif.getContentPane().remove(js);
			jif.removeInternalFrameListener(internalWindowsEvents);
//			jif.removeInternalFrameListener(TopLevel.getCurrentJFrame().getToolBar());
			// TopLevel.removeFromDesktop(jif);
//			content.getPanel().removePropertyChangeListener(TopLevel.getCurrentJFrame().getToolBar());
//			content.getPanel().removePropertyChangeListener(TopLevel.getCurrentJFrame().getToolBar());
            TopLevel.removeFromDesktop(jif);
		} else {
			jf.getContentPane().remove(js);
			jf.removeWindowListener(windowsEvents);
			jf.removeWindowFocusListener(windowsEvents);
//			content.getPanel().removePropertyChangeListener(EditWindow.propGoBackEnabled, ((TopLevel)jf).getToolBar());
//			content.getPanel().removePropertyChangeListener(EditWindow.propGoForwardEnabled, ((TopLevel)jf).getToolBar());
		}
	}

	//******************************** WINDOW CONTROL ********************************

	/**
	 * Method to show a cell in the right-part of this WindowFrame.
	 * Handles both circuit cells and text cells.
	 * @param cell the Cell to display.
	 */
	public void setCellWindow(Cell cell, CellHistory history)
	{
		if (cell != null && cell.getView().isTextView())
		{
			// want a TextWindow here
			if (!(getContent() instanceof TextWindow))
			{
				getContent().finished();
				content = new TextWindow(cell, this);
				int i = js.getDividerLocation();
				if (sideBarOnLeft) js.setRightComponent(content.getPanel()); else
					js.setLeftComponent(content.getPanel());
				js.setDividerLocation(i);
				content.fillScreen();
				if (history == null) addToHistory(cell, VarContext.globalContext, null);
				currentCellChanged(true);
				return;
			}
		} else
		{
			// want an EditWindow here
			if (!(getContent() instanceof EditWindow))
			{
				getContent().finished();
				Component c = js.getLeftComponent();
				if (sideBarOnLeft) c = js.getRightComponent();
				Dimension sz = c.getSize();
				content = EditWindow.CreateElectricDoc(cell, this, sz);
				int i = js.getDividerLocation();
				if (sideBarOnLeft) js.setRightComponent(content.getPanel()); else
					js.setLeftComponent(content.getPanel());
				js.setDividerLocation(i);
				content.fillScreen();
				if (history == null) addToHistory(cell, VarContext.globalContext, null);
				currentCellChanged(true);
				loadComponentMenuForTechnology();
				return;
			}
		}

		// proper window type already there: switch cells
		DisplayAttributes da = null;
		VarContext upperContext = VarContext.globalContext;
		if (history != null)
		{
			da = history.da;
			upperContext = history.context;
		}
		content.setCell(cell, upperContext, da);
        currentCellChanged(true);
		loadComponentMenuForTechnology();

        // Adding into WindowMenu
        WindowMenu.setDynamicMenus();
        if (history == null) addToHistory(cell, VarContext.globalContext, null);
	}

	public void moveEditWindow(GraphicsConfiguration gc) {

		if (TopLevel.isMDIMode()) return;           // only valid in SDI mode
		jf.setVisible(false);                       // hide old Frame
		//jf.getFocusOwner().setFocusable(false);
		//System.out.println("Set unfocasable: "+jf.getFocusOwner());
		depopulateJFrame();                         // remove all components from old Frame
		TopLevel oldFrame = jf;
		Cell cell = content.getCell();                  // get current cell
		String cellDescription = (cell == null) ? "no cell" : cell.describe(false);  // new title
		createJFrame(cellDescription, gc);          // create new Frame
		populateJFrame();                           // populate new Frame
		fireCellHistoryStatus();                // update tool bar history buttons
		oldFrame.finished();                        // clear and garbage collect old Frame
	}

	//******************************** EXPLORER PART ********************************

	/**
	 * Method to force the explorer tree to show the current library or signals list.
	 * @param openLib true to open the current library, false to open the signals list.
     * @param cell cell to select in ExplorerTree
     */
	public static void wantToOpenCurrentLibrary(boolean openLib, Cell cell)
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			wf.explorerTab.openLibraryInExplorerTree(Library.getCurrent(), cell, openLib);
		}
	}

	/**
     * Method to request that the library tree be reloaded.
     */
	public static void wantToRedoLibraryTree()
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
            wf.explorerTab.redoContentTrees(wf.content.loadExplorerTrees());
		}
	}

	public void wantToRedoSignalTree()
	{
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { wantToRedoSignalTree(); }
            });
            return;
        }
        if (explorerTab!=null)
            explorerTab.redoContentTrees(content.loadExplorerTrees());
	}

	public static void setSideBarLocation(boolean onLeft)
	{
		WindowFrame wf = getCurrentWindowFrame();
		if (wf.sideBarOnLeft == onLeft) return;
		wf.sideBarOnLeft = onLeft;

		wf.js.setLeftComponent(null);
		wf.js.setRightComponent(null);
		Dimension sz = wf.js.getSize();
		int loc = sz.width - wf.js.getDividerLocation();
		wf.js.setDividerLocation(loc);
		if (onLeft)
		{
			wf.js.setLeftComponent(wf.sideBar);
			wf.js.setRightComponent(wf.content.getPanel());
		} else
		{
			wf.js.setLeftComponent(wf.content.getPanel());
			wf.js.setRightComponent(wf.sideBar);
		}
	}

	//******************************** INTERFACE ********************************

	/**
	 * Method to get the content of this window.
	 * The content is the object in the right side (EditWindow, WaveformWindow, etc.)
	 * @return the content of this window.
	 */
	public WindowContent getContent() { return content; }

    /**
	 * Method to get the current WindowFrame. If there is no current
     * WindowFrame, then it will create a new EditWindow window frame.
	 * @return the current WindowFrame.
	 */
	public static WindowFrame getCurrentWindowFrame() {
        return getCurrentWindowFrame(true);
    }

    /**
     * Method to get the current WindowFrame. If 'makeNewFrame' is true,
     * then this will make a new EditWindow window frame if there is no
     * current frame. If 'makeNewFrame' is false, this will return null
     * if there is no current WindowFrame.
     * @param makeNewFrame whether or not to make a new WindowFrame if no current frame
     * @return the current WindowFrame. May return null if 'makeNewFrame' is false.
     */
    public static WindowFrame getCurrentWindowFrame(boolean makeNewFrame) {
        synchronized(windowList) {
            if ((curWindowFrame == null) && makeNewFrame) {
                for (WindowFrame wf: windowList) {
                    // get last in list
                    curWindowFrame = wf;
                }
                if (curWindowFrame == null)
                    curWindowFrame = createEditWindow(null);
            }
            return curWindowFrame;
        }
    }

	/**
	 * Method to set the current listener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @param listener the new lister to be in effect.
	 */
	public static void setListener(ElectricEventListener listener)
	{
        curListener = listener;
//		curMouseListener = listener;
//		curMouseMotionListener = listener;
//		curMouseWheelListener = listener;
//		curKeyListener = (KeyListener)listener;
	}

    public static interface ElectricEventListener extends
        MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, DatabaseChangeListener {
    }

	/**
	 * Method to get the current listener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @return the current listener.
	 */
	public static ElectricEventListener getListener()
	{
		if (curListener == null) curListener = ClickZoomWireListener.theOne;
		return curListener;
	}

	/**
	 * Method to get the current MouseListener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @return the current MouseListener.
	 */
	public static MouseListener getMouseListener()
	{
        return getListener();
	}

	/**
	 * Method to get the current MouseMotionListener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @return the current MouseMotionListener.
	 */
	public static MouseMotionListener getMouseMotionListenerListener()
	{
        return getListener();
	}

	/**
	 * Method to get the current MouseWheelListener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @return the current MouseWheelListener.
	 */
	public static MouseWheelListener getMouseWheelListenerListener()
	{
        return getListener();
	}

	/**
	 * Method to get the current KeyListener that responds to clicks in any window.
	 * There is a single listener in effect everywhere, usually controlled by the toolbar.
	 * @return the current KeyListener.
	 */
	public static KeyListener getKeyListenerListener()
	{
        return getListener();
	}

	/**
	 * Method to return the current Cell.
	 */
	public static Cell getCurrentCell()
	{
        assert SwingUtilities.isEventDispatchThread();
		WindowFrame wf = WindowFrame.getCurrentWindowFrame(false);
		if (wf == null) return null;
		return wf.getContent().getCell();
	}

	/**
	 * Method to insist on a current Cell.
	 * Prints an error message if there is no current Cell.
	 * @return the current Cell in the current Library.
	 * Returns NULL if there is no current Cell.
	 */
	public static Cell needCurCell()
	{
		Cell curCell = getCurrentCell();
		if (curCell == null)
		{
			System.out.println("There is no current cell for this operation.  To create one, use the 'New Cell' command from the 'Cell' menu.");
		}
		return curCell;
	}

	/**
	 * Method to set the cursor that is displayed in the WindowFrame.
	 * @param cursor the cursor to display here.
	 */
	public void setCursor(Cursor cursor)
	{
        if (!TopLevel.isMDIMode()) {
            jf.setCursor(cursor);
        }
        js.setCursor(cursor);
        content.setCursor(cursor);
	}

	public static void removeLibraryReferences(Library lib)
	{
		for (Iterator<WindowFrame> it = getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			Cell cell = content.getCell();
			if (cell != null && cell.getLibrary() == lib)
				content.setCell(null, null, null);
			content.fullRepaint();
		}
	}

    /**
     * Method to request focus on this window
     */
    public void requestFocus() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { requestFocusUnsafe(); }
            });
            return;
        }
        requestFocusUnsafe();
    }

    private void requestFocusUnsafe() {
        if (jif != null) {
            jif.toFront();
            try {
                jif.setSelected(true);
            } catch (java.beans.PropertyVetoException e) {}
        }
        if (jf != null) {
            jf.toFront();
            jf.requestFocus();
        }
    }

    public boolean isFocusOwner()
    {
        if (jif != null) return jif.isSelected();
        if (jf != null) return jf.isFocusOwner();
        return false;
    }

	/**
	 * Method to set the current WindowFrame.
	 * @param wf the WindowFrame to make current.
	 */
	private static void setCurrentWindowFrame(WindowFrame wf, boolean autoSwitchTech)
	{
        synchronized(windowList) {
            if (wf.finished) return;            // don't do anything, the window is finished
            curWindowFrame = wf;
            // set this to be last in list
            windowList.remove(wf);
            windowList.add(wf);
        }
		if (wf != null)
		{
            wf.currentCellChanged(autoSwitchTech);
            if (wf.getContent() != null) {
                Highlighter highlighter = wf.getContent().getHighlighter();
                if (highlighter != null) highlighter.gainedFocus();
            }
		}
		wantToRedoTitleNames();
	}

    private void currentCellChanged(boolean autoSwitchTech) {
        if (this != getCurrentWindowFrame(false)) return;
        Cell cell = getContent().getCell();
        if (cell != null)
        {
        	cell.getLibrary().setCurCell(cell);

            // if auto-switching technology, do it
            if (autoSwitchTech) autoTechnologySwitch(cell, this);
        }
    }

	public static void wantToRedoTitleNames()
	{
		// rebuild window titles
		for (Iterator<WindowFrame> it = getWindows(); it.hasNext(); )
		{
			WindowFrame w = it.next();
			WindowContent content = w.getContent();

			if (content != null) content.setWindowTitle();
		}
        WindowMenu.setDynamicMenus();
	}

	public void setWindowSize(Rectangle frameRect)
	{
		Dimension frameSize = new Dimension(frameRect.width, frameRect.height);
		if (TopLevel.isMDIMode())
		{
			jif.setSize(frameSize);
			jif.setLocation(frameRect.x, frameRect.y);
		} else
		{
			jf.setSize(frameSize);
			jf.setLocation(frameRect.x, frameRect.y);
			jf.validate();
		}
	}

	/**
	 * Method to record that this WindowFrame has been closed.
	 * This method is called from the event handlers on the windows.
	 */
	public void finished()
	{
        assert SwingUtilities.isEventDispatchThread();
        // if this was called from the code, instead of an event handler,
        // make sure we're not visible anymore
        if (TopLevel.isMDIMode()) {
            jif.setVisible(false);
            jif.dispose();
        }

        // remove references to this
        synchronized(windowList) {
            if (windowList.size() <= 1 && !TopLevel.isMDIMode())
            {
                FileMenu.quitCommand();
                //JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(),
                //    "Cannot close the last window");
                return;
            }
            windowList.remove(this);
            finished = true;
		    if (curWindowFrame == this) curWindowFrame = null;
        }

        // tell EditWindow it's finished
        content.finished();
		explorerTab.setModel(null);
        WindowMenu.setDynamicMenus();
        layersTab.finished();
        depopulateJFrame();

        if (!TopLevel.isMDIMode()) {
            // if SDI mode, TopLevel enclosing frame is closing, dispose of it
            jf.finished();
        }
    }

	/**
	 * Method to return the WaveformWindow associated with this frame.
	 * @return the WaveformWindow associated with this frame.
	 */
	public WaveformWindow getWaveformWindow() { return (WaveformWindow)content; }

	/**
	 * Method to return the Explorer tab associated with this WindowFrame.
	 * @return the Explorer tab associated with this WindowFrame.
	 */
	public ExplorerTree getExplorerTab() { return explorerTab; }

	/**
	 * Method to return the component palette associated with this WindowFrame.
	 * @return the component palette associated with this WindowFrame.
	 */
	public PaletteFrame getPaletteTab() { return paletteTab; }

	/**
	 * Method to return the layer visibility tab associated with this WindowFrame.
	 * @return the layer visibility tab associated with this WindowFrame.
	 */
	public LayerTab getLayersTab() { return layersTab; }

	/**
	 * Method to return the TopLevel associated with this WindowFrame.
     * In SDI mode this returns this WindowFrame's TopLevel Frame.
     * In MDI mode there is only one TopLevel frame, so this method will
     * return that Frame.
	 * @return the TopLevel associated with this WindowFrame.
	 */
	public TopLevel getFrame() {
        if (TopLevel.isMDIMode()) {
            return TopLevel.getCurrentJFrame();
        }
        return jf;
    }

    /**
     * Method to return the JInternalFrame associated with this WindowFrame.
     * In SDI mode this returns null.
     * @return the JInternalFrame associated with this WindowFrame.
     */
    public JInternalFrame getInternalFrame() {
        if (TopLevel.isMDIMode()) {
            return jif;
        }
        return null;
    }

    /**
     * Returns true if this window frame or it's components generated
     * this event.
     * @param e the event generated
     * @return true if this window frame or it's components generated this
     * event, false otherwise.
     */
    public boolean generatedEvent(java.awt.AWTEvent e) {
        if (e instanceof InternalFrameEvent) {
            JInternalFrame source = ((InternalFrameEvent)e).getInternalFrame();
            if (source == jif) return true;
            return false;
        }
        return false;
    }

	/**
	 * Method to return the number of WindowFrames.
	 * @return the number of WindowFrames.
	 */
	public static int getNumWindows() {
        synchronized(windowList) {
            return windowList.size();
        }
    }

	/**
	 * Method to return an Iterator over all WindowFrames.
	 * @return an Iterator over all WindowFrames.
	 */
	public static Iterator<WindowFrame> getWindows() {
        ArrayList<WindowFrame> listCopy = new ArrayList<WindowFrame>();
        synchronized(windowList) {
            listCopy.addAll(windowList);
        }
        return listCopy.iterator();
    }

	/**
	 * Centralized version of naming windows. Might move it to class
	 * that would replace WindowContext
	 * @param cell the cell in the window.
	 * @param prefix a prefix for the title.
	 */
	public String composeTitle(Cell cell, String prefix, int pageNo)
	{
		// StringBuffer should be more efficient
		StringBuffer title = new StringBuffer();

		if (cell != null && cell.isLinked())
		{
			title.append(prefix + cell.libDescribe());

			if (cell.isMultiPage())
			{
				title.append(" - Page " + (pageNo+1));
			}
            Library curLib = Library.getCurrent();
			if (cell.getLibrary() != curLib && curLib != null)
				title.append(" - Current library: " + curLib.getName());
		}
		else
			title.append("***NONE***");
		return (title.toString());
	}

    /**
     * Method to set the description on the window frame
     */
    public void setTitle(String title)
    {
        String curTitle = getTitle();
        if (title.equals(curTitle)) return;

        if (TopLevel.isMDIMode()) {
            if (jif != null) jif.setTitle(title);
        } else {
            if (jf != null) jf.setTitle(title);
        }
    }

    /**
     * Method to get the description on the window frame
     */
    public String getTitle()
    {
        if (TopLevel.isMDIMode()) {
            if (jif != null) return jif.getTitle();
        } else {
            if (jf != null) return jf.getTitle();
        }
        return "";
    }

    private static void removeUIBinding(JComponent comp, KeyStroke key) {
        removeUIBinding(comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), key);
        removeUIBinding(comp.getInputMap(JComponent.WHEN_FOCUSED), key);
        removeUIBinding(comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW), key);
    }
    private static void removeUIBinding(InputMap map, KeyStroke key) {
        if (map == null) return;
        map.remove(key);
        removeUIBinding(map.getParent(), key);
    }

    /**
	 * Method to automatically switch to the proper technology for a Cell.
	 * @param cell the cell being displayed.
	 * If technology auto-switching is on, make sure the right technology is displayed
	 * for the Cell.
	 */
	public static void autoTechnologySwitch(Cell cell, WindowFrame wf)
	{
		if (cell.getView().isTextView()) return;
		Technology tech = cell.getTechnology();
		if (tech != null && tech != Technology.getCurrent())
		{
			if (User.isAutoTechnologySwitch())
			{
				Job.getExtendedUserInterface().setCurrentTechnology(tech);
				wf.getPaletteTab().loadForTechnology(tech, wf);
                wf.getLayersTab().updateLayersTab();
			}
		}
	}

	// ************************** Cell History Traversal  *************************************

	/** List of CellHistory objects */                      private List<CellHistory> cellHistory;
	/** Location in history (points to valid location) */   private int cellHistoryLocation;
	/** History limit */                                    private static final int cellHistoryLimit = 20;

	/**
	 * Class to track CellHistory and associated values.
	 */
	public static class CellHistory
	{
		/** cell */												private Cell cell;
		/** context */											private VarContext context;
		/** the port that is highlighted */						private PortInst selPort;
		/** display attributes (scale, in-place, etc.) */		private DisplayAttributes da;
		/** highlights */										private List<Highlight> highlights;
		/** highlight offset*/									private double offX, offY;

		public Cell getCell() { return cell; }

		public VarContext getContext() { return context; }

		public void setContext(VarContext context) { this.context = context; }

		public void setSelPort(PortInst selPort) { this.selPort = selPort; }

		public DisplayAttributes getDisplayAttributes() { return da; }

		public void setDisplayAttributes(DisplayAttributes da) { this.da = da; }
	}

	/**
	 * Method to return the list of cell histories associated with this WindowFrame.
	 * @return the list of cell histories associated with this WindowFrame.
	 */
	public List<CellHistory> getCellHistoryList() { return cellHistory; }

	/**
	 * Method to return the current position in the cell history list.
	 * @return the current position in the cell history list.
	 */
	public int getCellHistoryLocation() { return cellHistoryLocation; }

	/**
	 * Go back in history list.
	 */
	public void cellHistoryGoBack()
	{
		// at start of history
		if (cellHistoryLocation <= 0) return;
		setCellByHistory(cellHistoryLocation-1);
	}

	/**
	 * Go forward in history list.
	 */
	public void cellHistoryGoForward()
	{
		if (cellHistoryLocation >= (cellHistory.size() - 1)) return; // at end of history
		setCellByHistory(cellHistoryLocation+1);
	}

	/** Returns true if we can go back in history list, false otherwise */
	private boolean cellHistoryCanGoBack()
	{
		if (cellHistoryLocation > 0) return true;
		return false;
	}

	/** Returns true if we can go forward in history list, false otherwise */
	private boolean cellHistoryCanGoForward()
	{
		if (cellHistoryLocation < cellHistory.size() - 1) return true;
		return false;
	}

	/**
	 * Method to updates back/forward button states.
	 * Used when new tool bar is created with existing edit window
	 * (when moving windows across displays).
	 */
	private void fireCellHistoryStatus()
	{
		ToolBar toolBar = getToolBar();
		if (toolBar == null) return;
		toolBar.updateCellHistoryStatus(cellHistoryCanGoBack(), cellHistoryCanGoForward());
	}

	/**
	 * Method to return the ToolBar associated with this WindowFrame.
     * In SDI mode this returns this WindowFrame's ToolBar.
     * In MDI mode there is only one ToolBar, so this method will
     * return that ToolBar.
	 * @return the TooolBar associated with this WindowFrame.
	 */
    private ToolBar getToolBar()
    {
        TopLevel topLevel = getFrame();
        return topLevel != null ? topLevel.getToolBar() : null;
    }

	/**
	 * Adds to cellHistory record list.
	 * Should only be called via non-history traversing modifications
	 * to history. (such as Edit->New Cell).
	 */
	public void addToHistory(Cell cell, VarContext context, DisplayAttributes da)
	{
		if (cell == null) return;

		CellHistory history = new CellHistory();
		history.cell = cell;
		history.context = context;
        if (da == null)
            da = new DisplayAttributes();
		history.da = da;

		// when user has moved back through history, and then edits a new cell,
		// get rid of forward history
		if (cellHistoryLocation < (cellHistory.size() - 1))
		{
			// inserting into middle of history: get rid of history forward of this
			for(int i=cellHistory.size()-1; i>cellHistoryLocation; i--)
				cellHistory.remove(i);
		}

		// update history
		cellHistory.add(history);
		cellHistoryLocation = cellHistory.size() - 1;

		// adjust if we are over the limit
		if (cellHistoryLocation > cellHistoryLimit)
		{
			cellHistory.remove(0);
			cellHistoryLocation--;
		}
		fireCellHistoryStatus();
//showHistory("add to history");
	}

//private void showHistory(String why)
//{
//	System.out.println("AFTER "+why+", CELL HISTORY:");
//	for(int i=0; i<cellHistory.size(); i++)
//	{
//		CellHistory ch = cellHistory.get(i);
//		System.out.print("  HISTORY "+i);
//		if (i == cellHistoryLocation) System.out.print("**");
//		System.out.println(" cell="+ch.cell.describe(false)+" scale="+ch.da.scale);
//	}
//}

	/**
	 * Records current cell state into history
	 * Assumes record pointed to by cellHistoryLocation is
	 * history record for the current cell/context.
	 */
	public void saveCurrentCellHistoryState()
	{
		if (cellHistoryLocation < 0) return;

		if (content instanceof EditWindow)
		{
			EditWindow wnd = (EditWindow)content;
			CellHistory current = cellHistory.get(cellHistoryLocation);
			current.context = wnd.getVarContext();

			// save zoom and pan
			// save down-in-place information
			current.da = new DisplayAttributes(wnd);

			// save selected port
            current.selPort = null;
			Highlighter highlighter = wnd.getHighlighter();
			if (highlighter.getNumHighlights() == 1)
			{
				Highlight h = highlighter.getOneHighlight();
		        if (h != null)
		        {
		        	ElectricObject eobj = h.getElectricObject();
			        if (eobj instanceof PortInst)
			        	current.selPort = (PortInst)eobj;
		        }
			}

			// save highlighting
			current.highlights = new ArrayList<Highlight>();
			Cell cell = content.getCell();
			for (Highlight h : highlighter.getHighlights()) {
				if (h.getCell() == cell)
					current.highlights.add(h);
			}
			Point2D off = highlighter.getHighlightOffset();
			if (off != null)
			{
				current.offX = off.getX();
				current.offY = off.getY();
			}
		}
//showHistory("save state");
	}

	/** Restores cell state from history record */
	public void setCellByHistory(int location)
	{
		// get cell history to go to
		CellHistory history = cellHistory.get(location);

		// see if cell still valid part of database. If not, nullify entry
		if (history.cell == null || !history.cell.isLinked())
		{
			history.cell = null;
			history.context = VarContext.globalContext;
			history.selPort = null;
			history.da = new WindowFrame.DisplayAttributes();
			history.highlights = new ArrayList<Highlight>();
			history.offX = history.offY = 0;
		}

		// update current cell
		setCellWindow(history.cell, history);
		if (history.cell != null && !history.cell.getView().isTextView())
		{
			EditWindow wnd = (EditWindow)content;
			if (history.selPort != null)
			{
				wnd.getHighlighter().clear();
				wnd.getHighlighter().addElectricObject(history.selPort, history.cell);
			} else if (history.highlights != null)
			{
				wnd.getHighlighter().setHighlightList(history.highlights);
				wnd.getHighlighter().setHighlightOffset((int)history.offX, (int)history.offY);
			}
		}
		content.fullRepaint();

		// point to new location *after* calling setCell, since setCell updates by current location
		cellHistoryLocation = location;
		fireCellHistoryStatus();
//showHistory("left/right buttons");
	}

	/**
	 * Method to find a CellHistory index that shows a given cell and context.
	 * @param cell the cell to find.
	 * @param context the cell context to find.
	 * @return the CellHistory index associated with that cell/context
	 * (-1 if not found).
	 */
	public int findCellHistoryIndex(Cell cell, VarContext context)
	{
		for (int i=cellHistory.size()-1; i>-1; i--)
		{
			CellHistory history = cellHistory.get(i);
			if (history.cell == cell && history.context.equals(context))
				return i;
		}
		return -1;
	}

    /**
     * Method to show statistics onCellHistory memory usages
     */
    public static void showCellHistoryStats() {
        Set<Topology> topologySet = new HashSet<Topology>();
        for (Iterator<WindowFrame> it = getWindows(); it.hasNext();) {
            WindowFrame wf = it.next();
            for (CellHistory ch : wf.cellHistory) {
                if (ch.selPort != null) {
                    topologySet.add(ch.selPort.getNodeInst().getTopology());
                }
                VarContext varContext = ch.context;
                while (varContext != null && varContext != VarContext.globalContext) {
                    Nodable no = varContext.getNodableOptional();
                    if (no != null) {
                        topologySet.add(no.getNodeInst().getTopology());
                    }
                    varContext = varContext.pop();
                }
                if (ch.highlights != null) {
                    for (Highlight h: ch.highlights) {
                        ElectricObject eobj = h.getElectricObject();
                        if (eobj instanceof ArcInst) {
                            topologySet.add(((ArcInst)eobj).getTopology());
                        } else if (eobj instanceof NodeInst) {
                            topologySet.add(((NodeInst)eobj).getTopology());
                        } else if (eobj instanceof PortInst) {
                            topologySet.add(((PortInst)eobj).getNodeInst().getTopology());
                        }
                    }
                }
            }
        }
        System.out.println("Topologies:");
        for (Topology topology: topologySet) {
            System.out.println("  " + topology.getCell());
        }
    }

    //******************************** HANDLERS FOR WINDOW EVENTS ********************************

	/**
	 * This class handles activation and close events for JFrame objects (used in SDI mode).
	 */
	private class WindowsEvents extends WindowAdapter
	{
        /** A weak reference to the WindowFrame */
		WeakReference<WindowFrame> wf;

		WindowsEvents(WindowFrame wf)
		{
			super();
			this.wf = new WeakReference<WindowFrame>(wf);
		}

		public void windowActivated(WindowEvent evt)
		{
			WindowFrame realWF = wf.get();
			realWF.usageClock = usageCounter++;
			WindowFrame.setCurrentWindowFrame(realWF, false);
			realWF.fireCellHistoryStatus();                // update tool bar history buttons

			// check all external text editors and update cells if appropriate
			ExternalEditing.updateEditors();
		}

		public void windowClosing(WindowEvent evt)
		{
			wf.get().finished();
		}
	}

	/**
	 * This class handles activation and close events for JInternalFrame objects (used in MDI mode).
	 */
	private class InternalWindowsEvents extends InternalFrameAdapter
	{
        /** A weak reference to the WindowFrame */
		WeakReference<WindowFrame> wf;

		InternalWindowsEvents(WindowFrame wf)
		{
			super();
			this.wf = new WeakReference<WindowFrame>(wf);
		}

		public void internalFrameClosing(InternalFrameEvent evt)
		{
			(wf.get()).finished();
		}

		public void internalFrameActivated(InternalFrameEvent evt)
		{
			WindowFrame realWF = wf.get();
			realWF.usageClock = usageCounter++;
			WindowFrame.setCurrentWindowFrame(realWF, false);
			realWF.fireCellHistoryStatus();                // update tool bar history buttons
		}
	}

    /**
     * Database change listener that updates library trees when needed
     */
    private static class LibraryTreeUpdater implements DatabaseChangeListener {

        private LibraryTreeUpdater() { UserInterfaceMain.addDatabaseChangeListener(this); }

        @Override
        public void databaseChanged(DatabaseChangeEvent e)
        {
            if (e.cellTreeChanged())
                wantToRedoLibraryTree();
            if (curListener != null)
                curListener.databaseChanged(e);
        }

//         public void databaseEndChangeBatch(Undo.ChangeBatch batch)
//         {
//             boolean changed = false;
//             for (Iterator it = batch.getChanges(); it.hasNext(); )
//             {
//                 Undo.Change change = it.next();
//                 if (change.getType() == Undo.Type.LIBRARYKILL ||
//                     change.getType() == Undo.Type.LIBRARYNEW ||
//                     change.getType() == Undo.Type.CELLKILL ||
//                     change.getType() == Undo.Type.CELLNEW ||
//                     change.getType() == Undo.Type.CELLGROUPMOD ||
//                     (change.getType() == Undo.Type.OBJECTRENAME && change.getObject() instanceof Cell) ||
// 					(change.getType() == Undo.Type.VARIABLENEW && change.getObject() instanceof Cell && ((Variable)change.getO1()).getKey() == Cell.MULTIPAGE_COUNT_KEY))
//                 {
//                     changed = true;
//                     break;
//                 }
//             }
//             if (changed)
//                 updateLibraryTrees();
//         }

//         public void databaseChanged(Undo.Change evt) {}
//         public boolean isGUIListener() { return true; }
    }

}
