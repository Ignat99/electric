/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Gerber.java
 * Input/output tool: Gerber input
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class reads files in Gerber files.
 */
public class Gerber extends Input<Object>
{
	private static double UNSCALE = 1000.0;

	private static class StringPart
	{
		char separator;
		double value;
	}

	private static class StandardCircle
	{
		int codeNumber;
		double diameter;
		double holeSize1;
		double holeSize2;
	}

	private static class StandardRectangle
	{
		int codeNumber;
		double width, height;
		double holeWidth, holeHeight;
	}

	private static class GerberLayer
	{
		String layerName;
		boolean mentionedUse;
		boolean polarityDark;
		PrimitiveNode pNp;
		private static int ePrimitiveIndex = 0;
		private static PrimitiveNode [] ePrimitives = null;
		private static Map<String,GerberLayer> allLayers = new HashMap<String,GerberLayer>();

		public GerberLayer(PrimitiveNode pNp)
		{
			this.pNp = pNp;
			polarityDark = true;
			mentionedUse = false;
		}

		public void usingIt()
		{
			if (mentionedUse) return;
			mentionedUse = true;
            System.out.println("Importing layer name '" + layerName + "' (polarity " + (polarityDark ? "dark" : "light") +
            	") into layer " + pNp.getName());
		}

		public static GerberLayer findLayer(String name)
		{
			GerberLayer gl = allLayers.get(name);
			if (gl == null)
			{
				if (ePrimitives == null)
				{
					ePrimitives = new PrimitiveNode[16];
					int j = 0;
					for(int i=0; i<8; i++)
					{
						ePrimitives[j++] = pcbTech.findNodeProto("Signal-" + (i+1) + "-Node");
						ePrimitives[j++] = pcbTech.findNodeProto("Power-" + (i+1) + "-Node");
					}
				}
				gl = new GerberLayer(ePrimitives[ePrimitiveIndex%ePrimitives.length]);
				ePrimitiveIndex++;
				allLayers.put(name, gl);
				gl.layerName = name;
			}
			return gl;
		}

		public void setPolarity(boolean dark)
		{
			polarityDark = dark;
		}
	}

	enum NumberFormat {OMIT_LEADING_ZEROS, OMIT_TRAILING_ZEROS, EXPLICIT_DECIMAL_POINT};

	private GerberPreferences localPrefs;
	private Cell curCell;
	private Map<Integer,StandardCircle> standardCircles = new HashMap<Integer,StandardCircle>();
	private Map<Integer,StandardRectangle> standardRectangles = new HashMap<Integer,StandardRectangle>();
	private NumberFormat currentNumberFormat = NumberFormat.EXPLICIT_DECIMAL_POINT;
	private boolean absoluteCoordinates;
	private int xFormatLeft, xFormatRight, yFormatLeft, yFormatRight;
	private double scaleFactor;
	private boolean fullCircle = false;
	private int currentPrepCode, currentDCode;
	private double lastXValue=0, lastYValue=0, curXValue, curYValue, curIValue, curJValue;
	private String lastPart;
	private static GerberLayer defaultGerberLayer = new GerberLayer(Artwork.tech().filledPolygonNode);
	private GerberLayer currentLayer;
	List<Poly.Point> polygonPoints = null;
	private static Technology pcbTech = Technology.findTechnology("pcb");

	public static class GerberPreferences extends InputPreferences
    {
		private boolean justThisFile;
		private boolean fillPolygons;
		private Map<String,Integer> layerColors;

		public GerberPreferences(boolean factory) {
            super(factory);
            if (!factory)
            {
                justThisFile = !IOTool.isGerberReadsAllFiles();
                fillPolygons = IOTool.isGerberFillsPolygons();
            }

            // cache layer colors
            layerColors = new HashMap<String,Integer>();
			for(int i=0; i<8; i++)
			{
				String layerName = "Signal-" + (i+1) + "-Node";
				PrimitiveNode np = pcbTech.findNodeProto(layerName);
				Layer layer = np.getNodeLayers()[0].getLayer();
				EGraphics gra = factory ? layer.getFactoryGraphics() : layer.getGraphics();
				int colorIndex = 0;
				if (gra.getTransparentLayer() > 0)
					colorIndex = EGraphics.makeIndex(gra.getTransparentLayer()); else
						colorIndex = EGraphics.makeIndex(gra.getColor());
				layerColors.put(layerName, new Integer(colorIndex));

				layerName = "Power-" + (i+1) + "-Node";
				np = pcbTech.findNodeProto(layerName);
				layer = np.getNodeLayers()[0].getLayer();
				gra = factory ? layer.getFactoryGraphics() : layer.getGraphics();
				colorIndex = 0;
				if (gra.getTransparentLayer() > 0)
					colorIndex = EGraphics.makeIndex(gra.getTransparentLayer()); else
						colorIndex = EGraphics.makeIndex(gra.getColor());
				layerColors.put(layerName, new Integer(colorIndex));
			}
        }

        @Override
        public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells, Map<CellId,BitSet> nodesToExpand, Job job)
        {
        	Gerber in = new Gerber(ep, this);
        	if (justThisFile)
        	{
				if (in.openTextInput(fileURL)) return null;
				lib = in.importALibrary(lib, tech, currentCells);
				in.closeInput();
        	} else
        	{
				lib = in.importALibrary(lib, tech, currentCells);
        	}
			return lib;
        }
    }

	/**
	 * Creates a new instance of Gerber.
	 */
	Gerber(EditingPreferences ep, GerberPreferences ap) {
        super(ep);
        localPrefs = ap;
    }

	/**
	 * Method to import a library from disk.
	 * @param lib the library to fill
     * @param currentCells this map will be filled with currentCells in Libraries found in library file
	 * @return the created library (null on error).
	 */
	protected Library importALibrary(Library lib, Technology tech, Map<Library,Cell> currentCells)
	{
		// make the cell
		String cellName = lib.getName();
		curCell = Cell.makeInstance(ep, lib, cellName + "{lay}");

    	if (localPrefs.justThisFile)
    	{
			try
			{
				readFile(lineReader);
			} catch (IOException e)
			{
				System.out.println("ERROR reading Gerber file: " + e.getMessage());
			}
    	} else
    	{
			// initialize the number of directories that need to be searched
			List<String> gerberfiles = new ArrayList<String>();

			// determine the current directory
			String topDirName = TextUtils.getFilePath(lib.getLibFile());

			// find all files that end with ".GBR" and include them in the search
			File topDir = new File(topDirName);
			String [] fileList = topDir.list();
			for(int i=0; i<fileList.length; i++)
			{
				if (!fileList[i].endsWith(".gbr")) continue;
                String fileName = topDirName + fileList[i];
				gerberfiles.add(fileName);
			}
			Collections.sort(gerberfiles);

			// read the file
			try
			{
				for(String fileName : gerberfiles)
				{
			        System.out.println("Reading: " + fileName);
			        URL fileURL = TextUtils.makeURLToFile(fileName);
					if (openTextInput(fileURL)) return null;
					readFile(lineReader);
					closeInput();
				}
			} catch (IOException e)
			{
				System.out.println("ERROR reading Gerber files: " + e.getMessage());
			}
    	}

		return lib;
	}

	/**
	 * Method to read the Gerber file.
	 */
	private void readFile(LineNumberReader lr)
		throws IOException
	{
		currentPrepCode = 1;
		currentLayer = defaultGerberLayer;
		lastPart = null;
		for(;;)
		{
			// get the next line of text
			String line = readSegment(lr);
			if (line == null) break;

			// handle RS274X "%" codes
			if (line.startsWith("%FS"))
			{
				handleFormatStatement(line);
				continue;
			}
			if (line.startsWith("%MO"))
			{
				handleEmbeddedUnits(line);
				continue;
			}
			if (line.startsWith("%IP"))
			{
				handleImagePolarity(line);
				continue;
			}
			if (line.startsWith("%AD"))
			{
				handleStandardShape(line);
				continue;
			}
			if (line.startsWith("%AS"))
			{
				// unknown!
				continue;
			}
			if (line.startsWith("%SF"))
			{
				// unknown!
				continue;
			}
			if (line.startsWith("%IN"))
			{
				// unknown!
				continue;
			}
			if (line.startsWith("%LN"))
			{
				// layer name
				int astPos = line.indexOf('*');
				if (astPos < 0) astPos = line.length();
				String layerName = line.substring(3, astPos);
				currentLayer = GerberLayer.findLayer(layerName);
                continue;
			}
			if (line.startsWith("%LP"))
			{
				// layer polarity
				if (line.charAt(3) == 'D') currentLayer.setPolarity(true); else
					currentLayer.setPolarity(false);
				continue;
			}
			handleOldStyleLine(line, lr);
		}
	}

	private String readSegment(LineNumberReader lr)
		throws IOException
	{
		String line;
		for(;;)
		{
			if (lastPart != null)
			{
				line = lastPart;
				lastPart = null;
			} else
			{
				line = lr.readLine();
				if (line == null) return null;
			}
			if (line.length() > 0) break;
		}

		if (line.charAt(0) != '%')
		{
			// see if line breaks at "*"
			int astPos = line.indexOf('*');
			if (astPos >= 0 && astPos+1 < line.length())
			{
				lastPart = line.substring(astPos+1);
				line = line.substring(0, astPos+1);
			}
		}
		return line;
	}

	private void handleOldStyleLine(String line, LineNumberReader lr)
	{
		boolean foundCoord = false;
		List<StringPart> parts = parseString(line);
		for(StringPart sp : parts)
		{
			if (sp.separator == 'G')
			{
				int prepCode = (int)sp.value;
				switch (prepCode)
				{
					case 04:		// Comment
					case 57:		// Comment
						return;
					case 36:		// start polygon
						if (localPrefs.fillPolygons)
							polygonPoints = new ArrayList<Poly.Point>();
						break;
					case 37:		// end polygon
						if (polygonPoints != null)
						{
							double cX = 0, cY = 0;
							if (polygonPoints.size() == 0)
							{
								System.out.println("Warning: zero-size polygon on line " + lr.getLineNumber());
							} else
							{
								double minX = polygonPoints.get(0).getX();
								double minY = polygonPoints.get(0).getY();
								double maxX = minX, maxY = minY;
								for(Point2D pt : polygonPoints)
								{
									cX += pt.getX();   cY += pt.getY();
									if (pt.getX() < minX) minX = pt.getX();
									if (pt.getX() > maxX) maxX = pt.getX();
									if (pt.getY() < minY) minY = pt.getY();
									if (pt.getY() > maxY) maxY = pt.getY();
								}
								cX /= polygonPoints.size();   cY /= polygonPoints.size();
								Poly.Point [] points = new Poly.Point[polygonPoints.size()];
								for(int i=0; i<polygonPoints.size(); i++)
									points[i] = polygonPoints.get(i);

								if (!currentLayer.polarityDark)
				                {
				                	// see if this layer can be subtracted from another
									boolean subtracted = false;
									Poly subtractPoly = new Poly(points);
									Rectangle2D bounds = new Rectangle2D.Double(minX, minY, maxX-minX, maxY-minY);
									for(Iterator<Geometric> it = curCell.searchIterator(bounds); it.hasNext(); )
									{
										Geometric geom = it.next();
										if (geom instanceof ArcInst) continue;
										NodeInst ni = (NodeInst)geom;
										if (ni.getProto().getTechnology() == pcbTech)
										{
											EPoint[] pts = ni.getTrace();
											Poly.Point[] adjPts = new Poly.Point[pts.length];
											for(int i=0; i<pts.length; i++)
												adjPts[i] = Poly.fromLambda(pts[i].getX()+ni.getAnchorCenterX(),
													pts[i].getY()+ni.getAnchorCenterY());
											Poly existing = new Poly(adjPts);
											PolyMerge merge = new PolyMerge();
											merge.add(Artwork.tech().defaultLayer, existing);
											merge.subtract(Artwork.tech().defaultLayer, subtractPoly);
											List<PolyBase> polys = merge.getMergedPoints(Artwork.tech().defaultLayer, true);
											for(PolyBase pb : polys)
											{
												Point2D [] newPts = pb.getPoints();
												ni.setTrace(newPts);
												subtracted = true;
												break;
											}
										}
									}
									if (subtracted) break;
				                }
								PrimitiveNode pNp = currentLayer.pNp;
								currentLayer.usingIt();
								Point2D ctr = new Point2D.Double(cX, cY);
								double width = maxX - minX;
								double height = maxY - minY;
								NodeInst ni = NodeInst.makeInstance(pNp, ep, ctr, width, height, curCell);
				                ni.setTrace(points);
							}
							polygonPoints = null;
						}
						break;
					case 70:		// inches
						scaleFactor = 25400000.0/UNSCALE;		// 1 inch = 25,400,000 nanometers
						break;
					case 71:		// millimeters
						scaleFactor = 1000000.0/UNSCALE;		// 1 millimeter = 1,000,000 nanometers
						break;
					case 75:		// full circles
						fullCircle = true;
						break;
					case 90:		// Absolute coordinates
						absoluteCoordinates = true;
						break;
					case 91:		// Relative coordinates
						absoluteCoordinates = false;
						break;
					case 1: case 2: case 3: case 10: case 11: case 12: case 60:
						currentPrepCode = prepCode;
						break;
				}
				continue;
			}
			if (sp.separator == 'D')
			{
				int dCode = (int)sp.value;
				if (dCode == 1 || dCode == 2) currentDCode = dCode;
				continue;
			}
			if (sp.separator == 'X')
			{
				curXValue = sp.value;
				foundCoord = true;
				continue;
			}
			if (sp.separator == 'Y')
			{
				curYValue = sp.value;
				foundCoord = true;
				continue;
			}
			if (sp.separator == 'I')
			{
				curIValue = sp.value;
				foundCoord = true;
				continue;
			}
			if (sp.separator == 'J')
			{
				curJValue = sp.value;
				foundCoord = true;
				continue;
			}
		}

		if (!foundCoord) return;

		// handle lines
		if (currentPrepCode == 12 || currentPrepCode == 11 || currentPrepCode == 01 ||
			currentPrepCode == 10 || currentPrepCode == 60)
		{
			switch (currentPrepCode)
			{
				case 12: curXValue /= 100;  curYValue /= 100;  break;
				case 11: curXValue /= 10;   curYValue /= 10;   break;
				case 10: curXValue *= 10;   curYValue *= 10;   break;
				case 60: curXValue *= 100;  curYValue *= 100;  break;
			}
			if (!absoluteCoordinates) { curXValue += lastXValue;   curYValue += lastYValue; }
			if (currentDCode == 1)
			{
				Point2D ctr = new Point2D.Double((curXValue+lastXValue)/2*scaleFactor,
					(curYValue+lastYValue)/2*scaleFactor);
				double width = Math.abs(curXValue - lastXValue)*scaleFactor;
				double height = Math.abs(curYValue - lastYValue)*scaleFactor;
				Poly.Point pt1 = Poly.fromLambda(lastXValue*scaleFactor, lastYValue*scaleFactor);
				Poly.Point pt2 = Poly.fromLambda(curXValue*scaleFactor, curYValue*scaleFactor);
				Poly.Point [] points = new Poly.Point[] {pt1, pt2};
				if (polygonPoints != null)
				{
					addPolygonPoints(points);
				} else
				{
					PrimitiveNode pNp = currentLayer.pNp;
					currentLayer.usingIt();
					NodeInst ni = NodeInst.makeInstance(pNp, ep, ctr, width, height, curCell);
					if (width != 0 || height != 0)
						ni.setTrace(points);
				}
			} else if (currentDCode == 3)
			{
				PrimitiveNode pNp = Artwork.tech().filledCircleNode;
				Point2D ctr = new Point2D.Double(curXValue*scaleFactor, curYValue*scaleFactor);
				double width = 1;
				double height = 1;
				NodeInst.makeInstance(pNp, ep, ctr, width, height, curCell);				
			}
			lastXValue = curXValue;
			lastYValue = curYValue;
		}

		// handle circles
		if (currentPrepCode == 2 || currentPrepCode == 3)
		{
			if (!absoluteCoordinates) { curXValue += lastXValue;   curYValue += lastYValue; }
			if (currentDCode == 1)
			{
				Point2D ctr = new Point2D.Double((lastXValue+curIValue)*scaleFactor,
					(lastYValue+curJValue)*scaleFactor);
				Point2D curveStart = new Point2D.Double(lastXValue*scaleFactor, lastYValue*scaleFactor);
				Point2D curveEnd = new Point2D.Double(curXValue*scaleFactor, curYValue*scaleFactor);
				if (currentPrepCode == 3) { Point2D swap = curveStart;  curveStart = curveEnd;  curveEnd = swap; }
				double diameter = ctr.distance(curveStart) * 2;
				double rotation = GenMath.figureAngle(ctr, curveEnd)*Math.PI/1800;
				double angle = GenMath.figureAngle(ctr, curveStart)*Math.PI/1800 - rotation;
				if (angle < 0) angle += Math.PI * 2;
				Poly.Point [] pointList = Artwork.fillEllipse(ctr, diameter, diameter, rotation, angle);
				if (polygonPoints != null)
				{
					addPolygonPoints(pointList);
				} else
				{
					currentLayer.usingIt();
					NodeInst ni = NodeInst.makeInstance(Artwork.tech().circleNode, ep, ctr, diameter, diameter, curCell);
					ni.setArcDegrees(rotation, angle, ep);
					Integer color = localPrefs.layerColors.get(currentLayer.pNp.getName());
					if (color != null)
						ni.newVar(Artwork.ART_COLOR, color, ep);
				}
			}

			lastXValue = curXValue;
			lastYValue = curYValue;
		}
	}

	private void addPolygonPoints(Poly.Point[] pts)
	{
		if (polygonPoints.size() != 0)
		{
			Point2D first = polygonPoints.get(0);
			Point2D last = polygonPoints.get(polygonPoints.size()-1);
			Point2D firstNew = pts[0];
			Point2D lastNew = pts[pts.length-1];

			double dist1 = first.distance(firstNew);
			double dist2 = first.distance(lastNew);
			double dist3 = last.distance(firstNew);
			double dist4 = last.distance(lastNew);
			double minDist = Math.min(Math.min(dist1, dist2), Math.min(dist3, dist4));
			if (dist1 == minDist)
			{
				for(int i=1; i<pts.length; i++) polygonPoints.add(0, pts[i]);
				return;
			}
			if (dist2 == minDist)
			{
				for(int i=pts.length-2; i>=0; i--) polygonPoints.add(0, pts[i]);
				return;
			}
			if (dist3 == minDist)
			{
				for(int i=1; i<pts.length; i++) polygonPoints.add(pts[i]);
				return;
			}
			if (dist4 == minDist)
			{
				for(int i=pts.length-2; i>=0; i--) polygonPoints.add(pts[i]);
				return;
			}
		}
		for(int i=0; i<pts.length; i++) polygonPoints.add(pts[i]);
	}

	/**
	 * Handle the %FS format statement.
	 * It has this syntax:
	 *     %FS{L|T|D}{A|I}(Nn)(Gn)(Xa)(Ya)(Zc)(Dn)(Mn)*%
	 * where:
	 * L  = leading zeros omitted
	 * T  = trailing zeros omitted
	 * D  = explicit decimal point (i.e. no zeros omitted)
	 * A  = absolute coordinate mode
	 * I  = incremental coordinate mode
	 * Nn = sequence number, where n is number of digits (rarely used)
	 * Gn = preparatory function code (rarely used)
	 * Xa = format of input data (5.5 is max)
	 * Yb = format of input data
	 * Zb = format of input data (Z is rarely if ever seen)
	 * Dn = draft code
	 * Mn = miscellaneous code
	 *
	 * @param line the format statement line.
	 */
	private void handleFormatStatement(String line)
	{
		switch (line.charAt(3))
		{
			case 'L': currentNumberFormat = NumberFormat.OMIT_LEADING_ZEROS;     break;
			case 'T': currentNumberFormat = NumberFormat.OMIT_TRAILING_ZEROS;    break;
			case 'D': currentNumberFormat = NumberFormat.EXPLICIT_DECIMAL_POINT; break;
		}
		switch (line.charAt(4))
		{
			case 'A': absoluteCoordinates = true;   break;
			case 'I': absoluteCoordinates = false;  break;
		}
		int pos = 5;
		for(;;)
		{
			if (line.charAt(pos) == '*') break;
			int value = TextUtils.atoi(line.substring(pos+1));
			switch (line.charAt(pos))
			{
				case 'N': break;
				case 'G': break;
				case 'X':
					xFormatLeft = value/10;
					xFormatRight = value%10;
					break;
				case 'Y':
					yFormatLeft = value/10;
					yFormatRight = value%10;
					break;
				case 'Z': break;
				case 'D': break;
				case 'M': break;
			}
			pos++;
			while (Character.isDigit(line.charAt(pos))) pos++;
		}
	}

	/**
	 * Handle the %MO embedded units line.
	 * It has this syntax:
	 *     %MOIN*%    for inches
	 *     %MOMM*%    for millimeters
	 * @param line the embedded units line.
	 */
	private void handleEmbeddedUnits(String line)
	{
		if (line.equals("%MOIN*%"))
		{
			scaleFactor = 25400000.0/UNSCALE;		// 1 inch = 25,400,000 nanometers
		}
		if (line.equals("%MOMM*%"))
		{
			scaleFactor = 1000000.0/UNSCALE;		// 1 millimeter = 1,000,000 nanometers
		}
	}

	/**
	 * Handle the %IP image polarity line.
	 * It has this syntax:
	 *     %IPPOS*%    for positive
	 *     %IPNEG*%    for negative
	 * @param line the image polarity line.
	 */
	private void handleImagePolarity(String line)
	{
	}

	/**
	 * Handle the %AD standard circle/rectangle line.
	 * It has this syntax:
	 *     %ADD{code}C,{$1}X{$2}X{$3}*%
	 * where
	 *  AD -      aperture description parameter
	 *  D{code}   d-code to which this aperture is assigned (10-999)
	 *  C         tells 274X this is a circle macro
	 *  $1        value (inches or millimeters) of the outside diameter
	 *  $2        optional, if present defines the diameter of the hole
	 *  $3        optional, if present the $2 and $3 represent the size of
     *            a rectangular hole.
     * OR:
	 *     %ADD{code}R,{$1}X{$2}X{$3}X{$4}*%
	 * where
	 *  AD -      aperture description parameter
	 *  D{code}   d-code to which this aperture is assigned (10-999)
	 *  R         tells 274X this is a rectangle macro
	 *  $1        value (inches or millimeters) of rect's length in X
	 *  $2        value if rect's height in Y
	 *  $3        optional, if present defines the diameter of the hole
	 *  $4        optional, if present the $2 and $3 represent the size of
	 *            a rectangular hole.
	 * @param line the standard circle line.
	 */
	private void handleStandardShape(String line)
	{
		int codeNumber = TextUtils.atoi(line.substring(4));
		int pos = 4;
		while (Character.isDigit(line.charAt(pos))) pos++;
		String params = line.substring(pos+1);
		List<StringPart> parts = parseString(params);
		if (line.charAt(pos) == 'C')
		{
			if (parts.size() < 1 || parts.size() > 3)
			{
				System.out.println("Illegal Standard Circle statement: '" + line + "' ('" + params + "' has " + parts.size() + " parts)");
				return;
			}
			StandardCircle sc = new StandardCircle();
			sc.codeNumber = codeNumber;
			sc.diameter = parts.get(0).value;
			if (parts.size() > 1) sc.holeSize1 = parts.get(1).value; else sc.holeSize1 = -1;
			if (parts.size() > 2) sc.holeSize2 = parts.get(2).value; else sc.holeSize2 = -1;
			standardCircles.put(new Integer(codeNumber), sc);
		} else if (line.charAt(pos) == 'R')
		{
			if (parts.size() != 2 && parts.size() != 4)
			{
				System.out.println("Illegal Standard Rectangle statement: '" + line + "' ('" + params + "' has " + parts.size() + " parts)");
				return;
			}
			StandardRectangle sr = new StandardRectangle();
			sr.codeNumber = codeNumber;
			sr.width = parts.get(0).value;
			sr.height = parts.get(1).value;
			if (parts.size() > 2)
			{
				sr.holeWidth = parts.get(2).value;
				sr.holeHeight = parts.get(3).value;
			} else
			{
				sr.holeWidth = -1;
				sr.holeHeight = -1;
			}
			standardRectangles.put(new Integer(codeNumber), sr);
		}
	}

	private List<StringPart> parseString(String str)
	{
		List<StringPart> parts = new ArrayList<StringPart>();
		int pos = 0;
		StringBuffer sb = new StringBuffer();
		for(;;)
		{
			if (str.charAt(pos) == '*') break;
			StringPart sp = new StringPart();
			sp.separator = str.charAt(pos);
			pos++;
			sb.delete(0, sb.length());
			boolean foundDecimal = false, neg = false;
			for(;;)
			{
				if (pos >= str.length()) break;
				char nxt = str.charAt(pos++);
				if (nxt != '-' && nxt != '.' && !Character.isDigit(nxt)) break;
				if (nxt == '-') { neg = true;   continue; }
				sb.append(nxt);
				if (nxt == '.') foundDecimal = true;
			}
			pos--;
			if (foundDecimal || sp.separator == 'G' || sp.separator == 'D' || sp.separator == 'M')
			{
				sp.value = TextUtils.atof(sb.toString());
				if (sp.value == 4) break;
			} else
			{
				int left = xFormatLeft, right = xFormatRight;
				if (sp.separator == 'Y') { left = yFormatLeft;   right = yFormatRight; }
				switch (currentNumberFormat)
				{
					case OMIT_LEADING_ZEROS:
						int loc = sb.length() - right;
						if (loc < 0) loc = 0;
						sb.insert(loc, '.');
						break;
					case OMIT_TRAILING_ZEROS:
						while (sb.length() < left) sb.append('0');
						sb.insert(left, '.');
						break;
					case EXPLICIT_DECIMAL_POINT:
						break;
				}
				sp.value = TextUtils.atof(sb.toString());
			}
			if (neg) sp.value = -sp.value;
			parts.add(sp);
		}
		return parts;
	}
}
