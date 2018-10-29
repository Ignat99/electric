package com.sun.electric.util;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.electric.database.text.Version;

/**************************************************************************************************************
 *  UsageFormatter class
 *  Class to log Electric usage and record that information on disk
 **************************************************************************************************************/
public class UsageFormatter extends Formatter 
{
	private String extraInfo;
	
	UsageFormatter(String extra) {extraInfo = extra;}
	
	private String calcDate(long millisecs) 
	{
		SimpleDateFormat date_format = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");
		Date resultdate = new Date(millisecs);
		return date_format.format(resultdate);
	}

	@Override
	public String format(LogRecord record) {
		StringBuffer buf = new StringBuffer(1000);
		buf.append(calcDate(record.getMillis()) + " - " 
				+ getUserAndHostInfo() + " - " + Version.getVersion() + " - " + extraInfo + "\n");
		return buf.toString();
	}
	
	public static String getUser() { return System.getProperty("user.name", "unknownUser"); }
	
	private static String getUserAndHostInfo()
	{
		String hostName = "unknownHost";
        try {
            InetAddress addr = InetAddress.getLocalHost();
            hostName = addr.getHostName();
        } catch (java.net.UnknownHostException e) {
        }
        return getUser() + "-" + hostName;
	}
	
	// function to open different handlers
	private static FileHandler getHandler(String path)
	{
		FileHandler fileHandler = null;
		
        try {
        	fileHandler = new FileHandler(path, true);
        }
        catch (Exception e)
        {
    		System.out.println("Error in opening Logger '"+ path + "'");
        }
        
        return fileHandler;
	}
	
	public static void logUsage(String name, String path, boolean tryHome, String extraInfo)
	{
        Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        logger.setLevel(Level.INFO);
        FileHandler fileHandler = getHandler(path + "/" + name);
        // try home directory
        if (fileHandler == null && tryHome)
        	fileHandler = getHandler(System.getenv("HOME") + "/" + name);
        if (fileHandler != null)
        {
        	logger.addHandler(fileHandler);
        	fileHandler.setFormatter(new UsageFormatter(extraInfo));
        }
        logger.info("Logging this session for '" + getUserAndHostInfo() + "'");
        // don't close the handler before UsageFormatter.format() is called.
        if (fileHandler != null)
            fileHandler.close();
	}
}