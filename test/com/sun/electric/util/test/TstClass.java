/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TstClass.java
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
package com.sun.electric.util.test;

/**
 * @author Felix Schmidt
 *
 */
public class TstClass { // Don't have Test in its name

	public static final String testString = "TestString";

	@SuppressWarnings("unused")
	@TestByReflection(testMethodName = "testMethod")
	private void testMethod() {

	}

	@SuppressWarnings("unused")
	@TestByReflection(testMethodName = "testMethod2")
	private String testMethod2() {
		return testString;
	}

	@SuppressWarnings("unused")
	@TestByReflection(testMethodName = "testMethod3")
	private String testMethod3(String value) {
		return value;
	}
}
