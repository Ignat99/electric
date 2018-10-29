/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FileType.java
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
package com.sun.electric.tool.io;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.tool.user.UserInterfaceMain;

import javax.swing.filechooser.FileFilter;
import java.util.ArrayList;
import java.io.FilenameFilter;
import java.io.File;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * A typesafe enum class that describes the types of files that can be read or written.
 */
public class FileType implements Serializable {

    public static FileTypeGroup[] getFileTypeGroups() { return FileTypeGroup.class.getEnumConstants(); }

    public static String getDatabaseGroupPath() { return JELIB.getGroupPath(); }
    
	/** all types */                        private static final ArrayList<FileType> allTypes = new ArrayList<FileType>();

	/** Describes any file.*/				public static final FileType ANY          = makeFileType("All", new String[] {}, "All Files");
	/** Describes ALS decks. */				public static final FileType ALS          = makeFileType("ALS", new String[] {"als"}, "ALS Simulation Deck (als)", FileTypeGroup.BUILTINSIMGRP);
	/** Describes ALS vector decks. */		public static final FileType ALSVECTOR    = makeFileType("ALS Vectors", new String[] {"vec"}, "ALS Vector Deck (vec)", FileTypeGroup.BUILTINSIMGRP);
	/** Describes Applicon 860 decks. */	public static final FileType APPLICON860  = makeFileType("Applicon 860", new String[] {"apl"}, "Applicon 860 Deck (apl)");
	/** Describes Bookshelf decks. */		public static final FileType BOOKSHELF    = makeFileType("Bookshelf Format", new String[] {"aux"}, "Bookshelf Aux File (aux)");
	/** Describes Calibre DESIGNrev.*/		public static final FileType CALIBREDRV   = makeFileType("CALIBREDRV", new String[] {"tcl"}, "CALIBREDRV Deck (tcl)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes CDL decks.*/				public static final FileType CDL          = makeFileType("CDL", new String[] {"cdl"}, "CDL Deck (cdl)", FileTypeGroup.SPICESIMGRP);
	/** Describes CIF files. */				public static final FileType CIF          = makeFileType("CIF", new String[] {"cif"}, "CIF File (cif)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes COSMOS output. */			public static final FileType COSMOS       = makeFileType("COSMOS", new String[] {"sim"}, "COSMOS File (sim)", FileTypeGroup.OTHERSIMGRP);
	/** Describes Dais input. */			public static final FileType DAIS         = makeFileType("Dais", new String[] {""}, "Dais Workspace (ends in _ws)", FileTypeGroup.EXPORTIMPORTGRP);
    /** Describes Calibre DRC Error files. */public static final FileType DB          = makeFileType("DB", new String[] {"db"}, "Calibre DRC Error File (db)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes DEF output. */			public static final FileType DEF          = makeFileType("DEF", new String[] {"def"}, "DEF File (def)", FileTypeGroup.EXPORTIMPORTGRP);
    /** Describes DELIB files.*/			public static final FileType DELIB        = makeFileType("DELIB", new String[] {"delib"}, "Directory Library File (delib)", FileTypeGroup.DATABASEGRP);
	/** Describes DFTM files.*/				public static final FileType DFTM         = makeFileType("DFTM", new String[] {"dftm"}, "Data flow/Transactional Memory Netlist (dftm)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes DSPF input. */			public static final FileType DSPF         = makeFileType("DSPF", new String[] {"dspf"}, "Detailed Standard Parasitic File (dspf)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes DXF output. */			public static final FileType DXF          = makeFileType("DXF", new String[] {"dxf"}, "DXF File (dxf)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Eagle files.*/			public static final FileType EAGLE        = makeFileType("Eagle", new String[] {"txt"}, "Eagle File (txt)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes ECAD files.*/				public static final FileType ECAD         = makeFileType("ECAD", new String[] {"enl"}, "ECAD File (enl)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes EDIF files.*/				public static final FileType EDIF         = makeFileType("EDIF", new String[] {"edif"}, "EDIF File (edif)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes ELIB files.*/				public static final FileType ELIB         = makeFileType("ELIB", new String[] {"elib"}, "Library File (elib)", FileTypeGroup.DATABASEGRP);
	/** Describes Encapsulated PS files. */	public static final FileType EPS          = makeFileType("Encapsulated PostScript", new String[] {"eps"}, "Encapsulated PostScript (eps)");
    /** Describes EPIC simulation output. */public static final FileType EPIC         = makeFileType("EPIC output", new String[] {"out"}, "EPIC simulation output (out)", FileTypeGroup.SPICESIMGRP);
    /** Describes Assura DRC Error files. */public static final FileType ERR          = makeFileType("ERR", new String[] {"err"}, "Assura DRC Error File (err)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes ESIM/RNL output. */		public static final FileType ESIM         = makeFileType("ESIM", new String[] {"sim"}, "ESIM File (sim)", FileTypeGroup.OTHERSIMGRP);
	/** Describes FastHenry files.*/		public static final FileType FASTHENRY    = makeFileType("FastHenry", new String[] {"inp"}, "FastHenry File (inp)", FileTypeGroup.OTHERSIMGRP);
	/** Describes Flattened Rectangles output. */ public static final FileType FLATRECT = makeFileType("FlatRect", new String[] {"txt"}, "Flattened Rectangle File (txt)", FileTypeGroup.OTHERSIMGRP);
	/** Describes FPGA files.*/				public static final FileType FPGA         = makeFileType("FPGA", new String[] {"fpga"}, "FPGA Architecture File (fpga)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes GDS files. */				public static final FileType GDS          = makeFileType("GDS", new String[] {"gds"}, "GDS File (gds)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes GDS layer Map files. */	public static final FileType GDSMAP       = makeFileType("GDS Map", new String[] {"map"}, "GDS Layer Map File (map)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Gerber files. */			public static final FileType GERBER       = makeFileType("Gerber", new String[] {"gbr"}, "Gerber File (gbr)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes HSpice output. */			public static final FileType HSPICEOUT    = makeFileTypeNumeric("HSpice Output", new String[] {"tr"}, "HSpice Output File (tr0,1,2...)", FileTypeGroup.SPICESIMGRP);
	/** Describes HPGL files. */			public static final FileType HPGL         = makeFileType("HPGL", new String[] {"hpgl2"}, "HPGL File (hpgl2)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes HTML files. */			public static final FileType HTML         = makeFileType("HTML", new String[] {"html"}, "HTML File (html)");
	/** Describes HTML files. */			public static final FileType I            = makeFileType("I", new String[] {"i"}, "Estimated Currents File (i)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes IRSIM decks. */			public static final FileType IRSIM        = makeFileType("IRSIM", new String[] {"sim"}, "IRSIM Deck (sim)", FileTypeGroup.BUILTINSIMGRP);
	/** Describes IRSIM parameter decks. */	public static final FileType IRSIMPARAM   = makeFileType("IRSIM Parameters", new String[] {"prm"}, "IRSIM Parameter Deck (prm)", FileTypeGroup.BUILTINSIMGRP);
	/** Describes IRSIM vector decks. */	public static final FileType IRSIMVECTOR  = makeFileType("IRSIM Vectors", new String[] {"cmd"}, "IRSIM Vector Deck (cmd)", FileTypeGroup.BUILTINSIMGRP);
	/** Describes Java source. */			public static final FileType JAVA         = makeFileType("Java", new String[] {"java", "bsh"}, "Java Script File (java, bsh)");
	/** Describes Jar file. */              public static final FileType JAR          = makeFileType("Jar", new String[] {"jar"}, "Java binary archive (jar)", FileTypeGroup.JARGRP);
	/** Describes JELIB files.*/			public static final FileType JELIB        = makeFileType("JELIB", new String[] {"jelib"}, "Library File (jelib)", FileTypeGroup.DATABASEGRP);
    /** Describes J3D files.*/				public static final FileType J3D          = makeFileType("J3D", new String[] {"j3d"}, "Java3D Demo File (j3d}");
	/** Describes Jython source. */			public static final FileType JYTHON       = makeFileType("Jython", new String[] {"jy", "py"}, "Jython Script File (jy, py)");
    /** Describes L files.*/				public static final FileType L            = makeFileType("L", new String[] {"L"}, "L File (L)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes LEF files.*/				public static final FileType LEF          = makeFileType("LEF", new String[] {"lef"}, "LEF File (lef)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Library files.*/			public static final FileType LIBFILE      = makeFileType("LIBFILE", new String[] {"jelib", "elib", "txt"}, "Library File", FileTypeGroup.DATABASEGRP);
	/** Describes Liberty input. */			public static final FileType LIB         = makeFileType("LIB", new String[] {"lib"}, "Liberty File (lib)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Lisp or ACL2 output. */	public static final FileType LISP         = makeFileType("LISP", new String[] {"lisp"}, "Lisp File (lisp)", FileTypeGroup.EXPORTIMPORTGRP);
    /** Describes Maxwell decks. */			public static final FileType MAXWELL      = makeFileType("Maxwell", new String[] {"mac"}, "Maxwell Deck (mac)", FileTypeGroup.OTHERSIMGRP);
	/** Describes MOSSIM decks. */			public static final FileType MOSSIM       = makeFileType("MOSSIM", new String[] {"ntk"}, "MOSSIM Deck (ntk)", FileTypeGroup.OTHERSIMGRP);
    /** Describes Movie files. */			public static final FileType MOV          = makeFileType("Movie", new String[] {"mov"}, "Movie File (mov)");
    /** Describes Pad Frame Array spec. */	public static final FileType PADARR       = makeFileType("Pad Array", new String[] {"arr"}, "Pad Generator Array File (arr)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Pads files. */			public static final FileType PADS         = makeFileType("Pads", new String[] {"asc"}, "Pads File (asc)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes PAL files. */				public static final FileType PAL          = makeFileType("PAL", new String[] {"pal"}, "PAL File (pal)", FileTypeGroup.OTHERSIMGRP);
	/** Describes PostScript files. */		public static final FileType POSTSCRIPT   = makeFileType("PostScript", new String[] {"ps"}, "PostScript (ps)");
	/** Describes PostScript files. */		public static final FileType PNG          = makeFileType("PNG", new String[] {"png"}, "PNG (png)");
	/** Describes Preferences files. */		public static final FileType PREFS        = makeFileType("Preferences", new String[] {"xml"}, "Preferences (xml)");
	/** Describes Project files. */			public static final FileType PROJECT      = makeFileType("Project Management", new String[] {"proj"}, "Project Management (proj)");
	/** Describes PSpice standard output.*/	public static final FileType PSPICEOUT    = makeFileType("PSpice Output", new String[] {"txt"}, "PSpice/Spice3 Text Output File (txt)", FileTypeGroup.SPICESIMGRP);
	/** Describes Raw Spice output. */		public static final FileType RAWSPICEOUT  = makeFileType("RawSpice Output", new String[] {"raw"}, "Spice Raw Output File (raw)", FileTypeGroup.SPICESIMGRP);
	/** Describes Raw SmartSpice output. */	public static final FileType RAWSSPICEOUT = makeFileType("Raw SmartSpice Output", new String[] {"raw"}, "SmartSPICE Raw Output File (raw)", FileTypeGroup.SPICESIMGRP);
	/** Describes Raw LTSpice output. */	public static final FileType RAWLTSPICEOUT= makeFileType("Raw LTSpice Output", new String[] {"raw"}, "LTSPICE Raw Output File (raw)", FileTypeGroup.SPICESIMGRP);
	/** Describes Readable Dump files. */	public static final FileType READABLEDUMP = makeFileType("ReadableDump", new String[] {"txt"}, "Readable Dump Library File (txt)", FileTypeGroup.DATABASEGRP);
	/** Describes RSIM output. */			public static final FileType RSIM         = makeFileType("RSIM", new String[] {"sim"}, "RSIM File (sim)", FileTypeGroup.OTHERSIMGRP);
	/** Describes ACL2 serialized data.*/	public static final FileType SAO          = makeFileType("SAO", new String[] {"sao"}, "ACL2 File (sao)", FileTypeGroup.JARGRP);
	/** Describes Silos decks.*/			public static final FileType SILOS        = makeFileType("Silos", new String[] {"sil"}, "Silos Deck (sil)", FileTypeGroup.OTHERSIMGRP);
	/** Describes Skill decks.*/			public static final FileType SKILL        = makeFileType("Skill", new String[] {"il"}, "Skill Deck (il)", FileTypeGroup.EXPORTIMPORTGRP);
    /** Describes Skill decks.*/			public static final FileType SKILLEXPORTSONLY = makeFileType("SkillExports Only", new String[] {"il"}, "Skill Deck (il)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Spice decks.*/			public static final FileType SPICE        = makeFileType("Spice", new String[] {"spi", "sp"}, "Spice Deck (spi, sp)", FileTypeGroup.SPICESIMGRP);
	/** Describes Spice standard output.*/	public static final FileType SPICEOUT     = makeFileType("Spice Output", new String[] {"spo"}, "Spice/GNUCap Output File (spo)", FileTypeGroup.SPICESIMGRP);
	/** Describes STL files.*/				public static final FileType STL          = makeFileType("STL", new String[] {"stl"}, "STL File (stl)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes Sue files.*/				public static final FileType SUE          = makeFileType("Sue", new String[] {"sue"}, "Sue File (sue)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes SVG files.*/				public static final FileType SVG          = makeFileType("SVG", new String[] {"svg"}, "Scalable Vector Graphics (svg)");
	/** Describes Tegas files. */			public static final FileType TEGAS        = makeFileType("Tegas", new String[] {"tdl"}, "Tegas File (tdl)", FileTypeGroup.OTHERSIMGRP);
	/** Describes Telesis decks. */			public static final FileType TELESIS      = makeFileType("Telesis", new String[] {"txt"}, "Telesis File (txt)", FileTypeGroup.EXPORTIMPORTGRP);
	/** Describes text files. */			public static final FileType TEXT         = makeFileType("Text", new String[] {"txt"}, "Text File (txt)");
	/** Describes Verilog decks. */			public static final FileType VERILOG      = makeFileType("Verilog", new String[] {"v", "vL"}, "Verilog Deck (v)", FileTypeGroup.VERILOGSIMGRP);
	/** Describes VerilogA decks. */	    public static final FileType VERILOGA     = makeFileType("VerilogA", new String[] {"va", "vLA"}, "VerilogA Deck (va)", FileTypeGroup.VERILOGSIMGRP);
	/** Describes Verilog output. */		public static final FileType VERILOGOUT   = makeFileType("Verilog Output", new String[] {"dump"}, "Verilog VCD Dump (vcd)", FileTypeGroup.VERILOGSIMGRP);
	/** Describes Xml files. */				public static final FileType XML          = makeFileType("XML", new String[] {"xml"}, "XML File (xml)");

	/** Describes default file format.*/	public static final FileType DEFAULTLIB   = JELIB;

	/** Valid Library formats */            public static final FileType libraryTypes[] = {JELIB, ELIB, DELIB};
	private static String [] libraryTypesExt;
	private static String libraryTypesExtReadable;
	static {
		ArrayList<String> exts = new ArrayList<String>();
		for (FileType type : libraryTypes)
        {
			String [] typeExts = type.getExtensions();
            for (String typeExt : typeExts)
                exts.add(typeExt);
		}
		libraryTypesExt = new String[exts.size()];
		StringBuffer buf = new StringBuffer("(");
		for (int i=0; i<exts.size(); i++) {
			libraryTypesExt[i] = exts.get(i);
			buf.append(exts.get(i));
			buf.append(", ");
		}
		if (buf.length() > 2) buf.replace(buf.length()-2, buf.length(), ")");
		libraryTypesExtReadable = buf.toString();
	}

	/** Valid library formats as a Type */  public static final FileType LIBRARYFORMATS = makeFileType("LibraryFormats",
            libraryTypesExt, "Library Formats "+libraryTypesExtReadable, FileTypeGroup.DATABASEGRP);

	private String name;
	private String [] extensions;
	private String desc;
	private boolean allowNumbers;
	private transient FileFilterSwing ffs;
	private transient FileFilterAWT ffa;
    private FileTypeGroup group;

	private FileType() {}

    public static enum FileTypeGroup
    {
        DATABASEGRP("Database"),
        OTHERSIMGRP("Others Simulation"), // other simulation tools
        SPICESIMGRP("SPICE Simulation"), // SPICE simulation tool
        BUILTINSIMGRP("Built-In Simulation"), // Built-in simulation tools
        VERILOGSIMGRP("Verilog Simulation"), // Verilog simulation tools
        EXPORTIMPORTGRP("Export-Import"), // Export/Import formats
        JARGRP("Electric Build"); // Electric Build

        String groupName;
        FileTypeGroup(String grpName)
        {
            groupName = grpName;
        }

        public String getGroupName() {
            return groupName;
        }

        public String toString()
        {
            return groupName;
        }
    }

    public void setGroupPath(String path)
    {
        if (group == null || path == null) return; // nothing to do
        EditingPreferences ep = UserInterfaceMain.getEditingPreferences();
        ep = ep.withGroupDirectory(group, path);
        UserInterfaceMain.setEditingPreferences(ep);
    }

    public String getGroupPath()
    {
        return getGroupPath(EditingPreferences.getInstance());
    }

    public String getGroupPath(EditingPreferences ep)
    {
        if (group == null) return null;
        String dir = ep.getGroupDirectory(group);
        return dir.isEmpty() ? ep.getWorkingDirectory() : dir;
    }

    private static FileType makeFileType(String name, String [] extensions, String desc, FileTypeGroup g)
    {
        FileType f = makeFileType(name, extensions, desc);
        f.group = g;
        return f;
    }

	private static FileType makeFileType(String name, String [] extensions, String desc)
	{
		FileType ft = new FileType();

		ft.name = name;
		ft.extensions = extensions;
		ft.desc = desc;
		ft.ffs = null;
		ft.ffa = null;
		ft.allowNumbers = false;
        ft.group = null;
		allTypes.add(ft);
		return ft;
	}

	private static FileType makeFileTypeNumeric(String name, String [] extensions, String desc, FileTypeGroup g)
	{
		FileType ft = makeFileType(name, extensions, desc);
		ft.allowNumbers = true;
        ft.group = g;
		return ft;
	}

	public String getName() { return name; }
	public String getDescription() { return desc; }
    public String getFirstExtension()
    {
        String[] exts = getExtensions();
        if (exts == null  || exts.length == 0) return "";
        return exts[0];
    }
    public String [] getExtensions()
	{
		if (allowNumbers)
		{
			String [] newExtensions = new String[extensions.length];
			for(int i=0; i<extensions.length; i++)
				newExtensions[i] = extensions[i] + "0";
			return newExtensions;
		}
		return extensions;
	}

    public static boolean matchExtension(String ext)
    {
    	String extLow = ext.toLowerCase();
    	for (FileType t : allTypes)
    	{
    		String[] exts = t.getExtensions();

	    	for (String s : exts)
	    	{
	    		if (s.toLowerCase().contains(extLow))
	    			return true;
	    	}
    	}
    	return false;
    }

	public FileFilterSwing getFileFilterSwing()
	{
		if (ffs == null) ffs = new FileFilterSwing(extensions, desc, allowNumbers);
		return ffs;
	}

	public FileFilterAWT getFileFilterAWT()
	{
		if (ffa == null) ffa = new FileFilterAWT(extensions, desc, allowNumbers);
		return ffa;
	}

    private Object readResolve() throws ObjectStreamException {
        for (FileType ft: allTypes) {
            if (name.equals(ft.name)) return ft;
        }
        return this;
    }

	/**
	 * Returns a printable version of this Type.
	 * @return a printable version of this Type.
	 */
	public String toString() { return name; }

	/**
	 * Get the Type for the specified filter
	 */
	public static FileType getType(FileFilter filter) {
		for (FileType type : allTypes) {
			if (type.ffs == filter) return type;
		}
		return null;
	}

	/**
	 * Get the Type for the specified filter
	 */
	public static FileType getType(FilenameFilter filter) {
		for (FileType type : allTypes) {
			if (type.ffa == filter) return type;
		}
		return null;
	}

    /**
     * Method to find a given type by name.
     * @param typeName FileType name
     * @return FileType instance
     */
    public static FileType findType(String typeName)
    {
        String n = typeName.toLowerCase();
        for (FileType type : allTypes)
        {
            if (type.name.toLowerCase().equals(n))
                return type;
		}
		return null;
    }

    /**
     * Method to find a given type by extension name.
     * @param extName name of the extension
     * @return FileType instance
     */
    public static FileType findTypeByExtension(String extName)
    {
        String n = extName.toLowerCase();
        for (FileType type : allTypes)
        {
            for (String ex : type.extensions)
            {
                if (ex.toLowerCase().equals(n))
                    return type;
            }
		}
		return null;
    }

    private static class FileFilterSwing extends FileFilter
	{
		/** list of valid extensions */				private String[] extensions;
		/** description of filter */				private String desc;
		/** true to allow digits in extension */	private boolean allowNumbers;

		/** Creates a new instance of FileFilterSwing */
		public FileFilterSwing(String[] extensions, String desc, boolean allowNumbers)
		{
			this.extensions = extensions;
			this.desc = desc;
			this.allowNumbers = allowNumbers;
		}

		/** Returns true if file has extension matching any of
		 * extensions is @param extensions.  Also returns true
		 * if file is a directory.  Returns false otherwise
		 */
		public boolean accept(File f)
		{
			if (f == null) return false;
			if (f.isDirectory()) return true;
			String fileName = f.getName();
			return matches(fileName, extensions, allowNumbers);
		}

		public String getDescription() { return desc; }
    }

	private static class FileFilterAWT implements FilenameFilter
	{
		/** list of valid extensions */				private String[] extensions;
		/** description of filter */				private String desc;
		/** true to allow digits in extension */	private boolean allowNumbers;

		/** Creates a new instance of FileFilterAWT */
		public FileFilterAWT(String[] extensions, String desc, boolean allowNumbers)
		{
			this.extensions = extensions;
			this.desc = desc;
			this.allowNumbers = allowNumbers;
		}

		/** Returns true if file has extension matching any of
		 * extensions is @param extensions.  Also returns true
		 * if file is a directory.  Returns false otherwise
		 */
		public boolean accept(File f, String fileName)
		{
			return matches(fileName, extensions, allowNumbers);
		}

		public String getDescription() { return desc; }
	}

	private static boolean matches(String fileName, String [] extensions, boolean allowNumbers)
	{
		// special case for ANY
		if (extensions.length == 0) return true;
		int i = fileName.lastIndexOf('.');
		if (i < 0) return false;
		String thisExtension = fileName.substring(i+1);
		if (thisExtension == null) return false;
        for (String extension : extensions)
		{
			if (extension.equalsIgnoreCase(thisExtension)) return true;
			if (allowNumbers)
			{
				if (thisExtension.length() > extension.length())
				{
					if (thisExtension.startsWith(extension))
					{
						boolean allDigits = true;
						for(int k=extension.length(); k<thisExtension.length(); k++)
						{
							if (!Character.isDigit(thisExtension.charAt(k))) allDigits = false;
						}
						if (allDigits) return true;
					}
				}
			}
		}
		return false;
	}

    /** Get the type from the fileName, or if no valid Library type found, return defaultType.
     */
    public static FileType getLibraryFormat(String fileName, FileType defaultType) {
            if (fileName != null)
        {
            if (fileName.endsWith(File.separator)) {
                fileName = fileName.substring(0, fileName.length()-File.separator.length());
            }
            for (FileType type :  FileType.libraryTypes)
            {
                if (fileName.endsWith("."+type.getFirstExtension())) return type;
            }
        }
        return defaultType;
    }
}
