/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: J3DMenu.java
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

package com.sun.electric.plugins.j3d.ui;

import static com.sun.electric.tool.user.menus.EMenuItem.SEPARATOR;

import com.sun.electric.api.movie.MovieCreator;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.plugins.j3d.View3DWindow;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.menus.EMenu;
import com.sun.electric.tool.user.menus.EMenuItem;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowContent;
import com.sun.electric.tool.user.ui.WindowFrame;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.Iterator;
import java.util.Map;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.GraphicsConfigTemplate3D;
import javax.media.j3d.VirtualUniverse;


/**
 * Class to handle the commands in the "3D" pulldown menu.
 * @author  Gilda Garreton
 * @version 0.1
 */
public class J3DMenu {

    // It can't be protected static -> reflection doesn't like it
    public static EMenu makeMenu() {
        /****************************** THE 3D MENU ******************************/

        return new EMenu("_3D Window",

        // mnemonic keys available: AB  EFGHIJKLMNOPQ S U WXYZ
        /** 3D view */
            new EMenuItem("_3D View") { public void run()
            {
                if (isJava3DAvailable()) create3DViewCommand(false);
            }},
            getMovieCreator() != null ?
            new EMenuItem("_Capture Frame/Animate") { public void run()
            {
                if (isJava3DAvailable())
                    J3DDemoDialog.create3DDemoDialog(TopLevel.getCurrentJFrame(), null);
            }} : null,
//		j3DMenu.addMenuItem("Open 3D Capacitance Window", null,
//			new ActionListener() { public void actionPerformed(ActionEvent e) { WindowMenu.create3DViewCommand(true); } });

//        MenuBar.Menu demoSubMenu = MenuBar.makeMenu("Capacitance _Demo");
//		j3DMenu.add(demoSubMenu);
//        demoSubMenu.addMenuItem("3D _View for Demo", null,
//            new ActionListener() { public void actionPerformed(ActionEvent e) { create3DViewCommand(true); } });
//        demoSubMenu.addMenuItem("Read Data From File", null,
//			new ActionListener() { public void actionPerformed(ActionEvent e) { readDemoDataFromFile(); } });
//        demoSubMenu.addMenuItem("_Read Data", null,
//			new ActionListener() { public void actionPerformed(ActionEvent e) { J3DViewDialog.create3DViewDialog(TopLevel.getCurrentJFrame()); } });

            SEPARATOR,
            new EMenuItem("_Test Hardware") { public void run() {
                if (isJava3DAvailable()) runHardwareTest(); }});
    }

    // ---------------------- THE 3D MENU FUNCTIONS -----------------

//    private static void readDemoDataFromFile()
//    {
//        WindowContent content = WindowFrame.getCurrentWindowFrame().getContent();
//        if (!(content instanceof View3DWindow))
//        {
//            System.out.println("Current Window Frame is not a 3D View for Read Demo Data");
//            return;
//        }
//        View3DWindow view3D = (View3DWindow)content;
//        view3D.addInterpolator(null); //J3DUtils.readDemoDataFromFile(view3D));
//        J3DDemoDialog.create3DDemoDialog(TopLevel.getCurrentJFrame());
//    }

    private static boolean isJava3DAvailable()
    {
        // Checking first if j3d is installed
        Class<?> j3DUtilsClass = Resources.get3DClass("utils.J3DUtils");
        if (j3DUtilsClass == null) // basic j3d is not available
        {
            System.out.println("Java3D is not available.");
            return false;
        }
        return true;
    }

    private static boolean movieCreatorChecked = false;
    private static MovieCreator movieCreatorInstance = null;

    /**
     * Method to return an instance of the MovieCreator class.
     * This class may not exist, in which case null is returned.
     * @return a MovieCreator class (null if unavailable).
     */
    public static MovieCreator getMovieCreator()
    {
        if (!movieCreatorChecked)
    	{
    		movieCreatorChecked = true;
    		try {
    			Class<?> clz = Class.forName("com.sun.electric.plugins.JMF.MovieCreatorJMF");
    			if (clz != null) movieCreatorInstance = (MovieCreator)clz.newInstance();
    		} catch (Exception e) {}
    		catch (Error e) {}
    		if (movieCreatorInstance == null) TextUtils.recordMissingComponent("MovieCreator");
    	}
        return movieCreatorInstance;
    }

    /**
	 * This method creates 3D view of current cell
     * @param transPerNode
     */
	public static void create3DViewCommand(Boolean transPerNode)
    {
        Cell curCell = WindowFrame.needCurCell();
	    if (curCell == null) return;

        if (!curCell.isLayout())
        {
            System.out.println("3D View only available for Layout views");
            return;
        }
        WindowContent view2D = WindowFrame.getCurrentWindowFrame(false).getContent();

        // 3D view can only be triggered by EditWindow instances
        if (!(view2D instanceof EditWindow)) return;
        WindowFrame frame = new WindowFrame();

        View3DWindow.create3DWindow(curCell, frame, view2D, transPerNode); // autoboxing
    }

    /**
     * Print hardware properties
     */
    private static void runHardwareTest()
    {
        for (Iterator<Map.Entry> it = VirtualUniverse.getProperties().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry e = it.next();
            System.out.println(e.getKey()+"="+e.getValue());
        }
        System.out.println();

        GraphicsDevice screenDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsConfiguration config = screenDevice.getBestConfiguration(new GraphicsConfigTemplate3D());
        for (Iterator<Map.Entry> it = new Canvas3D(config).queryProperties().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry e = it.next();
            System.out.println(e.getKey()+"="+e.getValue());
        }
    }
}
