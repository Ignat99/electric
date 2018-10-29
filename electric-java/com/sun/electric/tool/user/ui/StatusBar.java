/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: StatusBar.java
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
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNodeSize;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.memory.MemoryUsage;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.border.BevelBorder;

/**
 * This class manages the Electric status bar at the bottom of the edit window.
 */
public class StatusBar extends JPanel implements HighlightListener, DatabaseChangeListener {
    private WindowFrame frame;
    private String coords = null;
    private String hierCoords = null;
    private JLabel fieldSelected, fieldSize, fieldTech, fieldCoords, fieldHierCoords, memoryUsage;

    private static String selectionOverride = null;

    public StatusBar(WindowFrame frame) {
        super(new GridBagLayout());
        setBorder(new BevelBorder(BevelBorder.LOWERED));
        this.frame = frame;

        fieldSelected = new JLabel();
        fieldSelected.addMouseListener(new MouseAdapter()  
        {
        	public void mouseReleased(MouseEvent e) { doSelectionPopup(); }  
        });  
        addField(fieldSelected, 0, 0, 1, 0.0);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(new JSeparator(JSeparator.VERTICAL), gbc);

        fieldSize = new JLabel();
        addField(fieldSize, 2, 0, 1, 0.0);

        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(new JSeparator(JSeparator.VERTICAL), gbc);

        fieldTech = new JLabel();
        addField(fieldTech, 4, 0, 1, 0.0);

        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(new JSeparator(JSeparator.VERTICAL), gbc);

        fieldCoords = new JLabel();
        fieldCoords.setHorizontalAlignment(JLabel.RIGHT);
        addField(fieldCoords, 6, 0, 1, 0.35);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 7;
        add(new JSeparator(JSeparator.HORIZONTAL), gbc);

        fieldHierCoords = new JLabel(" ");
        fieldHierCoords.setHorizontalAlignment(JLabel.RIGHT);
        addField(fieldHierCoords, 1, 2, 6, 0.0);

        if (Job.getDebug()) {
//            gbc = new GridBagConstraints();
//            gbc.gridx = 0;
//            gbc.gridy = 1;
//            gbc.fill = GridBagConstraints.HORIZONTAL;
//            gbc.gridwidth = 7;
//            add(new JSeparator(JSeparator.HORIZONTAL), gbc);

            memoryUsage = new JLabel(" ");
            memoryUsage.setHorizontalAlignment(JLabel.LEFT);
            addField(memoryUsage, 0, 2, 1, 0.0);
        }

        // add myself as listener for highlight and database changes
        Highlighter.addHighlightListener(this);
        UserInterfaceMain.addDatabaseChangeListener(this);
    }

	private static class SavedSelection
	{
		String selectionName;
		List<Highlight> selection;
	}

	private static List<SavedSelection> allSelections = new ArrayList<SavedSelection>();

	/**
	 * Method to run when the user clicks on the selection status field
	 */
	private void doSelectionPopup()
	{
		JPopupMenu selections = new JPopupMenu("selections");
		for(SavedSelection ss : allSelections)
		{
			JMenuItem menuItem = new JMenuItem(ss.selectionName.trim());
			menuItem.addActionListener(new RestoreSelection(ss));
			selections.add(menuItem);
		}
		selections.add(new JSeparator());
		JMenuItem menuItem = new JMenuItem("Clear all Selections");
		Font oldFont = menuItem.getFont();
		Font newFont = new Font(oldFont.getFamily(), Font.BOLD, oldFont.getSize());
		menuItem.setFont(newFont);
		menuItem.addActionListener(new ClearAllSelections());
		selections.add(menuItem);

		menuItem = new JMenuItem("Save this Selection");
		menuItem.setFont(newFont);
		menuItem.addActionListener(new SaveSelection());
		selections.add(menuItem);
		
		selections.setVisible(true);
		selections.show(fieldSelected, 0, -selections.getHeight());
	}

	private class SaveSelection implements ActionListener
	{
		/**
		 * Respond to an action performed, in this case save the current selection.
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			EditWindow win = EditWindow.needCurrent();
			if (win == null) return;
			SavedSelection ss = new SavedSelection();
			ss.selectionName = fieldSelected.getText();
			ss.selection = win.getHighlighter().getHighlights();
			if (ss.selection.size() == 0)
			{
				System.out.println("Nothing is selected.");
				return;
			}
			allSelections.add(ss);
			System.out.println("Selection saved");
		}
	}

	private class ClearAllSelections implements ActionListener
	{
		/**
		 * Respond to an action performed, in this case clear all selections.
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			allSelections.clear();
			System.out.println("Selections cleared");
		}
	}

	private class RestoreSelection implements ActionListener
	{
		private SavedSelection ss;

		public RestoreSelection(SavedSelection ss) { this.ss = ss; }

		/**
		 * Respond to an action performed, in this case restore the chosen selection.
		 */
		@Override
		public void actionPerformed(ActionEvent e)
		{
			Cell cell = null;
			for(Highlight h : ss.selection)
			{
				cell = h.getCell();
				if (cell != null) break;
			}
			if (cell == null) return;
			EditWindow win = EditWindow.findWindow(cell);
			if (win != null) WindowFrame.showFrame(win.getWindowFrame()); else
			{
				WindowFrame wf = WindowFrame.createEditWindow(cell);
				win = (EditWindow)wf.getContent();
			}
			Highlighter highlighter = win.getHighlighter();
			highlighter.showHighlights(ss.selection);
			System.out.println("Restored selection: " + ss.selectionName);
		}
	}

	private void addField(JComponent field, int x, int y, int width, double weight) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.weightx = weight;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        int rightInsert = (ClientOS.isOSMac()) ? 20 : 4;
        gbc.insets = new Insets(0, 4, 0, rightInsert);
        add(field, gbc);
    }

    /**
     * Highlighter depends on MDI or SDI mode.
     * @return the highlighter to use. May be null if none.
     */
    private Highlighter getHighlighter() {
        if (TopLevel.isMDIMode()) {
            // get current internal frame highlighter
            EditWindow wnd = EditWindow.getCurrent();
            if (wnd == null)
                return null;
            return wnd.getHighlighter();
        }
        return frame.getContent().getHighlighter();
    }

    public static void setCoordinates(String coords, WindowFrame wf) {
        StatusBar sb = null;
        if (TopLevel.isMDIMode()) {
            sb = TopLevel.getCurrentJFrame().getStatusBar();
        } else {
            sb = wf.getFrame().getStatusBar();
        }
        sb.coords = coords;
        sb.redoStatusBar();
    }

    public static void setHierarchicalCoordinates(String hierCoords, WindowFrame wf) {
        StatusBar sb = null;
        if (TopLevel.isMDIMode()) {
            sb = TopLevel.getCurrentJFrame().getStatusBar();
        } else {
            sb = wf.getFrame().getStatusBar();
        }
        sb.hierCoords = hierCoords;
        sb.redoStatusBar();
    }

    public static void setSelectionOverride(String ov) {
        selectionOverride = ov;
        updateStatusBar();
    }

    @Override
    public void highlightChanged(Highlighter which) {
        updateSelectedText();
    }

    /**
     * Called when by a Highlighter when it loses focus. The argument is the
     * Highlighter that has gained focus (may be null).
     * @param highlighterGainedFocus the highlighter for the current window (may be null).
     */
    @Override
    public void highlighterLostFocus(Highlighter highlighterGainedFocus) {
    }

    /**
     * Method to update the status bar from current values. Call this when any
     * of those values change.
     */
    public static void updateStatusBar() {
        if (TopLevel.isMDIMode()) {
            StatusBar sb = TopLevel.getCurrentJFrame().getStatusBar();
            sb.redoStatusBar();
        } else {
            for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();) {
                WindowFrame wf = it.next();
                StatusBar sb = wf.getFrame().getStatusBar();
                if (sb != null)
                    sb.redoStatusBar();
            }
        }
    }

    private void updateUsedMemory() {
    	memoryUsage.setText("  " + MemoryUsage.getInstance().toString());
//    	if (fieldHierCoords.getText().trim().length() > 0) memoryUsage.setText(""); else
//    		memoryUsage.setText(MemoryUsage.getInstance().toString());
    }

    private void redoStatusBar() {
        updateSelectedText();
        if (Job.getDebug()) {
            updateUsedMemory();
        }

        Cell cell = null;
        WindowFrame thisFrame = frame;
        if (thisFrame == null) {
            thisFrame = WindowFrame.getCurrentWindowFrame(false);
            if (thisFrame != null)
                cell = thisFrame.getContent().getCell();
        } else {
            Cell cellInPanel = thisFrame.getContent().getCell();
            if (cellInPanel != null)
                cell = cellInPanel;
        }
        String sizeMsg = "";
        if (cell != null) {
            if (thisFrame.getContent() instanceof TextWindow) {
                TextWindow tw = (TextWindow) thisFrame.getContent();
                int len = tw.getLineCount();
                sizeMsg = "LINES: " + len;
            } else {
                Rectangle2D bounds = cell.getBounds();
                sizeMsg = "SIZE: " + TextUtils.formatDistance(bounds.getWidth(), cell.getTechnology())
                        + " x " + TextUtils.formatDistance(bounds.getHeight(), cell.getTechnology());
            }
        }
        fieldSize.setText(sizeMsg = "  " + sizeMsg + "  ");
        fieldSize.setToolTipText(sizeMsg);

        Technology tech = (cell != null) ? cell.getTechnology() : Technology.getCurrent();
        if (tech != null) {
            String message = "TECH: " + tech.getTechName();
            String foundry = tech.getPrefFoundry();

            boolean validFoundry = !foundry.equals("");
            if (tech.isScaleRelevant()) {
                message += " (scale=" + tech.getScale() + "nm";
                if (!validFoundry)
                    message += ")";
                else
                    // relevant foundry
                    message += ",foundry=" + foundry + ")";
            }
            fieldTech.setText("  " + message + "  ");
            fieldTech.setToolTipText(message);
        }

        if (coords == null)
            fieldCoords.setText("");
        else {
            fieldCoords.setText(coords);
            fieldCoords.setToolTipText(coords);
        }

        // if too many chars to display in space provided, truncate.
        if (hierCoords != null) {
            int width = fieldHierCoords.getFontMetrics(fieldHierCoords.getFont()).stringWidth(hierCoords);
            int widgetW = fieldHierCoords.getWidth();
            if (width > widgetW) {
                int chars = hierCoords.length() * widgetW / width;
                hierCoords = hierCoords.substring(hierCoords.length() - chars, hierCoords.length());
            }
            fieldHierCoords.setText(hierCoords);
        } else
            fieldHierCoords.setText(" ");
    }

    private void updateSelectedText() {
        String selectedMsg = "NOTHING SELECTED";
        if (selectionOverride != null) {
            selectedMsg = selectionOverride;
        } else {
            // count the number of nodes and arcs selected
            int nodeCount = 0, arcCount = 0, textCount = 0;
            Highlight lastHighlight = null;
            Highlighter highlighter = getHighlighter();
            if (highlighter == null) {
                fieldSelected.setText("  " + selectedMsg + "  ");
                fieldSelected.setToolTipText(selectedMsg);
                return;
            }
            NodeInst theNode = null;
            for (Highlight h : highlighter.getHighlights()) {
                if (!h.isValid())
                    continue;
                if (h.isHighlightEOBJ()) {
                    ElectricObject eObj = h.getElectricObject();
                    if (eObj instanceof PortInst) {
                        lastHighlight = h;
                        theNode = ((PortInst) eObj).getNodeInst();
                        nodeCount++;
                    } else if (eObj instanceof NodeInst) {
                        lastHighlight = h;
                        theNode = (NodeInst) eObj;
                        nodeCount++;
                    } else if (eObj instanceof ArcInst) {
                        lastHighlight = h;
                        arcCount++;
                    }
                } else if (h.isHighlightText()) {
                    lastHighlight = h;
                    textCount++;
                }
            }
            if (nodeCount + arcCount + textCount == 1) {
                selectedMsg = "SELECTED " + getSelectedText(lastHighlight);
                if (theNode != null) {
                    PrimitiveNodeSize npSize = theNode.getPrimitiveDependentNodeSize(null);
                    if (npSize != null) {
                        selectedMsg += " (size=";
                        selectedMsg += npSize.getWidthInString();
                        selectedMsg += "x";
                        selectedMsg += npSize.getLengthInString();
                        selectedMsg += ")";
                    } else {
                        double xSize = theNode.getLambdaBaseXSize();
                        double ySize = theNode.getLambdaBaseYSize();
                        selectedMsg += " (size="
                                + TextUtils.formatDistance(xSize, theNode.getProto().getTechnology()) + " x "
                                + TextUtils.formatDistance(ySize, theNode.getProto().getTechnology()) + ")";
                    }
                }
            } else {
                if (nodeCount + arcCount + textCount > 0) {
                    StringBuffer buf = new StringBuffer();
                    buf.append("SELECTED:");
                    if (nodeCount > 0)
                        buf.append(" " + nodeCount + " NODES");
                    if (arcCount > 0) {
                        if (nodeCount > 0)
                            buf.append(",");
                        buf.append(" " + arcCount + " ARCS");
                    }
                    if (textCount > 0) {
                        if (nodeCount + arcCount > 0)
                            buf.append(",");
                        buf.append(" " + textCount + " TEXT");
                    }
                    // add on info for last highlight
                    buf.append(". LAST: " + getSelectedText(lastHighlight));
                    selectedMsg = buf.toString();
                }
            }
        }
        fieldSelected.setText("  " + selectedMsg + "  ");
        fieldSelected.setToolTipText(selectedMsg);
    }

    private String addLayerInfo(PortProto pp) {
        // if the port is on a generic primitive which can connect to everything, say so
        PrimitivePort pRp = pp.getBasePort();
        if (pRp.getParent().getTechnology().isUniversalConnectivityPort(pRp))
            return " [ALL]";

        String descr = "";
        ArcProto[] cons = pp.getBasePort().getConnections();
        boolean first = true;
        for (int i = 0; i < cons.length; i++) {
            ArcProto ap = cons[i];
            if (ap.getTechnology() == Generic.tech())
                continue;
            if (first)
                descr += " [";
            else
                descr += ",";
            first = false;
            descr += cons[i].getName();
        }
        if (!first)
            descr += "]";
        return descr;
    }

    /**
     * Get a String describing the Highlight, to display in the "Selected" part
     * of the status bar.
     */
    private String getSelectedText(Highlight h) {
        assert h.isValid();
        PortInst thePort;
        NodeInst theNode;
        ArcInst theArc;
        if (h.isHighlightEOBJ()) {
            ElectricObject eObj = h.getElectricObject();
            if (eObj instanceof PortInst) {
                thePort = (PortInst) eObj;
                theNode = thePort.getNodeInst();
                String desc = (theNode.isCellInstance()) ? addLayerInfo(thePort.getPortProto()) : "";

                return "NODE: " + theNode.describe(true) + " PORT: \'" + thePort.getPortProto().getName()
                        + "\'" + desc;
            } else if (eObj instanceof NodeInst) {
                theNode = (NodeInst) eObj;
                Technology tech = theNode.getProto().getTechnology();
                String extra = "";
                if (Technology.getCurrent() != tech) {
                	extra += tech.getTechName() + ":";
                }
                return ("NODE: " + extra + theNode.describe(true));
            } else if (eObj instanceof ArcInst) {
                theArc = (ArcInst) eObj;
                Netlist netlist = theArc.getParent().getNetlist();
                if (netlist == null)
                    return ("netlist exception! try again");
                if (!theArc.isLinked())
                    return ("netlist exception! try again. ArcIndex = -1");
                Network net = netlist.getNetwork(theArc, 0);
                String netMsg = (net != null) ? "NETWORK: " + net.describe(true) + ", " : "";
                return netMsg + "ARC: " + theArc.describe(true);
            }
        } else if (h.isHighlightText()) {
            String descr = "TEXT: " + h.describe();
            if (h.getVarKey() == Export.EXPORT_NAME && h.getElectricObject() instanceof Export) {
                Export e = (Export) h.getElectricObject();
                descr += addLayerInfo(e.getOriginalPort().getPortProto());
            }
            return descr;
        }
        return null;
    }

    /**
     * Call when done with this Object. Cleans up references to this object.
     */
    public void finished() {
        if (!TopLevel.isMDIMode() && frame.getContent().getHighlighter() != null)
            Highlighter.removeHighlightListener(this);
        UserInterfaceMain.removeDatabaseChangeListener(this);
    }

    @Override
    public void databaseChanged(DatabaseChangeEvent e) {
        redoStatusBar();
    }
}
