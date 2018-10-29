/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDSLayers.java
 * Input/output tool: GDS layer parsing
 * Written by Steven M. Rubin.
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
package com.sun.electric.technology;

import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Layer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Class to define GDS information associated to a given layer.
 */
public class GDSLayers
{
	public static enum GDSLayerType
	{
		DRAWING(""), PIN("p"), TEXT("t"), HIGHVOLTAGE("h"), DUMMY("d");
		
		String extension;
		GDSLayerType(String s)
		{
			extension = s;
		}
		public String getExtension() {return extension;}
		public static GDSLayerType findType(String name)
		{
			try{
				GDSLayerType t = GDSLayerType.valueOf(name.toUpperCase());
				return t;
			}
			catch (Exception e)
			{
				return null; // just not supported
			}
		}
	}
	
    public static final GDSLayers EMPTY = new GDSLayers((int[])null);
	private Map<GDSLayerType,Integer> values = new HashMap<GDSLayerType,Integer>();
	
	public GDSLayers(int normalLayer, int normalType, int pinLayer, int pinType,
			int textLayer, int textType)
	{
		setLayerType(normalLayer, normalType, GDSLayerType.DRAWING);
		setLayerType(pinLayer, pinType, GDSLayerType.PIN);
		setLayerType(textLayer, textType, GDSLayerType.TEXT);
	}
	
	// constructor with each integer already containing the type
	public GDSLayers(int vals[])
	{
		if (vals == null) // empty
			return;
		GDSLayerType[] list = GDSLayerType.values();
		int maxNumTypes = list.length;
		// order: normal, pin, text, high voltage
		assert(vals.length == maxNumTypes);
		for (int i = 0; i < vals.length; i++)
		{
			if (vals[i] == -1) continue;
			values.put(list[i], vals[i]);
		}
	}

	public void setLayerType(int layerNum, int layerType, GDSLayerType type)
	{
		if (layerType != -1) layerNum |= (layerType << 16);
		values.put(type, layerNum);
	}
	public boolean hasLayerType(GDSLayerType type) {return values.get(type) != null;}
	
	public int getLayerNumber(GDSLayerType type)
	{
		Integer val = values.get(type);
		assert(val != null);
		return val & 0xFFFF;
	}
	
	public int getLayerType(GDSLayerType type)
	{
		Integer val = values.get(type);
		assert(val != null);
		return (val >> 16) & 0xFFFF;
	}
	
	public int getLayer(GDSLayerType type) {return values.get(type);}
	public String getString(GDSLayerType type) { return getString(type, false, false); }
	public String getString(GDSLayerType type, boolean preFix, boolean extension)
	{
        String s = "";
        Integer val = values.get(type);
        if (val == null || val == -1) return s;
        if (preFix && type != GDSLayerType.DRAWING)
        	s += ",";
        s += "" + getLayerNumber(type); // (pinLayer & 0xFFFF);
        int normalType = getLayerType(type); // (pinLayer >> 16) & 0xFFFF;
        if (normalType != 0) s += "/" + normalType;
        if (extension && type != GDSLayerType.DRAWING)
        	s += type.getExtension();
        return s;
	}
	
	/**
	 * Method to determine if the numbers in this GDSLayers are the same as another.
	 * @param other the other GDSLayers being compared with this.
	 * @return true if they have the same values.
	 */
	public boolean equals(GDSLayers other)
	{
		if (other == null) return false;
    	for (GDSLayerType t : GDSLayerType.values())
    	{
    		if (values.get(t) != other.values.get(t))
    			return false; // no match
    	}
    	return true; // everything matches
	}

    @Override
    public String toString() {
    	// Assume the first one is GDSLayerType.NORMAL
    	StringBuffer str = new StringBuffer();
    	
    	for (GDSLayerType t : GDSLayerType.values())
    	{
    		if (values.get(t) == null) continue;
    		str.append(getString(t, true, true));
    	}
    	String s = str.toString();
    	// check that we don't have ",<#>" cases when DRAWING value is not found for example
    	int index = s.indexOf(",");
    	if (index == 0) // first value
    	{
    		s = s.substring(1);
    		System.out.println("Warning: No drawing value found for '" + s + "'");
    	}
    	return s;
    }

	/**
	 * Method to parse the GDS layer string and get the layer numbers and types (plain, text, and pin).
	 * @param string the GDS layer string, of the form [NUM[/TYP]]*[,NUM[/TYP]t][,NUM[/TYP]p]
	 * @return a GDSLayers object with the values filled-in.
	 */
	public static GDSLayers parseLayerString(String string)
	{
		if (string.isEmpty())
			return null;
		
		// do it with StringTokenizer
		StringTokenizer parser = new StringTokenizer(string, ",.[]", false);
		// countType = 1 -> main value
		List<String> list = new ArrayList<String>();
		while (parser.hasMoreTokens())
			list.add(parser.nextToken());
		int maxNumTypes = GDSLayerType.values().length;
		assert(list.size() > 0 && list.size() <= maxNumTypes);
		int[] vals = new int[maxNumTypes];
		Arrays.fill(vals, 0, maxNumTypes, -1);
		int pos = -1;
		
		for (String s : list)
		{
			int gdsVal = -1;
			int gdsType = -1;
			String trimmed = s.trim();
			char lastCh = trimmed.charAt(trimmed.length()-1); // see if it is pin or text value
			if (lastCh == 't')
				pos = 2; // text
			else if (lastCh == 'p')
				pos = 1; // pin
			else if (lastCh == 'h')
				pos = 3; // high voltage
			else if (lastCh == 'd')
				pos = 4; // high voltage
			else
				pos = 0;
			if (vals[pos] != -1)
			{
				System.out.println("Multiple definition of a gds type in '" + string + "'. Ignoring definition for character '" + lastCh + "'");
				return null; // only one definition per string
			}
			
			// get components -> only a pair
			if (pos != 0) // normal -> remove letter
				s = s.substring(0, s.length()-1);
			StringTokenizer p = new StringTokenizer(s, "/", false);
			while (p.hasMoreTokens())
			{
				String item = p.nextToken();
				item = item.trim(); // just in case there are white spaces in btw
				int val = -1;
				try
				{
					val = Integer.parseInt(item);
				}
				catch (NumberFormatException e)
				{
					System.out.println("'" + item + "' is not a valid gds number/type. Ignoring defintion");
					return null;
				}
				if (gdsVal == -1)
					gdsVal = val; // TextUtils.atoi(item);
				else if (gdsType == -1)
					gdsType = val; // TextUtils.atoi(item);
				else
				{
					System.out.println("Error: '" + string + "' is not a valid gds description.");
					return null;
				}
			}
			if (gdsVal == -1) // not defined
				return EMPTY;
			vals[pos] = (gdsType != -1) ? gdsVal | (gdsType << 16) : gdsVal;
		}
		return new GDSLayers(vals);
	}

	// To determine GDS values associated to a given layer
    public static GDSLayers getGDSValues(Layer l)
    {
    	EDatabase database = EDatabase.clientDatabase();
    	Map<Setting,Object> context = database.getSettings();
    	Foundry t1 = l.getTechnology().getSelectedFoundry();
    	Setting s1 = t1.getGDSLayerSetting(l);
    	return GDSLayers.parseLayerString(context.get(s1).toString());
    }
}

