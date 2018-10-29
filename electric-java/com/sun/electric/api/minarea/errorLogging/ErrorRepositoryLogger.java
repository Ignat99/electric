/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorRepositoryLogger.java
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
public class ErrorRepositoryLogger extends AbstractErrorLogger {

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.electric.api.minarea.MinAreaChecker.ErrorLogger#printReports
     * ()
     */
    @Override
    public void printReports(long time) {
        System.out.println("***********************************************");
        if (violations.isEmpty()) {
            System.out.println("No DRC violation found: Good Job!");
        } else {
            System.out.println("DRC Min-Area Violations: " + violations.size());
            System.out.println();
            sortViolations();
            for (MinAreaViolation violation : violations) {
                System.out.println("reportMinAreaViolation("
                        + violation.toString() + ", " + violation.getMinArea()
                        + ");");
            }
            System.out.println("***********************************************");
        }
    }
}
