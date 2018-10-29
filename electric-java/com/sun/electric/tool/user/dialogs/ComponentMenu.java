/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ComponentMenuTab.java
 *
 * Copyright (c) 2007, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.text.PrefPackage;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Xml;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.tecEdit.Info;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.TextUtils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Component Menu" dialog.
 * This panel appears either in a Preferences tab or stand-alone in the technology editor.
 */
public class ComponentMenu extends EDialog
{
	private JList listNodes, listArcs, listCells, listSpecials, listPopup;
	private DefaultListModel modelNodes, modelArcs, modelCells, modelSpecials, modelPopup;
	private int menuWid, menuHei, menuSelectedX, menuSelectedY, menuDraggedX, menuDraggedY;
	private int lastListSelected = -1;
	private Object [][] menuArray;
	private Object [][] factoryMenuArray;
	private MenuView menuView;
	private String techName;
	private Xml.Technology xTech;
	private boolean changingNodeFields = false;
	private boolean changed;
	private boolean redisplayMenu;
	private static ComponentMenuPreferences compMenuPrefs;

	/**
	 * Method to display a dialog for showing the component menu.
	 * Called only from the technology editor.
	 */
	public static void showComponentMenuDialog(String techName, Xml.MenuPalette xmp,
		List<Xml.PrimitiveNodeGroup> nodeGroups, List<Xml.ArcProto> arcs)
	{
		ComponentMenu dialog = new ComponentMenu(TopLevel.getCurrentJFrame(), true);
		dialog.setTitle("Technology Edit: Component Menu Layout");
		Xml.Technology xTech = new Xml.Technology();
        xTech.nodeGroups.addAll(nodeGroups);
        xTech.arcs.addAll(arcs);
		xTech.menuPalette = xmp;
		int menuWid = xmp.numColumns;
		int menuHei = xmp.menuBoxes.size() / menuWid;
		Object[][] menuArray = new Object[menuHei][menuWid];
		int i = 0;
		for(int y=0; y<menuHei; y++)
		{
			for(int x=0; x<menuWid; x++)
			{
				Object obj = xmp.menuBoxes.get(i++);
				if (obj instanceof List)
				{
					List l = (List)obj;
					if (l.size() == 0) obj = null; else
						if (l.size() == 1) obj = l.get(0);
				}
				menuArray[y][x] = obj;
			}
		}
		dialog.showTechnology(techName, xTech, menuArray, menuArray);

		dialog.finishInitialization();
		dialog.setVisible(true);
	}

	/** Creates new form ComponentMenu */
	public ComponentMenu(Frame parent, boolean techEdit)
	{
		super(parent, true);
		initComponents();

		menuView = new MenuView();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;       gbc.gridy = 2;
		gbc.gridwidth = 1;   gbc.gridheight = 8;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 0.5;   gbc.weighty = 0.9;
		gbc.insets = new Insets(1, 4, 4, 4);
		Top.add(menuView, gbc);

		// load the node function combo-box
		List<PrimitiveNode.Function> funs = PrimitiveNode.Function.getFunctions();
		for(PrimitiveNode.Function fun : funs)
			nodeFunction.addItem(fun.getName());

		// make the nodes, arcs, specials, and popup lists
		modelNodes = new DefaultListModel();
		listNodes = new JList(modelNodes);
		nodeListPane.setViewportView(listNodes);
		listNodes.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent evt) { selectList(0); }
		});

		modelArcs = new DefaultListModel();
		listArcs = new JList(modelArcs);
		arcListPane.setViewportView(listArcs);
		listArcs.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent evt) { selectList(1); }
		});

		modelCells = new DefaultListModel();
		listCells = new JList(modelCells);
		cellListPane.setViewportView(listCells);
		listCells.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent evt) { selectList(2); }
		});
		for(Library lib : Library.getVisibleLibraries())
			libraryName.addItem(lib.getName());
		libraryName.setSelectedItem(Library.getCurrent());
		libraryChanged();
		libraryName.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { libraryChanged(); }
		});

		modelSpecials = new DefaultListModel();
		modelSpecials.addElement(Technology.SPECIALMENUCELL);
		modelSpecials.addElement(Technology.SPECIALMENUEXPORT);
		modelSpecials.addElement(Technology.SPECIALMENUMISC);
		modelSpecials.addElement(Technology.SPECIALMENUPURE);
		modelSpecials.addElement(Technology.SPECIALMENUSPICE);
		listSpecials = new JList(modelSpecials);
		specialListPane.setViewportView(listSpecials);
		listSpecials.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent evt) { selectList(3); }
		});

		modelPopup = new DefaultListModel();
		listPopup = new JList(modelPopup);
		popupListPane.setViewportView(listPopup);
		listPopup.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { showSelectedPopup(); }
		});
//		listPopup.addMouseListener(new MouseAdapter()
//		{
//			public void mouseClicked(MouseEvent evt) { showSelectedPopup(); }
//		});

		// setup listeners for the node detail fields
		nodeAngle.getDocument().addDocumentListener(new NodeFieldDocumentListener());
		nodeName.getDocument().addDocumentListener(new NodeFieldDocumentListener());
		nodeFunction.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { nodeInfoChanged(); }
		});

		if (techEdit)
		{
			JButton OK = new JButton("OK");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;       gbc.gridy = 4;
			gbc.insets = new Insets(25, 4, 4, 4);
			lowerRight.add(OK, gbc);
			OK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) { saveChanges();   closeDialog(null); }
			});

			JButton cancel = new JButton("Cancel");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;       gbc.gridy = 4;
			gbc.insets = new Insets(25, 4, 4, 4);
			lowerRight.add(cancel, gbc);
			cancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) { closeDialog(null); }
			});
		}
	}

	/**
	 * Method to load the dialog with menu information.
	 */
	public void showTechnology(String name, Xml.Technology xt, Object [][] theArray, Object [][] factoryArray)
	{
		techName = name;

		Technology tech = Technology.findTechnology(name);
		if (tech != null && compMenuPrefs.isModified(tech))
		{
			warningMessage.setForeground(Color.RED);
			warningMessage.setText("NOTE: This technology's component menu has been modified in the Preferences.  To restore the default menu, use the 'Reset' button.");
		}

		xTech = xt;
		menuArray = theArray;
		factoryMenuArray = factoryArray;
		menuHei = menuArray.length;
		menuWid = menuArray[0].length;

		// validate primitive functions
		for(int y=0; y<menuHei; y++)
		{
			for(int x=0; x<menuWid; x++)
			{
				Object o = menuArray[y][x];
				if (o instanceof Xml.PrimitiveNode)
				{
					Xml.PrimitiveNode np = (Xml.PrimitiveNode)o;
					if (np.function == null && tech != null)
					{
						PrimitiveNode pnp = tech.findNodeProto(np.name);
						if (pnp != null)
							np.function = pnp.getFunction();
					}
				}
			}
		}

		modelNodes.clear();
		for(Xml.PrimitiveNodeGroup ng : xTech.nodeGroups)
		{
            for (Xml.PrimitiveNode n: ng.nodes)
                modelNodes.addElement(n.name);
		}
		for(Xml.Layer l : xTech.layers)
		{
			if (l.pureLayerNode != null)
				modelNodes.addElement(l.pureLayerNode.name);
		}

		modelArcs.clear();
		for(Xml.ArcProto ap : xTech.arcs)
		{
			modelArcs.addElement(ap.name);
		}

		// display the menu
		showMenuSize();
		showSelected();
		menuView.repaint();
		changed = false;
		redisplayMenu = false;
	}

	public void factoryReset()
	{
		Technology tech = Technology.findTechnology(techName);
		compMenuPrefs.reset(tech);

		menuHei = factoryMenuArray.length;
		menuWid = factoryMenuArray[0].length;
		menuArray = new Object[menuHei][];
		for(int y=0; y<menuHei; y++)
		{
			menuArray[y] = new Object[menuWid];
			for(int x=0; x<menuWid; x++) menuArray[y][x] = factoryMenuArray[y][x];
		}
		menuSelectedY = menuSelectedX = 0;
		menuDraggedX = menuDraggedY = -1;
		showMenuSize();
		showSelected();
		menuView.repaint();
		changed = false;
		redisplayMenu = true;
		warningMessage.setText("");
	}

	/** return the name of this preferences tab. */
	public String getName() { return "Component Menu"; }

	/** return the panel to use for this preferences tab. */
	public JPanel getPanel() { return Top; }

	protected void escapePressed() { closeDialog(null); }

	public boolean isRedisplayMenu() { return redisplayMenu; }

	public boolean isSaveChanges() { return changed; }

	public Object[][] getMenuInfo() { return menuArray; }

    public static class ComponentMenuPreferences extends PrefPackage {
        private static final String KEY_COMPONENT_MENU = "ComponentMenuXMLfor";
        private static final String KEY_COMPONENT_MENU_EXTRA = "ComponentMenuXMLExtra";
        private static final String CONTINUATIONSTRING = "|||MORE|||";

        public Map<Technology,String> menuXmls = new HashMap<Technology,String>();

        public ComponentMenuPreferences(boolean factory)
        {
            super(factory);

            for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); ) {
                Technology tech = it.next();
                String menuXml = getTechXML(tech);

                // store the menu XML
                menuXmls.put(tech, menuXml);
            }
        }

        private String getTechXML(Technology tech)
        {
            Preferences techPrefs = getPrefRoot().node(TECH_NODE);
            String key = getKey(KEY_COMPONENT_MENU, tech.getId());
            String menuXml = techPrefs.get(key, "");

            // if the information is split over multiple keys, read all of them
            int which = 0;
            while (menuXml.endsWith(CONTINUATIONSTRING))
            {
            	which++;
            	key = getKey(KEY_COMPONENT_MENU_EXTRA + which + "for", tech.getId());
                String menuXmlMore = techPrefs.get(key, "");
                menuXml = menuXml.substring(0, menuXml.length() - CONTINUATIONSTRING.length()) + menuXmlMore;
            }
            return menuXml;
        }

        public boolean isModified(Technology tech)
        {
            String menuXml = getTechXML(tech);
            if (menuXml == null || menuXml.length() == 0) return false;
        	return true;
        }

        public void reset(Technology tech)
        {
            String key = getKey(KEY_COMPONENT_MENU, tech.getId());
            Preferences techPrefs = getPrefRoot().node(TECH_NODE);
            techPrefs.remove(key);
            try
            {
            	techPrefs.flush();
            } catch (BackingStoreException e) {}
            menuXmls.remove(tech);
        }

        /**
         * Store annotated option fields of the subclass into the specified Preferences subtree.
         * @param prefRoot the root of the Preferences subtree.
         * @param removeDefaults remove from the Preferences subtree options which have factory default value.
         */
        @Override
        public void putPrefs(Preferences prefRoot, boolean removeDefaults) {
            super.putPrefs(prefRoot, removeDefaults);
            Preferences techPrefs = prefRoot.node(TECH_NODE);
            for (Map.Entry<Technology,String> e: menuXmls.entrySet()) {
                Technology tech = e.getKey();
                String key = getKey(KEY_COMPONENT_MENU, tech.getId());
                String menuXml = e.getValue();
                if (removeDefaults && menuXml.length() == 0)
                    techPrefs.remove(key);
                else
                {
                	for(int which=0; ; which++)
                	{
                		String contents = menuXml;
                		if (contents.length() < Preferences.MAX_VALUE_LENGTH) menuXml = ""; else
                		{
                			contents = contents.substring(0, Preferences.MAX_VALUE_LENGTH - CONTINUATIONSTRING.length()) + CONTINUATIONSTRING;
                			menuXml = menuXml.substring(Preferences.MAX_VALUE_LENGTH - CONTINUATIONSTRING.length());
                		}
                		if (which > 0)
                            key = getKey(KEY_COMPONENT_MENU_EXTRA + which + "for", tech.getId());
                		techPrefs.put(key, contents);
                		if (menuXml.length() == 0) break;
                	}
                }
            }
        }
    }


	/**
	 * Method to return the current Xml menu palette for a specified technology.
     * @param tech specified technology
	 * @return the current menu palette.
	 */
    public static Xml.MenuPalette getMenuPalette(Technology tech) {
		// see if there is a preference for the component menu
		compMenuPrefs = new ComponentMenuPreferences(false);
		String nodeGroupXML = compMenuPrefs.menuXmls.get(tech);
		if (nodeGroupXML == null || nodeGroupXML.length() == 0)
            return tech.getFactoryMenuPalette();

		// parse the preference and build a component menu
		return tech.parseComponentMenuXML(nodeGroupXML);
    }

	/**
	 * Method called when the "OK" button is hit.
	 */
	private void saveChanges()
	{
		if (!changed) return;
		Xml.MenuPalette xmp = new Xml.MenuPalette();
		xmp.numColumns = menuWid;
		xmp.menuBoxes = new ArrayList<List<?>>();
		for(int y=0; y<menuHei; y++)
		{
			for(int x=0; x<menuWid; x++)
			{
				Object item = null;
				if (menuArray[y] != null)
					item = menuArray[y][x];
				if (item instanceof List)
				{
					xmp.menuBoxes.add((List)item);
				} else
				{
					List<Object> subList = new ArrayList<Object>();
					if (item != null) subList.add(item);
					xmp.menuBoxes.add(subList);
				}
			}
		}

		new SetMenuJob(xmp.writeXml());
	}

	/**
	 * Class to store the updated component menu on the Technology Library.
	 */
	private static class SetMenuJob extends Job
	{
		private String menuXML;

		private SetMenuJob(String menuXML)
		{
			super("Set Technology Library Component Menu", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.menuXML = menuXML;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			Library.getCurrent().newVar(Info.COMPMENU_KEY, menuXML, getEditingPreferences());
			return true;
		}
	}

	/**
	 * Class to handle special changes to changes to node fields.
	 */
	private class NodeFieldDocumentListener implements DocumentListener
	{
		public void changedUpdate(DocumentEvent e) { nodeInfoChanged(); }
		public void insertUpdate(DocumentEvent e) { nodeInfoChanged(); }
		public void removeUpdate(DocumentEvent e) { nodeInfoChanged(); }
	}

	/**
	 * Method called with one of the lists on the right is clicked.
	 * Remembers the last list clicked (node, arc, or special) so that
	 * the "add" button will take from the right list.
	 * @param list the list selected (0=node, 1=arc, 2=special).
	 */
	private void selectList(int list)
	{
		lastListSelected = list;
		switch (list)
		{
			case 0:		// nodes list
				listArcs.clearSelection();
				listCells.clearSelection();
				listSpecials.clearSelection();
				break;
			case 1:		// arcs list
				listNodes.clearSelection();
				listCells.clearSelection();
				listSpecials.clearSelection();
				break;
			case 2:		// cells list
				listNodes.clearSelection();
				listArcs.clearSelection();
				listSpecials.clearSelection();
				break;
			case 3:		// specials list
				listNodes.clearSelection();
				listArcs.clearSelection();
				listCells.clearSelection();
				break;
		}
	}

	private void showMenuSize()
	{
		menuSize.setText("\"" + techName + "\" Component menu (" + menuWid + " by " + menuHei + ")");
	}

	/**
	 * Method to show details about the selected menu entry.
	 */
	private void showSelected()
	{
		Object item = (menuArray[menuSelectedY] == null) ? null : menuArray[menuSelectedY][menuSelectedX];
		showSelectedObject(item, false);
	}

	/**
	 * Method to show details about an object in a menu entry.
	 * @param item the object to show in detail.
	 * @param fromPopup true if this is from a popup list.
	 */
	private void showSelectedObject(Object item, boolean fromPopup)
	{
		showThisNode(false, null, null);
		if (!fromPopup)
		{
			popupListPane.setViewportView(null);
			modelPopup.clear();
		}
		if (item instanceof Xml.PrimitiveNode)
		{
			Xml.PrimitiveNode np = (Xml.PrimitiveNode)item;
			if (!fromPopup) selectedMenuName.setText("Current entry: Node " + np.name);
			showThisNode(true, null, np);
		} else if (item instanceof Xml.MenuNodeInst)
		{
			Xml.MenuNodeInst ni = (Xml.MenuNodeInst)item;
			String name = "Current entry: Node " + ni.protoName;
			if (!fromPopup) selectedMenuName.setText(name);
			showThisNode(true, ni, null);
		} else if (item instanceof Xml.ArcProto)
		{
			Xml.ArcProto ap = (Xml.ArcProto)item;
			if (!fromPopup) selectedMenuName.setText("Current entry: Arc " + ap.name);
		} else if (item instanceof JSeparator)
		{
			if (!fromPopup) selectedMenuName.setText("Current entry: Separator");
		} else if (item instanceof List && !fromPopup)
		{
			selectedMenuName.setText("Current entry: Popup menu:");
			popupListPane.setViewportView(listPopup);
			List nodes = (List)item;
			for(Object obj : nodes)
			{
				if (obj instanceof Xml.PrimitiveNode) modelPopup.addElement(((Xml.PrimitiveNode)obj).name); else
				if (obj instanceof Xml.MenuNodeInst) modelPopup.addElement(getNodeName((Xml.MenuNodeInst)obj)); else
				if (obj instanceof Xml.ArcProto) modelPopup.addElement(((Xml.ArcProto)obj).name); else
				if (obj instanceof JSeparator) modelPopup.addElement("----------"); else
					modelPopup.addElement(obj);
			}
		} else if (item instanceof String)
		{
			String s = (String)item;
			if (s.startsWith("LOADCELL "))
			{
				if (!fromPopup) selectedMenuName.setText("Current entry: Cell " + s.substring(9));
			} else
			{
				if (!fromPopup) selectedMenuName.setText("Current entry: Special " + s);
			}
		} else
		{
			if (!fromPopup) selectedMenuName.setText("Current entry: Empty");
		}
	}

	/**
	 * Method called when the user clicks on an entry in a Popup list.
	 */
	private void showSelectedPopup()
	{
		Object item = (menuArray[menuSelectedY] == null) ? null : menuArray[menuSelectedY][menuSelectedX];
		if (item == null) return;
		if (item instanceof List)
		{
			List nodes = (List)item;
			int index = listPopup.getSelectedIndex();
			if (index < 0 || index >= nodes.size()) return;
			Object obj = nodes.get(index);
			showSelectedObject(obj, true);
		}
	}

	/**
	 * Method to determine the name of a node (may use its displayed name).
	 * @param ni the node to describe.
	 * @return the name to use for that node.
	 */
	private String getNodeName(Xml.MenuNodeInst ni)
	{
		return ni.protoName;
	}

	private void nodeInfoChanged()
	{
		if (changingNodeFields) return;
		Object item = (menuArray[menuSelectedY] == null) ? null : menuArray[menuSelectedY][menuSelectedX];
		if (item == null) return;
		int index = -1;
		List nodes = null;
		if (item instanceof List)
		{
			nodes = (List)item;
			index = listPopup.getSelectedIndex();
			if (index < 0 || index >= nodes.size()) return;
			item = nodes.get(index);
		}
		String protoName;
		if (item instanceof Xml.MenuNodeInst) protoName = ((Xml.MenuNodeInst)item).protoName; else
			if (item instanceof Xml.PrimitiveNode) protoName = ((Xml.PrimitiveNode)item).name; else return;
		Xml.MenuNodeInst newItem = new Xml.MenuNodeInst();
		newItem.protoName = protoName;
		newItem.function = PrimitiveNode.Function.findName((String)nodeFunction.getSelectedItem());
		newItem.rotation = TextUtils.atoi(nodeAngle.getText()) * 10;
		newItem.text = nodeName.getText().trim();
		if (index < 0)
		{
			menuArray[menuSelectedY][menuSelectedX] = newItem;
		} else
		{
			nodes.set(index, newItem);
		}
		menuView.repaint();
		changed = true;
		redisplayMenu = true;
	}

	/**
	 * Method called when the library popup above the cells list changes.
	 */
	private void libraryChanged()
	{
		modelCells.clear();
		String libName = (String)libraryName.getSelectedItem();
		if (libName == null) return;
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
		{
			Cell cell = it.next();
			modelCells.addElement(cell.noLibDescribe());
		}
	}

	/**
	 * Method to show details about the selected node.
	 * @param valid true if this is a valid node (false to dim all detail fields).
	 * @param ni the NodeInst (may be null but still have a valid prototype).
	 * @param np the NodeProto.
	 */
	private void showThisNode(boolean valid, Xml.MenuNodeInst ni, Xml.PrimitiveNode np)
	{
		changingNodeFields = true;
		nodeName.setText("");
		nodeAngle.setText("");
		nodeFunction.setSelectedIndex(0);
		if (valid)
		{
			nodeAngle.setEnabled(true);
			nodeAngleLabel.setEnabled(true);
			nodeFunction.setEnabled(true);
			nodeFunctionLabel.setEnabled(true);
			nodeName.setEnabled(true);
			nodeNameLabel.setEnabled(true);
			if (ni == null)
			{
				nodeAngle.setText("0");
				if (np != null) 
				{
					com.sun.electric.technology.PrimitiveNode.Function function = np.function;
					
					if (function == null)
					{
						function = com.sun.electric.technology.PrimitiveNode.Function.UNKNOWN;
						System.out.print("Error determining function associated to " + np.name + ": ");
						System.out.println("assigning  " + function.getName());
					}
					nodeFunction.setSelectedItem(function);
				}
			} else
			{
				nodeAngle.setText((ni.rotation / 10) + "");
				com.sun.electric.technology.PrimitiveNode.Function function = ni.function;
				
				if (function == null)
				{
					function = com.sun.electric.technology.PrimitiveNode.Function.UNKNOWN;
					System.out.print("Error determining function associated to " + ni.protoName + ": ");
					System.out.println("assigning  " + function.getName());
				}
				nodeFunction.setSelectedItem(function.getName());
				if (ni.text != null)
					nodeName.setText(ni.text);
			}
		} else
		{
			nodeAngle.setEnabled(false);
			nodeAngleLabel.setEnabled(false);
			nodeFunction.setEnabled(false);
			nodeFunctionLabel.setEnabled(false);
			nodeName.setEnabled(false);
			nodeNameLabel.setEnabled(false);
		}
		changingNodeFields = false;
	}

	/**
	 * Method called when the "add" button is clicked.
	 * Adds an entry to the menu or popup.
	 * @param obj the object to add to the menu entry.
	 */
	private void addToMenu(Object obj)
	{
		if (menuArray[menuSelectedY] == null)
			menuArray[menuSelectedY] = new Object[menuWid];
		Object item = menuArray[menuSelectedY][menuSelectedX];
		if (item == null)
		{
			menuArray[menuSelectedY][menuSelectedX] = obj;
		} else if (item instanceof List)
		{
			List popupItems = (List)item;
			if (!isUniformType(popupItems, obj)) return;
			popupItems.add(obj);
		} else
		{
			List<Object> newList = new ArrayList<Object>();
			newList.add(item);
			if (!isUniformType(newList, obj)) return;
			newList.add(obj);
			menuArray[menuSelectedY][menuSelectedX] = newList;
		}
		menuView.repaint();
		showSelected();
		changed = true;
		redisplayMenu = true;
	}

	/**
	 * Method to tell whether a proposed new object fits with an existing list (all nodes, all arcs, etc).
	 * Arcs cannot be added to node lists, nodes cannot be added to arc lists, and special text cannot
	 * be added to any list.
	 * @param list the list to test.
	 * @param newOne the object being added to the list.
	 * @return true if the object fits in the list.
	 */
	private boolean isUniformType(List list, Object newOne)
	{
		if (newOne instanceof String)
		{
			Job.getUserInterface().showErrorMessage("Must remove everything in the menu before adding 'special text'",
				"Cannot Add");
			return false;
		}
		for(Object oldOne : list)
		{
			if (oldOne instanceof Xml.ArcProto)
			{
				if (!(newOne instanceof Xml.ArcProto))
				{
					Job.getUserInterface().showErrorMessage("Existing Arc menu can only have other arcs added to it",
						"Cannot Add");
					return false;
				}
			} else if (oldOne instanceof Xml.PrimitiveNode)
			{
				if (!(newOne instanceof Xml.PrimitiveNode))
				{
					Job.getUserInterface().showErrorMessage("Existing Primitive Node menu can only have other primitive nodes added to it",
						"Cannot Add");
					return false;
				}
			} else if (oldOne instanceof String && ((String)oldOne).startsWith("LOADCELL "))
			{
				if (!(newOne instanceof String && ((String)newOne).startsWith("LOADCELL ")))
				{
					Job.getUserInterface().showErrorMessage("Existing Cell menu can only have other cells added to it",
						"Cannot Add");
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Class for catching drags in the menu palette view.
	 */
	private class MenuViewDropTarget implements DropTargetListener
	{
        @Override
		public void dragEnter(DropTargetDragEvent e) { dragAction(e); }

        @Override
		public void dragOver(DropTargetDragEvent e) { dragAction(e); }

		private Point getDraggedObject(DataFlavor [] flavors)
		{
			if (flavors.length > 0)
			{
				if (flavors[0] instanceof PointDataFlavor)
				{
					PointDataFlavor npdf = (PointDataFlavor)flavors[0];
					Point obj = npdf.getFlavorObject();
					return obj;
				}
			}
			return null;
		}

		private void dragAction(DropTargetDragEvent e)
		{
			Point obj = getDraggedObject(e.getCurrentDataFlavors());
			if (obj != null)
			{
				int action = e.getDropAction();
				e.acceptDrag(action);

				// determine the window
				Point destPt = menuView.getPaletteEntry(e.getLocation().x, e.getLocation().y);
				if (destPt != null)
				{
					menuDraggedX = destPt.x;
					menuDraggedY = destPt.y;
				} else
				{
					menuDraggedX = menuDraggedY = -1;
				}
				menuView.repaint();
				return;
			}
		}

        @Override
		public void dropActionChanged(DropTargetDragEvent e)
		{
			e.acceptDrag(e.getDropAction());
		}

        @Override
		public void dragExit(DropTargetEvent e) {}

        @Override
		public void drop(DropTargetDropEvent dtde)
		{
			dtde.acceptDrop(DnDConstants.ACTION_LINK);
			Point sourcePt = getDraggedObject(dtde.getCurrentDataFlavors());
			if (sourcePt == null)
			{
				dtde.dropComplete(false);
				menuDraggedX = menuDraggedY = -1;
				return;
			}

			// determine the window
			DropTarget dt = (DropTarget)dtde.getSource();
			if (!(dt.getComponent() instanceof JPanel))
			{
				dtde.dropComplete(false);
				menuDraggedX = menuDraggedY = -1;
				return;
			}
			Point destPt = menuView.getPaletteEntry(dtde.getLocation().x, dtde.getLocation().y);
			if (destPt == null) return;
			menuDraggedX = menuDraggedY = -1;
			menuSelectedX = destPt.x;   menuSelectedY = destPt.y;
			menuArray[destPt.y][destPt.x] = menuArray[sourcePt.y][sourcePt.x];
			menuArray[sourcePt.y][sourcePt.x] = null;
			menuView.repaint();
			showSelected();
			changed = true;
			redisplayMenu = true;
		}
	}

	/**
	 * Class to define a custom data flavor that packages a Point in the menu that was moved.
	 */
	private static class PointDataFlavor extends DataFlavor
	{
		private Point pt;

		PointDataFlavor(Point pt)
		{
			super(Point.class, "Java Point Object");
			this.pt = pt;
		}

		public Point getFlavorObject() { return pt; }
	}

	/**
	 * Class to define a custom transferable that packages a Point in the menu that was moved.
	 */
	public static class PointTransferable implements Transferable
	{
		private PointDataFlavor df;

		public PointTransferable(Point pt)
		{
			df = new PointDataFlavor(pt);
		}

        @Override
		public DataFlavor[] getTransferDataFlavors()
		{
			DataFlavor [] it = new DataFlavor[1];
			it[0] = df;
			return it;
		}

        @Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			if (flavor == df) return true;
			return false;
		}

        @Override
		public Object getTransferData(DataFlavor flavor)
		{
			if (flavor != df) return null;
			return df.pt;
		}
	}

	private MenuViewDropTarget menuViewDropTarget = new MenuViewDropTarget();

	/**
	 * Class to draw the menu.
	 */
	private class MenuView extends JPanel implements MouseListener, DragGestureListener, DragSourceListener
	{
		/** Variable needed for drag-and-drop */	private DragSource dragSource = null;

		MenuView()
		{
			addMouseListener(this);
			// initialize drag-and-drop from this palette
			dragSource = DragSource.getDefaultDragSource();
			dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);

			// a drop target for moving palette entries
			new DropTarget(this, DnDConstants.ACTION_LINK, menuViewDropTarget, true);
		}

		/**
		 * Method to repaint this MenuView.
		 */
		public void paint(Graphics g)
		{
			// clear the area
			Dimension dim = getSize();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, dim.width, dim.height);

			// draw black menu dividers
			g.setColor(Color.BLACK);
			for(int i=0; i<=menuHei; i++)
			{
				int y = (dim.height-1) - (dim.height-1) * i / menuHei;
				g.drawLine(0, y, dim.width-1, y);
			}
			for(int i=0; i<=menuWid; i++)
			{
				int x = (dim.width-1) * i / menuWid;
				g.drawLine(x, 0, x, dim.height-1);
			}

			// draw all of the menu entries
			for (int i = 0; i < menuWid; i++)
			{
				for (int j = 0; j < menuHei; j++)
				{
					int lowX = (dim.width-1) * i / menuWid;
					int lowY = (dim.height-1) - (dim.height-1) * (j+1) / menuHei;
					int highX = (dim.width-1) * (i+1) / menuWid;
					int highY = (dim.height-1) - (dim.height-1) * j / menuHei;
					Object item = (menuArray[j] == null) ? null : menuArray[j][i];
					Color borderColor = null;
					if (item instanceof Xml.PrimitiveNode)
					{
						Xml.PrimitiveNode np = (Xml.PrimitiveNode)item;
						int midY = (lowY + highY) / 2;
						showString(g, "Node", lowX, highX, lowY, midY);
						showString(g, np.name, lowX, highX, midY, highY);
						borderColor = Color.BLUE;
					} else if (item instanceof Xml.MenuNodeInst)
					{
						Xml.MenuNodeInst ni = (Xml.MenuNodeInst)item;
						int midY = (lowY + highY) / 2;
						showString(g, "Node", lowX, highX, lowY, midY);
						showString(g, getNodeName(ni), lowX, highX, midY, highY);
						borderColor = Color.BLUE;
					} else if (item instanceof Xml.ArcProto)
					{
						Xml.ArcProto ap = (Xml.ArcProto)item;
						int midY = (lowY + highY) / 2;
						showString(g, "Arc", lowX, highX, lowY, midY);
						showString(g, ap.name, lowX, highX, midY, highY);
						borderColor = Color.RED;
					} else if (item instanceof List)
					{
						List popupItems = (List)item;
						for(Object o : popupItems)
						{
							if (o instanceof Xml.PrimitiveNode || o instanceof Xml.MenuNodeInst) borderColor = Color.BLUE; else
								if (o instanceof Xml.ArcProto) borderColor = Color.RED;
						}
						showString(g, "POPUP", lowX, highX, lowY, highY);
					} else if (item instanceof String)
					{
						String s = (String)item;
						if (s.startsWith("LOADCELL "))
						{
							String cellName = s.substring(9);
							int midY = (lowY + highY) / 2;
							showString(g, "Cell", lowX, highX, lowY, midY);
							showString(g, cellName, lowX, highX, midY, highY);
							borderColor = Color.BLUE;
						} else
						{
							showString(g, "\"" + (String)item + "\"", lowX, highX, lowY, highY);
						}
					}
					if (borderColor != null)
					{
						g.setColor(borderColor);
						g.drawLine(lowX+1, lowY-1, highX-1, lowY-1);
						g.drawLine(highX-1, lowY-1, highX-1, highY+1);
						g.drawLine(highX-1, highY+1, lowX+1, highY+1);
						g.drawLine(lowX+1, highY+1, lowX+1, lowY-1);
					}
				}
			}

			// highlight the selected menu element
			if (menuSelectedX >= 0 && menuSelectedY >= 0)
			{
				int lowX = (dim.width-1) * menuSelectedX / menuWid;
				int lowY = (dim.height-1) - (dim.height-1) * (menuSelectedY+1) / menuHei;
				int highX = (dim.width-1) * (menuSelectedX+1) / menuWid;
				int highY = (dim.height-1) - (dim.height-1) * menuSelectedY / menuHei;
				g.setColor(Color.GREEN);
				g.drawLine(lowX, lowY, highX, lowY);
				g.drawLine(highX, lowY, highX, highY);
				g.drawLine(highX, highY, lowX, highY);
				g.drawLine(lowX, highY, lowX, lowY);
				g.drawLine(lowX+1, lowY+1, highX-1, lowY+1);
				g.drawLine(highX-1, lowY+1, highX-1, highY-1);
				g.drawLine(highX-1, highY-1, lowX+1, highY-1);
				g.drawLine(lowX+1, highY-1, lowX+1, lowY+1);
			}

			// highlight the dragged menu element
			if (menuDraggedX >= 0 && menuDraggedY >= 0)
			{
				int lowX = (dim.width-1) * menuDraggedX / menuWid;
				int lowY = (dim.height-1) - (dim.height-1) * (menuDraggedY+1) / menuHei;
				int highX = (dim.width-1) * (menuDraggedX+1) / menuWid;
				int highY = (dim.height-1) - (dim.height-1) * menuDraggedY / menuHei;
				g.setColor(new Color(0, 200, 0));
				g.drawLine(lowX, lowY, highX, lowY);
				g.drawLine(highX, lowY, highX, highY);
				g.drawLine(highX, highY, lowX, highY);
				g.drawLine(lowX, highY, lowX, lowY);
				g.drawLine(lowX+1, lowY+1, highX-1, lowY+1);
				g.drawLine(highX-1, lowY+1, highX-1, highY-1);
				g.drawLine(highX-1, highY-1, lowX+1, highY-1);
				g.drawLine(lowX+1, highY-1, lowX+1, lowY+1);
			}
		}

		private void showString(Graphics g, String msg, int lowX, int highX, int lowY, int highY)
		{
			g.setColor(Color.BLACK);
			Font font = new Font(User.getDefaultFont(), Font.PLAIN, 9);
			g.setFont(font);
			FontRenderContext frc = new FontRenderContext(null, true, true);
			for(;;)
			{
				GlyphVector gv = font.createGlyphVector(frc, msg);
				LineMetrics lm = font.getLineMetrics(msg, frc);
				double txtHeight = lm.getHeight();
				Rectangle2D rasRect = gv.getLogicalBounds();
				double txtWidth = rasRect.getWidth();
				if (txtWidth <= highX-lowX)
				{
					Graphics2D g2 = (Graphics2D)g;
					g2.drawGlyphVector(gv, (float)(lowX + (highX-lowX - txtWidth)/2),
						(float)(lowY + highY + txtHeight)/2 - lm.getDescent());
					break;
				}
				msg = msg.substring(0, msg.length()-1);
			}
		}

		private Point getPaletteEntry(int x, int y)
		{
			Dimension dim = getSize();
			int pX = x / (dim.width / menuWid);
			int pY = (menuHei-1) - (y / (dim.height / menuHei));
			if (pX < 0 || pX >= menuWid || pY < 0 || pY >= menuHei) return null;
			return new Point(pX, pY);
		}

		// the MouseListener events
		public void mousePressed(MouseEvent evt)
		{
			Point pt = getPaletteEntry(evt.getX(), evt.getY());
			if (pt == null) return;
			menuSelectedX = pt.x;
			menuSelectedY = pt.y;
			showSelected();
			repaint();
		}

		public void mouseReleased(MouseEvent evt) {}
		public void mouseClicked(MouseEvent evt) {}
		public void mouseEntered(MouseEvent evt) {}
		public void mouseExited(MouseEvent evt) {}

		@Override
		public void dragGestureRecognized(DragGestureEvent e)
		{
			// make a Transferable Object
			PointTransferable transferable = new PointTransferable(new Point(menuSelectedX, menuSelectedY));

			// begin the drag
			dragSource.startDrag(e, DragSource.DefaultLinkDrop, transferable, this);			
		}

		@Override
		public void dragEnter(DragSourceDragEvent dsde) {}

		@Override
		public void dragOver(DragSourceDragEvent dsde) {}

		@Override
		public void dropActionChanged(DragSourceDragEvent dsde) {}

		@Override
		public void dragExit(DragSourceEvent dse) {}

		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        Top = new javax.swing.JPanel();
        nodeListPane = new javax.swing.JScrollPane();
        arcListPane = new javax.swing.JScrollPane();
        menuSize = new javax.swing.JLabel();
        specialListPane = new javax.swing.JScrollPane();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        addButton = new javax.swing.JButton();
        removeButton = new javax.swing.JButton();
        lowerRight = new javax.swing.JPanel();
        addRow = new javax.swing.JButton();
        deleteRow = new javax.swing.JButton();
        addColumn = new javax.swing.JButton();
        deleteColumn = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        rotateColumnDown = new javax.swing.JButton();
        rotateColumnUp = new javax.swing.JButton();
        swapColumns = new javax.swing.JButton();
        splitColumn = new javax.swing.JButton();
        lowerLeft = new javax.swing.JPanel();
        selectedMenuName = new javax.swing.JLabel();
        popupListPane = new javax.swing.JScrollPane();
        nodeAngleLabel = new javax.swing.JLabel();
        nodeFunctionLabel = new javax.swing.JLabel();
        nodeNameLabel = new javax.swing.JLabel();
        nodeName = new javax.swing.JTextField();
        nodeFunction = new javax.swing.JComboBox();
        nodeAngle = new javax.swing.JTextField();
        cellListPane = new javax.swing.JScrollPane();
        jLabel1 = new javax.swing.JLabel();
        libraryName = new javax.swing.JComboBox();
        warningMessage = new javax.swing.JLabel();

        setTitle("Component Menu");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.BorderLayout(0, 10));

        Top.setLayout(new java.awt.GridBagLayout());

        nodeListPane.setPreferredSize(new java.awt.Dimension(200, 200));
        nodeListPane.setRequestFocusEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        Top.add(nodeListPane, gridBagConstraints);

        arcListPane.setPreferredSize(new java.awt.Dimension(200, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        Top.add(arcListPane, gridBagConstraints);

        menuSize.setText("Menu");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        Top.add(menuSize, gridBagConstraints);

        specialListPane.setOpaque(false);
        specialListPane.setPreferredSize(new java.awt.Dimension(200, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        Top.add(specialListPane, gridBagConstraints);

        jLabel2.setText("Nodes:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 40, 1, 4);
        Top.add(jLabel2, gridBagConstraints);

        jLabel3.setText("Arcs:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 40, 1, 4);
        Top.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Special:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 40, 1, 4);
        Top.add(jLabel4, gridBagConstraints);

        addButton.setText("<< Add");
        addButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(addButton, gridBagConstraints);

        removeButton.setText("Remove");
        removeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(removeButton, gridBagConstraints);

        lowerRight.setBorder(javax.swing.BorderFactory.createTitledBorder("Row and Column Commands"));
        lowerRight.setLayout(new java.awt.GridBagLayout());

        addRow.setText("Add Below Current");
        addRow.setToolTipText("Add a new row below the current entry (highlighted in green)");
        addRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addRowActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 8, 4);
        lowerRight.add(addRow, gridBagConstraints);

        deleteRow.setText("Delete Current");
        deleteRow.setToolTipText("Delete the row with the current entry (highlighted in green)");
        deleteRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteRowActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 8, 4);
        lowerRight.add(deleteRow, gridBagConstraints);

        addColumn.setText("Add to Right of Current");
        addColumn.setToolTipText("Add a new column to the right of the current entry (highlighted in green)");
        addColumn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addColumnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 2, 4);
        lowerRight.add(addColumn, gridBagConstraints);

        deleteColumn.setText("Delete Current");
        deleteColumn.setToolTipText("Delete the column with the current entry (highlighted in green)");
        deleteColumn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteColumnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 2, 4);
        lowerRight.add(deleteColumn, gridBagConstraints);

        jLabel5.setText("Rows:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 8, 4);
        lowerRight.add(jLabel5, gridBagConstraints);

        jLabel6.setText("Columns:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 2, 4);
        lowerRight.add(jLabel6, gridBagConstraints);

        rotateColumnDown.setText("Rotate Current Down");
        rotateColumnDown.setToolTipText("Rotate the entries in the current column (the column with the current entry, highlighted in green), shifting everything down and moving the bottom entry to the top.");
        rotateColumnDown.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateColumnDownActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        lowerRight.add(rotateColumnDown, gridBagConstraints);

        rotateColumnUp.setText("Rotate Current Up");
        rotateColumnUp.setToolTipText("Rotate the entries in the current column (the column with the current entry, highlighted in green), shifting everything up and moving the top entry to the bottom.");
        rotateColumnUp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateColumnUpActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        lowerRight.add(rotateColumnUp, gridBagConstraints);

        swapColumns.setText("Swap with Right");
        swapColumns.setToolTipText("Swap the entries in the current column (the column with the current entry, highlighted in green), with the entries in the column to its right.");
        swapColumns.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swapColumnsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        lowerRight.add(swapColumns, gridBagConstraints);

        splitColumn.setText("Split to Right");
        splitColumn.setToolTipText("Insert a new column to the right of the current column (the column with the current entry, highlighted in green).  Then move the entries at the bottom of the current column to the top of the new one.");
        splitColumn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                splitColumnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        lowerRight.add(splitColumn, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        Top.add(lowerRight, gridBagConstraints);

        lowerLeft.setLayout(new java.awt.GridBagLayout());

        selectedMenuName.setText("selected menu");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        lowerLeft.add(selectedMenuName, gridBagConstraints);

        popupListPane.setPreferredSize(new java.awt.Dimension(200, 70));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        lowerLeft.add(popupListPane, gridBagConstraints);

        nodeAngleLabel.setText("Angle:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        lowerLeft.add(nodeAngleLabel, gridBagConstraints);

        nodeFunctionLabel.setText("Function:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        lowerLeft.add(nodeFunctionLabel, gridBagConstraints);

        nodeNameLabel.setText("Label:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        lowerLeft.add(nodeNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        lowerLeft.add(nodeName, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        lowerLeft.add(nodeFunction, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        lowerLeft.add(nodeAngle, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        Top.add(lowerLeft, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        Top.add(cellListPane, gridBagConstraints);

        jLabel1.setText("Cells:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 40, 1, 4);
        Top.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        Top.add(libraryName, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        Top.add(warningMessage, gridBagConstraints);

        getContentPane().add(Top, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void deleteColumnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteColumnActionPerformed
		if (menuWid <= 1)
		{
			Job.getUserInterface().showErrorMessage("There must be at least one column...cannot delete the last one",
				"Cannot Remove Column");
			return;
		}
		for(int y=0; y<menuHei; y++)
		{
			Object [] newRow = new Object[menuWid-1];
			int fill = 0;
			for(int x=0; x<menuWid; x++)
			{
				if (x == menuSelectedX) continue;
				newRow[fill++] = menuArray[y][x];
			}
			menuArray[y] = newRow;
		}
		menuWid--;
		if (menuSelectedX >= menuWid) menuSelectedX--;
		menuView.repaint();
		showSelected();
		showMenuSize();
		changed = true;
		redisplayMenu = true;
	}//GEN-LAST:event_deleteColumnActionPerformed

    private void addColumnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addColumnActionPerformed
		for(int y=0; y<menuHei; y++)
		{
			Object [] newRow = new Object[menuWid+1];
			int fill = 0;
			for(int x=0; x<menuWid; x++)
			{
				newRow[fill++] = menuArray[y][x];
				if (x == menuSelectedX) newRow[fill++] = null;
			}
			menuArray[y] = newRow;
		}
		menuWid++;
		menuSelectedX++;
		menuView.repaint();
		showSelected();
		showMenuSize();
		changed = true;
		redisplayMenu = true;
	}//GEN-LAST:event_addColumnActionPerformed

    private void deleteRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteRowActionPerformed
		if (menuHei <= 1)
		{
			Job.getUserInterface().showErrorMessage("There must be at least one row...cannot delete the last one",
				"Cannot Remove Row");
			return;
		}
		Object [][] newMenu = new Object[menuHei-1][];
		int fill = 0;
		for(int y=0; y<menuHei; y++)
		{
			if (y == menuSelectedY) continue;
			newMenu[fill++] = menuArray[y];
		}
		menuArray = newMenu;
		menuHei--;
		if (menuSelectedY >= menuHei) menuSelectedY--;
		menuView.repaint();
		showSelected();
		showMenuSize();
		changed = true;
		redisplayMenu = true;
	}//GEN-LAST:event_deleteRowActionPerformed

    private void addRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addRowActionPerformed
		Object [][] newMenu = new Object[menuHei+1][];
		int fill = 0;
		for(int y=0; y<menuHei; y++)
		{
			if (y == menuSelectedY) newMenu[fill++] = new Object[menuWid];
			newMenu[fill++] = menuArray[y];
		}
		menuArray = newMenu;
		menuHei++;
		menuSelectedY++;
		menuView.repaint();
		showSelected();
		showMenuSize();
		changed = true;
		redisplayMenu = true;
	}//GEN-LAST:event_addRowActionPerformed

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
		if (menuArray[menuSelectedY] == null)
			menuArray[menuSelectedY] = new Object[menuWid];
		Object item = menuArray[menuSelectedY][menuSelectedX];
		if (item == null) return;
		if (item instanceof List)
		{
			List popupItems = (List)item;
			int index = listPopup.getSelectedIndex();
			if (index < 0)
			{
				Job.getUserInterface().showErrorMessage("Must first select the popup item to be removed from the list",
					"Cannot Remove");
				return;
			}
			popupItems.remove(index);
			if (popupItems.size() == 1) menuArray[menuSelectedY][menuSelectedX] = popupItems.get(0);
		} else
		{
			menuArray[menuSelectedY][menuSelectedX] = null;
		}
		menuView.repaint();
		showSelected();
		changed = true;
		redisplayMenu = true;
	}//GEN-LAST:event_removeButtonActionPerformed

    private void addButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButtonActionPerformed
		switch (lastListSelected)
		{
			case 0:	// add a node
				String nodeName = (String)listNodes.getSelectedValue();
				Xml.PrimitiveNode pnp = xTech.findNode(nodeName);
				addToMenu(pnp);
				break;
			case 1: // add an arc
				String arcName = (String)listArcs.getSelectedValue();
				Xml.ArcProto ap = xTech.findArc(arcName);
				addToMenu(ap);
				break;
			case 2: // add a cell
				String cellName = (String)listCells.getSelectedValue();
				String libName = (String)libraryName.getSelectedItem();
				addToMenu("LOADCELL " + libName + ":" + cellName);
				break;
			case 3:	// add a special text
				String specialName = (String)listSpecials.getSelectedValue();
				addToMenu(specialName);
				break;
		}
	}//GEN-LAST:event_addButtonActionPerformed

    private void rotateColumnDownActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateColumnDownActionPerformed
    	Object first = menuArray[0][menuSelectedX];
		for(int y=1; y<menuHei; y++)
			menuArray[y-1][menuSelectedX] = menuArray[y][menuSelectedX];
		menuArray[menuHei-1][menuSelectedX] = first;
		menuView.repaint();
		showSelected();
		changed = true;
		redisplayMenu = true;
    }//GEN-LAST:event_rotateColumnDownActionPerformed

    private void rotateColumnUpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateColumnUpActionPerformed
    	Object first = menuArray[menuHei-1][menuSelectedX];
		for(int y=menuHei-1; y>0; y--)
			menuArray[y][menuSelectedX] = menuArray[y-1][menuSelectedX];
		menuArray[0][menuSelectedX] = first;
		menuView.repaint();
		showSelected();
		changed = true;
		redisplayMenu = true;
    }//GEN-LAST:event_rotateColumnUpActionPerformed

    private void splitColumnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_splitColumnActionPerformed
		for(int y=0; y<menuHei; y++)
		{
			Object [] newRow = new Object[menuWid+1];
			int fill = 0;
			for(int x=0; x<menuWid; x++)
			{
				newRow[fill++] = menuArray[y][x];
				if (x == menuSelectedX) newRow[fill++] = null;
			}
			menuArray[y] = newRow;
		}
		int sourceFill = menuHei - ((menuHei+1) / 2);
		int destFill = menuHei-1;
		for(int y=sourceFill; y>=0; y--)
		{
			menuArray[destFill--][menuSelectedX+1] = menuArray[y][menuSelectedX];
			menuArray[y][menuSelectedX] = null;
		}
		menuWid++;
		menuView.repaint();
		showSelected();
		showMenuSize();
		changed = true;
		redisplayMenu = true;
    }//GEN-LAST:event_splitColumnActionPerformed

    private void swapColumnsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swapColumnsActionPerformed
    	if (menuSelectedX >= menuWid-1)
    	{
    		Job.getUserInterface().showErrorMessage("The current column is already the rightmost, so it cannot be swapped with the column to its right.",
    			"Cannot Swap Columns");
    		return;    	
    	}
		for(int y=0; y<menuHei; y++)
		{
			Object swap = menuArray[y][menuSelectedX];
			menuArray[y][menuSelectedX] = menuArray[y][menuSelectedX+1];
			menuArray[y][menuSelectedX+1] = swap;
		}
		menuView.repaint();
		showSelected();
		changed = true;
		redisplayMenu = true;
    }//GEN-LAST:event_swapColumnsActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel Top;
    private javax.swing.JButton addButton;
    private javax.swing.JButton addColumn;
    private javax.swing.JButton addRow;
    private javax.swing.JScrollPane arcListPane;
    private javax.swing.JScrollPane cellListPane;
    private javax.swing.JButton deleteColumn;
    private javax.swing.JButton deleteRow;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JComboBox libraryName;
    private javax.swing.JPanel lowerLeft;
    private javax.swing.JPanel lowerRight;
    private javax.swing.JLabel menuSize;
    private javax.swing.JTextField nodeAngle;
    private javax.swing.JLabel nodeAngleLabel;
    private javax.swing.JComboBox nodeFunction;
    private javax.swing.JLabel nodeFunctionLabel;
    private javax.swing.JScrollPane nodeListPane;
    private javax.swing.JTextField nodeName;
    private javax.swing.JLabel nodeNameLabel;
    private javax.swing.JScrollPane popupListPane;
    private javax.swing.JButton removeButton;
    private javax.swing.JButton rotateColumnDown;
    private javax.swing.JButton rotateColumnUp;
    private javax.swing.JLabel selectedMenuName;
    private javax.swing.JScrollPane specialListPane;
    private javax.swing.JButton splitColumn;
    private javax.swing.JButton swapColumns;
    private javax.swing.JLabel warningMessage;
    // End of variables declaration//GEN-END:variables

}
