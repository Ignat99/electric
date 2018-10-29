/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorLoggerCSVFile.java
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
package com.sun.electric.api.minarea.errorLogging;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import com.sun.electric.api.minarea.errorLogging.io.CSVFile;
import com.sun.electric.api.minarea.errorLogging.io.CSVInput;
import com.sun.electric.api.minarea.errorLogging.io.CSVWriter;

public class ErrorLoggerCSVFile extends AbstractErrorLogger {

	private String fileName;

	/**
	 * Constructor: Please set the VM-argument -Dminarea.csv to the output file
	 * name. If the file do not exist the algorithm will create a new one,
	 * otherwise it will append to the existing one.
	 */
	public ErrorLoggerCSVFile() {
		this.fileName = System.getProperty("minarea.csv");
	}

	public void printReports(long time) {

		try {
			List<String> csvEntry = new LinkedList<String>();
			csvEntry.add(String.valueOf(time));
			for (MinAreaViolation violation : violations) {
				csvEntry.add(this.getViolationString(violation));
			}

			CSVFile csvFile = new CSVFile(';');
			File file = new File(this.fileName);
			if (file.exists()) {
				CSVInput input = new CSVInput(file);
				csvFile = input.readCSVFile(';', false);
			}

			csvFile.addLine(csvEntry);

			CSVWriter writer = new CSVWriter(file);
			writer.printCSVFile(csvFile);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public String getViolationString(MinAreaViolation violation) {
		return new StringBuilder().append(violation.getMinArea()).append("|")
				.append(violation.getX()).append("|").append(violation.getY())
				.toString();
	}
}
