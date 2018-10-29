/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PadGenerator.java
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
package com.sun.electric.tool.generator;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.lib.LibFile;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.routing.AutoStitch;
import com.sun.electric.tool.routing.AutoStitch.AutoOptions;
import com.sun.electric.tool.user.CellChangeJobs;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ViewChanges;
import com.sun.electric.tool.user.IconParameters;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Class to generate pad frames from a specification file.
 */
public class PadGenerator
{
	/**
	 * Method to generate a pad frame from an array file.
	 * Schedules a change job to generate the pad frame.
	 * @param destLib destination library.
	 * @param fileName the array file name.
     * @param ep EditingPreferences
	 */
	public static void makePadFrame(Library destLib, String fileName, EditingPreferences ep)
	{
		if (fileName == null) return;
		new MakePadFrame(destLib, fileName);
	}

	/**
	 * Method to generate a pad frame from an array file.
	 * Presumes that it is being run from inside a change job.
	 * @param destLib destination library.
	 * @param fileName the array file name.
	 * @param job the Job running this task (null if none).
	 */
	public static Cell makePadFrameUseJob(Library destLib, String fileName, Job job, EditingPreferences ep)
	{
		PadGenerator pg = new PadGenerator(destLib, fileName, ep);
		return pg.makePadFrame(job);
	}

	private static class MakePadFrame extends Job
	{
		private Library destLib;
		private String fileName;
		private Cell frameCell;

		private MakePadFrame(Library destLib, String fileName)
		{
			super("Pad Frame Generator", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.destLib = destLib;
			this.fileName = fileName;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			frameCell = makePadFrameUseJob(destLib, fileName, this, getEditingPreferences());
			fieldVariableChanged("frameCell");
			return true;
		}

        @Override
		public void terminateOK()
		{
			UserInterface ui = Job.getUserInterface();
			ui.displayCell(frameCell);
		}
	}

	private Library destLib;						// destination library
	private String fileName;						// name of file with pad array instructions
	private String padframename;					// name of pad frame cell
	private String corename;						// core cell to stick in pad frame
	private int lineno;								// line no of the pad array file we are processing
	private Library cellLib;						// library containing pad cells
	private boolean copycells;						// if we copy cells into the library with the pad ring
	private List<View> views;						// list of strings defining views of pad frame to create.
	private int angle;								// angle of placed instances
	private HashMap<String,ArrayAlign> alignments;	// how to align adjacent instances
	private HashMap<String,PadExports> exports;		// which ports to export
	private List<Object> orderedCommands;			// list of orderedCommands to do
	private boolean coreAllOnOneSide = false;
    private final EditingPreferences ep;

    private static class ArrayAlign
	{
		int lineno;
		String cellname;
		String inport;
		String outport;
	}

	private static class PadExports
	{
		int lineno;
		String cellname;
		String padname;
		String corename;
	}

	private static class PlacePad
	{
		int lineno;
		String cellname;
		String exportsname;
		int gap;
		NodeInst ni;
		List<PortAssociate> associations;
		List<ExportAssociate> exportAssociations;
		Double locx;
		Double locy;
	}

	private static class Rotation
	{
		int angle;
	}

	private static class ReverseDirection
	{
	}

	private static class PortAssociate
	{
		boolean export;
		String portname;
		String assocname;
	}

	private static class ExportAssociate
	{
		String padportName;
		String exportName;
	}

	private PadGenerator(Library destLib, String fileName, EditingPreferences ep)
	{
        this.ep = ep;
		this.destLib = destLib;
		this.fileName = fileName;
		alignments = new HashMap<String,ArrayAlign>();
		exports = new HashMap<String,PadExports>();
		views = new ArrayList<View>();
		angle = 0;
		lineno = 1;
		orderedCommands = new ArrayList<Object>();
    }

	private Cell makePadFrame(Job job)
	{
		String lineRead;

		File inputFile = new File(fileName);
		if (inputFile == null || !inputFile.canRead())
		{
			System.out.println("Error reading file "+fileName);
			return null;
		}

		try
		{
			FileReader readFile = new FileReader(inputFile);
			BufferedReader readLine = new BufferedReader(readFile);

			lineRead = readLine.readLine();
			while (lineRead != null)
			{
				StringTokenizer str = new StringTokenizer(lineRead, " \t");
				if (str.hasMoreTokens())
				{
					String keyWord = str.nextToken();

					if (keyWord.charAt(0) != ';')
					{
						do
						{
							if (keyWord.equals("celllibrary"))
							{
								if (!processCellLibrary(str)) return null;
								continue;
							} else if (keyWord.equals("views"))
							{
								if (!processViews(str)) return null;
								continue;
							} else if (keyWord.equals("cell"))
							{
								if (!processCell(str)) return null;
								continue;
							} else if (keyWord.equals("core"))
							{
								if (!processCore(str)) return null;
								continue;
							} else if (keyWord.equals("rotate"))
							{
								if (!processRotate(str)) return null;
								continue;
							} else if (keyWord.equals("reverse"))
							{
								if (!processReverse(str)) return null;
								continue;
							} else if (keyWord.equals("align"))
							{
								if (!processAlign(str)) return null;
								continue;
							} else if (keyWord.equals("export"))
							{
								if (!processExport(str)) return null;
								continue;
							} else if (keyWord.equals("place"))
							{
								if (!processPlace(str)) return null;
								continue;
							} else if (keyWord.equals("coreExportsAllOnOneSideOfIcon")) {
								coreAllOnOneSide = true;
								continue;
							}
							System.out.println("Line " + lineno + ": unknown keyword'" + keyWord + "'");
							break;

						} while (str.hasMoreTokens());
					}
				}

				lineRead = readLine.readLine();
				lineno++;
			}
		} catch (IOException e1) {}

		Cell frameCell = createPadFrames(job);
		return frameCell;
	}

	/**
	 * Process the celllibrary keyword
	 * @return true on success, false on error.
	 */
	private boolean processCellLibrary(StringTokenizer str)
	{
		String keyWord;
		if (str.hasMoreTokens())
		{
			keyWord = str.nextToken();

			URL fileURL = TextUtils.makeURLToFile(keyWord);

			cellLib = Library.findLibrary(TextUtils.getFileNameWithoutExtension(fileURL));
			if (cellLib == null)
			{
				// library does not exist: see if in same directory is pad frame file
				StringBuilder errmsg = new StringBuilder();
				String fileDir = TextUtils.getFilePath(TextUtils.makeURLToFile(fileName));
				fileURL = TextUtils.makeURLToFile(fileDir + keyWord);
				if (!TextUtils.URLExists(fileURL, errmsg))
				{
					// library does not exist: see if file can be found locally
					if (!TextUtils.URLExists(fileURL, errmsg))
					{
						// try the Electric library area
						fileURL = LibFile.getLibFile(keyWord);
						if (!TextUtils.URLExists(fileURL, errmsg))
						{
							System.out.println(errmsg.toString());
							return false;
						}
					}
				}

				FileType style = FileType.DEFAULTLIB;
				if (TextUtils.getExtension(fileURL).equals("txt")) style = FileType.READABLEDUMP;
				if (TextUtils.getExtension(fileURL).equals("elib")) style = FileType.ELIB;
				cellLib = LibraryFiles.readLibrary(ep, fileURL, null, style, false);
				if (cellLib == null)
				{
					err("cannot read library " + keyWord);
					return false;
				}
			}
		}

		if (str.hasMoreTokens())
		{
			keyWord = str.nextToken();
			if (keyWord.equals("copy")) copycells = true;
		}
		return true;
	}

	/**
	 * Process any Views.
	 * @return true on success, false on error.
	 */
	private boolean processViews(StringTokenizer str)
	{
		String keyWord;
		while (str.hasMoreTokens())
		{
			keyWord = str.nextToken();
			View view = View.findView(keyWord);
			if (view != null)
				views.add(view);
			else
				err("Unknown view '" + keyWord + "', ignoring");
		}
		return true;
	}

	/**
	 * Process the cell keyword
	 * @return true on success, false on error.
	 */
	private boolean processCell(StringTokenizer str)
	{
		if (str.hasMoreTokens())
		{
			padframename = str.nextToken();
			return true;
		}
		return false;
	}

	/**
	 * Process the core keyword
	 * @return true on success, false on error.
	 */
	private boolean processCore(StringTokenizer str)
	{
		if (str.hasMoreTokens())
		{
			corename = str.nextToken();
			return true;
		}
		return false;
	}

	/**
	 * Process the rotate keyword
	 * @return true on success, false on error.
	 */
	private boolean processRotate(StringTokenizer str)
	{
		String keyWord;
		int angle = 0;
		if (str.hasMoreTokens())
		{
			keyWord = str.nextToken();
			if (keyWord.equals("c"))
			{
				angle = 2700;
			} else if (keyWord.equals("cc"))
			{
				angle = 900;
			} else
			{
				System.out.println("Line " + lineno + ": incorrect rotation " + keyWord);
				return false;
			}
			Rotation rot = new Rotation();
			rot.angle = angle;
			orderedCommands.add(rot);
			return true;
		}
		return false;
	}

	private boolean processReverse(StringTokenizer str)
	{
		orderedCommands.add(new ReverseDirection());
		return true;
	}

	/**
	 * Process the align keyword
	 * @return true on success, false on error.
	 */
	private boolean processAlign(StringTokenizer str)
	{
		String keyWord;
		ArrayAlign aa = new ArrayAlign();
		aa.lineno = lineno;
		keyWord = str.nextToken();

		if (keyWord.equals(""))
		{
			System.out.println("Line " + lineno + ": missing 'cell' name");
			return false;
		}
		aa.cellname = keyWord;

		keyWord = str.nextToken();

		if (keyWord.equals(""))
		{
			System.out.println("Line " + lineno + ": missing 'in port' name");
			return false;
		}
		aa.inport = keyWord;

		keyWord = str.nextToken();

		if (keyWord.equals(""))
		{
			System.out.println("Line " + lineno + ": missing 'out port' name");
			return false;
		}
		aa.outport = keyWord;
		alignments.put(aa.cellname, aa);
		return true;
	}

	/**
	 * Process the export keyword
	 * @return true on success, false on error.
	 */
	private boolean processExport(StringTokenizer str)
	{
		String keyWord;
		PadExports pe = new PadExports();
		pe.lineno = lineno;
		pe.padname = null;
		pe.corename = null;

		keyWord = str.nextToken();
		if (keyWord.equals(""))
		{
			System.out.println("Line " + lineno + ": missing 'cell' name");
			return false;
		}
		pe.cellname = keyWord;

		if (str.hasMoreTokens())
		{
			keyWord = str.nextToken();
			pe.padname = keyWord;
			if (str.hasMoreTokens())
			{
				keyWord = str.nextToken();
				pe.corename = keyWord;
			}
		}
		exports.put(pe.cellname, pe);
		return true;
	}

	private boolean processPlace(StringTokenizer str)
	{
		PlacePad pad = new PlacePad();
		pad.lineno = lineno;
		pad.exportsname = null;
		pad.gap = 0;
		pad.ni = null;
		pad.associations = new ArrayList<PortAssociate>();
		pad.exportAssociations = new ArrayList<ExportAssociate>();
		pad.locx = null;
		pad.locy = null;
		if (!str.hasMoreTokens())
		{
			err("Cell name missing");
			return false;
		}
		pad.cellname = str.nextToken();

		while (str.hasMoreTokens())
		{
			String keyWord = str.nextToken();

			if (keyWord.equals("export"))
			{
				// export xxx=xxxx
				if (!str.hasMoreTokens())
				{
					err("Missing export assignment after 'export' keyword");
					return false;
				}
				keyWord = str.nextToken();
				ExportAssociate ea = new ExportAssociate();
				ea.padportName = getLHS(keyWord);
				if (ea.padportName == null)
				{
					err("Bad export assignment after 'export' keyword");
					return false;
				}
				ea.exportName = getRHS(keyWord, str);
				if (ea.exportName == null)
				{
					err("Bad export assignment after 'export' keyword");
					return false;
				}
				pad.exportAssociations.add(ea);
			} else
			{
				// name=xxxx or gap=xxxx
				String lhs = getLHS(keyWord);
				String rhs = getRHS(keyWord, str);
				if (lhs == null || rhs == null)
				{
					err("Parse error on assignment of " + keyWord);
					return false;
				}
				if (lhs.equals("gap"))
				{
					try
					{
						pad.gap = Integer.parseInt(rhs);
					} catch (java.lang.NumberFormatException e)
					{
						err("Error parsing integer for 'gap' = " + rhs);
						return false;
					}
				} else if (lhs.equals("name"))
				{
					pad.exportsname = rhs;
				} else if (lhs.equals("x"))
				{
					try
					{
						pad.locx = new Double(rhs);
					} catch (NumberFormatException e)
					{
						System.out.println(e.getMessage());
						pad.locx = null;
					}
				} else if (lhs.equals("y"))
				{
					try
					{
						pad.locy = new Double(rhs);
					} catch (NumberFormatException e)
					{
						System.out.println(e.getMessage());
						pad.locy = null;
					}
				} else
				{
					// port association
					PortAssociate pa = new PortAssociate();
					pa.export = false;
					pa.portname = lhs;
					pa.assocname = rhs;
					pad.associations.add(pa);
				}
			}
		}
		orderedCommands.add(pad);
		return true;
	}

	private String getLHS(String keyword)
	{
		if (keyword.indexOf("=") != -1)
		{
			return keyword.substring(0, keyword.indexOf("="));
		}
		return keyword;
	}

	private String getRHS(String keyword, StringTokenizer str)
	{
		if (keyword.indexOf("=") != -1)
		{
			if (keyword.substring(keyword.indexOf("=") + 1).equals(""))
			{
				// LHS= RHS
				if (!str.hasMoreTokens()) return null;
				return str.nextToken();
			}

			// LHS=RHS
			return keyword.substring(keyword.indexOf("=") + 1);
		}

		if (!str.hasMoreTokens()) return null;
		keyword = str.nextToken();
		if (keyword.equals("="))
		{
			// LHS = RHS
			if (!str.hasMoreTokens()) return null;
			return str.nextToken();
		}

		// LHS =RHS
		return keyword.substring(keyword.indexOf("=") + 1);
	}

	/**
	 * Print the error message with the current line number.
	 * @param msg
	 */
	private void err(String msg)
	{
		System.out.println("Line " + lineno + ": " + msg);
	}

	private Cell createPadFrames(Job job)
	{
		Cell frameCell = null;
		if (views.size() == 0)
		{
			frameCell = createPadFrame(padframename, View.LAYOUT, job);
		} else
		{
			for (View view : views)
			{
				if (view == View.SCHEMATIC) view = View.ICON;
				frameCell = createPadFrame(padframename, view, job);
			}
		}
		return frameCell;

	}

	private Cell createPadFrame(String name, View view, Job job)
	{
		angle = 0;

		// first, try to create cell
		CellName n = CellName.parseName(name);
		if (n != null && (n.getView() == null || n.getView() == View.UNKNOWN))
		{
			// no view in cell name, append appropriately
			if (view == null)
			{
				name = name + "{lay}";
			} else
			{
				if (view == View.ICON)
				{
					// create a schematic, place icons of pads in it
					name = name + "{sch}";
				} else
				{
					name = name + "{" + view.getAbbreviation() + "}";
				}
			}
		}

		Cell framecell = Cell.makeInstance(ep, destLib, name);
		if (framecell == null)
		{
			System.out.println("Could not create pad frame Cell: " + name);
			return null;
		}

		List<Export> padPorts = new ArrayList<Export>();
		List<Export> corePorts = new ArrayList<Export>();

		NodeInst lastni = null;
		int lastRotate = 0;
		String lastpadname = null;
		boolean reversed = false;

		// cycle through all orderedCommands, doing them
		for (Object obj : orderedCommands)
		{
			// Rotation commands are ordered with respect to Place commands.
			if (obj instanceof Rotation)
			{
				angle = (angle + ((Rotation) obj).angle) % 3600;
				continue;
			}
			if (obj instanceof ReverseDirection)
			{
				reversed = !reversed;
				continue;
			}

			// otherwise this is a Place command
			PlacePad pad = (PlacePad) obj;
			lineno = pad.lineno;

			// get cell
			String cellname = pad.cellname;
			if (!cellname.endsWith("}"))
			{
				if (view != null) cellname = cellname + "{" + view.getAbbreviation() + "}";
			}
			Cell cell = cellLib.findNodeProto(cellname);
			if (cell == null)
			{
				err("Could not create pad Cell: " + cellname);
				continue;
			}

			// if copying cell, copy it into current library
			if (copycells)
			{
				Cell existing = cell;
				cell = null;
				for(Iterator<Cell> cIt = destLib.getCells(); cIt.hasNext(); )
				{
					Cell thereCell = cIt.next();
					if (thereCell.getName().equals(existing.getName()) && thereCell.getView() == existing.getView())
					{
						cell = thereCell;
						break;
					}
				}
				if (cell == null)
				{
					List<Cell> fromCells = new ArrayList<Cell>();
					fromCells.add(existing);
					CellChangeJobs.copyRecursively(fromCells, destLib, false, false, false, true, true, null, ep);
					for(Iterator<Cell> cIt = destLib.getCells(); cIt.hasNext(); )
					{
						Cell thereCell = cIt.next();
						if (thereCell.getName().equals(existing.getName()) && thereCell.getView() == existing.getView())
						{
							cell = thereCell;
							break;
						}
					}
					if (cell == null)
					{
						err("Could not copy in pad Cell " + cellname);
						continue;
					}
				}
			}

			// get array alignment for this cell
			ArrayAlign aa = alignments.get(pad.cellname);
			if (aa == null)
			{
				err("No port alignment for cell " + pad.cellname);
				continue;
			}

			int gapx = 0, gapy = 0;
			double centerX = 0, centerY = 0;
			if (lastni != null)
			{
				// get info on last nodeinst created
				ArrayAlign lastaa = alignments.get(lastpadname);

				// get previous node's outport - use it to place this nodeinst
				PortProto pp = (lastni.getProto()).findPortProto(lastaa.outport);
				if (pp == null)
				{
					err("no port called '" + lastaa.outport + "' on " + lastni);
					continue;
				}

				Poly poly = (lastni.findPortInstFromProto(pp)).getPoly();
				centerX = poly.getCenterX();
				centerY = poly.getCenterY();
			}

			Point2D pointCenter = new Point2D.Double(centerX, centerY);
			boolean flipLR = false;
			boolean flipUD = false;
			if (reversed) flipUD = true;
			Orientation orient = Orientation.fromJava(angle, flipLR, flipUD);
			NodeInst ni = NodeInst.makeInstance(cell, ep, pointCenter, cell.getDefWidth(), cell.getDefHeight(), framecell, orient, null);
			if (ni == null)
			{
				err("problem creating" + cell + " instance");
				continue;
			}

			if (lastni != null)
			{
				int gap = pad.gap;
				if (reversed) gap = -gap;
				switch (lastRotate)
				{
					case 0:	gapx = gap;      gapy = 0;     break;
					case 900:  gapx = 0;     gapy = gap;   break;
					case 1800: gapx = -gap;  gapy = 0;     break;
					case 2700: gapx = 0;     gapy = -gap;  break;
				}
				PortProto inport = cell.findPortProto(aa.inport);
				if (inport == null)
				{
					err("No port called '" + aa.inport + "' on " + cell);
					continue;
				}
				Poly poly = ni.findPortInstFromProto(inport).getPoly();
				double tempx = centerX - poly.getCenterX() + gapx;
				double tempy = centerY - poly.getCenterY() + gapy;
				ni.move(tempx, tempy);
			}

			double dx = 0, dy = 0;
			if (pad.locx != null)
				dx = pad.locx.doubleValue() - ni.getAnchorCenterX();
			if (pad.locy != null)
				dy = pad.locy.doubleValue() - ni.getAnchorCenterY();
			ni.move(dx, dy);

			// create exports

			// get export for this cell, if any
			if (pad.exportsname != null)
			{
				PadExports pe = exports.get(pad.cellname);
				if (pe != null)
				{
					// pad export
					Export pppad = cell.findExport(pe.padname);
					if (pppad == null)
					{
						err("no port called '" + pe.padname + "' on Cell " + cell.noLibDescribe());
					} else
					{
						pppad = Export.newInstance(framecell, ni.findPortInstFromProto(pppad), pad.exportsname, ep);
						if (pppad == null) err("Creating export " + pad.exportsname); else
						{
							TextDescriptor td = pppad.getTextDescriptor(Export.EXPORT_NAME);
							pppad.setTextDescriptor(Export.EXPORT_NAME, td.withAbsSize(14));
							padPorts.add(pppad);
						}
					}

					// core export
					if (pe.corename != null)
					{
						Export ppcore = cell.findExport(pe.corename);
						if (ppcore == null)
						{
							err("no port called '" + pe.corename + "' on Cell " + cell.noLibDescribe());
						} else
						{
							ppcore = Export.newInstance(framecell, ni.findPortInstFromProto(ppcore), "core_" + pad.exportsname, ep);
							if (ppcore == null) err("Creating export core_" + pad.exportsname); else
							{
								TextDescriptor td = ppcore.getTextDescriptor(Export.EXPORT_NAME).withAbsSize(14);
								corePorts.add(ppcore);
								ppcore.setTextDescriptor(Export.EXPORT_NAME, td);
							}
						}
					} else
					{
						corePorts.add(null);
					}
				}
			}

			// create exports from export pad=name command
			for (ExportAssociate ea : pad.exportAssociations)
			{
				Export pp = cell.findExport(ea.padportName);
				if (pp == null)
				{
					err("no port called '" + ea.padportName + "' on Cell " + cell.noLibDescribe());
				} else
				{
					pp = Export.newInstance(framecell, ni.findPortInstFromProto(pp), ea.exportName, ep);
					if (pp == null)
						err("Creating export "+ea.exportName);
					else
					{
						TextDescriptor td = pp.getTextDescriptor(Export.EXPORT_NAME).withAbsSize(14);
						corePorts.add(pp);
						pp.setTextDescriptor(Export.EXPORT_NAME, td);
					}
				}
			}
			lastni = ni;
			lastRotate = angle;
			lastpadname = pad.cellname;
			pad.ni = ni;
		}

		// auto stitch everything
		AutoOptions prefs = new AutoOptions(true);
		prefs.createExports = false;
		AutoStitch.runAutoStitch(framecell, null, null, job, null, null, true, false, ep, prefs, false, null);

		if (corename != null)
		{
			// first, try to create cell
			String corenameview = corename;
			CellName coreName = CellName.parseName(corename);
			if (view != null && coreName.getView() == View.UNKNOWN)
			{
				corenameview = corename + "{" + view.getAbbreviation() + "}";
			}
			Cell corenp = destLib.findNodeProto(corenameview);
			if (corenp == null)
			{
				System.out.println("Line " + lineno + ": cannot find core cell " + corenameview);
			} else
			{

				Rectangle2D bounds = framecell.getBounds();
				Point2D center = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
				DBMath.gridAlign(center, ep.getAlignmentToGrid());

				NodeInst ni = NodeInst.makeInstance(corenp, ep, center, corenp.getDefWidth(), corenp.getDefHeight(), framecell);

				Map<Export,PortInst> trueBusEnd = new HashMap<Export,PortInst>();
				for (Object obj : orderedCommands)
				{
					if (obj instanceof PlacePad)
					{
						PlacePad pad = (PlacePad) obj;
						for (PortAssociate pa : pad.associations)
						{
							if (pad.ni == null) continue;

							boolean nameArc = false;
							PortInst pi1 = null;
							PortProto corepp = corenp.findPortProto(pa.assocname);
							if (corepp != null) pi1 = ni.findPortInstFromProto(corepp);
							if (pi1 == null)
							{
								// see if there are bus ports on the core
								for(Iterator<PortProto> it = corenp.getPorts(); it.hasNext(); )
								{
									Export e = (Export)it.next();
									Name eName = e.getNameKey();
									int wid = eName.busWidth();
									if (wid <= 1) continue;
									for(int i=0; i<wid; i++)
									{
										if (eName.subname(i).toString().equals(pa.assocname))
										{
											pi1 = trueBusEnd.get(e);
											if (pi1 == null)
											{
												// make a short bus arc from the port to a bus pin which gets used for connections
												PortInst pi = ni.findPortInstFromProto(e);
												PolyBase portPoly = pi.getPoly();
												Rectangle2D portRect = portPoly.getBounds2D();
												PrimitiveNode busPinProto = Schematics.tech().busPinNode;
												NodeInst busPin = NodeInst.makeInstance(busPinProto, ep,
														EPoint.fromLambda(portRect.getCenterX(), portRect.getCenterY()),
													busPinProto.getDefWidth(ep), busPinProto.getDefHeight(ep), framecell);
												pi1 = busPin.getOnlyPortInst();
												ArcProto busArcProto = Schematics.tech().bus_arc;
												ArcInst.makeInstance(busArcProto, ep, pi, pi1);
												trueBusEnd.put(e, pi1);
											}
											nameArc = true;
											break;
										}
									}
									if (pi1 != null) break;
								}
							}
							if (pi1 == null)
							{
								PortInst pi = pad.ni.findPortInst(pa.portname);
								Export.newInstance(pad.ni.getParent(), pi, pa.assocname, ep);
								continue;
							}
							PortInst pi2 = pad.ni.findPortInst(pa.portname);
                            if (pi2 == null)
                            {
                                err("no port called '" + pa.portname + "' on Cell " + pad.cellname);
                                continue;
                            }
                            ArcProto ap = Generic.tech().unrouted_arc;
							ArcInst ai = ArcInst.newInstanceBase(ap, ep, ap.getDefaultLambdaBaseWidth(ep), pi1, pi2);
							if (nameArc)
							{
								ai.setName(pa.assocname, ep);
							} else
							{
								// give generated name based on the connection
								String netName = "PADFRAME";
								if (pad.exportAssociations != null && pad.exportAssociations.size() > 0)
									netName += "_" + pad.exportAssociations.get(0).exportName;
								netName += "_" + pa.assocname;
								ai.setName(netName, ep);
							}
						}
					}
				}
			}
		}

		if (view == View.ICON)
		{
			// This is a crock until the functionality here can be folded
			// into CircuitChanges.makeIconViewCommand()

			// get icon style controls
			double leadLength = ep.getIconGenLeadLength();
			double leadSpacing = ep.getIconGenLeadSpacing();

			// create the new icon cell
			String iconCellName = framecell.getName() + "{ic}";
			Cell iconCell = Cell.makeInstance(ep, destLib, iconCellName);
			if (iconCell == null)
			{
				Job.getUserInterface().showErrorMessage("Cannot create Icon cell " + iconCellName,
					"Icon creation failed");
				return framecell;
			}
			iconCell.setWantExpanded();

			// determine the size of the "black box" core
			double ySize = Math.max(Math.max(padPorts.size(), corePorts.size()), 5) * leadSpacing;
			double xSize = 3 * leadSpacing;

			// create the "black box"
			NodeInst bbNi = null;
			if (ep.isIconGenDrawBody())
			{
				bbNi = NodeInst.newInstance(Artwork.tech().openedThickerPolygonNode, ep, new Point2D.Double(0, 0), xSize, ySize, iconCell);
				if (bbNi == null) return framecell;
				bbNi.newVar(Artwork.ART_COLOR, new Integer(EGraphics.RED), ep);

				EPoint[] points = new EPoint[5];
				points[0] = EPoint.fromLambda(-0.5 * xSize, -0.5 * ySize);
				points[1] = EPoint.fromLambda(-0.5 * xSize, 0.5 * ySize);
				points[2] = EPoint.fromLambda(0.5 * xSize, 0.5 * ySize);
				points[3] = EPoint.fromLambda(0.5 * xSize, -0.5 * ySize);
				points[4] = EPoint.fromLambda(-0.5 * xSize, -0.5 * ySize);
				bbNi.setTrace(points);

				// put the original cell name on it
				bbNi.newDisplayVar(Schematics.SCHEM_FUNCTION, framecell.getName(), ep);
			}

			// get icon preferences
			int exportTech = ep.getIconGenExportTech();
			boolean drawLeads = ep.isIconGenDrawLeads();
			int exportStyle = ep.getIconGenExportStyle();
			int exportLocation = ep.getIconGenExportLocation();
			boolean ad = ep.isIconsAlwaysDrawn();

			if (coreAllOnOneSide)
			{
				List<Export> padTemp = new ArrayList<Export>();
				List<Export> coreTemp = new ArrayList<Export>();
				for (Export pp : padPorts)
				{
					if (pp.getName().startsWith("core_"))
						coreTemp.add(pp);
					else
						padTemp.add(pp);
				}
				for (Export pp : corePorts)
				{
					if (pp == null)
					{
						coreTemp.add(pp);
						continue;
					}

					if (pp.getName().startsWith("core_"))
						coreTemp.add(pp);
					else
						padTemp.add(pp);
				}
				padPorts = padTemp;
				corePorts = coreTemp;
			}

			// place pins around the Black Box
			int total = 0;
			int leftSide = padPorts.size();
			int rightSide = corePorts.size();
			for (Export pp : padPorts)
			{
				if (pp.isBodyOnly()) continue;

				// determine location of the port
				double spacing = leadSpacing;
				double xPos = 0, yPos = 0;
				double xBBPos = 0, yBBPos = 0;
				xBBPos = -xSize / 2;
				xPos = xBBPos - leadLength;
				if (leftSide * 2 < rightSide) spacing = leadSpacing * 2;
				yBBPos = yPos = ySize / 2 - ((ySize - (leftSide - 1) * spacing) / 2 + total * spacing);

				int rotation = ViewChanges.iconTextRotation(pp, ep);
				if (IconParameters.makeIconExport(pp, ep, 0, xPos, yPos, xBBPos, yBBPos, iconCell,
					rotation))
						total++;
			}
			total = 0;
			for (Export pp : corePorts)
			{
				if (pp == null)
				{
					total++;
					continue;
				}
				if (pp.isBodyOnly()) continue;

				// determine location of the port
				double spacing = leadSpacing;
				double xPos = 0, yPos = 0;
				double xBBPos = 0, yBBPos = 0;
				xBBPos = xSize / 2;
				xPos = xBBPos + leadLength;
				if (rightSide * 2 < leftSide) spacing = leadSpacing * 2;
				yBBPos = yPos = ySize / 2 - ((ySize - (rightSide - 1) * spacing) / 2 + total * spacing);
				int rotation = ViewChanges.iconTextRotation(pp, ep);
				if (IconParameters.makeIconExport(pp, ep, 1, xPos, yPos, xBBPos, yBBPos, iconCell,
					rotation))
						total++;
			}

			// if no body, leads, or cell center is drawn, and there is only 1 export, add more
			if (!ep.isIconGenDrawBody() && !ep.isIconGenDrawLeads() &&
				ep.isPlaceCellCenter() && total <= 1)
			{
				NodeInst.newInstance(Generic.tech().invisiblePinNode, ep, new Point2D.Double(0, 0), xSize, ySize, iconCell);
			}

			// place an icon in the schematic
			int exampleLocation = ep.getIconGenInstanceLocation();
			Point2D iconPos = new Point2D.Double(0, 0);
			Rectangle2D cellBounds = framecell.getBounds();
			Rectangle2D iconBounds = iconCell.getBounds();
			double halfWidth = iconBounds.getWidth() / 2;
			double halfHeight = iconBounds.getHeight() / 2;
			switch (exampleLocation)
			{
				case 0:		// upper-right
					iconPos.setLocation(cellBounds.getMaxX() + halfWidth, cellBounds.getMaxY() + halfHeight);
					break;
				case 1:		// upper-left
					iconPos.setLocation(cellBounds.getMinX() - halfWidth, cellBounds.getMaxY() + halfHeight);
					break;
				case 2:		// lower-right
					iconPos.setLocation(cellBounds.getMaxX() + halfWidth, cellBounds.getMinY() - halfHeight);
					break;
				case 3:		// lower-left
					iconPos.setLocation(cellBounds.getMinX() - halfWidth, cellBounds.getMinY() - halfHeight);
					break;
			}
			DBMath.gridAlign(iconPos, ep.getAlignmentToGrid());
			double px = iconCell.getBounds().getWidth();
			double py = iconCell.getBounds().getHeight();
			NodeInst.makeInstance(iconCell, ep, iconPos, px, py, framecell);
		}
		return framecell;
	}

}
