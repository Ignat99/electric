/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Launcher.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.api.minarea.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;

import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.MinAreaChecker;
import com.sun.electric.api.minarea.errorLogging.AbstractErrorLogger;
import com.sun.electric.api.minarea.errorLogging.ErrorLoggerFactory;
import com.sun.electric.api.minarea.errorLogging.ErrorLoggerFactory.LoggerTypes;

/**
 *
 */
public class Launcher {

	private static void help() {
		System.out.println("Usage: file.lay minarea errorLogger [algorithm.properties]");
		System.out.println("    file.lay             - file with serialized layout");
		System.out.println("    minarea              - minarea threashold");
		System.out.println("    errorLogger          - error logger type: {stdout, electric, csv}");
		System.out.println("    algorithm.properties - optional file with algorithm properties");
	}

	public static void main(String[] args) {
		if (args.length < 3) {
			help();
			System.exit(0);
		}
		String layoutFileName = args[0];

		long minarea = 0;

		// parse minarea parameter, if the parameter value is not of type long,
		// than throw Error
		try {
			minarea = Long.valueOf(args[1]);
		} catch (NumberFormatException ex) {
			throw new Error("Parameter minarea must be of type long.");
		}

		// parse errorLogger parameter, if the parameter value is not a constant
		// in LoggerTypes, than throw Error
		LoggerTypes errorLoggerType;
		try {
			errorLoggerType = LoggerTypes.valueOf(args[2]);
		} catch (IllegalArgumentException ex) {
			throw new Error("Parameter errorLogger must be a constant of LoggerTypes.");
		}

		String algorithmPropertiesFileName = args.length > 3 ? args[3] : null;
		
		try {
			File layoutFile = new File(layoutFileName);
			InputStream is;
			if (layoutFile.canRead()) {
				is = new FileInputStream(layoutFileName);
				System.out.println("file " + layoutFileName);
			} else {
				is = Launcher.class.getResourceAsStream(layoutFileName);
				System.out.println("resource " + layoutFileName);
			}
			ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(is));
			LayoutCell topCell = (LayoutCell) in.readObject();
			in.close();

			// Get instance of MinAreaChecker
			ServiceLoader<MinAreaChecker> minAreaCheckerServiceLoader = ServiceLoader.load(MinAreaChecker.class);
			
			// Available MinArea Checkers
			for (Iterator<MinAreaChecker> it = minAreaCheckerServiceLoader.iterator(); it.hasNext(); ) {
	            MinAreaChecker checker = it.next();
	            System.out.println("Min area checker implementation '" + checker.getAlgorithmName() + "'");
			
//			Iterator<MinAreaChecker> it = minAreaCheckerServiceLoader.iterator();
//			if (!it.hasNext()) {
//				System.out.println("Implementation of com.sun.electric.api.minarea.MinAreaChecker is not found in the classpath");
//				System.exit(1);
//			}
//			MinAreaChecker checker = it.next();
				System.out.println("topCell " + topCell.getName() + " [" + topCell.getBoundingMinX() + ".."
						+ topCell.getBoundingMaxX() + "]x[" + topCell.getBoundingMinY() + ".." + topCell.getBoundingMaxY()
						+ "] minarea=" + minarea);
				Properties parameters = checker.getDefaultParameters();
				if (algorithmPropertiesFileName != null) {
					InputStream propertiesIn = new FileInputStream(algorithmPropertiesFileName);
					parameters.load(propertiesIn);
					propertiesIn.close();
				}
				AbstractErrorLogger logger = ErrorLoggerFactory.createErrorLogger(errorLoggerType, topCell.getName());
				System.out.println("algorithm " + checker.getAlgorithmName() + " parameters:" + parameters + " errorLogger:"
						+ errorLoggerType);
	
				ElapseTimer timer = ElapseTimer.createInstance().start();
				checker.check(topCell, minarea, parameters, logger);
				timer.end();
	
				logger.printReports(timer.getTime());
	
				System.out.println("Elapsed time for min-area checking: " + timer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * FIXME This function works only on unix-style operating systems ...
	 * 
	 * @return the process ID in a string.
	 * @throws IOException
	 */
	public static String getPID() throws IOException {
		/*
		 * byte[] bo = new byte[100]; String[] cmd = {"bash", "-c",
		 * "echo $PPID"}; Process process = Runtime.getRuntime().exec(cmd);
		 * process.getInputStream().read(bo); String pid = new String(bo);
		 * return pid.trim();
		 */

		return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	}
}
