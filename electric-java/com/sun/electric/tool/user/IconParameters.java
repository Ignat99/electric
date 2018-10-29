package com.sun.electric.tool.user;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.JobException;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
	 * Class to define parameters for automatic icon generation
 */
public class IconParameters implements Serializable
{
    /** side for input ports (when placeByCellLocation false) */		int inputSide;
    /** side for output ports (when placeByCellLocation false) */		int outputSide;
    /** side for bidirectional ports (when placeByCellLocation false) */int bidirSide;
    /** side for power ports (when placeByCellLocation false) */		int pwrSide;
    /** side for ground ports (when placeByCellLocation false) */		int gndSide;
    /** side for clock ports (when placeByCellLocation false) */		int clkSide;
    /** rotation of top text (when placeByCellLocation true) */			int topRot;
    /** rotation of bottom text (when placeByCellLocation true) */		int bottomRot;
    /** rotation of left text (when placeByCellLocation true) */		int leftRot;
    /** rotation of right text (when placeByCellLocation true) */		int rightRot;
    /** skip top text (when placeByCellLocation true) */				boolean topSkip;
    /** skip bottom text (when placeByCellLocation true) */				boolean bottomSkip;
    /** skip left text (when placeByCellLocation true) */				boolean leftSkip;
    /** skip right text (when placeByCellLocation true) */				boolean rightSkip;

    public static IconParameters makeInstance(boolean userDefaults)
    {
        return new IconParameters(userDefaults);
    }

    private IconParameters(boolean userDefaults)
    {
        inputSide = 0;
        outputSide = 1;
        bidirSide = 2;
        pwrSide = 3;
        gndSide = 3;
        clkSide = 0;
        topRot = 0;
        bottomRot = 0;
        leftRot = 0;
        rightRot = 0;
        topSkip = false;
        bottomSkip = false;
        leftSkip = false;
        rightSkip = false;
        if (userDefaults)
        {
            inputSide = User.getIconGenInputSide();
            outputSide = User.getIconGenOutputSide();
            bidirSide = User.getIconGenBidirSide();
            pwrSide = User.getIconGenPowerSide();
            gndSide = User.getIconGenGroundSide();
            clkSide = User.getIconGenClockSide();
            topRot = User.getIconGenTopRot();
            bottomRot = User.getIconGenBottomRot();
            leftRot = User.getIconGenLeftRot();
            rightRot = User.getIconGenRightRot();
            topSkip = User.isIconGenTopSkip();
            bottomSkip = User.isIconGenBottomSkip();
            leftSkip = User.isIconGenLeftSkip();
            rightSkip = User.isIconGenRightSkip();
        }
    }

    private static class ShadowExport implements Comparable<ShadowExport>
    {
    	String exportName;
    	List<Integer> indices;
    	List<Export> originals;
    	Point2D center;
    	boolean isBus;
    	boolean nearLeft, nearRight, nearTop, nearBottom;

    	ShadowExport(String name)
    	{
    		exportName = name;
    		indices = new ArrayList<Integer>();
    		originals = new ArrayList<Export>();
    		isBus = false;
    		nearLeft = nearRight = nearTop = nearBottom = false;
    	}

		/**
		 * Method to sort Exports by their angle about the cell center.
		 */
		public int compareTo(ShadowExport other)
		{
			Cell cell = originals.get(0).getParent();
			ERectangle bounds = cell.getBounds();
			Point2D cellCtr = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
			double angle1 = DBMath.figureAngleRadians(cellCtr, center);
			double angle2 = DBMath.figureAngleRadians(cellCtr, other.center);
			if (angle1 < angle2) return 1;
			if (angle1 > angle2) return -1;
			return 0;
		}
    }

    /**
     * Method to create an icon for a cell.
     * @param curCell the cell to turn into an icon.
     * @param ep EditingPreferences with default sizes and text descriptors.
     * @return the icon cell (null on error).
     */
    public Cell makeIconForCell(Cell curCell, EditingPreferences ep)
        throws JobException
    {
        // create the new icon cell
    	boolean schematicSource = (curCell.getView() == View.SCHEMATIC);
        String iconCellName = curCell.getName() + "{ic}";
        Cell iconCell = Cell.makeInstance(ep, curCell.getLibrary(), iconCellName);
        if (iconCell == null)
            throw new JobException("Cannot create Icon cell " + iconCellName);
        iconCell.setWantExpanded();
        ERectangle cellBounds = curCell.getBounds();

        // make the "shadow" list of exports to place
    	Map<String,ShadowExport> shadowExports = new HashMap<String,ShadowExport>();
    	for(Iterator<PortProto> it = curCell.getPorts(); it.hasNext(); )
    	{
    		Export e = (Export)it.next();
    		if (e.isBodyOnly()) continue;
    		String name = e.getName();
    		if (schematicSource)
    		{
        		ShadowExport se = new ShadowExport(name);
        		shadowExports.put(name, se);
        		se.isBus = e.getNameKey().isBus();
        		se.originals.add(e);
    		} else
    		{
        		Integer index = null;
        		int indexPos = name.indexOf('[');
        		if (indexPos >= 0)
        		{
        			String indexPart = name.substring(indexPos+1);
        			int lastSq = indexPart.indexOf(']');
        			if (lastSq == indexPart.length()-1)
        			{
        				int indexVal = TextUtils.atoi(indexPart.substring(0, lastSq));
        				index = Integer.valueOf(indexVal);
        				name = name.substring(0, indexPos);
        			}
        		}
        		ShadowExport se = shadowExports.get(name);
        		if (se == null) shadowExports.put(name, se = new ShadowExport(name));
        		if (index != null) se.indices.add(index);
        		se.originals.add(e);
    		}
    	}
    	List<ShadowExport> shadowExportList = new ArrayList<ShadowExport>();
    	for(String seName : shadowExports.keySet())
    	{
    		ShadowExport se = shadowExports.get(seName);
    		double x = 0, y = 0;
    		for(Export e : se.originals)
    		{
    			Point2D ctr = e.getOriginalPort().getCenter();
    			x += ctr.getX();
    			y += ctr.getY();
    			if (ctr.getX() < cellBounds.getMinX() + cellBounds.getWidth()/10.0) se.nearLeft = true;
    			if (ctr.getX() > cellBounds.getMaxX() - cellBounds.getWidth()/10.0) se.nearRight = true;
    			if (ctr.getY() < cellBounds.getMinY() + cellBounds.getHeight()/10.0) se.nearBottom = true;
    			if (ctr.getY() > cellBounds.getMaxY() - cellBounds.getHeight()/10.0) se.nearTop = true;
    		}
    		se.center = new Point2D.Double(x / se.originals.size(), y / se.originals.size());

    		if (se.indices.size() == 1)
    		{
    			se.exportName += "[" + se.indices.get(0) + "]";
    		} else if (se.indices.size() > 0)
    		{
    			Collections.sort(se.indices);
    			String index = "";
    			for(int i=0; i<se.indices.size(); i++)
    			{
    				int start = se.indices.get(i).intValue();
    				int end = start;
    				for(int j=i+1; j<se.indices.size(); j++)
    				{
    					if (se.indices.get(j).intValue() != end+1) break;
    					end++;
    					i++;
    				}
    				if (start == end) index += "," + start; else
    					index += "," + start + ":" + end;
    			}
    			se.exportName += "[" + index.substring(1) + "]";
    			se.isBus = true;
    		}
    		shadowExportList.add(se);    		
    	}

    	// determine number of ports on each side
        int leftSide = 0, rightSide = 0, bottomSide = 0, topSide = 0;
        Map<ShadowExport,Integer> portIndex = new HashMap<ShadowExport,Integer>();
        Map<ShadowExport,Integer> portSide = new HashMap<ShadowExport,Integer>();
        Map<ShadowExport,Integer> portRotation = new HashMap<ShadowExport,Integer>();

        // make a sorted list of exports
        if (ep.getIconGenExportPlacement() == 1)
        {
        	// place exports according to their location in the cell
            Collections.sort(shadowExportList);

            // figure out how many exports go on each side
            int numExports = shadowExportList.size();
        	int numSides = (topSkip ? 0 : 1) + (bottomSkip ? 0 : 1) + (leftSkip ? 0 : 1) + (rightSkip ? 0 : 1);
        	if (numSides == 0) { numSides = 1;  leftSkip = false; }
            if (schematicSource)
            {
            	int numOnSide = numExports / numSides;
            	if (topSkip) topSide = 0; else topSide = numOnSide;
            	if (bottomSkip) bottomSide = 0; else bottomSide = numOnSide;
            	if (leftSkip) leftSide = 0; else leftSide = numOnSide;
            	if (rightSkip) rightSide = 0; else rightSide = numOnSide;
            } else
            {
            	for(ShadowExport se : shadowExportList)
            	{
            		if (se.nearBottom && !bottomSkip) { bottomSide++;  continue; }
            		if (se.nearTop && !topSkip) { topSide++;  continue; }
            		if (se.nearLeft && !leftSkip) { leftSide++;  continue; }
            		if (se.nearRight && !rightSkip) { rightSide++;  continue; }
            	}
            }
            int cycle = 0;
            while (leftSide + rightSide + topSide + bottomSide < numExports)
            {
            	switch (cycle)
            	{
            		case 0: if (!leftSkip) leftSide++;      break;
            		case 1: if (!rightSkip) rightSide++;    break;
            		case 2: if (!topSkip) topSide++;        break;
            		case 3: if (!bottomSkip) bottomSide++;  break;
            	}
            	cycle = (cycle+1) % 4;
            }

            // make an array of points in the middle of each side
            int[] sides = new int[numExports];
            int fill = 0;
            for(int i=0; i<leftSide; i++) sides[fill++] = 0;
            for(int i=0; i<topSide; i++) sides[fill++] = 1;
            for(int i=0; i<rightSide; i++) sides[fill++] = 2;
            for(int i=0; i<bottomSide; i++) sides[fill++] = 3;

            // rotate the points and find the rotation with the least distance to the side points
            double [] totDist = new double[numExports];
            for(int i=0; i<numExports; i++)
            {
                totDist[i] = 0;
                for(int j=0; j<numExports; j++)
                {
                    Point2D ppCtr = shadowExportList.get((j+i)%numExports).center;
                    double dist = 0;
                    switch (sides[j])
                    {
                    	case 0:		// distance to left side
                    		dist = Math.abs(ppCtr.getX() - cellBounds.getMinX());   break;
                    	case 1:		// distance to top side
                    		dist = Math.abs(ppCtr.getY() - cellBounds.getMaxY());   break;
                    	case 2:		// distance to right side
                    		dist = Math.abs(ppCtr.getX() - cellBounds.getMaxX());   break;
                    	case 3:		// distance to bottom side
                    		dist = Math.abs(ppCtr.getY() - cellBounds.getMinY());   break;
                    }
                    totDist[i] += dist;
                }
            }
            double bestDist = Double.MAX_VALUE;
            int bestIndex = -1;
            for(int i=0; i<numExports; i++)
            {
                if (totDist[i] < bestDist)
                {
                    bestDist = totDist[i];
                    bestIndex = i;
                }
            }

            // assign ports along each side
            for(int i=0; i<leftSide; i++)
            {
            	ShadowExport se = shadowExportList.get((i+bestIndex)%numExports);
                portSide.put(se, new Integer(0));
                portIndex.put(se, new Integer(leftSide-i-1));
                portRotation.put(se, new Integer(leftRot));
            }
            for(int i=0; i<topSide; i++)
            {
            	ShadowExport se = shadowExportList.get((i+leftSide+bestIndex)%numExports);
                portSide.put(se, new Integer(2));
                portIndex.put(se, new Integer(topSide-i-1));
                portRotation.put(se, new Integer(topRot));
            }
            for(int i=0; i<rightSide; i++)
            {
            	ShadowExport se = shadowExportList.get((i+leftSide+topSide+bestIndex)%numExports);
                portSide.put(se, new Integer(1));
                portIndex.put(se, new Integer(i));
                portRotation.put(se, new Integer(rightRot));
            }
            for(int i=0; i<bottomSide; i++)
            {
            	ShadowExport se = shadowExportList.get((i+leftSide+topSide+rightSide+bestIndex)%numExports);
                portSide.put(se, new Integer(3));
                portIndex.put(se, new Integer(i));
                portRotation.put(se, new Integer(bottomRot));
            }
        } else
        {
            // place exports according to their characteristics
        	Collections.sort(shadowExportList, new ShadowExportByName());
            if (ep.isIconGenReverseExportOrder())
                Collections.reverse(shadowExportList);
            for(ShadowExport se : shadowExportList)
            {
            	Export e = se.originals.get(0);
                int index = iconPosition(e);
                portSide.put(se, new Integer(index));
                switch (index)
                {
                    case 0: portIndex.put(se, new Integer(leftSide++));    break;
                    case 1: portIndex.put(se, new Integer(rightSide++));   break;
                    case 2: portIndex.put(se, new Integer(topSide++));     break;
                    case 3: portIndex.put(se, new Integer(bottomSide++));  break;
                }
                int rotation = ViewChanges.iconTextRotation(e, ep);
                portRotation.put(se, new Integer(rotation));
            }
        }

        // determine the size of the "black box" core
        double xSize, ySize;
        if (ep.getIconGenExportPlacement() == 1 && ep.isIconGenExportPlacementExact())
        {
            xSize = curCell.getDefWidth();
            ySize = curCell.getDefHeight();
        } else
        {
            ySize = Math.max(Math.max(leftSide, rightSide), 5) * ep.getIconGenLeadSpacing();
            xSize = Math.max(Math.max(topSide, bottomSide), 3) * ep.getIconGenLeadSpacing();
        }

        // create the "black box"
        NodeInst bbNi = null;
        if (ep.isIconGenDrawBody())
        {
            bbNi = NodeInst.newInstance(Artwork.tech().openedThickerPolygonNode, ep, new Point2D.Double(0,0), xSize, ySize, iconCell);
            if (bbNi == null) return null;
            EPoint[] boxOutline = new EPoint[5];
            if (ep.getIconGenExportPlacement() == 1 && ep.isIconGenExportPlacementExact())
            {
                boxOutline[0] = EPoint.fromLambda(cellBounds.getMinX(), cellBounds.getMinY());
                boxOutline[1] = EPoint.fromLambda(cellBounds.getMinX(), cellBounds.getMaxY());
                boxOutline[2] = EPoint.fromLambda(cellBounds.getMaxX(), cellBounds.getMaxY());
                boxOutline[3] = EPoint.fromLambda(cellBounds.getMaxX(), cellBounds.getMinY());
                boxOutline[4] = EPoint.fromLambda(cellBounds.getMinX(), cellBounds.getMinY());
            } else
            {
                boxOutline[0] = EPoint.fromLambda(-xSize/2, -ySize/2);
                boxOutline[1] = EPoint.fromLambda(-xSize/2,  ySize/2);
                boxOutline[2] = EPoint.fromLambda( xSize/2,  ySize/2);
                boxOutline[3] = EPoint.fromLambda( xSize/2, -ySize/2);
                boxOutline[4] = EPoint.fromLambda(-xSize/2, -ySize/2);
            }
            bbNi.setTrace(boxOutline);

            // put the original cell name on it
            TextDescriptor td = ep.getAnnotationTextDescriptor().withRelSize(ep.getIconGenBodyTextSize());
            bbNi.newVar(Schematics.SCHEM_FUNCTION, curCell.getName(), td);
        }

        // place pins around the Black Box
        int total = 0;
        for(ShadowExport se : shadowExportList)
        {
            // determine location and side of the port
            int portPosition = portIndex.get(se).intValue();
            int index = portSide.get(se).intValue();
            double spacing = ep.getIconGenLeadSpacing();
            double xPos = 0, yPos = 0;
            double xBBPos = 0, yBBPos = 0;
            if (ep.getIconGenExportPlacement() == 1 && ep.isIconGenExportPlacementExact())
            {
                xBBPos = xPos = se.center.getX();
                yBBPos = yPos = se.center.getY();
            } else
            {
                switch (index)
                {
                    case 0:		// left side
                        xBBPos = -xSize/2;
                        xPos = xBBPos - ep.getIconGenLeadLength();
                        if (leftSide*2 < rightSide) spacing = ep.getIconGenLeadSpacing() * 2;
                        yBBPos = yPos = ySize/2 - ((ySize - (leftSide-1)*spacing) / 2 + portPosition * spacing);
                        break;
                    case 1:		// right side
                        xBBPos = xSize/2;
                        xPos = xBBPos + ep.getIconGenLeadLength();
                        if (rightSide*2 < leftSide) spacing = ep.getIconGenLeadSpacing() * 2;
                        yBBPos = yPos = ySize/2 - ((ySize - (rightSide-1)*spacing) / 2 + portPosition * spacing);
                        break;
                    case 2:		// top
                        if (topSide*2 < bottomSide) spacing = ep.getIconGenLeadSpacing() * 2;
                        xBBPos = xPos = xSize/2 - ((xSize - (topSide-1)*spacing) / 2 + portPosition * spacing);
                        yBBPos = ySize/2;
                        yPos = yBBPos + ep.getIconGenLeadLength();
                        break;
                    case 3:		// bottom
                        if (bottomSide*2 < topSide) spacing = ep.getIconGenLeadSpacing() * 2;
                        xBBPos = xPos = xSize/2 - ((xSize - (bottomSide-1)*spacing) / 2 + portPosition * spacing);
                        yBBPos = -ySize/2;
                        yPos = yBBPos - ep.getIconGenLeadLength();
                        break;
                }
            }

            int rotation = portRotation.get(se).intValue();
            if (makeIconExportBase(se.exportName, se.isBus, se.originals.get(0), ep, index, xPos, yPos, xBBPos, yBBPos, iconCell, rotation))
            	total++;
        }

        // if no body, leads, or cell center is drawn, and there is only 1 export, add more
        if (!ep.isIconGenDrawBody() && !ep.isIconGenDrawLeads() && ep.isPlaceCellCenter() && total <= 1)
        {
            NodeInst.newInstance(Generic.tech().invisiblePinNode, ep, new Point2D.Double(0,0), xSize, ySize, iconCell);
        }

        return iconCell;
    }

    /**
     * Method to determine the side of the icon that port "pp" belongs on.
     */
    private int iconPosition(Export pp)
    {
        PortCharacteristic character = pp.getCharacteristic();

        // special detection for power and ground ports
        if (pp.isPower()) character = PortCharacteristic.PWR;
        if (pp.isGround()) character = PortCharacteristic.GND;

        // see which side this type of port sits on
        if (character == PortCharacteristic.IN) return inputSide;
        if (character == PortCharacteristic.OUT) return outputSide;
        if (character == PortCharacteristic.BIDIR) return bidirSide;
        if (character == PortCharacteristic.PWR) return pwrSide;
        if (character == PortCharacteristic.GND) return gndSide;
        if (character.isClock()) return clkSide;
        return inputSide;
    }
    
    /**
	 * Helper method to create an export in an icon.
     * @param pp the Export to build.
     * @param ep EditingPreferences with default sizes and text descriptors.
     * @param index the side (0: left, 1: right, 2: top, 3: bottom).
     * @param xPos the export location
     * @param yPos the export location
     * @param xBBPos the central box location
     * @param yBBPos the central box location.
     * @param np the cell in which to create the export.
     * @param textRotation
     * @return true if the export was created.
     */
    public static boolean makeIconExport(Export pp, EditingPreferences ep, int index,
    	double xPos, double yPos, double xBBPos, double yBBPos, Cell np, int textRotation)
    {
    	boolean isBus = pp.getBasePort().connectsTo(Schematics.tech().bus_arc) && pp.getNameKey().isBus();
    	return makeIconExportBase(pp.getName(), isBus, pp, ep, index, xPos, yPos, xBBPos, yBBPos, np, textRotation);
    }
    
    /**
	 * Helper method to create an export in an icon.
     * @param name the name of the Export to build.
     * @param ep EditingPreferences with default sizes and text descriptors.
     * @param index the side (0: left, 1: right, 2: top, 3: bottom).
     * @param xPos the export location
     * @param yPos the export location
     * @param xBBPos the central box location
     * @param yBBPos the central box location.
     * @param np the cell in which to create the export.
     * @param textRotation
     * @return true if the export was created.
     */
    public static boolean makeIconExportBase(String name, boolean isBus, Export sample, EditingPreferences ep,
            int index, double xPos, double yPos, double xBBPos, double yBBPos,
                                         Cell np, int textRotation)
    {
        // presume "universal" exports (Generic technology)
        NodeProto pinType = Generic.tech().universalPinNode;
        double pinSizeX = 0, pinSizeY = 0;
        if (ep.getIconGenExportTech() != 0)
        {
            // instead, use "schematic" exports (Schematic Bus Pins)
            pinType = Schematics.tech().busPinNode;
            pinSizeX = pinType.getDefWidth(ep);
            pinSizeY = pinType.getDefHeight(ep);
        }

        // determine the type of wires used for leads
        ArcProto wireType = Schematics.tech().wire_arc;
        if (isBus)
        {
            wireType = Schematics.tech().bus_arc;
            pinType = Schematics.tech().busPinNode;
            pinSizeX = pinType.getDefWidth(ep);
            pinSizeY = pinType.getDefHeight(ep);
        }

        // if the export is on the body (no leads) then move it in
        if (!ep.isIconGenDrawLeads())
        {
            xPos = xBBPos;   yPos = yBBPos;
        }

        // make the pin with the port
        NodeInst pinNi = NodeInst.newInstance(pinType, ep, new Point2D.Double(xPos, yPos), pinSizeX, pinSizeY, np);
        if (pinNi == null) return false;

        // export the port that should be on this pin
        PortInst pi = pinNi.getOnlyPortInst();
        Export port = Export.newInstance(np, pi, name, ep, sample.getCharacteristic());
        if (port != null)
        {
            TextDescriptor td = port.getTextDescriptor(Export.EXPORT_NAME);
            if (textRotation != 0) td = td.withRotation(TextDescriptor.Rotation.getRotationAt(textRotation));
            switch (ep.getIconGenExportStyle())
            {
                case 0:		// Centered
                    td = td.withPos(TextDescriptor.Position.CENT);
                    break;
                case 1:		// Inward
                    switch (index)
                    {
                        case 0:	// left side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 1: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 2: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 3: td = td.withPos(TextDescriptor.Position.UP);     break;
                        	}
                        	break;
                        case 1:	// right side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 1: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 2: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 3: td = td.withPos(TextDescriptor.Position.UP);     break;
                        	}
                        	break;
                        case 2:	// top side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 1: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 2: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 3: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        	}
                        	break;
                        case 3:	// bottom side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 1: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 2: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 3: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        	}
                        	break;
                    }
                    break;
                case 2:		// Outward
                    switch (index)
                    {
                        case 0:	// left side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 1: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 2: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 3: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        	}
                        	break;
                        case 1:	// right side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 1: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 2: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 3: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        	}
                        	break;
                        case 2:	// top side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 1: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        		case 2: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 3: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        	}
                        	break;
                        case 3:	// bottom side
                        	switch (textRotation)
                        	{
                        		case 0: td = td.withPos(TextDescriptor.Position.DOWN);   break;
                        		case 1: td = td.withPos(TextDescriptor.Position.LEFT);   break;
                        		case 2: td = td.withPos(TextDescriptor.Position.UP);     break;
                        		case 3: td = td.withPos(TextDescriptor.Position.RIGHT);  break;
                        	}
                        	break;
                    }
                    break;
            }
            port.setTextDescriptor(Export.EXPORT_NAME, td);
            double xOffset = 0, yOffset = 0;
            int loc = ep.getIconGenExportLocation();
            if (!ep.isIconGenDrawLeads()) loc = 0;
            switch (loc)
            {
                case 0:		// port on body
                    xOffset = xBBPos - xPos;   yOffset = yBBPos - yPos;
                    break;
                case 1:		// port on lead end
                    break;
                case 2:		// port on lead middle
                    xOffset = (xPos+xBBPos) / 2 - xPos;
                    yOffset = (yPos+yBBPos) / 2 - yPos;
                    break;
            }
            port.setOff(Export.EXPORT_NAME, xOffset, yOffset);
            port.setAlwaysDrawn(ep.isIconsAlwaysDrawn());
            port.copyVarsFrom(sample);
        }

        // add lead if requested
        if (ep.isIconGenDrawLeads())
        {
            pinType = wireType.findPinProto();
            if (pinType == Schematics.tech().busPinNode)
                pinType = Generic.tech().invisiblePinNode;
            double wid = pinType.getDefWidth(ep);
            double hei = pinType.getDefHeight(ep);
            NodeInst ni = NodeInst.newInstance(pinType, ep, new Point2D.Double(xBBPos, yBBPos), wid, hei, np);
            if (ni != null)
            {
                PortInst head = ni.getOnlyPortInst();
                PortInst tail = pinNi.getOnlyPortInst();
                ArcInst ai = ArcInst.makeInstance(wireType, ep,
                    head, tail, new Point2D.Double(xBBPos, yBBPos),
                        new Point2D.Double(xPos, yPos), null);
                if (ai != null && wireType == Schematics.tech().bus_arc)
                {
                    ai.setHeadExtended(false);
                    ai.setTailExtended(false);
                }
            }
        }
        return true;
    }

	/**
	 * Comparator class for sorting ShadowExports by their name.
	 */
	private static class ShadowExportByName implements Comparator<ShadowExport>
	{
		/**
		 * Method to sort Exports by their angle about the cell center.
		 */
		public int compare(ShadowExport p1, ShadowExport p2)
		{
			String p1Name = p1.exportName;
			String p2Name = p2.exportName;
			return p1Name.compareTo(p2Name);
		}
	}
}
