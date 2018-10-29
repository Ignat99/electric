package com.sun.electric.tool.ncc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ErrorLogger;

public class CalibreLVSErrors {
	private Cell cell;
	private BufferedReader in;
	private boolean noPopupMessages;
	private int lineno;
	private String filename;
	private String type;
    private ErrorLogger logger;
    private List<LVSErrorGroup> groups;            // list of LVS errors
    private double scale;
	
	/**
	 * Method to read LVS Errors produced by Calibre
	 * @param filename
	 * @param cell
	 * @param type
	 * @param noPopupMessages
	 * @return number of errors. Negative number in case of valid data.
	 */
	public static int importErrors(String filename, Cell cell, String type, boolean noPopupMessages) {
        BufferedReader in;
        try {
            FileReader reader = new FileReader(filename);
            in = new BufferedReader(reader);
        } catch (IOException e) {
            System.out.println("Error importing "+type+" Errors: "+e.getMessage());
            return -1;
        }

        CalibreLVSErrors errors = new CalibreLVSErrors(cell, in, type, noPopupMessages);
        errors.filename = filename;
        
        // read errors
        errors.readErrors();
        // finish
        return errors.done();
    }
	
	CalibreLVSErrors(Cell cell, BufferedReader in, String type, boolean noPopupMessages)
	{
		assert(in != null);
		this.cell = cell;
        this.in = in;
        lineno = 0;
        this.type = type;
        this.groups = new ArrayList<LVSErrorGroup>();
        this.scale = cell.getTechnology().getScale();
	}
	
    private String readLine() throws IOException {
        return readLine(false);
    }
    
    private String readLine(boolean errorOnEOF) throws IOException {
        // if in is null we ignore
        if (in == null) return null;

        String line = in.readLine();
        if (line == null && errorOnEOF) {
            System.out.println("Unexpected End of File!");
            in = null;          // ignore rest of readLine requests
            return null;
        }
        lineno++;
        return line;
    }

    private static final String spaces = "[\\s\\t ]+";
    private static final String coordinates = "[\\s\\t(,)]+";
    
	private boolean readErrors()
	{
		List<String> linesToAnalyze = new ArrayList<String>();
		int errorLine = -1;
		int stage = -1;
		String layName = null, srcName = null;
		LVSErrorNet net = null;
		LVSErrorGroup grp = null;
		
        try {
            while(true) {
            	String nextLine = readLine(); // looking for "INCORRECT NETS"
            	if (nextLine == null)
            		return true; // end of file
            	nextLine = nextLine.trim();
            	// detect if it is an empty line
            	if (nextLine.isEmpty())
            	{
            		if (stage == 3 && linesToAnalyze.size() > 0) // reach end of an error section
            		{
	    				net.neterrors.add(new LVSNError(linesToAnalyze, errorLine));
	    				linesToAnalyze = new ArrayList<String>();
	    				errorLine = -1;
            		}
            		continue; //empty
            	}
            	if (nextLine.toUpperCase().contains("LAYOUT CELL NAME"))
            	{
        			String [] parts = nextLine.split(spaces);
        			assert (parts.length == 4);
            		layName = parts[3];
            	}
            	else if (nextLine.toUpperCase().contains("SOURCE CELL NAME"))
            	{
        			String [] parts = nextLine.split(spaces);
        			assert (parts.length == 4);
        			srcName = parts[3];
        			grp = null;
            	}
            	else if (nextLine.toUpperCase().contains("INCORRECT NETS"))
            	{
            		stage = 0;
            	}
            	else if (nextLine.toUpperCase().contains("INCORRECT INSTANCES") || 
            			nextLine.toUpperCase().contains("INSTANCES OF CELLS WITH NON-FLOATING EXTRA PINS"))
            	{
            		stage = 4;
            	}
            		// look for ********
            	else if (nextLine.startsWith("***"))
            	{
        			if (stage == 1)
        				stage = 2; // found net block
        			else if (stage == 5)
        				stage = 6; // found instances block
        			else if (stage == 3 || stage == 7)
        			{
        				// end of the group
        				if (stage == 7 && linesToAnalyze.size() > 0)
        				{
        					grp.insterrors.add(new LVSNError(linesToAnalyze, errorLine));
        					linesToAnalyze = new ArrayList<String>();
        					errorLine = -1;
        				}
        				stage = -1;
        			}
            	}
            	else if (nextLine.startsWith("DISC#"))
            	{
            		if (stage == 0)
            			stage = 1; // stage=1 only it comes from INCORRECT NETS
            		else if (stage == 4)
            			stage = 5; // stage=5 only it comes from INCORRECT INSTANCES
            		
            		if (stage == 1 || stage == 5) // incorrect something found
            		{
            			if (grp == null)
            			{
	                		grp = new LVSErrorGroup(layName, srcName, cell);
	                		groups.add(grp);
	                		layName = srcName = null; // clean for the next cell
            			}
            		}
            	}
            	else if (nextLine.contains("---"))
            	{
            		if (stage == 3) // another net
            		{
            			if (nextLine.toLowerCase().contains("devices on"))
            				continue; // skip this one
            			
            			// with linesToAnalyze.get(0).startsWith("(") trying to detect that
            			// the first element is not the second name of the net name
            			if (linesToAnalyze.size() > 0)
            			{
            				String firstElem = linesToAnalyze.get(0);
            				if (firstElem.startsWith("("))
	            			{
		            			net.neterrors.add(new LVSNError(linesToAnalyze, errorLine));
	        					errorLine = -1;
		            			linesToAnalyze = new ArrayList<String>();
		            			stage = 2; 
		            			assert(false); // validate
	            			}
            				else if (linesToAnalyze.size() == 1)
            				{
            					//System.out.println("This is the name of the other net? " + firstElem);
		            			linesToAnalyze = new ArrayList<String>();
            				}
            			}
            			else
            			{
            				if (net.neterrors.size() > 0)
            					stage = 2; // look for net name again only if it got something for the current net
            			}
            		}
            		else if (stage == 7) // got instance
            		{
            			grp.insterrors.add(new LVSNError(linesToAnalyze, errorLine));
            			linesToAnalyze = new ArrayList<String>();
    					errorLine = -1;
            			stage = 6;
            		}
            	}
            	else
            	{
            		// incorrect nets
            		if (stage == 2)
            		{
            			// get # elements, they start with a number
            			String [] parts = nextLine.split(spaces);
            			assert(parts.length >= 4);
            			try {
            				int number = Integer.parseInt(parts[0]);
            				stage = 3; // looking for first "("
            				net = new LVSErrorNet(parts[1] + "-" + parts[2] + "-" + parts[3], this.lineno);
            				grp.nets.add(net);
            				errorLine = this.lineno;
                        } catch (NumberFormatException e) {
                            continue; // not found
                        }
            		}
            		// incorrect instances
            		else if (stage == 6)
            		{
            			// get # elements, they start with a number
            			String [] parts = nextLine.split(spaces);
        				assert(parts.length >= 2);
            			try {
            				int number = Integer.parseInt(parts[0]);
            				stage = 7; // looking for first "("
            				String tmp = "";
            				for (int i = 1; i < parts.length; i++)
            					tmp += parts[i] + "\t";
            				tmp += parts[parts.length-1]; // not adding \t to the last one
            				linesToAnalyze.add(tmp);
            				errorLine = this.lineno;
                        } catch (NumberFormatException e) {
                            continue; // not found
                        }
            		}
            		else if (stage == 3 || stage == 7)
            		{
            			// collect groups
            			linesToAnalyze.add(nextLine);
            			if (errorLine == -1)
            				errorLine = this.lineno;
            		}
            	}
            }
        } catch (IOException e) {
            System.out.println("Error reading file: "+e.getMessage());
            return false;
        }
	}
	
	private int done() {
        try {
            in.close();
        } catch (IOException e) {}

        if (groups == null || groups.isEmpty())
        {
        	System.out.println("No erros found in '" + this.filename + "'");
        	return -1; // no errors found
        }
        // populate error logger
        logger = ErrorLogger.newInstance("Calibre "+type+" Errors");
        int sortKey = 0;
        int count = 0;

        for (Iterator<LVSErrorGroup> it = groups.iterator(); it.hasNext(); ) 
        {
        	LVSErrorGroup v = it.next();
            int localCount = 0;
            
            // Analyzing the nets
            for (Iterator<LVSErrorNet> it2 = v.nets.iterator(); it2.hasNext(); ) {
            	LVSErrorNet netError = it2.next();
            	String netName = "Net '" + netError.netName + "'";
            	localCount += collectErrors(netError.neterrors, netName, v.cell, sortKey, false);
            	
            	// in case no coordinates are found

                if (netError.neterrors.size() == 0)
                {
                	// error in net without devices
                    logger.logMessageWithLines(netName + ". Line=" + netError.errorLine + ": ", null, null, cell, sortKey, true);
                    localCount++;
                }
            }
            
            // Analyzing the instances
            localCount += collectErrors(v.insterrors, "Instance", v.cell, sortKey, false);
            
            logger.setGroupName(sortKey, "(" + (localCount) + ") " + "Lay: '" + v.layName + "', Source: '" + v.srcName + "'");
            sortKey++;
            count += localCount;
        }
        
        System.out.println(type+" Imported "+count+" errors from "+filename);
        if (count == 0 && !noPopupMessages) {
        	Job.getUserInterface().showInformationMessage(type+" Imported Zero Errors", type+" Import Complete");
        }
        logger.termLogging(!noPopupMessages);
        return logger.getNumErrors();
	}
	
	// private function to collect the errors
	private int collectErrors(List<LVSNError> errors, String name, Cell cell, int sortKey, boolean reportAll)
	{
		int localCount = 0;
		int localY = 1;
		
        for (Iterator<LVSNError> it3 = errors.iterator(); it3.hasNext();) 
        {
        	LVSNError lvsError = it3.next();
        	String errorDesc = "";
        	List<EPoint> elist = new ArrayList<EPoint>();
        	
        	for (String s : lvsError.info)
        	{
        		errorDesc += s + "\n";
        		if (!s.contains("(")) 
        			continue; // ignore the rest
        		String [] parts = s.split(coordinates);
        		if (parts.length > 2)
        		{
            		double px, py;
        			try {
        				// Style like text(x,y)more test
        				px = scale * Double.parseDouble(parts[1]);
        				py = scale * Double.parseDouble(parts[2]);
        				elist.add(EPoint.fromLambda(px, py));
        				
                    } catch (NumberFormatException e) {
                        continue; // not found
                    }
        		}
        	}
        	// If reportAll=false, only report errors with coordinates
        	if (reportAll || (!reportAll && elist.size() > 0))
        	{
        		logger.logMessageWithLines(name+". Line=" + lvsError.errorLine + ": "+errorDesc, elist, null, cell, sortKey, true);
        		localY++;
        		localCount++;
        	}
        }
        return localCount;
	}

// -----------------------------------------------------------------------------
	// Private classes
    private static class LVSNError {
        private final List<String> info;
        private final int errorLine;

        private LVSNError(List<String> list, int line) {
            this.info = list;
            this.errorLine = line;
        }
    }
    
    private static class LVSErrorNet {
    	private final String netName;
    	private List<LVSNError> neterrors;
        private final int errorLine;
    	
    	private LVSErrorNet(String netName, int errorLine)
    	{
    		this.netName = netName;
    		this.errorLine = errorLine;
    		neterrors = new ArrayList<LVSNError>();
    	}
    }
    
    static class LVSErrorGroup {
    	private final String layName;
    	private final String srcName;
        private final Cell cell;
    	private List<LVSErrorNet> nets;
    	private List<LVSNError> insterrors; // instance errors
    	
    	private LVSErrorGroup(String layName, String srcName, Cell cell)
    	{
    		this.layName = layName;
    		this.srcName = srcName;
    		this.cell = cell;
    		nets = new ArrayList<LVSErrorNet>();
    		insterrors = new ArrayList<LVSNError>();
    	}
    }
}
