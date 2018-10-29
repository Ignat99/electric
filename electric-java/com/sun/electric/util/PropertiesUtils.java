/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PropertiesUtils.java
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
package com.sun.electric.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;

/**
 * @author Felix Schmidt
 *
 */
public class PropertiesUtils {

	private PropertiesUtils() {

	}

	/**
	 *
	 * @param propsName
	 * @throws Exception
	 */
	public static Properties load(String propsName) throws Exception {
		Properties props = new Properties();
		URL url = ClassLoader.getSystemResource(propsName);
		props.load(url.openStream());
		return props;
	}

	/**
	 *
	 * @param propsFile
	 * @throws Exception
	 */
	public static Properties load(File propsFile) throws Exception {
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(propsFile);
		props.load(fis);
		fis.close();
		return props;
	}

}
