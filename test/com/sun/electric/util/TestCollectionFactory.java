/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TestCollectionFactory.java
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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Felix Schmidt
 */
public class TestCollectionFactory
{

    @Test
    public void testArrayMergeSuccess()
    {
        Integer[] arr1 = new Integer[]
        {
            1, 2
        };
        Integer[] arr2 = new Integer[]
        {
            3, 4, 5
        };
        verifyResult(new Integer[]
        {
            1, 2, 3, 4, 5
        }, CollectionFactory.arrayMerge(arr1, arr2));
    }

    @Test
    public void testArrayMergeEmpty()
    {
        Integer[] arr1 = new Integer[]
        {
        };
        Integer[] arr2 = new Integer[]
        {
            3, 4, 5
        };
        verifyResult(new Integer[]
        {
            3, 4, 5
        }, CollectionFactory.arrayMerge(arr1, arr2));
    }

    @Test
    public void testArrayMergeEmptyBoth()
    {
        Integer[] arr1 = new Integer[]
        {
        };
        Integer[] arr2 = new Integer[]
        {
        };
        Assert.assertNull(CollectionFactory.arrayMerge(arr1, arr2));
    }

    @Test
    public void testArrayMergeNull()
    {
        Integer[] arr1 = null;
        Integer[] arr2 = new Integer[]
        {
            3, 4, 5
        };
        verifyResult(new Integer[]
        {
            3, 4, 5
        }, CollectionFactory.arrayMerge(arr1, arr2));
    }

    @Test
    public void testArrayMergeNullBoth()
    {
        Integer[] arr1 = null;
        Integer[] arr2 = null;
        Assert.assertNull(CollectionFactory.arrayMerge(arr1, arr2));
    }

    private void verifyResult(Integer[] expected, Integer[] result)
    {
        Assert.assertEquals(expected.length, result.length);

        for (int i = 0; i < expected.length; i++)
        {
            Assert.assertEquals(expected[i], result[i]);
        }
    }

}
