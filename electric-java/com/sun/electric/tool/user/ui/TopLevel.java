/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TopLevel.java
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

import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.menus.EMenuBar;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.user.menus.MenuCommands;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.TextUtils;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Class to define a top-level window.
 * In MDI mode (used by Windows to group multiple documents into a single window) this class is
 * used for that single top window.
 * In SDI mode (used elsewhere to give each cell its own window) this class is used many times for each window.
 */
public class TopLevel extends JFrame
{
    /** true to resize initial MDI window forces redraw) */	private static final boolean MDIINITIALRESIZE = true;
    /** True if in MDI mode, otherwise SDI. */				private static UserInterfaceMain.Mode mode;
	/** The desktop pane (if MDI). */						private static JDesktopPane desktop = null;
	/** The main frame (if MDI). */							private static TopLevel topLevel = null;
	/** The size of the screen. */							private static Dimension scrnSize;
    /** The rate of double-clicks. */						private static int doubleClickDelay;
	/** The cursor being displayed. */						private static Cursor cursor;
    /** If busy cursor is overriding the normal cursor */	private static boolean busyCursorOn = false;
	/** List of modeless dialogs to keep on top. */			private static Set<Window> modelessDialogs = new HashSet<Window>();
	/** The WindowAdapter for keeping dialogs on top. */	private static WindowAdapter onTopWindowAdapter = new MyWindowAdapter();

	/** The only status bar (if MDI). */					private StatusBar sb = null;
    /** The menu bar */                                     private EMenuBar.Instance menuBar;
    /** The tool bar */                                     private ToolBar toolBar;

	/**
	 * Constructor to build a window.
	 * @param name the title of the window.
	 */
	public TopLevel(String name, Rectangle bound, WindowFrame frame, GraphicsConfiguration gc, boolean createStructure)
	{
		super(name, gc);
		setLocation(bound.x, bound.y);
		setSize(bound.width, bound.height);
		getContentPane().setLayout(new BorderLayout());

		// set an icon on the window
		setIconImage(getFrameIcon().getImage());

        if (createStructure)
            createStructure(frame);

		if (isMDIMode())
		{
			addWindowListener(new WindowsEvents());
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			addComponentListener(new ReshapeComponentAdapter());
 		} else
		{
			setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            //setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//            addWindowFocusListener(EDialog.dialogFocusHandler);
		}
		addWindowFocusListener(onTopWindowAdapter);

		cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

		// For 3D: LightWeight v/s heavy: mixing awt and swing
		try {
			javax.swing.JPopupMenu.setDefaultLightWeightPopupEnabled(false);
			javax.swing.ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
			enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Method to register a dialog as "modeless" so that it can be kept on top (if requested).
	 * @param dialog the dialog to keep on top.
	 */
	public static void addModelessDialog(Window dialog) { modelessDialogs.add(dialog); }

	/**
	 * Method to remove a modeless dialog from the list of dialogs to keep on top (if requested).
	 * @param dialog the dialog to remove from the list (because it is being deleted).
	 */
	public static void removeModelessDialog(JFrame dialog) { modelessDialogs.remove(dialog); }

	/**
	 * Class to keep modeless dialogs on top when requested.
	 */
	private static class MyWindowAdapter extends WindowAdapter
	{
		public void windowGainedFocus(WindowEvent e)
		{
			if (!User.isKeepModelessDialogsOnTop()) return;
			for(Window jf : modelessDialogs)
			{
				if (jf.isVisible()) jf.toFront();
			}
		}
	}

	public void createStructure(WindowFrame frame) {
		// create the menu bar
        try{
            menuBar = MenuCommands.menuBar().genInstance();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
		setJMenuBar(menuBar);

		// create the tool bar
		toolBar = ToolBar.createToolBar();
		getContentPane().add(toolBar, BorderLayout.NORTH);

		// create the status bar
		sb = new StatusBar(frame);
		getContentPane().add(sb, BorderLayout.SOUTH);
    }

	/**
	 * Method to return the Icon to use in windows.
	 * @return the Icon to use in windows.
	 */
	public static ImageIcon getFrameIcon()
	{
		return Resources.getResource(TopLevel.class, "IconElectric.gif");
	}

    public static void InitializeMessagesWindow() {
		if (isMDIMode())
		{
			String loc = User.cacheWindowLoc.getString();
			Rectangle bound = parseBound(loc);
			if (bound == null)
				bound = new Rectangle(scrnSize);
			if (MDIINITIALRESIZE) bound.width--;

			// make the desktop
			desktop = new JDesktopPane();
            try{
            	topLevel = new TopLevel("Electric", bound, null, null, false);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
			topLevel.getContentPane().add(desktop, BorderLayout.CENTER);
            topLevel.setVisible(true);
		}
        MessagesWindow.init();
    }

	/**
	 * Method to initialize the window system with the specified mode.
     * If mode is null, the mode is implied by the operating system.
	 */
	public static void InitializeWindows()
	{
		// in MDI, create the top frame now
//		if (isMDIMode())
//		{
//			String loc = cacheWindowLoc.getString();
//			Rectangle bound = parseBound(loc);
//			if (bound == null)
//				bound = new Rectangle(scrnSize);
//			if (MDIINITIALRESIZE) bound.width--;
//
//			// make the desktop
//			desktop = new JDesktopPane();
//            try{
//            	topLevel = new TopLevel("Electric", bound, null, null);
//            } catch (Exception e)
//            {
//                e.printStackTrace();
//            }
//			topLevel.getContentPane().add(desktop, BorderLayout.CENTER);
//            topLevel.setVisible(true);
//		}
//
//		// initialize the messagesWindow window
//        messagesWindow = new MessagesWindow();
        if (isMDIMode())
            topLevel.createStructure(null);
        WindowFrame.createEditWindow(null);
        FileMenu.updateRecentlyOpenedLibrariesList();
		if (MDIINITIALRESIZE && isMDIMode())
		{
			SwingUtilities.invokeLater(new Runnable() { public void run() {
				Dimension old = topLevel.getSize();
			    topLevel.setSize(new Dimension(old.width+1, old.height));
			}});
		}
    }

	/**
	 * Method to initialize the window system.
	 */
	public static void OSInitialize(UserInterfaceMain.Mode mode)
	{
		// setup the size of the screen
        Toolkit tk = Toolkit.getDefaultToolkit();
        scrnSize = tk.getScreenSize();
        Object click = tk.getDesktopProperty("awt.multiClickInterval");
        if (click == null) doubleClickDelay = 500; else
            doubleClickDelay = Integer.parseInt(click.toString());

        // a more advanced way of determining the size of a screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice [] gs = ge.getScreenDevices();
        if (gs.length > 0) {
            GraphicsDevice gd = gs[0];
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle r = gc.getBounds();
            scrnSize.setSize(r.width, r.height);
        }
		// setup specific look-and-feel
        UserInterfaceMain.Mode osMode = null;
		try{
            switch (ClientOS.os)
            {
                case WINDOWS:
                    osMode = UserInterfaceMain.Mode.MDI;

                    scrnSize.height -= 30;
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    break;
                case UNIX:
                    osMode = UserInterfaceMain.Mode.SDI;
                    //UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    break;
                case MACINTOSH:
                    osMode = UserInterfaceMain.Mode.SDI;
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.MacLookAndFeel");
                    break;
			}
		} catch(Exception e) {}

        // set the windowing mode
        if (mode == null)
            TopLevel.mode = osMode;
        else
            TopLevel.mode = mode;
        //TopLevel.mode = Mode.MDI;

        // set current working directory
//        String setting = User.getInitialWorkingDirectorySetting();
//        if (setting.equals(User.INITIALWORKINGDIRSETTING_BASEDONOS))
//        {
//            // default is last used dir
//            if (ClientOS.isOSLinux()) {
//                // switch to current dir
//                User.setWorkingDirectory(System.getProperty("user.dir"));
//            }
//        }
//        else if (setting.equals(User.INITIALWORKINGDIRSETTING_USECURRENTDIR))
//            User.setWorkingDirectory(System.getProperty("user.dir"));
        // else
            // default is to use last used dir

		// in MDI, create the top frame now
//		if (isMDIMode())
//		{
//			String loc = cacheWindowLoc.getString();
//			Rectangle bound = parseBound(loc);
//			if (bound == null)
//				bound = new Rectangle(scrnSize);
//
//			// make the desktop
//			desktop = new MyDesktop();
//            try{
//			topLevel = new TopLevel("Electric", bound, null, null);
//            } catch (Exception e)
//            {
//                e.printStackTrace();
//            }
//			topLevel.getContentPane().add(desktop, BorderLayout.CENTER);
//            topLevel.setVisible(true);
//		}
	}

	private static Rectangle parseBound(String loc)
	{
		int lowX = TextUtils.atoi(loc);
		int commaPos = loc.indexOf(',');
		if (commaPos < 0) return null;
		int lowY = TextUtils.atoi(loc.substring(commaPos+1));
		int spacePos = loc.indexOf(' ');
		if (spacePos < 0) return null;
		int width = TextUtils.atoi(loc.substring(spacePos+1));
		int xPos = loc.indexOf('x');
		if (xPos < 0) return null;
		int height = TextUtils.atoi(loc.substring(xPos+1));
		return new Rectangle(lowX, lowY, width, height);
	}

    /**
	 * Method to tell whether Electric is running in SDI or MDI mode.
	 * SDI is Single Document Interface, where each document appears in its own window.
	 * This is used on UNIX/Linux and on Macintosh.
	 * MDI is Multiple Document Interface, where the main window has all documents in it as subwindows.
	 * This is used on Windows.
	 * @return true if Electric is in MDI mode.
	 */
	public static boolean isMDIMode() { return (mode == UserInterfaceMain.Mode.MDI); }

    /**
	 * Method to return status bar associated with this TopLevel.
	 * @return the status bar associated with this TopLevel.
	 */
	public StatusBar getStatusBar() { return sb; }

    /**
     * Get the tool bar associated with this TopLevel
     * @return the ToolBar.
     */
    public ToolBar getToolBar() { return toolBar; }

    /** Get the Menu Bar. Unfortunately named because getMenuBar() already exists */
    public EMenuBar.Instance getTheMenuBar() { return menuBar; }

    /** Get the Menu Bar. Unfortunately named because getMenuBar() already exists */
    public EMenuBar getEMenuBar() { return menuBar.getMenuBarGroup(); }

    /**
     * Method to return the speed of double-clicks (in milliseconds).
     * @return the speed of double-clicks (in milliseconds).
     */
    public static int getDoubleClickSpeed() { return doubleClickDelay; }

    /**
	 * Method to return the size of the screen that Electric is on.
	 * @return the size of the screen that Electric is on.
	 */
	public static Dimension getScreenSize()
	{
		if (isMDIMode())
		{
			Rectangle bounds = topLevel.getBounds();
			Rectangle dBounds = desktop.getBounds();
			if (dBounds.width != 0 && dBounds.height != 0)
			{
				return new Dimension(dBounds.width, dBounds.height);
			}
			return new Dimension(bounds.width-8, bounds.height-96);
		}
		return new Dimension(scrnSize);
	}

	/**
	 * Method to add an internal frame to the desktop.
	 * This only makes sense in MDI mode, where the desktop has multiple subframes.
	 * @param jif the internal frame to add.
	 */
	public static void addToDesktop(JInternalFrame jif)
	{
        if (desktop.isVisible() && !Job.isClientThread())
            SwingUtilities.invokeLater(new ModifyToDesktopSafe(jif, true)); else
            	(new ModifyToDesktopSafe(jif, true)).run();
    }

	/**
	 * Method to remove an internal frame from the desktop.
	 * This only makes sense in MDI mode, where the desktop has multiple subframes.
	 * @param jif the internal frame to remove.
	 */
	public static void removeFromDesktop(JInternalFrame jif)
	{
        if (desktop.isVisible() && !Job.isClientThread())
            SwingUtilities.invokeLater(new ModifyToDesktopSafe(jif, false)); else
            	(new ModifyToDesktopSafe(jif, false)).run();
    }

    private static class ModifyToDesktopSafe implements Runnable
    {
        private JInternalFrame jif;
        private boolean add;

        private ModifyToDesktopSafe(JInternalFrame jif, boolean add) { this.jif = jif;  this.add = add; }

        public void run()
        {
        	if (add)
        	{
	            desktop.add(jif);
	            try
	            {
	            	jif.show();
	            } catch (ClassCastException e)
	            {
	            	// Jake Baker keeps getting a ClassCastException here, so for now, let's catch it
	            	System.out.println("ERROR: Could not show new window: " + e.getMessage());
	            }
        	} else
        	{
                desktop.remove(jif);
        	}
        }
    }

	public static Cursor getCurrentCursor() { return cursor; }

	public static synchronized void setCurrentCursor(Cursor cursor)
	{
        TopLevel.cursor = cursor;
        setCurrentCursorPrivate(cursor);
    }

    private static synchronized void setCurrentCursorPrivate(Cursor cursor)
    {
        if (mode == UserInterfaceMain.Mode.MDI) {
            JFrame jf = TopLevel.getCurrentJFrame();
            if (jf != null) jf.setCursor(cursor);
        }
        for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
        {
            WindowFrame wf = it.next();
            wf.setCursor(cursor);
        }
	}

    public static synchronized List<ToolBar> getToolBars() {
        ArrayList<ToolBar> toolBars = new ArrayList<ToolBar>();
        if (mode == UserInterfaceMain.Mode.MDI) {
            toolBars.add(topLevel.getToolBar());
        } else {
            for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); ) {
                WindowFrame wf = it.next();
                toolBars.add(wf.getFrame().getToolBar());
            }
        }
        return toolBars;
    }

    public static synchronized List<EMenuBar.Instance> getMenuBars() {
        ArrayList<EMenuBar.Instance> menuBars = new ArrayList<EMenuBar.Instance>();
        if (mode == UserInterfaceMain.Mode.MDI) {
            if (topLevel != null)
                if (topLevel.getTheMenuBar() != null)
                	menuBars.add(topLevel.getTheMenuBar());
        } else {
            for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); ) {
                WindowFrame wf = it.next();
                menuBars.add(wf.getFrame().getTheMenuBar());
            }
        }
        return menuBars;
    }

    /**
     * The busy cursor overrides any other cursor.
     * Call clearBusyCursor to reset to last set cursor
     */
    public static synchronized void setBusyCursor(boolean on) {
        if (on) {
            if (!busyCursorOn)
                setCurrentCursorPrivate(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            busyCursorOn = true;
        } else {
            // if the current cursor is a busy cursor, set it to the last normal cursor
            if (busyCursorOn)
                setCurrentCursorPrivate(getCurrentCursor());
            busyCursorOn = false;
        }
    }

	/**
	 * Method to return the current JFrame on the screen.
	 * @return the current JFrame.
	 */
	public static TopLevel getCurrentJFrame()
	{
        return getCurrentJFrame(true);
	}

	/**
	 * Method to return the current JFrame on the screen.
     * @param makeNewFrame whether or not to make a new WindowFrame if no current frame
	 * @return the current JFrame.
	 */
	public static TopLevel getCurrentJFrame(boolean makeNewFrame)
	{
		if (isMDIMode()) return topLevel;

		WindowFrame wf = WindowFrame.getCurrentWindowFrame(makeNewFrame);
        if (wf == null) return null;
		return wf.getFrame();
	}

//	/**
//	 * Method to set the WindowFrame associated with this top-level window.
//	 * This only makes sense for SDI applications where a WindowFrame is inside of a TopLevel.
//	 * @param wf the WindowFrame to associatd with this.
//	 */
//	public void setWindowFrame(WindowFrame wf) { this.wf = wf; }

    /**
     * Method called when done with this Frame.  Both the menuBar
     * and toolBar have persistent state in static hash tables to maintain
     * consistency across different menu bars and tool bars in SDI mode.
     * Those references must be nullified for garbage collection to reclaim
     * that memory.  This is really for SDI mode, because in MDI mode the
     * TopLevel is only closed on exit, and all the application memory will be freed.
     * <p>
     * NOTE: JFrame does not get garbage collected after dispose() until
     * some arbitrary point later in time when the garbage collector decides
     * to free it.
     */
    public void finished()
    {
        //System.out.println(this.getClass()+" being disposed of");
        // clean up menubar
        setJMenuBar(null);

		removeWindowFocusListener(onTopWindowAdapter);

		// TODO: figure out why Swing still sends events to finished menuBars
        menuBar.finished(); menuBar = null;

        // clean up toolbar
        Container container = getContentPane();
        if (container != null) container.remove(toolBar);
//        getContentPane().remove(toolBar);
        toolBar.finished(); toolBar = null;

        // clean up scroll bar
        if (container != null) container.remove(sb);
        sb.finished(); sb = null;
        /* Note that this gets called from WindowFrame, and
            WindowFrame has a reference to EditWindow, so
            WindowFrame will call wnd.finished(). */
        // dispose of myself
        super.dispose();
    }

    /**
     * Method to return a list of possible window areas.
     * On MDI systems, there is just one window areas.
     * On SDI systems, there is one window areas for each display head on the computer.
     * @return an array of window areas.
     */
    public static Rectangle [] getWindowAreas()
	{
		Rectangle [] areas;
		if (isMDIMode())
		{
			TopLevel tl = getCurrentJFrame();
			Dimension sz = tl.getContentPane().getSize();
			areas = new Rectangle[1];
			areas[0] = new Rectangle(0, 0, sz.width, sz.height);
		} else
		{
			areas = getDisplays();
		}
		return areas;
	}

    /**
     * Method to return a list of display areas, one for each display head on the computer.
     * @return an array of display areas.
     */
    public static Rectangle [] getDisplays()
	{
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice [] gs = ge.getScreenDevices();
		Rectangle [] areas = new Rectangle[gs.length];
		for (int j = 0; j < gs.length; j++)
		{
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration gc = gd.getDefaultConfiguration();
			areas[j] = gc.getBounds();
		}
		return areas;
	}

	/**
     * Print error message <code>msg</code> and stack trace
     * if <code>print</code> is true.
     * @param print print error message and stack trace if true
     * @param msg error message to print
     */
    public static void printError(boolean print, String msg)
    {
        if (print) {
            Throwable t = new Throwable(msg);
            System.out.println(t.toString());
            ActivityLogger.logException(t);
        }
    }

	private static class ReshapeComponentAdapter extends ComponentAdapter
	{
		public void componentMoved (ComponentEvent e) { saveLocation(e); }
		public void componentResized (ComponentEvent e) { saveLocation(e); }

		private void saveLocation(ComponentEvent e)
		{
			TopLevel frame = (TopLevel)e.getSource();
			Rectangle bounds = frame.getBounds();
			User.cacheWindowLoc.setString(bounds.getMinX() + "," + bounds.getMinY() + " " +
				bounds.getWidth() + "x" + bounds.getHeight());
		}
	}

	/**
	 * This class handles activation and close events for JFrame objects (used in MDI mode).
	 */
	private static class WindowsEvents extends WindowAdapter
	{
		WindowsEvents() { super(); }

		public void windowClosing(WindowEvent evt) {
            FileMenu.quitCommand();
        }

		public void windowActivated(WindowEvent evt)
		{
			// check all external text editors and update cells if appropriate
			ExternalEditing.updateEditors();
		}
	}
}
