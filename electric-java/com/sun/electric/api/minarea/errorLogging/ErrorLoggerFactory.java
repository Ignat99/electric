/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorLoggerFactory.java
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

/**
 * @author Felix Schmidt
 * 
 */
public class ErrorLoggerFactory {

    public enum LoggerTypes {

        stdout, electric, csv
    }

    private ErrorLoggerFactory() {
    }

    public static AbstractErrorLogger createErrorLogger(LoggerTypes type, String topCell) {
        if (type.equals(LoggerTypes.stdout)) {
            return new ErrorRepositoryLogger();
        } else if (type.equals(LoggerTypes.electric)) {
            return new ErrorToElectricReporter(topCell);
        } else if (type.equals(LoggerTypes.csv)) {
            return new ErrorLoggerCSVFile();
        } else {
            throw new IllegalArgumentException();
        }
    }
}
