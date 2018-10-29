/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDSMap.java
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
package com.sun.electric.tool.io.input;

import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.GDSLayers;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.GDSLayers.GDSLayerType;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.TextUtils;

import java.awt.Color;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Class to handle the "import GDS MAP" command.
 */
public class GDSMap extends EDialog
{
	private HashMap<String,JComboBox> assocCombosDrawing;
	private List<MapLine> drawingEntries;
	private List<MapLine> pinEntries;
	private List<MapLine> textEntries;
	private Technology tech;

	private static class MapLine
	{
		String name;
		int layer;
		int type;
	}

	/*
	 * Method to export indexes associated to GDS layers.
	 * These are ASCII files. Elements are DRAWINGly sorted by the stream index
	 */
	public static void exportMapFile()
	{
		Technology tech = Technology.getCurrent();
		String fileName = OpenFile.chooseOutputFile(FileType.GDSMAP, "Exporting GDS Layer Map for '" + tech.getTechName() + "'", "gdsmap.map");
		if (fileName == null) return;
		PrintWriter printWriter = TextUtils.openPrintWriterFromFileName(fileName, false);
		
		System.out.println("Writing GDS Layer Map from '" + fileName + "' ");
		String text = com.sun.electric.database.text.TextUtils.generateFileHeader(null, "#", "#", true, true);
		printWriter.println(text);
		
		// Map Header
		printWriter.println("# GDS Map for technology '" + tech.getTechName() + "'");
		printWriter.println("# Layer Name \t\t Layer Purpose \t Layer Stream Number \t Datatype Stream Number");
		printWriter.println();
		
		List<Layer> list = tech.getLayersSortedByRule(Layer.LayerSortingType.ByGDSIndex);
		for (Layer l : list)
		{
			GDSLayers gdsL = GDSLayers.getGDSValues(l);
			if (gdsL == null) 
			{
				System.out.println("WARNING: No GDS data found for layer '" + l.getName() + "'");
				continue;
			}
			int layerNum = gdsL.getLayerNumber(GDSLayerType.DRAWING);
			int layerType = gdsL.getLayerType(GDSLayerType.DRAWING);
			printWriter.println(l.getName() + "\t\tdrawing\t" + layerNum + "\t" + layerType); 
			if (gdsL.hasLayerType(GDSLayerType.PIN))
				printWriter.println(l.getName() + "\t\tpin\t" + gdsL.getLayerNumber(GDSLayerType.PIN) + "\t" + gdsL.getLayerType(GDSLayerType.PIN)); 
			if (gdsL.hasLayerType(GDSLayerType.TEXT))
				printWriter.println(l.getName() + "\t\ttext\t" + gdsL.getLayerNumber(GDSLayerType.TEXT) + "\t" + gdsL.getLayerType(GDSLayerType.TEXT)); 
			
			// Extra code to export in XML tech format to easy incorporation of new GDS values in XML files
			//System.out.print("<layerGds layer=\"" + l.getName() + "\" gds=\"" + gdsL.toString() + "\"/>\n");
		}
		printWriter.close();
		System.out.println("done.");
	}
	
	/*
	 * Method to import GDS Map file containing GDS layer information.
	 * These are ascii files. Elements are DRAWINGly sorted by the stream index
	 */
	public static void importMapFile()
	{
		Technology tech = Technology.getCurrent();
		String fileName = OpenFile.chooseInputFile(FileType.GDSMAP, "Importing GDS Layer Map File for '" + tech.getTechName() + "'", null);
		if (fileName == null) return;
		HashSet<String> allNames = new HashSet<String>();
		
		List<MapLine> drawingEntries = new ArrayList<MapLine>();
		List<MapLine> pinEntries = new ArrayList<MapLine>(); 
		List<MapLine> textEntries = new ArrayList<MapLine>(); 
		URL url = TextUtils.makeURLToFile(fileName);
		
		System.out.print("Reading of GDS Layer Map from '" + fileName + "' ");
		try
		{
			URLConnection urlCon = url.openConnection();
			InputStreamReader is = new InputStreamReader(urlCon.getInputStream());
			LineNumberReader lineReader = new LineNumberReader(is);
			for(;;)
			{
				String buf = lineReader.readLine();
				if (buf == null) break;
				buf = buf.trim();
				if (buf.length() == 0) continue;
				if (buf.charAt(0) == '#') continue;

				// get the layer name
				int spaPos = buf.indexOf(' ');
				if (spaPos < 0) continue;
				String layerName = buf.substring(0, spaPos);
				buf = buf.substring(spaPos+1).trim();

				// get the layer purpose
				spaPos = buf.indexOf(' ');
				if (spaPos < 0) continue;
				String layerPurpose = buf.substring(0, spaPos);
				buf = buf.substring(spaPos+1).trim();

				// get the GDS number and type
				spaPos = buf.indexOf(' ');
				if (spaPos < 0) continue;
				int gdsNumber = TextUtils.atoi(buf.substring(0, spaPos));
				buf = buf.substring(spaPos+1).trim();
				int gdsType = TextUtils.atoi(buf);

				// only want layers whose purpose is "drawing" or "pin"
				if (!layerPurpose.equalsIgnoreCase("drawing") &&
					!layerPurpose.equalsIgnoreCase("pin") &&
					!layerPurpose.equalsIgnoreCase("text")) continue;
				
				// remember this for later
				MapLine ml = new MapLine();
				ml.name = layerName;
				ml.layer = gdsNumber;
				ml.type = gdsType;
				if (layerPurpose.equalsIgnoreCase("drawing")) 
					drawingEntries.add(ml); 
				else if (layerPurpose.equalsIgnoreCase("pin")) 
					pinEntries.add(ml);
				else
					textEntries.add(ml);
				
				allNames.add(layerName);
			}
			lineReader.close();
		} catch (IOException e)
		{
			System.out.println("Error reading " + fileName);
			return;
		}
		new GDSMap(TopLevel.getCurrentJFrame(), allNames, drawingEntries, pinEntries, textEntries);
		System.out.println("done.");
	}

	private void addElement(MapLine map, String purpose, int row, JPanel panel, Foundry foundry, 
			Map<Setting,Object> context)
	{
		String value = map.name + " (" + purpose + "," + map.layer;
		value += (map.type != 0) ? "/" + map.type + ")" : ")";
		JLabel lab = new JLabel(value);		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; gbc.gridy = row; gbc.ipadx = 5; 
		gbc.anchor = GridBagConstraints.WEST;
		panel.add(lab, gbc);

		JComboBox comboBox = new JComboBox();
		
		comboBox.addItem("<IGNORE>");
		Layer foundLayer = null;
		
		for(Iterator<Layer> lIt = tech.getLayers(); lIt.hasNext(); )
		{
			Layer lay = lIt.next();
			String layName = lay.getName();
			comboBox.addItem(layName);
			if (layName.equals(map.name))
			{
				assert(foundLayer == null);
				foundLayer = lay;
			}
		}
		String valueFromfile = value;
		
		value = "";
		if (foundLayer != null)
		{
			comboBox.setSelectedItem(map.name);
			Setting set = foundry.getGDSLayerSetting(foundLayer);
			Object obj = context.get(set);
			GDSLayers l =  GDSLayers.parseLayerString(obj.toString());
			if (purpose.toLowerCase().equals("drawing"))
				value = map.name + " (" + purpose + "," + l.getString(GDSLayerType.DRAWING) + ")";
			else if (purpose.toLowerCase().equals("pin"))
				value = map.name + " (" + purpose + "," + l.getString(GDSLayerType.PIN) + ")";
			else if (purpose.toLowerCase().equals("text"))
				value = map.name + " (" + purpose + "," + l.getString(GDSLayerType.TEXT) + ")";
		}
//		String savedAssoc = getSavedAssoc(name);
//		if (savedAssoc.length() > 0) comboBox.setSelectedItem(savedAssoc);
		// Action
		gbc = new GridBagConstraints();
		gbc.gridx = 1; gbc.gridy = row; gbc.ipadx = 5; 
		panel.add(comboBox, gbc);
		
		// Current data
		lab = new JLabel(value);
		if (value.equals(valueFromfile))
			lab.setForeground(Color.GRAY);
		else
			lab.setForeground(Color.RED);
		gbc = new GridBagConstraints();
		gbc.gridx = 2; gbc.gridy = row; gbc.ipadx = 5; 
		panel.add(lab, gbc);
		
		assocCombosDrawing.put(map.name, comboBox);
	}
	
	/** Creates new form Layer Map Association */
	public GDSMap(Frame parent, HashSet<String> allNames, List<MapLine> drawingEntries, 
			List<MapLine> pinEntries, List<MapLine> textEntries)
	{
		super(parent, true);
		this.drawingEntries = drawingEntries;
		this.pinEntries = pinEntries;
		this.textEntries = textEntries;
		this.tech = Technology.getCurrent();
        getContentPane().setLayout(new GridBagLayout());
        setTitle("GDS Layer Map Association");
        setName("");
        addWindowListener(new WindowAdapter()
		{
            public void windowClosing(WindowEvent evt) { closeDialog(evt); }
        });

		// make a list of names
		List<String> nameList = new ArrayList<String>();
		for(String name : allNames)
			nameList.add(name);
		Collections.sort(nameList, TextUtils.STRING_NUMBER_ORDER);

		// show the list
		assocCombosDrawing = new HashMap<String,JComboBox>();
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		int row = 0;
		// Adding the row titles
		// Layer name
		JLabel lab = new JLabel("Imported data");		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; gbc.gridy = row; gbc.ipadx = 5; gbc.ipady = 15;
		gbc.anchor = GridBagConstraints.CENTER;
		panel.add(lab, gbc);
		lab = new JLabel("Action");		
		gbc = new GridBagConstraints();
		gbc.gridx = 1; gbc.gridy = row; gbc.ipadx = 5; gbc.ipady = 15;
		gbc.anchor = GridBagConstraints.CENTER;
		panel.add(lab, gbc);
		lab = new JLabel("Current data");		
		gbc = new GridBagConstraints();
		gbc.gridx = 2; gbc.gridy = row; gbc.ipadx = 5; gbc.ipady = 15;
		gbc.anchor = GridBagConstraints.CENTER;
		panel.add(lab, gbc);
		Foundry foundry = tech.getSelectedFoundry();
		
		row = 2;
		EDatabase database = EDatabase.clientDatabase();
		Map<Setting,Object> context = database.getSettings();
		
		for (MapLine map : drawingEntries)
		{
			addElement(map, "drawing", row, panel, foundry, context);
			row++;
		}
			// Looking for a possible pin. Here is done
			// so the elements will be together in the dialog box
		for (MapLine n : pinEntries)
		{
			// found here
			addElement(n, "pin", row, panel, foundry, context);
			row++;
		}
		for (MapLine n : textEntries)
		{
			// found here
			addElement(n, "text", row, panel, foundry, context);
			row++;
		}

		JScrollPane assocPane = new JScrollPane();
		assocPane.setViewportView(panel);			
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;   gbc.weighty = 1.0;
		gbc.insets = new Insets(4, 4, 4, 4);
        getContentPane().add(assocPane, gbc);

		lab = new JLabel("Mapping these layer names to the '" + tech.getTechName() + "' technology:");
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 0;
		gbc.gridwidth = 2;
		gbc.insets = new Insets(4, 4, 4, 4);
        getContentPane().add(lab, gbc);

		JButton ok = new JButton("OK");
        ok.addActionListener(new ActionListener()
		{
            public void actionPerformed(ActionEvent evt) { ok(); }
        });
		gbc = new GridBagConstraints();
		gbc.gridx = 1;   gbc.gridy = 2;
		gbc.insets = new Insets(4, 4, 4, 4);
        getContentPane().add(ok, gbc);
        getRootPane().setDefaultButton(ok);

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener()
		{
            public void actionPerformed(ActionEvent evt) { closeDialog(null); }
        });
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 2;
		gbc.insets = new Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gbc);

        pack();
		setVisible(true);
	}

	public void termDialog()
	{
		for(String name : assocCombosDrawing.keySet())
		{
			JComboBox combo = assocCombosDrawing.get(name);
			String layerName = "";
			if (combo.getSelectedIndex() != 0)
				layerName = (String)combo.getSelectedItem();
			setSavedAssoc(name, layerName);
		}

		// wipe out all GDS layer associations
        Foundry foundry = tech.getSelectedFoundry();

        if (foundry == null)
        {
            System.out.println("No foundry associated for the mapping");
            return;
        }

        Setting.SettingChangeBatch changeBatch = new Setting.SettingChangeBatch();
//        for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); ) {
//            Layer layer = it.next();
//            changeBatch.add(foundry.getGDSLayerSetting(layer), "");
//        }

		// set the associations
        // missing text/pin entries if no drawing information is found
		for(MapLine ml : drawingEntries)
		{
			String layerName = getSavedAssoc(ml.name);
			if (layerName.length() == 0) continue;
			Layer layer = tech.findLayer(layerName);
			if (layer == null) continue;
			String layerInfo = "" + ml.layer;
			if (ml.type != 0) layerInfo += "/" + ml.type;
			for(MapLine pMl : pinEntries)
			{
				if (pMl.name.equals(ml.name))
				{
					if (pMl.layer != -1)
					{
						layerInfo += "," + pMl.layer;
						if (pMl.type != 0) layerInfo += "/" + pMl.type;
						layerInfo += "p";
					}
					break;
				}
			}
			for(MapLine tMl : textEntries)
			{
				if (tMl.name.equals(ml.name))
				{
					if (tMl.layer != -1)
					{
						layerInfo += "," + tMl.layer;
						if (tMl.type != 0) layerInfo += "/" + tMl.type;
						layerInfo += "t";
					}
					break;
				}
			}
            changeBatch.add(foundry.getGDSLayerSetting(layer), layerInfo);
		}
        new OKUpdate(changeBatch).startJob();
	}

	private void ok()
	{
		termDialog();
		closeDialog(null);
	}

	/** Closes the dialog */
	private void closeDialog(WindowEvent evt)
	{
		setVisible(false);
		dispose();
	}

	private String getSavedAssoc(String mapName)
	{
        Preferences preferences = Pref.getPrefRoot().node(IOTool.getIOTool().prefs.relativePath());
        return preferences.get("GDSMappingFor" + mapName, "");
	}

	private void setSavedAssoc(String mapName, String layerName)
	{
        Preferences preferences = Pref.getPrefRoot().node(IOTool.getIOTool().prefs.relativePath());
        preferences.put("GDSMappingFor" + mapName, layerName);
	}

	/**
	 * Class to update GDS Map.
	 */
	private static class OKUpdate extends Job
	{
		private Setting.SettingChangeBatch changeBatch;

		private OKUpdate(Setting.SettingChangeBatch changeBatch)
		{
			super("Update GDS Mapping", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.changeBatch = changeBatch;
		}

		public boolean doIt() throws JobException
		{
			getDatabase().implementSettingChanges(changeBatch);
			return true;
		}

		@Override
		public void terminateOK()
		{
		}
	}
}
