/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BitMapMinAreaChecker.scala
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
package com.sun.electric.plugins.minarea.bitmapscala

import com.sun.electric.api.minarea.{MinAreaChecker, ManhattanOrientation, LayoutCell, ErrorLogger}
import com.sun.electric.api.minarea.geometry.{Point, Shapes}
import java.util.{BitSet, Properties}
import scala.collection.immutable.TreeSet
import scala.collection.mutable.{Stack, HashMap, ArrayBuffer}
import scala.language.implicitConversions

class BitMapMinAreaChecker extends MinAreaChecker {
  val DEBUG = 0

  override def getAlgorithmName = "BitMapScala"

  override def getDefaultParameters = {
    val parameters = new Properties()
    parameters.put(MinAreaChecker.REPORT_TILES, java.lang.Boolean.TRUE)
    parameters
  }

  implicit def fn2RectHandler(f: (Int, Int, Int, Int) => Unit) = {
    new LayoutCell.RectangleHandler {
      override def apply(minX: Int, minY: Int, maxX: Int, maxY: Int) = {
        f(minX, minY, maxX, maxY)
      }
    }
  }

  implicit def fn2SubHandler(f: (LayoutCell, Int, Int, ManhattanOrientation) => Unit) = {
    new LayoutCell.SubcellHandler {
      override def apply(subCell: LayoutCell, anchorX: Int, anchorY: Int, subOrient: ManhattanOrientation) = {
        f(subCell, anchorX, anchorY, subOrient)
      }
    }
  }

  // traverse flattened rectangles
  private def flattenRects(top: LayoutCell) = (proc: (Int, Int, Int, Int) => Unit) => {
    def flatten(t: LayoutCell, x: Int, y: Int, orient: ManhattanOrientation): Unit = {
      val a = new Array[Int](4)
      t.traverseRectangles((minX: Int, minY: Int, maxX: Int, maxY: Int) => {
          a(0) = minX
          a(1) = minY
          a(2) = maxX
          a(3) = maxY
          orient.transformRects(a, 0, 1)
          proc(a(0) + x, a(1) + y, a(2) + x, a(3) + y)
        })
      t.traverseSubcellInstances((subCell: LayoutCell, anchorX: Int, anchorY: Int, subOrient: ManhattanOrientation) => {
          a(0) = anchorX
          a(1) = anchorY
          orient.transformPoints(a, 0, 1)
          flatten(subCell, a(0) + x, a(1) + y, orient.concatenate(subOrient))
        })

    }
    flatten(top, 0, 0, ManhattanOrientation.R0)
  }

  /**
   * @param topCell top cell of the layout
   * @param minArea minimal area of valid polygon
   * @param parameters algorithm parameters
   * @param errorLogger an API to report violations
   */
  override def check(topCell: LayoutCell, minArea: Long, parameters: Properties, errorLogger: ErrorLogger) = {
    
    val reportTiles = java.lang.Boolean.parseBoolean(parameters.get(MinAreaChecker.REPORT_TILES).toString)

    // find unique coordinates
    var xcoords = new TreeSet[Int]()
    var ycoords = new TreeSet[Int]()
    flattenRects(topCell)((minX: Int, minY: Int, maxX: Int, maxY: Int) => {
        if (DEBUG >= 4)
          println(" flat [" + minX + ".." + maxX + "]x[" + minY + ".." + maxY + "]")
        xcoords = xcoords + minX + maxX
        ycoords = ycoords + minY + maxY
      })

    val xsize = xcoords.size - 1
    val ysize = ycoords.size - 1

    // xa,ya maps coordinate index to coordinate value
    // xm,ym maps coordinate value to coordinate index
    val xa = new Array[Int](xsize + 1)
    val ya = new Array[Int](ysize + 1)
    val xm = new HashMap[Int, Int]()
    val ym = new HashMap[Int, Int]()
    for (x <- xcoords) {
      xa(xm.size) = x
      xm.put(x, xm.size)
    }
    for (y <- ycoords) {
      ya(ym.size) = y
      ym.put(y, ym.size)
    }

    // fill bit map
    val bitMap = new Array[BitSet](xsize)
    for (i <- 0 until bitMap.length) bitMap(i) = new BitSet
    flattenRects(topCell)((minX: Int, minY: Int, maxX: Int, maxY: Int) => {
        val ymin = ym(minY)
        val ymax = ym(maxY)
        for (x <- xm(minX) until xm(maxX)) bitMap(x).set(ymin, ymax)
      })

    if (DEBUG >= 4) {
      println("xcoords=" + xcoords)
      println("ycoords=" + ycoords)
      printBitMap(bitMap, xsize, ysize)
    }

    // stack of tiles to fill polygon
    var stack = new Stack[Point]
    var polyArea: Long = 0
    var curPolyBB = new ArrayBuffer[Point]()

    def pushTile(x: Int, y: Int) = {
      val w: Long = xa(x + 1) - xa(x)
      val h: Long = ya(y + 1) - ya(y)
      polyArea += w * h
      bitMap(x).clear(y)
      val p = new Point(x, y)
      if (reportTiles) curPolyBB += p
      stack.push(p)
    }

    // find polygons in reverse lexicographical order
    var totalArea = 0L
    var x = xsize - 1
    while (x >= 0) {
      var y = ysize - 1
      while (y >= 0) {
        if (bitMap(x).get(y)) {
          polyArea = 0
          assert(curPolyBB.isEmpty)
          // find polygon area and erase polygon from bit map
          pushTile(x, y)
          while (!stack.isEmpty) {
            val p = stack.top
            if (p.getX - 1 >= 0 && bitMap(p.getX - 1).get(p.getY))
              pushTile(p.getX - 1, p.getY)
            else if (p.getX + 1 < xsize && bitMap(p.getX + 1).get(p.getY))
              pushTile(p.getX + 1, p.getY)
            else if (p.getY - 1 >= 0 && bitMap(p.getX).get(p.getY - 1))
              pushTile(p.getX, p.getY - 1)
            else if (p.getY + 1 < ysize && bitMap(p.getX).get(p.getY + 1))
              pushTile(p.getX, p.getY + 1)
            else
              stack.pop
          }
          totalArea += polyArea
          if (polyArea < minArea) {
            var shape: java.awt.Shape = null
            if (reportTiles) {
              val tiles = new Array[Int](curPolyBB.size*4)
              for (i <- 0 until curPolyBB.size) {
                val p = curPolyBB(i)
                val x = p.getX
                val y = p.getY
                tiles(i*4 + 0) = xa(x)
                tiles(i*4 + 1) = ya(y)
                tiles(i*4 + 2) = xa(x+1)
                tiles(i*4 + 3) = ya(y+1)
              }
              shape = Shapes.fromTiles(tiles)
            }
            errorLogger.reportMinAreaViolation(polyArea, xa(x+1), ya(y+1), shape)
          }
          curPolyBB.clear
        }
        y -= 1
      }
      x -= 1
    }

    if (DEBUG >= 1) {
      println("Total Area " + totalArea)
    }
  }

  def printBitMap(bitMap: Array[BitSet], xsize: Int, ysize: Int) = {
    var y = ysize - 1
    while (y >= 0) {
      for (x <- 0 until xsize) print(if (bitMap(x).get(y)) 'X' else ' ')
      println
      y -= 1
    }
  }
}
