/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MemoryUsage.java
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
package com.sun.electric.util.memory;

public class MemoryUsage {
    
    private static MemoryUsage instance = new MemoryUsage();
    
    private MemoryUsage() {
        
    }
    
    public static MemoryUsage getInstance() {
        return instance;
    }
    
    public long getHeapSize() {
        return Runtime.getRuntime().totalMemory();
    }
    
    public long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }
    
    public long getUsedMemory() {
        return getHeapSize() - Runtime.getRuntime().freeMemory();
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        
        builder.append(Memory.formatMemorySize(getUsedMemory()));
        builder.append("/");
        builder.append(Memory.formatMemorySize(getHeapSize()));
        builder.append("/");
        builder.append(Memory.formatMemorySize(getMaxMemory()));
        
        return builder.toString();
    }


    /**
	 * Method to return the amount of memory being used by Electric.
	 * Calls garbage collection and delays to allow completion, so the method is SLOW.
	 * @return the number of bytes being used by Electric.
	 */
	public static long getMemoryUsage()
	{
		collectGarbage();
		collectGarbage();
        return instance.getUsedMemory();
	}
    
    /**
	 * Method to collect garbagereturn the amount of memory being used by Electric.
	 * Garbage collection delays to allow completion, so the method is SLOW.
	 */
	public static void collectGarbage()
	{
		try
		{
			System.gc();
			Thread.sleep(100);
			System.runFinalization();
			Thread.sleep(100);
		} catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
	}

}
