/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SpiceNetlistReader.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.input.spicenetlist;

import java.io.*;
import java.util.*;

/**
 * Parse a spice netlist. Ignores comments, and
 * coalesces split lines into single lines.
 * User: gainsley
 * Date: Aug 3, 2006
 */
public class SpiceNetlistReader {

    private static final boolean STRIP_QUOTES = false;
    private static final boolean WRITE_ONLY_USED = true;

    private final boolean parseLibraries;
    private File file;
    private BufferedReader reader;
    private Iterator<String> macroReader;
    private StringBuilder lines;
    private int lineno;

    private final Map<File, SpiceLibrary> libraries = new LinkedHashMap<>();
    private final Map<String,String> options = new LinkedHashMap<>();
    private final Map<String,String> globalParams = new LinkedHashMap<>();
    private final Map<String,SpiceModel> globalModels = new LinkedHashMap<>();
    private final List<SpiceInstance> topLevelInstances = new ArrayList<>();
    private final Map<String,SpiceSubckt> subckts = new LinkedHashMap<>();
    private final List<String> globalNets = new ArrayList<>();
    private List<SpiceSubckt> currentSubcktStack = new ArrayList<>();

    public SpiceNetlistReader() {
        this(false);
    }

    public SpiceNetlistReader(boolean parseLibraries) {
        this.parseLibraries = parseLibraries;
        reader = null;
        lines = null;
    }

    public Map<String,String> getOptions() { return options; }
    public Map<String,String> getGlobalParams() { return globalParams; }
    public Map<String,SpiceModel> getGlobalModels() { return globalModels; }
    public List<SpiceInstance> getTopLevelInstances() { return topLevelInstances; }
    public Collection<SpiceSubckt> getSubckts() { return subckts.values(); }
    public List<String> getGlobalNets() { return globalNets; }
    public SpiceSubckt getSubckt(String name) {
        return subckts.get(name.toLowerCase());
    }

//    private static final boolean DEBUG = true;

    // ============================== Parsing ==================================

//    enum TType { PAR, PARVAL, WORD }

    public void readFile(String fileName, boolean verbose) throws FileNotFoundException {
        readFile(new File(fileName), verbose);
    }

    public void readFile(File file, boolean verbose) throws FileNotFoundException {
        this.file = file;
        reader = new BufferedReader(new FileReader(file));
        lines = new StringBuilder();

        String line = null;
        lineno = 0;
        try {
            while ((line = readLine()) != null) {
                parseLine(line, verbose);
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("Error reading file "+file.getPath()+": "+e.getMessage());
        }
    }

    private void readMacro(String libFileName, String macroName, boolean verbose) {
        File libFile = resolveFile(libFileName);
        SpiceLibrary library;
        try {
            library = readLibrary(libFile, verbose);
        } catch (IOException e) {
            prErr("Can't read library file " + libFileName);
            return;
        }
        Integer macroStart = library.libIndex.get(macroName.toLowerCase());
        if (macroStart == null)
        {
            prErr("Can't file libary " + macroName + " in file " + libFileName);
            return;
        }

        File saveFile = file;
        BufferedReader saveReader = reader;
        Iterator<String> saveMacroReader = macroReader;
        StringBuilder saveLines = lines;
        int saveLine = lineno;
        try
        {
            file = libFile;
            reader = null;
            macroReader = library.lines.listIterator(macroStart);
            lines = new StringBuilder();
            lineno = macroStart;
            String line;

            while ((line = readLine()) != null)
            {
                if (line.length() >= 5 && line.substring(0, 5).toLowerCase().equals(".endl"))
                {
                    break;
                }
                parseLine(line, verbose);
            }
        } catch (IOException e) {
            System.out.println("Error reading file "+file.getPath()+": "+e.getMessage());
        } finally
        {
            file = saveFile;
            reader = saveReader;
            macroReader = saveMacroReader;
            lines = saveLines;
            lineno = saveLine;
        }
    }

    private void parseLine(String line, boolean verbose)
    {
        line = line.trim();
        String[] tokens = getTokens(line);
        if (tokens.length == 0)
            return;
        String keyword = tokens[0].toLowerCase();

        if (keyword.equals(".include"))
        {
            if (tokens.length < 2)
            {
                prErr("No file specified for .include");
                return;
            }
            String ifile = tokens[1];
            File newFile = resolveFile(ifile);
//            if (ifile.startsWith("/") || !ifile.startsWith("\\"))
//            {
//                // absolute path
//                newFile = new File(ifile);
//            }
//            {
//                // relative path, add to current path
//                newFile = new File(file.getParent(), ifile);
//                ifile = newFile.getPath();
//            }
            File saveFile = file;
            BufferedReader saveReader = reader;
            Iterator<String> saveMacroReader = macroReader;
            StringBuilder saveLines = lines;
            int saveLine = lineno;

            try
            {
                if (verbose)
                    System.out.println("Reading include file " + ifile);
                readFile(newFile, verbose);
            } catch (FileNotFoundException e)
            {
                file = saveFile;
                lineno = saveLine;
                prErr("Include file does not exist: " + ifile);
            }
            file = saveFile;
            reader = saveReader;
            macroReader = saveMacroReader;
            lines = saveLines;
            lineno = saveLine;
        } else if (parseLibraries && keyword.startsWith(".lib"))
        {
            if (tokens.length < 3)
            {
                prErr("No library specified for .lib");
                return;
            }
            String lfile = tokens[1];
            String macroName = tokens[2];
            readMacro(lfile, macroName, verbose);
        } else if (keyword.startsWith(".opt"))
        {
            if (currentSubckt() != null)
            {
                prErr(".opt inside .subckt");
            }
            parseOptions(options, 1, tokens);
        } else if (keyword.equals(".param"))
        {
            SpiceSubckt currentSubckt = currentSubckt();
            parseParams(currentSubckt != null ? currentSubckt.getLocalParams() : globalParams, 1, tokens);
        } else if (keyword.equals(".subckt"))
        {
            SpiceSubckt currentSubckt = currentSubckt();
            SpiceSubckt newSubckt = parseSubckt(tokens);
            SpiceSubckt oldSubckt = currentSubckt != null
                ? currentSubckt.addSubckt(newSubckt)
                : subckts.put(newSubckt.getName().toLowerCase(), newSubckt);
            if (oldSubckt != null)
            {
                prErr("Subckt " + currentSubckt.getName()
                    + " already defined");
            }
            currentSubcktStack.add(newSubckt);
        } else if (keyword.equals(".global"))
        {
            if (currentSubckt() != null)
            {
                prErr(".global inside .subckt");
            }
            for (int i = 1; i < tokens.length; i++)
            {
                if (!globalNets.contains(tokens[i]))
                    globalNets.add(tokens[i]);
            }
        } else if (keyword.startsWith(".ends"))
        {
            SpiceSubckt currentSubckt = currentSubckt();
            if (currentSubckt != null) {
                for (SpiceInstance inst: currentSubckt.getInstances()) {
                    inst.linkModel(this, currentSubcktStack);
                }
                currentSubcktStack.remove(currentSubcktStack.size() - 1);
            }
        } else if (keyword.startsWith(".end"))
        {
            // end of file
            if (currentSubckt() != null)
            {
                prErr("Expected .ends " + currentSubckt().getName());
            }
        } else if (keyword.equals(".model"))
        {
            parseModel(tokens);
        } else if (keyword.equals(".ic"))
        {
            SpiceInstance ic = parseInitial(tokens);
            addInstance(ic);
        } else if (keyword.equals(".tran"))
        {
            SpiceInstance ic = parseTran(tokens);
            addInstance(ic);
        } else if (keyword.startsWith("x"))
        {
            SpiceInstance inst = parseSubcktInstance(tokens);
            addInstance(inst);
        } else if (keyword.startsWith("r"))
        {
            SpiceInstance inst = parseResistor(tokens);
            addInstance(inst);
        } else if (keyword.startsWith("c"))
        {
            SpiceInstance inst = parseCapacitor(tokens);
            addInstance(inst);
        } else if (keyword.startsWith("v"))
        {
            SpiceInstance inst = parseVoltageSource(tokens);
            addInstance(inst);
        } else if (keyword.startsWith("m"))
        {
            SpiceInstance inst = parseMosfet(tokens);
            addInstance(inst);
        } else if (keyword.equals(".protect") || keyword.equals(".unprotect"))
        {
            // ignore
        } else
        {
            prWarn("Parser does not recognize: " + line);
        }
    }

    private File resolveFile(String newName)
    {
        if (newName.startsWith("'") && newName.endsWith("'"))
        {
            newName = newName.substring(1, newName.length() - 1);
        }
        if (newName.startsWith("/") || newName.startsWith("\\"))
        {
            // absolute path
            return new File(newName);
        }
        {
            // relative path, add to current path
            return new File(file.getParent(), newName);
        }
    }

    private SpiceLibrary readLibrary(File libFile, boolean verbose) throws IOException {
        SpiceLibrary theLibrary = libraries.get(libFile);
        if (theLibrary == null) {
            if (verbose)
                System.out.println("Reading library file " + libFile);
            theLibrary = new SpiceLibrary(libFile);
            libraries.put(libFile, theLibrary);
        }
        return theLibrary;
    }

    void prErr(String msg) {
        System.out.println("Error ("+getLocation()+"): "+msg);
    }
    private void prWarn(String msg) {
        System.out.println("Warning ("+getLocation()+"): "+msg);
    }
    private String getLocation() {
        return file.getName()+":"+lineno;
    }

    /**
     * Get the tokens in a line.  Tokens are separated by whitespace,
     * unless that whitespace is surrounded by single quotes, or parentheses.
     * When quotes are used and STRIP_QUOTES=true , those quotes are removed from the string literal.
     * The construct <code>name=value</code> is returned as three tokens,
     * the second being the char '='.
     * @param line the line to parse
     * @return an array of tokens
     */
    private String [] getTokens(String line) {
        List<String> tokens = new ArrayList<>();
        int start = 0;
        boolean inquotes = false;
        int inparens = 0;
        int i;
        for (i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (inquotes) {
                if (c == '\'') {
                    if (inparens > 0) continue;
                    // end string literal
                    tokens.add(line.substring(start, STRIP_QUOTES ? i : i + 1));
                    start = i+1;
                    inquotes = false;
                }
            }
            else if (c == '\'') {
                assert STRIP_QUOTES || inparens == 0;
                if (inparens > 0) continue;
                inquotes = true;
                if (start != i) {
                    prErr("Improper use of open quote '");
                    break;
                }
                start = STRIP_QUOTES ? i+1 : i;
            }
            // else !inquotes:
            else if (Character.isWhitespace(c) && inparens == 0) {
                // end of token (unless just more whitespace)
                if (start < i)
                    tokens.add(line.substring(start, i));
                start = i+1;
            }
            else if ((STRIP_QUOTES || inquotes) && c == '(') {
                inparens++;
            }
            else if ((STRIP_QUOTES || inquotes) && c == ')') {
                if (inparens == 0) {
                    prErr("Too many ')'s");
                    break;
                }
                inparens--;
            }
            else if (c == '=') {
                if (start < i)
                    tokens.add(line.substring(start, i));
                tokens.add("=");
                start = i+1;
            }
            else if (c == '*') {
                break; // rest of line is comment
            }
        }
        if (start < i) {
            tokens.add(line.substring(start, i));
        }

        if (inparens != 0)
            prErr("Unmatched parentheses");

        // join {par, =, val} to {par=val}
        List<String> joined = new ArrayList<String>();
        for (int j=0; j<tokens.size(); j++) {
            if (tokens.get(j).equals("=")) {
                if (j == 0) {
                    prErr("No right hand side to assignment");
                } else if (j == tokens.size()-1) {
                    prErr("No left hand side to assignment");
                } else {
                    int last = joined.size() - 1;
                    joined.set(last, joined.get(last)+"="+tokens.get(++j));
                }
            } else {
                joined.add(tokens.get(j));
            }
        }
        String ret [] = new String[joined.size()];
        for (int k=0; k<joined.size(); k++)
            ret[k] = joined.get(k);
        return ret;
    }

    private SpiceSubckt currentSubckt() {
        return currentSubcktStack.isEmpty() ? null : currentSubcktStack.get(currentSubcktStack.size() - 1);
    }

    private void parseOptions(Map<String,String> map, int start, String [] tokens) {
        for (int i=start; i<tokens.length; i++) {
            int e = tokens[i].indexOf('=');
            String pname = tokens[i];
            String value = "true";
            if (e > 0) {
                pname = tokens[i].substring(0, e);
                value = tokens[i].substring(e+1);
            }
            if (pname == null || value == null) {
                prErr("Bad option value: "+tokens[i]);
                continue;
            }
            map.put(pname.toLowerCase(), value);
        }
    }

    private void parseParams(Map<String,String> map, int start, String [] tokens) {
        for (int i=start; i<tokens.length; i++) {
            parseParam(map, tokens[i], null);
        }
    }

    private void parseParam(Map<String,String> map, String parval, String defaultParName) {
        int e = parval.indexOf('=');
        String pname = defaultParName;
        String value = parval;
        if (e > 0) {
            pname = parval.substring(0, e);
            value = parval.substring(e+1);
            if (defaultParName != null && !defaultParName.equalsIgnoreCase(pname)) {
                prWarn("Expected param "+defaultParName+", but got "+pname);
            }
        }
        if (pname == null || value == null) {
            prErr("Badly formatted param=val: "+parval);
            return;
        }
        String old = map.put(pname.toLowerCase(), value);
        if (old != null) {
            prWarn("Redefinition of param " + pname + " " + old + " --> " + value);
        }
    }

    private SpiceSubckt parseSubckt(String [] parts) {
        SpiceSubckt subckt = new SpiceSubckt(parts[1]);
        int i=2;
        for (; i<parts.length; i++) {
            if (parts[i].indexOf('=') > 0) break;  // parameter
            subckt.addPort(parts[i]);
        }
        parseParams(subckt.getParams(), i, parts);
        return subckt;
    }

    private SpiceInstance parseSubcktInstance(String [] parts) {
        String name = parts[0].substring(1);
        List<String> nets = new ArrayList<>();
        int i=1;
        for (; i<parts.length; i++) {
            if (parts[i].contains("=")) break;  // parameter
            nets.add(parts[i]);
        }
        String subcktName = nets.remove(nets.size()-1); // last one is subckt reference
        SpiceSubckt subckt = null;
        for (int j = currentSubcktStack.size() - 1; subckt == null && j >= 0; j--) {
            subckt = currentSubcktStack.get(j).findSubckt(subcktName);
        }
        if (subckt == null)
        {
            subckt = subckts.get(subcktName.toLowerCase());
        }
        if (subckt == null) {
            prErr("Cannot find subckt for "+subcktName);
            return null;
        }
        SpiceInstance inst = new SpiceInstance(subckt, name);
        for (String net : nets)
            inst.addNet(net);
        Map<String,String> instParams = inst.getParams();
        parseParams(instParams, i, parts);
        for (String key : subckt.getParams().keySet()) {
            // set default param values
            if (!instParams.containsKey(key)) {
                instParams.put(key, subckt.getParams().get(key));
            }
        }
        // consistency check
        if (inst.getNets().size() != subckt.getPorts().size()) {
            prErr("Number of ports do not match: "+inst.getNets().size()+
                  " (instance "+name+") vs "+subckt.getPorts().size()+
                  " (subckt "+subckt.getName()+")");
        }
        return inst;
    }

    private void addInstance(SpiceInstance inst) {
        if (inst == null) return;
        SpiceSubckt currentSubckt = currentSubckt();
        if (currentSubckt != null)
            currentSubckt.addInstance(inst);
        else
            topLevelInstances.add(inst);
    }

    private SpiceInstance parseResistor(String [] parts) {
        if (parts.length < 4) {
            prErr("Not enough arguments for resistor");
            return null;
        }
        SpiceInstance inst = new SpiceInstance(parts[0]);
        for (int i=1; i<3; i++) {
            inst.addNet(parts[i]);
        }
        parseParam(inst.getParams(), parts[3], "r");
        return inst;
    }

    private SpiceInstance parseCapacitor(String [] parts) {
        if (parts.length < 4) {
            prErr("Not enough arguments for capacitor");
            return null;
        }
        SpiceInstance inst = new SpiceInstance(parts[0]);
        for (int i=1; i<3; i++) {
            inst.addNet(parts[i]);
        }
        parseParam(inst.getParams(), parts[3], "c");
        return inst;
    }

    private SpiceInstance parseVoltageSource(String [] parts) {
        if (parts.length < 4) {
            prErr("Not enough arguments for voltage");
            return null;
        }
        SpiceInstance inst = new SpiceInstance(parts[0]);
        for (int i=1; i<3; i++) {
            inst.addNet(parts[i]);
        }
        parseParam(inst.getParams(), parts[3], "dc");
        return inst;
    }

    private SpiceInstance parseMosfet(String [] parts) {
        if (parts.length < 8) {
            prErr("Not enough arguments for mosfet");
            return null;
        }
        SpiceInstance inst = new SpiceInstance(parts[0]);
        int i=1;
        for (; i<5; i++) {
            inst.addNet(parts[i]);
        }
        String modelName = parts[i];
        inst.addModName(modelName);
        i++;
        parseParams(inst.getParams(), i, parts);
        return inst;
    }

    private void parseModel(String[] parts)
    {
        if (parts.length < 3)
        {
            prErr("Not enough arguments for token");
            return;
        }
        int i = 1;
        String modName = parts[i++];
        String modFlag = parts[i++];
        String modSuffix = "";
        int ind = modName.indexOf('.');
        if (ind >= 0)
        {
            modSuffix = modName.substring(ind);
            modName = modName.substring(0, ind);
        }
        SpiceModel model;
        SpiceSubckt currentSubckt = currentSubckt();
        if (currentSubckt != null)
        {
            model = currentSubckt.addModel(modName, modFlag);
        } else
        {
            String key = modName.toLowerCase();
            model = globalModels.get(key);
            if (model == null)
            {
                model = new SpiceModel(modName, modFlag);
                globalModels.put(key, model);
            }
        }
        if (!modFlag.equals(model.getFlag()))
        {
            prErr("model flag " + modFlag + " mismatches previos flag " + model.getFlag());
        }
        String[] paramTokens;
        if (parts.length >= 4 && parts[i].equals("(") && parts[parts.length - 1].equals(")"))
        {
            paramTokens = Arrays.copyOfRange(parts, i + 1, parts.length - 1);
        } else
        {
            paramTokens = Arrays.copyOfRange(parts, i, parts.length);
        }
        parseParams(model.newParams(modSuffix), 0, paramTokens);
    }

    private SpiceInstance parseInitial(String[] parts) {
        SpiceInstance inst = new SpiceInstance(parts[0]);
        int i = 1;
        parseParams(inst.getParams(), i, parts);
        return inst;
    }

    private SpiceInstance parseTran(String[] parts) {
        if (parts.length < 3) {
            prErr("Not enough arguments for resistor");
            return null;
        }
        SpiceInstance inst = new SpiceInstance(parts[0] + " " + parts[1] + " " + parts[2]);
        return inst;
    }

    private void parseComment(String line) {
        SpiceSubckt currentSubckt = currentSubckt();
        if (currentSubckt == null) return;
        String [] parts = line.split("\\s+");
        for (int i=0; i<parts.length; i++) {
            if (parts[i].equalsIgnoreCase("PORT") && i+2 < parts.length) {
                String dir = parts[i+1];
                String port = parts[i+2];
                SpiceSubckt.PortType type = SpiceSubckt.PortType.valueOf(dir);
                if (type != null)
                    currentSubckt.setPortType(port, type);
                return;
            }
        }
    }

    /**
     * Read one line of the spice file. This concatenates continuation lines
     * that start with '+' to the first line, replacing '+' with ' '.  It
     * also ignores comment lines (lines that start with '*').
     * @return one spice line
     * @throws IOException
     */
    private String readLine() throws IOException {
        while (true) {
            lineno++;
            String line = null;
            if (macroReader != null && macroReader.hasNext()) {
                line = macroReader.next();
            } else if (reader != null) {
                line = reader.readLine();
            }
            if (line == null) {
                // EOF
                if (lines.length() == 0) return null;
                return removeString();
            }
            line = line.trim();
            if (line.startsWith("*")) {
                // comment line
                parseComment(line);
                continue;
            }
            if (line.startsWith("+")) {
                // continuation line
                lines.append(" ");
                lines.append(line.substring(1));
                continue;
            }
            // normal line
            if (lines.length() == 0) {
                // this is the first line read, read next line to see if continued
                lines.append(line);
            } else {
                // beginning of next line, save it and return completed line
                String ret = removeString();
                lines.append(line);
                return ret;
            }
        }
    }

    private String removeString() {
        String ret = lines.toString();
        lines.delete(0, lines.length());
        return ret;
    }

    // ================================= Writing ====================================

    public void writeFile(String fileName) {
        if (fileName == null) {
            write(System.out);
        } else {
            try {
                PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName)));
                write(out);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void write(PrintStream out) {
        Set<SpiceSubckt> usedSubckts = null;
        Set<SpiceModel> usedModels = null;
        if (WRITE_ONLY_USED)
        {
            usedSubckts = new HashSet<>();
            usedModels = new HashSet<>();
            for (SpiceInstance inst : topLevelInstances)
            {
                inst.markUsed(usedSubckts, usedModels);
            }
        }

        out.println("* Spice netlist");
        for (String key : options.keySet()) {
            String value = options.get(key);
            if (value == null) {
                out.println(".option "+key);
            } else {
                out.println(".option "+key+"="+options.get(key));
            }
        }
        out.println();
        for (String key : globalParams.keySet()) {
            out.println(".param "+key+"="+globalParams.get(key));
        }
        out.println();
        for (SpiceModel model : globalModels.values()) {
            model.write(out, usedModels);
        }
        out.println();
        for (String net : globalNets) {
            out.println(".global "+net);
        }
        out.println();
        for (String subcktName : subckts.keySet()) {
            SpiceSubckt subckt = subckts.get(subcktName);
            subckt.write(out, usedSubckts, usedModels);
            out.println();
        }
        for (SpiceInstance inst : topLevelInstances) {
            inst.write(out);
        }
        out.println();
        out.println(".end");
        out.flush();
        if (out != System.out) out.close();
    }

    static void multiLinePrint(PrintStream out, boolean isComment, String str)
    {
        // put in line continuations, if over 78 chars long
        char contChar = '+';
        if (isComment) contChar = '*';
        int lastSpace = -1;
        int count = 0;
        boolean insideQuotes = false;
        int lineStart = 0;
        for (int pt = 0; pt < str.length(); pt++)
        {
            char chr = str.charAt(pt);
//			if (sim_spice_machine == SPICE2)
//			{
//				if (islower(*pt)) *pt = toupper(*pt);
//			}
            if (chr == '\n')
            {
                out.print(str.substring(lineStart, pt+1));
                count = 0;
                lastSpace = -1;
                lineStart = pt+1;
            } else
            {
                if (chr == ' ' && !insideQuotes) lastSpace = pt;
                if (chr == '\'') insideQuotes = !insideQuotes;
                count++;
                if (count >= 78 && !insideQuotes && lastSpace > -1)
                {
                    String partial = str.substring(lineStart, lastSpace+1);
                    out.print(partial + "\n" + contChar);
                    count = count - partial.length();
                    lineStart = lastSpace+1;
                    lastSpace = -1;
                }
            }
        }
        if (lineStart < str.length())
        {
            String partial = str.substring(lineStart);
            out.print(partial);
        }
    }

    static class SpiceLibrary
    {
        private final List<String> lines = new ArrayList<>();
        private final Map<String,Integer> libIndex = new HashMap<>();

        SpiceLibrary(File file) throws IOException
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (line.length() >= 4 && line.substring(0, 4).toLowerCase().equals(".lib"))
                    {
                        String[] pieces = line.split("[ \t]+");
                        if (pieces.length == 2) {
                            libIndex.put(pieces[1].toLowerCase(), lines.size());
                        }
                    }
                }
            }
        }
    }

    // ======================== Spice Netlist Information ============================

    // =================================== test ================================

    public static void main(String [] args) {
        SpiceNetlistReader reader = new SpiceNetlistReader();

        try {
            reader.readFile(new File("/import/async/cad/2006/bic/jkg/bic/testSims/test_clk_regen.spi"), true);
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        reader.writeFile("/tmp/output.spi");
    }

//    private static void testLineParserTests() {
//        SpiceNetlistReader reader = new SpiceNetlistReader();
//        reader = new SpiceNetlistReader();
//        reader.file = new File("/none");
//        reader.lineno = 1;
//        testLineParser(reader, ".measure tran vmin min v( data) from=0ns to=1.25ns  ");
//        testLineParser(reader, ".param poly_res_corner='1.0 * p' * 0.8 corner");
//        testLineParser(reader, ".param poly_res_corner   =    '1.0 * p' * 0.8 corner");
//        testLineParser(reader, ".param AVT0N = AGAUSS(0.0,  '0.01 / 0.1' , 1)");
//    }
//
//    private static void testLineParser(SpiceNetlistReader reader, String line) {
//        System.out.println("Parsing: "+line);
//        String [] tokens = reader.getTokens(line);
//        for (int i=0; i<tokens.length; i++) {
//            System.out.println(i+": "+tokens[i]);
//        }
//    }
}
