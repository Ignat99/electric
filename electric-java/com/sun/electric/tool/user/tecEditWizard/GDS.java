/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDS.java
 *
 * Copyright (c) 2008, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.tecEditWizard;

import java.awt.*;

import javax.swing.*;

import com.sun.electric.technology.GDSLayers.GDSLayerType;

/**
 * Class to handle the "GDS" tab of the Numeric Technology Editor dialog.
 */
public class GDS extends TechEditWizardPanel
{
    private JPanel gds;
    private JScrollPane gdsPane;
    private LabelContainer[] metalContainers, viaContainers;
    private LabelContainer[] basicContainers;

    private class LabelContainer
    {
        JLabel label;
        JTextField valueField;
        JTextField pinField;
        JTextField textField;
        JTextField hVoltField; // high voltage
    }

    /** Creates new form GDS */
	public GDS(TechEditWizard parent, boolean modal)
	{
		super(parent, modal);

        setTitle("GDS");
        setName("");

        gds = new JPanel();
        gds.setLayout(new GridBagLayout());

        gdsPane = new javax.swing.JScrollPane();

        // Head
        JLabel heading = new JLabel("Name");
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;   gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        gds.add(heading, gbc);

        heading = new JLabel("DRAWING");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;   gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        gds.add(heading, gbc);

        heading = new JLabel("Pin");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;   gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        gds.add(heading, gbc);

        heading = new JLabel("Text");
        gbc = new GridBagConstraints();
        gbc.gridx = 5;   gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        gds.add(heading, gbc);
        
        heading = new JLabel("H. Voltage");
        gbc = new GridBagConstraints();
        gbc.gridx = 7;   gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        gds.add(heading, gbc);

        gdsPane.setViewportView(gds);

        getContentPane().setLayout(new java.awt.GridBagLayout());
        getContentPane().add(gdsPane, new java.awt.GridBagConstraints());

        TechEditWizardData data = wizard.getTechEditData();
        TechEditWizardData.LayerInfo[] basics = data.getBasicLayers();
        basicContainers = new LabelContainer[basics.length];

        for (int i = 0; i < basics.length; i++)
        {
            basicContainers[i] = addRow(basics[i], i + 1, 4);
        }
	}

    private void setValues(LabelContainer con, TechEditWizardData.LayerInfo info)
    {
        con.valueField.setText(info.getString(GDSLayerType.DRAWING));
    }

    /** return the panel to use for this Numeric Technology Editor tab. */
	public Component getComponent() { return gdsPane; }

	/** return the name of this Numeric Technology Editor tab. */
	public String getName() { return "GDS"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the GDS tab.
	 */
	public void init()
	{
		TechEditWizardData data = wizard.getTechEditData();
        TechEditWizardData.LayerInfo[] basics = data.getBasicLayers();

        for (int i = 0; i < basicContainers.length; i++)
        {
            setValues(basicContainers[i], basics[i]);
        }

		// remove former metal data
		if (metalContainers != null)
            for (LabelContainer mc : metalContainers)
            {
                gds.remove(mc.label);
                gds.remove(mc.valueField);
                gds.remove(mc.textField);
                gds.remove(mc.hVoltField);
            }

        if (viaContainers != null)
            for (LabelContainer vc : viaContainers)
            {
                gds.remove(vc.label);
                gds.remove(vc.valueField);
            }

        // add appropriate number of metal layers
		int numMetals = data.getNumMetalLayers();
		metalContainers = new LabelContainer[numMetals];
		viaContainers = new LabelContainer[numMetals-1];
        int base = basics.length + 1;
        for(int i=0; i<numMetals; i++)
    	{
            metalContainers[i] = addRow(data.getGDSMetal()[i], base+i*2, 4);

            if (i < numMetals-1)
            {
                viaContainers[i] = addRow(data.getGDSVia()[i], base+1+i*2, 10);
            }
    	}
        this.pack();
    }

    private LabelContainer addRow(TechEditWizardData.LayerInfo gdsValue, int posY, int left)
    {
        LabelContainer cont = new LabelContainer();

        cont.label = new JLabel(gdsValue.name);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;   gbc.gridy = posY;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, left, 1, 0);
        gds.add(cont.label, gbc);

        cont.valueField = new JTextField();
        cont.valueField.setText(gdsValue.getString(GDSLayerType.DRAWING));
        cont.valueField.setColumns(4);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;   gbc.gridy = posY;
        gbc.insets = new Insets(4, 0, 1, 2);
        gds.add(cont.valueField, gbc);

        cont.pinField = new JTextField();
        cont.pinField.setText(gdsValue.getString(GDSLayerType.PIN));
        cont.pinField.setColumns(4);
        gbc = new GridBagConstraints();
        gbc.gridx = 3;   gbc.gridy = posY;
        gbc.insets = new Insets(4, 0, 1, 2);
        gds.add(cont.pinField, gbc);

        cont.textField = new JTextField();
        cont.textField.setText(gdsValue.getString(GDSLayerType.TEXT));
        cont.textField.setColumns(4);
        gbc = new GridBagConstraints();
        gbc.gridx = 5;   gbc.gridy = posY;
        gbc.insets = new Insets(4, 0, 1, 2);
        gds.add(cont.textField, gbc);
        
        cont.hVoltField = new JTextField();
        cont.hVoltField.setText(gdsValue.getString(GDSLayerType.HIGHVOLTAGE));
        cont.hVoltField.setColumns(4);
        gbc = new GridBagConstraints();
        gbc.gridx = 7;   gbc.gridy = posY;
        gbc.insets = new Insets(4, 0, 1, 2);
        gds.add(cont.hVoltField, gbc);
        return cont;
    }

    private void setData(TechEditWizardData.LayerInfo info, LabelContainer cont)
    {
    	String s = cont.valueField.getText();
    	if (!cont.pinField.getText().isEmpty())
    		s += "," + cont.pinField.getText() + "p";
    	if (!cont.textField.getText().isEmpty())
    		s += "," + cont.textField.getText() + "t";
    	if (!cont.hVoltField.getText().isEmpty())
    		s += "," + cont.hVoltField.getText() + "h";
    	
        info.setGDSData(s);
    }

    /**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the GDS tab.
	 */
	public void term()
	{
		TechEditWizardData data = wizard.getTechEditData();
        TechEditWizardData.LayerInfo[] metalLayers = data.getGDSMetal();
        TechEditWizardData.LayerInfo[] viaLayers = data.getGDSVia();

        for(int i=0; i<metalLayers.length; i++)
        {
            setData(metalLayers[i], metalContainers[i]);
        	if (i < metalLayers.length-1)
                setData(viaLayers[i], viaContainers[i]);
        }

        TechEditWizardData.LayerInfo[] basics = data.getBasicLayers();
        for (int i = 0; i < basicContainers.length; i++)
        {
            setData(basics[i], basicContainers[i]);
        }
	}
}
