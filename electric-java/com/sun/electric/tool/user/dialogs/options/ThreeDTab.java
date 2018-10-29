/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ThreeDTab.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.dialogs.options;

import com.sun.electric.database.text.Setting;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.Layer;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.util.math.MutableDouble;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "3D" tab of the Preferences dialog.
 */
public class ThreeDTab extends PreferencePanel
{
    /** Creates new form ThreeDTab depending on if 3Dplugin is on or not */
    public static ThreeDTab create3DTab(PreferencesFrame parent, boolean modal)
    {
        ThreeDTab tab = null;

        Class<?> plugin = Resources.get3DClass("ui.JThreeDTab");
        if (plugin != null)
        {
            try
            {
                Constructor instance = plugin.getDeclaredConstructor(new Class[]{PreferencesFrame.class, Boolean.TYPE});
                Object panel = instance.newInstance(new Object[] {parent, new Boolean(modal)});
                tab = (ThreeDTab)panel;
            }
            catch (Exception e)
            {
                System.out.println("Cannot create instance of 3D plugin JThreeDTab: " + e.getMessage());
            }
        }
        else
            tab = new ThreeDTab(parent, modal);
        return tab;
    }

	/** Creates new form ThreeDTab */
	public ThreeDTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();
	}

	/** return the panel to use for user preferences. */
    @Override
	public JPanel getUserPreferencesPanel() { return threeD; }

	/** return the name of this preferences tab. */
    @Override
	public String getName() { return "3D"; }

	private boolean initial3DTextChanging = false;
	private JList threeDLayerList;
	private DefaultListModel threeDLayerModel;
	protected Map<Layer, MutableDouble> threeDThicknessMap, threeDDistanceMap;
	private JPanel threeDSideView;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the 3D tab.
	 */
    @Override
	public void init()
	{
		threeDTechnology.setText("Layer cross section for technology '" + curTech.getTechName() + "'");
		threeDLayerModel = new DefaultListModel();
		threeDLayerList = new JList(threeDLayerModel);
		threeDLayerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		threeDLayerPane.setViewportView(threeDLayerList);
		threeDLayerList.clearSelection();
		threeDLayerList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { threeDValuesChanged(false); }
		});
//		threeDLayerList.addMouseListener(new MouseAdapter()
//		{
//			public void mouseClicked(MouseEvent evt) { threeDValuesChanged(false); }
//		});
		threeDThicknessMap = new HashMap<Layer, MutableDouble>();
		threeDDistanceMap = new HashMap<Layer, MutableDouble>();
        // Sorted by Height to be consistent with LayersTab
		for(Layer layer : curTech.getLayersSortedByUserPreference())
		{
			threeDLayerModel.addElement(layer.getName());
            double thickness = getDouble(layer.getThicknessSetting());
            double distance = getDouble(layer.getDistanceSetting());
			threeDThicknessMap.put(layer, new MutableDouble(thickness));
			threeDDistanceMap.put(layer, new MutableDouble(distance));
		}
		threeDLayerList.setSelectedIndex(0);
		threeDHeight.getDocument().addDocumentListener(new ThreeDInfoDocumentListener(this));
		threeDThickness.getDocument().addDocumentListener(new ThreeDInfoDocumentListener(this));

		threeDSideView = new ThreeDSideView(this);
		threeDSideView.setMinimumSize(new java.awt.Dimension(200, 450));
		threeDSideView.setPreferredSize(new java.awt.Dimension(200, 450));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 2;       gbc.gridy = 1;
		gbc.gridwidth = 2;   gbc.gridheight = 4;
		gbc.weightx = 0.5;   gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new java.awt.Insets(4, 4, 4, 4);
		threeD.add(threeDSideView, gbc);
		threeDValuesChanged(false);
	}

    /**
     * Method called when other preferences tab changed the layers sorting algorithm
     */
    @Override
    public void refresh()
    {
    	if (threeDLayerModel == null) return; // not created yet
    	threeDLayerModel.clear();
    	// re-do the layers model based on new sorting algorithm
		for(Layer layer : curTech.getLayersSortedByUserPreference())
		{
			threeDLayerModel.addElement(layer.getName());
		}
    }
    
	private class ThreeDSideView extends JPanel
		implements MouseMotionListener, MouseListener
	{
		ThreeDTab dialog;
		double lowHeight = Double.MAX_VALUE, highHeight = Double.MIN_VALUE;

		ThreeDSideView(ThreeDTab dialog)
		{
			this.dialog = dialog;
			addMouseListener(this);
			addMouseMotionListener(this);

            LayerVisibility lv = LayerVisibility.getLayerVisibility();
			for(Iterator<Layer> it = dialog.curTech.getLayers(); it.hasNext(); )
			{
				Layer layer = it.next();
				if (!lv.isVisible(layer)) continue;
				MutableDouble thickness = dialog.threeDThicknessMap.get(layer);
				MutableDouble distance = dialog.threeDDistanceMap.get(layer);
				double dis = distance.doubleValue();
				double thick = thickness.doubleValue() / 2;
				double valLow = dis - thick;
				double valHig = dis + thick;

				if (valLow < lowHeight)
					lowHeight = valLow;
				if (valHig > highHeight)
					highHeight = valHig;
			}
			lowHeight -= 4;
			highHeight += 4;
		}

		/**
		 * Method to repaint this ThreeDSideView.
		 */
        @Override
		public void paint(Graphics g)
		{
			Dimension dim = getSize();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, dim.width, dim.height);
			g.setColor(Color.BLACK);
			g.drawLine(0, 0, 0, dim.height-1);
			g.drawLine(0, dim.height-1, dim.width-1, dim.height-1);
			g.drawLine(dim.width-1, dim.height-1, dim.width-1, 0);
			g.drawLine(dim.width-1, 0, 0, 0);

			String layerName = (String)dialog.threeDLayerList.getSelectedValue();
			Layer selectedLayer = dialog.curTech.findLayer(layerName);
			for(Iterator<Layer> it = dialog.curTech.getLayers(); it.hasNext(); )
			{
				Layer layer = it.next();
				//if (!layer.isVisible()) continue;
				if (layer == selectedLayer) g.setColor(Color.RED); else
					g.setColor(Color.BLACK);
				MutableDouble thickness = dialog.threeDThicknessMap.get(layer);
				MutableDouble distance = dialog.threeDDistanceMap.get(layer);
				double dis = distance.doubleValue() + thickness.doubleValue()/2;
				int yValue = dim.height - (int)((dis - lowHeight) / (highHeight - lowHeight) * dim.height + 0.5);
				int yHeight = (int)(thickness.doubleValue() / (highHeight - lowHeight) * dim.height + 0.5);
				if (yHeight == 0)
				{
					g.drawLine(0, yValue, dim.width/3, yValue);
				} else
				{
					//yHeight -= 4;
					int firstPart = dim.width / 6;
					int pointPos = dim.width / 4;
					g.drawLine(0, yValue-yHeight/2, firstPart, yValue-yHeight/2);
					g.drawLine(0, yValue+yHeight/2, firstPart, yValue+yHeight/2);
					g.drawLine(firstPart, yValue-yHeight/2, pointPos, yValue);
					g.drawLine(firstPart, yValue+yHeight/2, pointPos, yValue);
					g.drawLine(pointPos, yValue, dim.width/3, yValue);
				}
				String string = layer.getName();
				Font font = new Font(User.getDefaultFont(), Font.PLAIN, 9);
				g.setFont(font);
				FontRenderContext frc = new FontRenderContext(null, true, true);
				GlyphVector gv = font.createGlyphVector(frc, string);
				LineMetrics lm = font.getLineMetrics(string, frc);
				double txtHeight = lm.getHeight();
				Graphics2D g2 = (Graphics2D)g;
				g2.drawGlyphVector(gv, dim.width/3 + 1, (float)(yValue + txtHeight/2) - lm.getDescent());
			}
		}

		// the MouseListener events
		public void mousePressed(MouseEvent evt)
		{
			Dimension dim = getSize();
			String layerName = (String)dialog.threeDLayerList.getSelectedValue();
			Layer selectedLayer = dialog.curTech.findLayer(layerName);
			MutableDouble height = dialog.threeDDistanceMap.get(selectedLayer);
			int yValue = dim.height - (int)((height.doubleValue() - lowHeight) / (highHeight - lowHeight) * dim.height + 0.5);
			if (Math.abs(yValue - evt.getY()) > 5)
			{
				int bestDist = dim.height;
				for(Iterator<Layer> it = dialog.curTech.getLayers(); it.hasNext(); )
				{
					Layer layer = it.next();
					height = dialog.threeDDistanceMap.get(layer);
					yValue = dim.height - (int)((height.doubleValue() - lowHeight) / (highHeight - lowHeight) * dim.height + 0.5);
					int dist = Math.abs(yValue - evt.getY());
					if (dist < bestDist)
					{
						bestDist = dist;
						selectedLayer = layer;
					}
				}
				dialog.threeDLayerList.setSelectedValue(selectedLayer.getName(), true);
				dialog.threeDValuesChanged(false);
			}
		}
		public void mouseReleased(MouseEvent evt) {}
		public void mouseClicked(MouseEvent evt) {}
		public void mouseEntered(MouseEvent evt) {}
		public void mouseExited(MouseEvent evt) {}

		// the MouseMotionListener events
		public void mouseMoved(MouseEvent evt) {}
		public void mouseDragged(MouseEvent evt)
		{
			Dimension dim = getSize();
			String layerName = (String)dialog.threeDLayerList.getSelectedValue();
			Layer layer = dialog.curTech.findLayer(layerName);
			MutableDouble height = threeDDistanceMap.get(layer);
			double newHeight = (double)(dim.height - evt.getY()) / dim.height * (highHeight - lowHeight) + lowHeight;
			if (height.doubleValue() != newHeight)
			{
				height.setValue(newHeight);
				dialog.threeDHeight.setText(TextUtils.formatDistance(newHeight));
				repaint();
			}
		}
	}

	/**
	 * Class to handle changes to the thickness or height.
	 */
	private static class ThreeDInfoDocumentListener implements DocumentListener
	{
		ThreeDTab dialog;

		ThreeDInfoDocumentListener(ThreeDTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.threeDValuesChanged(true); }
		public void insertUpdate(DocumentEvent e) { dialog.threeDValuesChanged(true); }
		public void removeUpdate(DocumentEvent e) { dialog.threeDValuesChanged(true); }
	}

	private void threeDValuesChanged(boolean set)
	{
		if (threeDLayerList == null)
			return; // dialog not ready yet
        if (!set)
           initial3DTextChanging = true;
        else if (initial3DTextChanging) return;
		String layerName = (String)threeDLayerList.getSelectedValue();
		Layer layer = curTech.findLayer(layerName);
		if (layer == null) return;
		MutableDouble thickness = threeDThicknessMap.get(layer);
		MutableDouble height = threeDDistanceMap.get(layer);
        if (set)
        {
            thickness.setValue(TextUtils.atofDistance(threeDThickness.getText()));
            height.setValue(TextUtils.atofDistance(threeDHeight.getText()));
        }
        else
        {
            threeDHeight.setText(TextUtils.formatDistance(height.doubleValue()));
            threeDThickness.setText(TextUtils.formatDistance(thickness.doubleValue()));
        }
        if (!set) initial3DTextChanging = false;
        if (threeDSideView != null)
        	threeDSideView.repaint();
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the 3D tab.
	 */
    @Override
	public void term()
	{
		for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
			MutableDouble thickness = threeDThicknessMap.get(layer);
			MutableDouble height = threeDDistanceMap.get(layer);
            setDouble(layer.getThicknessSetting(), thickness.doubleValue());
            setDouble(layer.getDistanceSetting(), height.doubleValue());
		}
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
		for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
            Setting thicknessSetting = layer.getThicknessSetting();
            Setting distanceSetting = layer.getDistanceSetting();
            setDouble(thicknessSetting, thicknessSetting.getDoubleFactoryValue());
            setDouble(distanceSetting, distanceSetting.getDoubleFactoryValue());
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        threeD = new javax.swing.JPanel();
        threeDTechnology = new javax.swing.JLabel();
        threeDLayerPane = new javax.swing.JScrollPane();
        jLabel45 = new javax.swing.JLabel();
        jLabel47 = new javax.swing.JLabel();
        threeDThickness = new javax.swing.JTextField();
        threeDHeight = new javax.swing.JTextField();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Edit Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        threeD.setLayout(new java.awt.GridBagLayout());

        threeDTechnology.setText("Layer cross section for technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(threeDTechnology, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(threeDLayerPane, gridBagConstraints);

        jLabel45.setText("Thickness:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(jLabel45, gridBagConstraints);

        jLabel47.setText("Distance:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(jLabel47, gridBagConstraints);

        threeDThickness.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(threeDThickness, gridBagConstraints);

        threeDHeight.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        threeD.add(threeDHeight, gridBagConstraints);

        getContentPane().add(threeD, new java.awt.GridBagConstraints());

        pack();
    }//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel45;
    private javax.swing.JLabel jLabel47;
    private javax.swing.JPanel threeD;
    private javax.swing.JTextField threeDHeight;
    private javax.swing.JScrollPane threeDLayerPane;
    private javax.swing.JLabel threeDTechnology;
    private javax.swing.JTextField threeDThickness;
    // End of variables declaration//GEN-END:variables

}
