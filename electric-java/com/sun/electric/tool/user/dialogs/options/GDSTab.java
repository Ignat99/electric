/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDSTab.java
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
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.GDSLayers;
import com.sun.electric.technology.GDSLayers.GDSLayerType;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.TextUtils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.Iterator;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "GDS" tab of the Preferences dialog.
 */
public class GDSTab extends PreferencePanel
{
	private JList gdsLayersList;
	private DefaultListModel gdsLayersModel;
	private boolean changingGDS = false;
    private Setting gdsOutMergesBoxesSetting = IOTool.getGDSOutMergesBoxesSetting();
    private Setting gdsOutWritesExportPinsSetting = IOTool.getGDSOutWritesExportPinsSetting();
    private Setting gdsOutUpperCaseSetting = IOTool.getGDSOutUpperCaseSetting();
    private Setting gdsOutDefaultTextLayerSetting = IOTool.getGDSDefaultTextLayerSetting();
    private Setting gdsOutputConvertsBracketsInExportsSetting = IOTool.getGDSOutputConvertsBracketsInExportsSetting();
    private Setting gdsOutCollapseVddGndPinNamesSetting = IOTool.getGDSOutColapseVddGndPinNamesSetting();
    private Setting gdsOutWriteExportCharacteristicsSetting = IOTool.getGDSOutWriteExportCharacteristicsSetting();
    private Setting gdsCellNameLenMaxSetting = IOTool.getGDSCellNameLenMaxSetting();
    private Setting gdsInputScaleSetting = IOTool.getGDSInputScaleSetting();
    private Setting gdsOutputPrecisionSetting = IOTool.getGDSOutputPrecisionSetting();
    private Setting gdsOutputUnitsPerMeterSetting = IOTool.getGDSOutputUnitsPerMeterSetting();

    /** Creates new form GDSTab */
	public GDSTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(gdsLayerNumber);
	    EDialog.makeTextFieldSelectAllOnTab(gdsLayerType);
	    EDialog.makeTextFieldSelectAllOnTab(gdsPinLayer);
	    EDialog.makeTextFieldSelectAllOnTab(gdsPinType);
	    EDialog.makeTextFieldSelectAllOnTab(gdsTextLayer);
	    EDialog.makeTextFieldSelectAllOnTab(gdsTextType);
	    EDialog.makeTextFieldSelectAllOnTab(gdsHighVLayer);
	    EDialog.makeTextFieldSelectAllOnTab(gdsHighVType);
	    EDialog.makeTextFieldSelectAllOnTab(gdsCellNameLenMax);
	    EDialog.makeTextFieldSelectAllOnTab(gdsDefaultTextLayer);
	    EDialog.makeTextFieldSelectAllOnTab(gdsInputScale);
	    EDialog.makeTextFieldSelectAllOnTab(gdsOutputPrecision);
	    EDialog.makeTextFieldSelectAllOnTab(gdsOutputUnitsPerMeter);
	}

	/** return the JPanel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return preferences; }

	/** return the JPanel to use for the project preferences. */
	public JPanel getProjectPreferencesPanel() { return projectSettings; }

	/** return the name of this preferences tab. */
	public String getName() { return "GDS"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the GDS tab.
	 */
	public void init()
	{
		// user preferences
		gdsInputMergesBoxes.setSelected(IOTool.isGDSInMergesBoxes());
		gdsInputExpandsCells.setSelected(IOTool.isGDSInExpandsCells());
        gdsSimplifyCells.setSelected(IOTool.isGDSInSimplifyCells());
		gdsInputInstantiatesArrays.setSelected(IOTool.isGDSInInstantiatesArrays());
        gdsArraySimplification.addItem("None");
        gdsArraySimplification.addItem("Merge individual arrays");
        gdsArraySimplification.addItem("Merge all arrays");
        gdsArraySimplification.setSelectedIndex(IOTool.getGDSArraySimplification());
        gdsUnknownLayers.addItem("Ignore");
        gdsUnknownLayers.addItem("Convert to DRC Exclusion layer");
        gdsUnknownLayers.addItem("Convert to random layer");
        gdsUnknownLayers.setSelectedIndex(IOTool.getGDSInUnknownLayerHandling());
        gdsCadenceCompatibility.setSelected(IOTool.isGDSCadenceCompatibility());
        gdsDumpText.setSelected(IOTool.isGDSDumpReadable());
        gdsExportAllCells.setSelected(IOTool.isGDSWritesEntireLibrary());
        gdsExportFlatDesign.setSelected(IOTool.isGDSFlatDesign());
        gdsConvertNCCExportsConnectedByParentPins.setSelected(IOTool.getGDSConvertNCCExportsConnectedByParentPins());
        gdsVisibility.setSelected(IOTool.isGDSOnlyInvisibleLayers());
		gdsIncludesText.setSelected(IOTool.isGDSIncludesText());

        // project preferences
		gdsOutputMergesBoxes.setSelected(getBoolean(gdsOutMergesBoxesSetting));
		gdsOutputWritesExportPins.setSelected(getBoolean(gdsOutWritesExportPinsSetting));
		gdsOutputUpperCase.setSelected(getBoolean(gdsOutUpperCaseSetting));
        gdsOutputColapseNames.setSelected(getBoolean(gdsOutCollapseVddGndPinNamesSetting));
        gdsOutputWritesCharacteristics.setSelected(getBoolean(gdsOutWriteExportCharacteristicsSetting));
		gdsDefaultTextLayer.setText(Integer.toString(getInt(gdsOutDefaultTextLayerSetting)));
        gdsOutputConvertsBracketsInExports.setSelected(getBoolean(gdsOutputConvertsBracketsInExportsSetting));
        gdsCellNameLenMax.setText(Integer.toString(getInt(gdsCellNameLenMaxSetting)));
        gdsInputScale.setText(TextUtils.formatDouble(getDouble(gdsInputScaleSetting), 0));
        gdsOutputPrecision.setText(TextUtils.formatDouble(IOTool.getGDSOutputPrecision()));
        DecimalFormat myFormatter = new DecimalFormat("#,###");
        String output = myFormatter.format(IOTool.getGDSOutputUnitsPerMeter());
        gdsOutputUnitsPerMeter.setText(output);

        // build the layers list
		gdsLayersModel = new DefaultListModel();
		gdsLayersList = new JList(gdsLayersModel);
		gdsLayersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		gdsLayerList.setViewportView(gdsLayersList);
		gdsLayersList.clearSelection();
		gdsLayersList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { gdsClickLayer(); }
		});
		TechGDSTab currentTech = null;
		
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology tech = it.next();
			TechGDSTab t = new TechGDSTab(tech);
			if (tech == Technology.getCurrent())
			{
				assert (currentTech == null);
				currentTech = t;
			}
			technologySelection.addItem(t);
		}
		technologySelection.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { techChanged(); }
		});
		technologySelection.setSelectedItem(currentTech);

        foundrySelection.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { foundryChanged(); }
		});


        // to set foundry the first time
        techChanged();

		GDSDocumentListener myDocumentListener = new GDSDocumentListener(this);
		gdsLayerNumber.getDocument().addDocumentListener(myDocumentListener);
		gdsLayerType.getDocument().addDocumentListener(myDocumentListener);
		gdsPinLayer.getDocument().addDocumentListener(myDocumentListener);
		gdsPinType.getDocument().addDocumentListener(myDocumentListener);
		gdsTextLayer.getDocument().addDocumentListener(myDocumentListener);
		gdsTextType.getDocument().addDocumentListener(myDocumentListener);
		gdsHighVLayer.getDocument().addDocumentListener(myDocumentListener);
		gdsHighVType.getDocument().addDocumentListener(myDocumentListener);
    }

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the GDS tab.
	 */
	public void term()
	{
		// user preferences
		boolean currentValue = gdsInputMergesBoxes.isSelected();
		if (currentValue != IOTool.isGDSInMergesBoxes())
			IOTool.setGDSInMergesBoxes(currentValue);
		currentValue = gdsIncludesText.isSelected();
		if (currentValue != IOTool.isGDSIncludesText())
			IOTool.setGDSIncludesText(currentValue);
		currentValue = gdsInputExpandsCells.isSelected();
		if (currentValue != IOTool.isGDSInExpandsCells())
			IOTool.setGDSInExpandsCells(currentValue);
        currentValue = gdsSimplifyCells.isSelected();
        if (currentValue != IOTool.isGDSInSimplifyCells())
			IOTool.setGDSInSimplifyCells(currentValue);
		currentValue = gdsInputInstantiatesArrays.isSelected();
		if (currentValue != IOTool.isGDSInInstantiatesArrays())
			IOTool.setGDSInInstantiatesArrays(currentValue);
		int currentI = gdsArraySimplification.getSelectedIndex();
        if (currentI != IOTool.getGDSArraySimplification())
        	IOTool.setGDSArraySimplification(currentI);
		currentI = gdsUnknownLayers.getSelectedIndex();
		if (currentI != IOTool.getGDSInUnknownLayerHandling())
			IOTool.setGDSInUnknownLayerHandling(currentI);
		currentValue = gdsCadenceCompatibility.isSelected();
		if (currentValue != IOTool.isGDSCadenceCompatibility())
			IOTool.setGDSCadenceCompatibility(currentValue);
		currentValue = gdsDumpText.isSelected();
		if (currentValue != IOTool.isGDSDumpReadable())
			IOTool.setGDSDumpReadable(currentValue);
		currentValue = gdsExportAllCells.isSelected();
		if (currentValue != IOTool.isGDSWritesEntireLibrary())
			IOTool.setGDSWritesEntireLibrary(currentValue);
		currentValue = gdsExportFlatDesign.isSelected();
		if (currentValue != IOTool.isGDSFlatDesign())
			IOTool.setGDSFlatDesign(currentValue);
		currentValue = gdsConvertNCCExportsConnectedByParentPins.isSelected();
        if (currentValue != IOTool.getGDSConvertNCCExportsConnectedByParentPins())
            IOTool.setGDSConvertNCCExportsConnectedByParentPins(currentValue);
        currentValue = gdsVisibility.isSelected();
        if (currentValue != IOTool.isGDSOnlyInvisibleLayers())
            IOTool.setGDSOnlyInvisibleLayers(currentValue);

        // project preferences
        setBoolean(gdsOutMergesBoxesSetting, gdsOutputMergesBoxes.isSelected());
        setBoolean(gdsOutWritesExportPinsSetting, gdsOutputWritesExportPins.isSelected());
        setBoolean(gdsOutUpperCaseSetting, gdsOutputUpperCase.isSelected());
        setInt(gdsOutDefaultTextLayerSetting, TextUtils.atoi(gdsDefaultTextLayer.getText()));
        setBoolean(gdsOutputConvertsBracketsInExportsSetting, gdsOutputConvertsBracketsInExports.isSelected());
        setBoolean(gdsOutCollapseVddGndPinNamesSetting, gdsOutputColapseNames.isSelected());
        setBoolean(gdsOutWriteExportCharacteristicsSetting, gdsOutputWritesCharacteristics.isSelected());        
        setInt(gdsCellNameLenMaxSetting, TextUtils.atoi(gdsCellNameLenMax.getText()));
        setDouble(gdsInputScaleSetting, TextUtils.atof(gdsInputScale.getText()));
        setDouble(gdsOutputPrecisionSetting, TextUtils.atof(gdsOutputPrecision.getText()));
        setDouble(gdsOutputUnitsPerMeterSetting, TextUtils.atof(gdsOutputUnitsPerMeter.getText()));
    }

    /**
     * Method called when other preferences tab changed the layers sorting algorithm
     */
    @Override
    public void refresh()
    {
    	foundryChanged();
    }
    
	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		// user preferences
		if (IOTool.isFactoryGDSInMergesBoxes() != IOTool.isGDSInMergesBoxes())
			IOTool.setGDSInMergesBoxes(IOTool.isFactoryGDSInMergesBoxes());
		if (IOTool.isFactoryGDSInExpandsCells() != IOTool.isGDSInExpandsCells())
			IOTool.setGDSInExpandsCells(IOTool.isFactoryGDSInExpandsCells());
		if (IOTool.isFactoryGDSInSimplifyCells() != IOTool.isGDSInSimplifyCells())
			IOTool.setGDSInSimplifyCells(IOTool.isFactoryGDSInSimplifyCells());
		if (IOTool.isFactoryGDSInInstantiatesArrays() != IOTool.isGDSInInstantiatesArrays())
			IOTool.setGDSInInstantiatesArrays(IOTool.isFactoryGDSInInstantiatesArrays());
		if (IOTool.getFactoryGDSArraySimplification() != IOTool.getGDSArraySimplification())
			IOTool.setGDSArraySimplification(IOTool.getFactoryGDSArraySimplification());
		if (IOTool.getFactoryGDSInUnknownLayerHandling() != IOTool.getGDSInUnknownLayerHandling())
			IOTool.setGDSInUnknownLayerHandling(IOTool.getFactoryGDSInUnknownLayerHandling());
		if (IOTool.isFactoryGDSCadenceCompatibility() != IOTool.isGDSCadenceCompatibility())
			IOTool.setGDSCadenceCompatibility(IOTool.isFactoryGDSCadenceCompatibility());
		if (IOTool.isFactoryGDSDumpReadable() != IOTool.isGDSDumpReadable())
			IOTool.setGDSDumpReadable(IOTool.isFactoryGDSDumpReadable());
		if (IOTool.isFactoryGDSWritesEntireLibrary() != IOTool.isGDSWritesEntireLibrary())
			IOTool.setGDSWritesEntireLibrary(IOTool.isFactoryGDSWritesEntireLibrary());
		if (IOTool.isFactoryGDSFlatDesign() != IOTool.isGDSFlatDesign())
			IOTool.setGDSFlatDesign(IOTool.isFactoryGDSFlatDesign());
		if (IOTool.getFactoryGDSConvertNCCExportsConnectedByParentPins() != IOTool.getGDSConvertNCCExportsConnectedByParentPins())
			IOTool.setGDSConvertNCCExportsConnectedByParentPins(IOTool.getFactoryGDSConvertNCCExportsConnectedByParentPins());
		if (IOTool.isFactoryGDSOnlyInvisibleLayers() != IOTool.isGDSOnlyInvisibleLayers())
			IOTool.setGDSOnlyInvisibleLayers(IOTool.isFactoryGDSOnlyInvisibleLayers());
		if (IOTool.isFactoryGDSIncludesText() != IOTool.isGDSIncludesText())
			IOTool.setGDSIncludesText(IOTool.isFactoryGDSIncludesText());

        // project preferences
        Foundry foundry = (Foundry)foundrySelection.getSelectedItem();
        if (foundry == null) return;
        Technology tech = ((TechGDSTab)technologySelection.getSelectedItem()).tech;
        for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
        {
        	Layer layer = it.next();
        	Setting set = foundry.getGDSLayerSetting(layer);
        	Object factoryObj = set.getFactoryValue();
        	if (factoryObj instanceof String)
        		setString(set, (String)factoryObj);
        }

        setDouble(gdsInputScaleSetting, ((Double)gdsInputScaleSetting.getFactoryValue()).doubleValue());
        setBoolean(gdsOutMergesBoxesSetting, ((Boolean)gdsOutMergesBoxesSetting.getFactoryValue()).booleanValue());
        setBoolean(gdsOutWritesExportPinsSetting, ((Boolean)gdsOutWritesExportPinsSetting.getFactoryValue()).booleanValue());
        setBoolean(gdsOutUpperCaseSetting, ((Boolean)gdsOutUpperCaseSetting.getFactoryValue()).booleanValue());
        setBoolean(gdsOutputConvertsBracketsInExportsSetting, ((Boolean)gdsOutputConvertsBracketsInExportsSetting.getFactoryValue()).booleanValue());
        setBoolean(gdsOutCollapseVddGndPinNamesSetting, ((Boolean)gdsOutCollapseVddGndPinNamesSetting.getFactoryValue()).booleanValue());
        setBoolean(gdsOutWriteExportCharacteristicsSetting, ((Boolean)gdsOutWriteExportCharacteristicsSetting.getFactoryValue()).booleanValue());
        setInt(gdsCellNameLenMaxSetting, ((Integer)gdsCellNameLenMaxSetting.getFactoryValue()).intValue());
        setInt(gdsOutDefaultTextLayerSetting, ((Integer)gdsOutDefaultTextLayerSetting.getFactoryValue()).intValue());
        setDouble(gdsOutputPrecisionSetting, ((Double)gdsOutputPrecisionSetting.getFactoryValue()).doubleValue());
        setDouble(gdsOutputUnitsPerMeterSetting, ((Double)gdsOutputUnitsPerMeterSetting.getFactoryValue()).doubleValue());
	}

    private void foundryChanged()
    {
        Foundry foundry = (Foundry)foundrySelection.getSelectedItem();
        if (foundry == null) return;
        Technology tech = ((TechGDSTab)technologySelection.getSelectedItem()).tech;
		// show the list of layers in the technology
		gdsLayersModel.clear();
		
		for (Layer layer : tech.getLayersSortedByUserPreference())
        {
            String str = layer.getName();
            String gdsLayer = getString(foundry.getGDSLayerSetting(layer));
            if (gdsLayer != null && gdsLayer.length() > 0) str += " (" + gdsLayer + ")";
			gdsLayersModel.addElement(str);
        }
		gdsLayersList.setSelectedIndex(0);
		gdsClickLayer();
    }

    private void setFoundries(Technology tech)
    {
        foundrySelection.removeAllItems();
        // Foundry
        for (Iterator<Foundry> itF = tech.getFoundries(); itF.hasNext();)
        {
            foundrySelection.addItem(itF.next());
        }
        foundrySelection.setSelectedItem(tech.getSelectedFoundry());
        foundryChanged();
    }

	private void techChanged()
	{
		Technology tech = ((TechGDSTab)technologySelection.getSelectedItem()).tech;
		if (tech == null) return;

		// set the foundries for the technology
        setFoundries(tech);
	}

    /**
	 * Method called when the user clicks on a layer name in the scrollable list.
	 */
	private void gdsClickLayer()
	{
		changingGDS = true;
		String str = (String)gdsLayersList.getSelectedValue();
		GDSLayers numbers = gdsGetNumbers(str);
		if (numbers == null) numbers = GDSLayers.EMPTY;
		if (!numbers.hasLayerType(GDSLayerType.DRAWING))
		{
			gdsLayerNumber.setText("");
			gdsLayerType.setText("");
		} else
		{
			int layerNum = numbers.getLayerNumber(GDSLayerType.DRAWING);
			int layerType = numbers.getLayerType(GDSLayerType.DRAWING);
			gdsLayerNumber.setText(Integer.toString(layerNum));
			gdsLayerType.setText(Integer.toString(layerType));
		}
		if (!numbers.hasLayerType(GDSLayerType.PIN))
		{
			gdsPinLayer.setText("");
			gdsPinType.setText("");
		} else
		{
			gdsPinLayer.setText(Integer.toString(numbers.getLayerNumber(GDSLayerType.PIN)));
			gdsPinType.setText(Integer.toString(numbers.getLayerType(GDSLayerType.PIN)));
		}
		if (!numbers.hasLayerType(GDSLayerType.TEXT))
		{
			gdsTextLayer.setText("");
			gdsTextType.setText("");
		} else
		{
			gdsTextLayer.setText(Integer.toString(numbers.getLayerNumber(GDSLayerType.TEXT)));
			gdsTextType.setText(Integer.toString(numbers.getLayerType(GDSLayerType.TEXT)));
		}
		if (!numbers.hasLayerType(GDSLayerType.HIGHVOLTAGE))
		{
			gdsHighVLayer.setText("");
			gdsHighVType.setText("");
		} else
		{
			gdsHighVLayer.setText(Integer.toString(numbers.getLayerNumber(GDSLayerType.HIGHVOLTAGE)));
			gdsHighVType.setText(Integer.toString(numbers.getLayerType(GDSLayerType.HIGHVOLTAGE)));
		}
		changingGDS = false;
	}

	/**
	 * Method to parse the line in the scrollable list and return the GDS layer numbers part
	 * (in parentheses).
	 */
	private GDSLayers gdsGetNumbers(String str)
	{
		if (str == null) return null;
		int openParen = str.indexOf('(');
		if (openParen < 0) return null;
		int closeParen = str.lastIndexOf(')');
		if (closeParen < 0) return null;
		String gdsNumbers = str.substring(openParen+1, closeParen);
		GDSLayers numbers = GDSLayers.parseLayerString(gdsNumbers);
		return numbers;
	}

	/**
	 * Method to parse the line in the scrollable list and return the Layer.
	 */
	private Layer gdsGetLayer(String str)
	{
		int openParen = str.indexOf('(');
		String layerName = openParen >= 0 ? str.substring(0, openParen-1) : str;

        Technology tech = ((TechGDSTab)technologySelection.getSelectedItem()).tech;
		if (tech == null) return null;

		Layer layer = tech.findLayer(layerName);
		return layer;
	}

	/**
	 * Function to detect if string really correspond to a valid gds number
	 * to avoid getting those invalid strings in the Layers list.
	 */
	private boolean isValidGDSValue(String s)
	{
		try
		{
			int v = Integer.parseInt(s);
			return v > -1;
		}
		catch (NumberFormatException e)
		{
			if (!s.equals(""))
				System.out.println("'" + s + "' is an invalid gds value. Correct text field");
			return false;
		}
	}
	
	/**
	 * Method called when the user types a new layer number into one of the 3 edit fields.
	 */
	private void gdsNumbersChanged()
	{
		if (changingGDS) return;
		String str = (String)gdsLayersList.getSelectedValue();
		Layer layer = gdsGetLayer(str);
		if (layer == null) return;

		// the layer information
		String newLine = "";
		String val = gdsLayerNumber.getText().trim();
		if (isValidGDSValue(val))
		{
			newLine += val;
			val = gdsLayerType.getText().trim();
			if (isValidGDSValue(val))
			{
				int layerType = TextUtils.atoi(val);
				if (layerType != 0) newLine += "/" + layerType;
			}
			newLine += GDSLayerType.DRAWING.getExtension();
		}

		// the pin information
		val = gdsPinLayer.getText().trim();
		if (isValidGDSValue(val))
		{
			newLine += "," + val;
			val = gdsPinType.getText().trim();
			if (isValidGDSValue(val))
			{
				int pinType = TextUtils.atoi(val);
				if (pinType != 0) newLine += "/" + pinType;
			}
			newLine += GDSLayerType.PIN.getExtension();
		}

		// the text information
		val = gdsTextLayer.getText().trim();
		if (isValidGDSValue(val))
		{
			newLine += "," + val;
			val = gdsTextType.getText().trim();
			if (isValidGDSValue(val))
			{
				int textType = TextUtils.atoi(val);
				if (textType != 0) newLine += "/" + textType;
			}
			newLine += GDSLayerType.TEXT.getExtension();
		}

		// the high voltage information
		val = gdsHighVLayer.getText().trim();
		if (isValidGDSValue(val))
		{
			newLine += "," + val;
			val = gdsHighVType.getText().trim();
			if (isValidGDSValue(val))
			{
				int highVType = TextUtils.atoi(val);
				if (highVType != 0) newLine += "/" + highVType;
			}
			newLine += GDSLayerType.HIGHVOLTAGE.getExtension();
		}
		
		String wholeLine = layer.getName();
        if (newLine.length() > 0) wholeLine = wholeLine + " (" + newLine + ")";
		int index = gdsLayersList.getSelectedIndex();
		gdsLayersModel.set(index, wholeLine);
        Foundry foundry = (Foundry)foundrySelection.getSelectedItem();
        setString(foundry.getGDSLayerSetting(layer), newLine);
	}

	/**
	 * Class to handle special changes to changes to a GDS layer.
	 */
	private static class GDSDocumentListener implements DocumentListener
	{
		GDSTab dialog;

		GDSDocumentListener(GDSTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.gdsNumbersChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.gdsNumbersChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.gdsNumbersChanged(); }
	}

	// To have ability to store directly the technology and not
    // to depend on names to search the technology instance
    private static class TechGDSTab
    {
        public Technology tech;

        TechGDSTab(Technology t) { tech = t; }

        // This avoids to call Technology.toString() and get extra text.
        public String toString() { return tech.getTechName(); }
    }

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        preferences = new javax.swing.JPanel();
        Import = new javax.swing.JPanel();
        gdsInputMergesBoxes = new javax.swing.JCheckBox();
        gdsInputExpandsCells = new javax.swing.JCheckBox();
        gdsInputInstantiatesArrays = new javax.swing.JCheckBox();
        gdsSimplifyCells = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        gdsArraySimplification = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        gdsUnknownLayers = new javax.swing.JComboBox();
        gdsCadenceCompatibility = new javax.swing.JCheckBox();
        gdsDumpText = new javax.swing.JCheckBox();
        Export = new javax.swing.JPanel();
        gdsExportAllCells = new javax.swing.JCheckBox();
        gdsExportFlatDesign = new javax.swing.JCheckBox();
        gdsConvertNCCExportsConnectedByParentPins = new javax.swing.JCheckBox();
        gdsVisibility = new javax.swing.JCheckBox();
        gdsIncludesText = new javax.swing.JCheckBox();
        projectSettings = new javax.swing.JPanel();
        gdsLayerList = new javax.swing.JScrollPane();
        technologySelection = new javax.swing.JComboBox();
        jLabel10 = new javax.swing.JLabel();
        gdsFoundryName = new javax.swing.JLabel();
        foundrySelection = new javax.swing.JComboBox();
        outputPanel = new javax.swing.JPanel();
        gdsOutputMergesBoxes = new javax.swing.JCheckBox();
        gdsOutputWritesExportPins = new javax.swing.JCheckBox();
        gdsOutputUpperCase = new javax.swing.JCheckBox();
        gdsOutputConvertsBracketsInExports = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        gdsCellNameLenMax = new javax.swing.JTextField();
        gdsOutputColapseNames = new javax.swing.JCheckBox();
        gdsOutputWritesCharacteristics = new javax.swing.JCheckBox();
        jLabel15 = new javax.swing.JLabel();
        gdsOutputPrecision = new javax.swing.JTextField();
        jLabel16 = new javax.swing.JLabel();
        gdsOutputUnitsPerMeter = new javax.swing.JTextField();
        inputPanel = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        gdsInputScale = new javax.swing.JTextField();
        layerPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        gdsLayerNumber = new javax.swing.JTextField();
        gdsPinLayer = new javax.swing.JTextField();
        gdsTextLayer = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        gdsLayerType = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        gdsPinType = new javax.swing.JTextField();
        gdsTextType = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        gdsHighVLayer = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        gdsHighVType = new javax.swing.JTextField();
        exportImport = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        gdsDefaultTextLayer = new javax.swing.JTextField();

        setTitle("IO Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        preferences.setLayout(new java.awt.GridBagLayout());

        Import.setBorder(javax.swing.BorderFactory.createTitledBorder("Import"));
        Import.setLayout(new java.awt.GridBagLayout());

        gdsInputMergesBoxes.setText("Merge boxes (slow)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsInputMergesBoxes, gridBagConstraints);

        gdsInputExpandsCells.setText("Expand cells");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsInputExpandsCells, gridBagConstraints);

        gdsInputInstantiatesArrays.setText("Instantiate arrays");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsInputInstantiatesArrays, gridBagConstraints);

        gdsSimplifyCells.setText("Simplify contact vias");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsSimplifyCells, gridBagConstraints);

        jLabel3.setText("Array simplification:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(jLabel3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 4);
        Import.add(gdsArraySimplification, gridBagConstraints);

        jLabel1.setText("Unknown layers:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 4);
        Import.add(gdsUnknownLayers, gridBagConstraints);

        gdsCadenceCompatibility.setText("Cadence compatibility");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsCadenceCompatibility, gridBagConstraints);

        gdsDumpText.setText("Dump readable data while reading");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Import.add(gdsDumpText, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        preferences.add(Import, gridBagConstraints);

        Export.setBorder(javax.swing.BorderFactory.createTitledBorder("Export"));
        Export.setLayout(new java.awt.GridBagLayout());

        gdsExportAllCells.setText("Export all cells in Library");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Export.add(gdsExportAllCells, gridBagConstraints);

        gdsExportFlatDesign.setText("Flat design");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Export.add(gdsExportFlatDesign, gridBagConstraints);

        gdsConvertNCCExportsConnectedByParentPins.setText("Use NCC annotations for exports");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        Export.add(gdsConvertNCCExportsConnectedByParentPins, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        preferences.add(Export, gridBagConstraints);

        gdsVisibility.setText("Use visibility as filter");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        preferences.add(gdsVisibility, gridBagConstraints);

        gdsIncludesText.setText("Include text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        preferences.add(gdsIncludesText, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(preferences, gridBagConstraints);

        projectSettings.setLayout(new java.awt.GridBagLayout());

        gdsLayerList.setMinimumSize(new java.awt.Dimension(200, 200));
        gdsLayerList.setOpaque(false);
        gdsLayerList.setPreferredSize(new java.awt.Dimension(200, 200));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridheight = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        projectSettings.add(gdsLayerList, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        projectSettings.add(technologySelection, gridBagConstraints);

        jLabel10.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        projectSettings.add(jLabel10, gridBagConstraints);

        gdsFoundryName.setText("Foundry:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        projectSettings.add(gdsFoundryName, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        projectSettings.add(foundrySelection, gridBagConstraints);

        outputPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Export"));
        outputPanel.setLayout(new java.awt.GridBagLayout());

        gdsOutputMergesBoxes.setText("Output merges Boxes (slow)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 4, 2, 4);
        outputPanel.add(gdsOutputMergesBoxes, gridBagConstraints);

        gdsOutputWritesExportPins.setText("Output writes export Pins");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputWritesExportPins, gridBagConstraints);

        gdsOutputUpperCase.setText("Output all upper-case");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputUpperCase, gridBagConstraints);

        gdsOutputConvertsBracketsInExports.setText("Output converts brackets in exports");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputConvertsBracketsInExports, gridBagConstraints);

        jLabel5.setText("Max chars in cell name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(jLabel5, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsCellNameLenMax, gridBagConstraints);

        gdsOutputColapseNames.setText("Output collapses VDD/GND pin names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputColapseNames, gridBagConstraints);

        gdsOutputWritesCharacteristics.setText("Output writes export chacteristics");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputWritesCharacteristics, gridBagConstraints);

        jLabel15.setText("Precision:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(jLabel15, gridBagConstraints);

        gdsOutputPrecision.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputPrecision, gridBagConstraints);

        jLabel16.setText("Units/meter:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(jLabel16, gridBagConstraints);

        gdsOutputUnitsPerMeter.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(gdsOutputUnitsPerMeter, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        projectSettings.add(outputPanel, gridBagConstraints);

        inputPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Import"));
        inputPanel.setLayout(new java.awt.GridBagLayout());

        jLabel11.setText("Scale by:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        inputPanel.add(jLabel11, gridBagConstraints);

        gdsInputScale.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        inputPanel.add(gdsInputScale, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        projectSettings.add(inputPanel, gridBagConstraints);

        layerPanel.setLayout(new java.awt.GridBagLayout());

        jLabel2.setText("Layer:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        layerPanel.add(jLabel2, gridBagConstraints);

        gdsLayerNumber.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsLayerNumber, gridBagConstraints);

        gdsPinLayer.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsPinLayer, gridBagConstraints);

        gdsTextLayer.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsTextLayer, gridBagConstraints);

        jLabel8.setText("Text:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(jLabel8, gridBagConstraints);

        jLabel7.setText("Pin:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(jLabel7, gridBagConstraints);

        jLabel6.setText("Normal:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(jLabel6, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsLayerType, gridBagConstraints);

        jLabel4.setText("Type:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        layerPanel.add(jLabel4, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsPinType, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsTextType, gridBagConstraints);

        jLabel13.setText("Clear these field to ignore the layer");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        layerPanel.add(jLabel13, gridBagConstraints);

        gdsHighVLayer.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsHighVLayer, gridBagConstraints);

        jLabel14.setText("High V:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(jLabel14, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(gdsHighVType, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        projectSettings.add(layerPanel, gridBagConstraints);

        exportImport.setBorder(javax.swing.BorderFactory.createTitledBorder("Export/Import"));
        exportImport.setName("Import/Export"); // NOI18N
        exportImport.setLayout(new java.awt.GridBagLayout());

        jLabel9.setText("Default text layer:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        exportImport.add(jLabel9, gridBagConstraints);

        gdsDefaultTextLayer.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        exportImport.add(gdsDefaultTextLayer, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        projectSettings.add(exportImport, gridBagConstraints);
        exportImport.getAccessibleContext().setAccessibleDescription("Export/Import");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(projectSettings, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel Export;
    private javax.swing.JPanel Import;
    private javax.swing.JPanel exportImport;
    private javax.swing.JComboBox foundrySelection;
    private javax.swing.JComboBox gdsArraySimplification;
    private javax.swing.JCheckBox gdsCadenceCompatibility;
    private javax.swing.JTextField gdsCellNameLenMax;
    private javax.swing.JCheckBox gdsConvertNCCExportsConnectedByParentPins;
    private javax.swing.JTextField gdsDefaultTextLayer;
    private javax.swing.JCheckBox gdsDumpText;
    private javax.swing.JCheckBox gdsExportAllCells;
    private javax.swing.JCheckBox gdsExportFlatDesign;
    private javax.swing.JLabel gdsFoundryName;
    private javax.swing.JTextField gdsHighVLayer;
    private javax.swing.JTextField gdsHighVType;
    private javax.swing.JCheckBox gdsIncludesText;
    private javax.swing.JCheckBox gdsInputExpandsCells;
    private javax.swing.JCheckBox gdsInputInstantiatesArrays;
    private javax.swing.JCheckBox gdsInputMergesBoxes;
    private javax.swing.JTextField gdsInputScale;
    private javax.swing.JScrollPane gdsLayerList;
    private javax.swing.JTextField gdsLayerNumber;
    private javax.swing.JTextField gdsLayerType;
    private javax.swing.JCheckBox gdsOutputColapseNames;
    private javax.swing.JCheckBox gdsOutputConvertsBracketsInExports;
    private javax.swing.JCheckBox gdsOutputMergesBoxes;
    private javax.swing.JTextField gdsOutputPrecision;
    private javax.swing.JTextField gdsOutputUnitsPerMeter;
    private javax.swing.JCheckBox gdsOutputUpperCase;
    private javax.swing.JCheckBox gdsOutputWritesCharacteristics;
    private javax.swing.JCheckBox gdsOutputWritesExportPins;
    private javax.swing.JTextField gdsPinLayer;
    private javax.swing.JTextField gdsPinType;
    private javax.swing.JCheckBox gdsSimplifyCells;
    private javax.swing.JTextField gdsTextLayer;
    private javax.swing.JTextField gdsTextType;
    private javax.swing.JComboBox gdsUnknownLayers;
    private javax.swing.JCheckBox gdsVisibility;
    private javax.swing.JPanel inputPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel layerPanel;
    private javax.swing.JPanel outputPanel;
    private javax.swing.JPanel preferences;
    private javax.swing.JPanel projectSettings;
    private javax.swing.JComboBox technologySelection;
    // End of variables declaration//GEN-END:variables
}
