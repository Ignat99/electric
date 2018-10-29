/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SimpleChecker.java
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
package com.sun.electric.plugins.minarea.deltamerge1

import com.sun.electric.api.minarea.ErrorLogger
import com.sun.electric.api.minarea.LayoutCell
import com.sun.electric.api.minarea.ManhattanOrientation
import com.sun.electric.api.minarea.MinAreaChecker
import com.sun.electric.api.minarea.geometry.Point

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.Arrays
import java.io.DataOutputStream;
import java.util.BitSet
import java.util.Properties

//import scala.concurrent.Future
//import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable.TreeSet
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.Stack
import scala.math.{min, max}


/**
 * Simple MinAreaChecker
 */
object SimpleChecker {
  val PARALLEL = false
  val DEBUG = 0
  val DEFAULT_RECTS_PER_STRIPE: java.lang.Long = 100000
}

class SimpleChecker extends MinAreaChecker {
  
  /**
   * 
   * @return the algorithm name
   */
  def getAlgorithmName = "DeltaMerge1"
  
  /**
   * 
   * @return the names and default values of algorithm parameters
   */
  def getDefaultParameters() = {
    val parameters = new Properties()
    parameters.put(MinAreaChecker.REPORT_TILES, java.lang.Boolean.TRUE)
    parameters.put(MinAreaChecker.RECTS_PER_STRIPE, SimpleChecker.DEFAULT_RECTS_PER_STRIPE)
    parameters
  }
  
  /**
   * @param topCell
   *            top cell of the layout
   * @param minArea
   *            minimal area of valid polygon
   * @param parameters
   *            algorithm parameters
   * @param errorLogger
   *            an API to report violations
   */
  /**
   * @param topCell top cell of the layout
   * @param minArea minimal area of valid polygon
   * @param parameters algorithm parameters
   * @param errorLogger an API to report violations
   */
  override def check(topCell: LayoutCell, minArea: Long, parameters: Properties, errorLogger: ErrorLogger) = {
    val task = new CheckerTask(topCell, minArea, errorLogger)
    val rectsPerStripe = java.lang.Long.parseLong(parameters.get(MinAreaChecker.RECTS_PER_STRIPE).toString)
    val numRects = countRects(topCell, new HashMap[LayoutCell,Long]())
    val numStripes = (numRects/rectsPerStripe + 1).asInstanceOf[Int]
    if (SimpleChecker.DEBUG >= 1) println("numStripes="+numStripes)
    task.check(numStripes)
  }
  
  def countRects(t: LayoutCell, counted: HashMap[LayoutCell,Long]): Long = {
    counted.get(t) match {
      case Some(count) => count
      case None =>
        var count: Long = t.getNumRectangles
        t.traverseSubcellInstances(new LayoutCell.SubcellHandler {
            def apply(subCell: LayoutCell, anchorX: Int, anchorY: Int, subOrient: ManhattanOrientation) = {
              count += countRects(subCell, counted)
            }
          })
        counted.put(t, count)
        count
    }
  }
}

class CheckerTask(topCell: LayoutCell, minArea: Long, errorLogger: ErrorLogger) {
  var totalArea = 0L
  def getTopCell = topCell
  def getMinArea = minArea
  def reportMinAreaViolation(area: Long, x: Int, y: Int) = {
    if (area < minArea) errorLogger.reportMinAreaViolation(area, x, y, null)
  }
  def check(recommendedNumStripes: Int) = {
    // serial preparations
    val dx = topCell.getBoundingMaxX - topCell.getBoundingMinX
    val numStripes = min(recommendedNumStripes, dx)
    val x0 = topCell.getBoundingMinX
    val x = new Array[Int](numStripes + 1)
    val dxl: Long = dx
    for (i <- 0 until x.size) {
      x(i) = x0 + (dxl*i/numStripes).asInstanceOf[Int]
    }
    if (SimpleChecker.DEBUG >= 2) {
      print("StripesX:")
      for (i <- 0 until x.size) print(" "+x(i))
      println
    }
    val exts = new Array[Function0[StripeExts]](numStripes)
    
    for (i <- 0 until numStripes) {
      val stripeChecker = new StripeChecker(this, x(i), x(i + 1))
//      if (SimpleChecker.PARALLEL) {
//        exts(i) = Future { stripeChecker() }
//      } else {
        exts(i) = stripeChecker
//      }
    }
    
    // serial reduce
    var result = StripeExts.emptyStripeExts(topCell.getBoundingMinX)
    for (i <- 0 until exts.size) {
      result = StripeExts.connectStripes(result, exts(i)(), minArea, errorLogger)
      if (SimpleChecker.DEBUG >= 3) result.printIt
    }
    result = StripeExts.connectStripes(result, StripeExts.emptyStripeExts(topCell.getBoundingMaxX), minArea, errorLogger)
    assert(result.getMinSegs.isEmpty && result.getMaxSegs.isEmpty)
    if (SimpleChecker.DEBUG >= 1) println("Total Area "+result.getTotalArea)
  }
}

/**
 * @param topCell top cell of the layout
 * @param minArea minimal area of valid polygon
 * @param stripeMinX x-coordinate of left stripe boundary
 * @param stripeMaxX x-coordinate of right stripe boundary
 * @param errorLogger an API to report violations
 */
class StripeChecker(task: CheckerTask, stripeMinX: Int, stripeMaxX: Int) extends Function0[StripeExts] {
  // traverse flattened rectangles
  private def flattenRects(top: LayoutCell, proc: (Int, Int, Int, Int) => Unit) = {
    def flatten(t: LayoutCell, x: Int, y: Int, orient: ManhattanOrientation): Unit = {
      val a = new Array[Int](4)
      t.traverseRectangles(new LayoutCell.RectangleHandler {
          def apply(minX: Int, minY: Int, maxX: Int, maxY: Int) = {
            a(0) = minX
            a(1) = minY
            a(2) = maxX
            a(3) = maxY
            orient.transformRects(a, 0, 1)
            val tminX = max(a(0) + x, stripeMinX)
            val tminY = a(1) + y
            val tmaxX = min(a(2) + x, stripeMaxX)
            val tmaxY = a(3) + y
            if (tminX < tmaxX) proc(tminX, tminY, tmaxX, tmaxY)
          }
        })
      t.traverseSubcellInstances(new LayoutCell.SubcellHandler {
          def apply(subCell: LayoutCell, anchorX: Int, anchorY: Int, subOrient: ManhattanOrientation) = {
            a(0) = anchorX
            a(1) = anchorY
            orient.transformPoints(a, 0, 1)
            val newAnchorX = a(0) + x
            val newanchorY = a(1) + y
            val newOrient = orient.concatenate(subOrient)
            a(0) = subCell.getBoundingMinX
            a(1) = subCell.getBoundingMinY
            a(2) = subCell.getBoundingMaxX
            a(3) = subCell.getBoundingMaxY
            newOrient.transformRects(a, 0, 1)
            if (a(0) + newAnchorX < stripeMaxX && a(2) + newAnchorX > stripeMinX) {
              flatten(subCell, newAnchorX, newanchorY, newOrient)
            }
          }
        })
      
    }
    flatten(top, 0, 0, ManhattanOrientation.R0)
  }
  
  override def apply(): StripeExts = {
//    applyBitMap
    applyDeltaMerge
  }
  
  def applyDeltaMerge(): StripeExts = {
    val ps = new PointsSorter
    flattenRects(task.getTopCell, (minX: Int, minY: Int, maxX: Int, maxY: Int) => {
        ps.put(minX, minY, maxX, maxY)
      })
    var bout = new ByteArrayOutputStream
    val out = new DataOutputStream(bout)
    val dm = new DeltaMerge
    dm.loop(ps.fix, out)
    val ba = bout.toByteArray
    bout = null
    
    val inpS = new DataInputStream(new ByteArrayInputStream(ba))
    val up = new UnloadPolys
    val trees = up.loop(inpS, false)
    inpS.close
    
//    val inpS2 = new DataInputStream(new ByteArrayInputStream(ba))
//    val up2 = new UnloadPolys2
//    val trees2 = up2.loop(inpS2, false)
//    inpS2.close
//    
//    compareTrees(trees, trees2)
    
    var totalArea = 0L
    val minSegs = new ArrayBuffer[ExtSegment]()
    val maxSegs = new ArrayBuffer[ExtSegment]()
    
    def traversePolyTree(obj: PolyTree, level: Int, minArea: Long): Unit = {
      if (level % 2 == 0) {
        val poly = obj.getPoly
        if (SimpleChecker.DEBUG >= 4) {
          for (i <- 0 until poly.length/2) {
            print(" ("+poly(i*2)+","+poly(i*2+1)+")")
          }
          println
        }
        var area = obj.getAreaOfPoly
        val sit = obj.getSons.iterator
        while (sit.hasNext) {
          val son = sit.next
          val hole = son.getPoly
          if (SimpleChecker.DEBUG >= 4) {
            print(" hole")
            for (i <- 0 until hole.length/2) {
              print(" ("+hole(i*2)+","+hole(i*2+1)+")")
            }
            println
          }
          area -= son.getAreaOfPoly
        }
        var pe: ExtPoly = if (area < minArea) null else StripeExts.hugePoly
        if (level == 0) {
          var i = 0
          while (i*4 < poly.size) {
            val p1x = poly(i*4+0)
            val p1y = poly(i*4+1)
            val p2x = poly(i*4+2)
            val p2y = poly(i*4+3)
            assert(p1x == p2x)
            val x = p1x
            if (x == stripeMinX) {
              val y1 = p1y
              val y2 = p2y
              assert(y1 > y2)
              if (pe == null) {
                pe = new ExtPoly
                pe.area = area
                pe.x = poly(2)
                pe.y = poly(3)
              }
              minSegs += new ExtSegment(y2, y1, pe)
            } else if (x == stripeMaxX) {
              val y1 = p1y
              val y2 = p2y
              assert(y1 < y2)
              if (pe == null) {
                pe = new ExtPoly
                pe.area = area
                pe.x = poly(2)
                pe.y = poly(3)
              }
              maxSegs += new ExtSegment(y1, y2, pe)
            }
            i += 1
          }
        }
        totalArea += area
        if (area < minArea && pe == null) {
          task.reportMinAreaViolation(area, poly(2), poly(3))
        }
      }
      val sit = obj.getSons.iterator
      while (sit.hasNext) {
        val son = sit.next
        traversePolyTree(son, level + 1, minArea)
      }
    }
    
    val it = trees.iterator
    while (it.hasNext) {
      val tree = it.next
      traversePolyTree(tree, 0, task.getMinArea)
    }
    val minSegments = minSegs.toArray
    val maxSegments = maxSegs.toArray
    Arrays.sort(minSegments, StripeExts.compareByY)
    Arrays.sort(maxSegments, StripeExts.compareByY)
    val stripeExts = new StripeExts(stripeMinX, stripeMaxX, minSegments, maxSegments, totalArea)
    if (SimpleChecker.DEBUG >= 3) stripeExts.printIt
    if (SimpleChecker.DEBUG >= 2) println("Total Area "+totalArea)
    stripeExts
  }
  
  def compareTrees(ts1: java.lang.Iterable[PolyTree], ts2: java.lang.Iterable[PolyTree]): Unit = {
    val it1 = ts1.iterator
    val it2 = ts2.iterator
    while (it1.hasNext && it2.hasNext) {
      val pt1 = it1.next
      val pt2 = it2.next
      assert(Arrays.equals(pt1.getPoly, pt2.getPoly))
      compareTrees(pt1.getSons, pt2.getSons)
    }
    assert(!it1.hasNext && !it2.hasNext)
  }
  
  def applyBitMap(): StripeExts = {
    
    // find unique coordinates
    var xcoords = new TreeSet[Int]()
    var ycoords = new TreeSet[Int]()
    flattenRects(task.getTopCell, (minX: Int, minY: Int, maxX: Int, maxY: Int) => {
        if (SimpleChecker.DEBUG >= 4)
          println(" flat ["+minX+".."+maxX+"]x["+minY+".."+maxY+"]")
        xcoords = xcoords + minX + maxX
        ycoords = ycoords + minY + maxY
      })
    if (xcoords.isEmpty) {
      return new StripeExts(stripeMinX, stripeMaxX, new Array[ExtSegment](0), new Array[ExtSegment](0), 0)
    }
  
    val xsize = xcoords.size - 1
    val ysize = ycoords.size - 1
    
    // xa,ya maps coordinate index to coordinate value
    // xm,ym maps coordinate value to coordinate index
    val xa = new Array[Int](xsize + 1)
    val ya = new Array[Int](ysize + 1)
    val xm = new HashMap[Int,Int]()
    val ym = new HashMap[Int,Int]()
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
    flattenRects(task.getTopCell, (minX: Int, minY: Int, maxX: Int, maxY: Int) => {
        val ymin = ym(minY)
        val ymax = ym(maxY)
        for (x <- xm(minX) until xm(maxX)) bitMap(x).set(ymin, ymax)
      })

    if (SimpleChecker.DEBUG >= 4) {
      println("xcoords="+xcoords)
      println("ycoords="+ycoords)
      printBitMap(bitMap, xsize, ysize)
    }

    val stripeMin = new Array[ExtPoly](ysize)
    val stripeMax = new Array[ExtPoly](ysize)
    
    // stack of tiles to fill polygon
    var stack = new Stack[Point]
    var polyArea: Long = 0
    
    def pushTile(x: Int, y: Int) = {
      val w: Long = xa(x + 1) - xa(x)
      val h: Long = ya(y + 1) - ya(y)
      polyArea += w*h
      bitMap(x).clear(y)
      stack.push(new Point(x,y))
    }
    
    // find polygons in reverse lexicographical order
    var totalArea = 0L
    var x = xsize - 1
    while (x >= 0) {
      var y = ysize - 1
      while (y >= 0) {
        if (bitMap(x).get(y)) {
          polyArea = 0
          // find polygon area and erase polygon from bit map
          var ext: ExtPoly = null
          pushTile(x, y)
          while (!stack.isEmpty) {
            val p = stack.top
            if (p.getX == 0 && stripeMin(p.getY) == null) {
              if (ext == null) ext = new ExtPoly
              stripeMin(p.getY) = ext
            }
            if (p.getX + 1 == xsize && stripeMax(p.getY) == null) {
              if (ext == null) ext = new ExtPoly
              stripeMax(p.getY) = ext
            }
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
          if (ext != null) {
            ext.x = xa(x + 1)
            ext.y = ya(y + 1)
            ext.area = polyArea
          } else {
            task.reportMinAreaViolation(polyArea, xa(x + 1), ya(y + 1))
          }
        }
        y -= 1
      }
      x -= 1
    }
    
    val minSegments = collectSegments(stripeMin, ya)
    val maxSegments = collectSegments(stripeMax, ya)
    val stripeExts = new StripeExts(stripeMinX, stripeMaxX, minSegments, maxSegments, totalArea)
    if (SimpleChecker.DEBUG >= 3) stripeExts.printIt
    if (SimpleChecker.DEBUG >= 2) println("Total Area "+totalArea)
    stripeExts
  }
  
  def collectSegments(stripeExts: Array[ExtPoly], ya: Array[Int]): Array[ExtSegment] = {
    val ysize = stripeExts.length
    val buf =  new ArrayBuffer[ExtSegment]
    var curP: ExtPoly = null
    var ymin = 0
    for (y <- 0 until ysize) {
      val p = stripeExts(y)
      if (p != null) {
        if (curP != null) {
          assert(curP == p)
        } else {
          curP = p
          ymin = ya(y)
        }
      } else if (curP != null) {
        val ymax = ya(y)
        buf += new ExtSegment(ymin, ymax, if (curP.area < task.getMinArea) curP else StripeExts.hugePoly)
        curP = null
      }
    }
    if (curP != null) {
      val ymax = ya(ysize)
      buf += new ExtSegment(ymin, ymax, if (curP.area < task.getMinArea) curP else StripeExts.hugePoly)
    }
    buf.toArray
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


