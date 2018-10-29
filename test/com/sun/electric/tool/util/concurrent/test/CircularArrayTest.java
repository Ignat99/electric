/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CircularArrayTest.java
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
package com.sun.electric.tool.util.concurrent.test;

import org.junit.Test;

/**
 * @author Felix Schmidt
 * 
 */
public class CircularArrayTest {

	@Test
	public void testIndexCalculation() {
		// for (int i = 0; i < 32; i++) {
		// System.out.println(1 << i);
		// Assert.assertTrue((1 << i) >= 0);
		// }
	}

	@Test
	public void testModuloOp() {

		Integer[][] values = { { 7, 3 }, { 7, -3 }, { -7, 3 }, { -7, -3 } };

		for (Integer[] value : values) {
			int rest = value[0] % value[1];
			System.out.println(value[0] + " % " + value[1] + " = " + rest);
		}
	}

}
