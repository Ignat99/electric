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

import java.util.LinkedHashMap

import scala.math.{min, max}

object StripeExts {
  val hugePoly = new ExtPoly
  hugePoly.area = Long.MaxValue
  
  val compareByY = new java.util.Comparator[ExtSegment] {
    override def compare(s1: ExtSegment, s2: ExtSegment) = {
      val y1 = s1.getYmin
      val y2 = s2.getYmin
      if (y1 < y2) -1 else if (y1 > y2) +1 else 0
    }
  }
  
  def emptyStripeExts(x: Int) = new StripeExts(x, x, new Array[ExtSegment](0), new Array[ExtSegment](0), 0)
  
  def connectStripes(l: StripeExts, u: StripeExts, minArea: Long, errorLogger: ErrorLogger): StripeExts = {
    assert(l.getXmax == u.getXmin) 
    val lmin = l.getMinSegs
    val lmax = l.getMaxSegs
    val umin = u.getMinSegs
    val umax = u.getMaxSegs
    val rmin = new Array[ExtSegment](lmin.size)
    val rmax = new Array[ExtSegment](umax.size)
    
    val polys = new LinkedHashMap[ExtPoly,Int]()
    // put external polys first
    addP(polys, StripeExts.hugePoly)
    for (s <- lmin) addP(polys, s.getP)
    for (s <- umax) addP(polys, s.getP)
    // put polys on connection line
    val innerPolys = polys.size
    for (s <- lmax) addP(polys, s.getP)
    for (s <- umin) addP(polys, s.getP)
    val polyInds = initInds(polys.size)
    
    var il = 0
    var iu = 0
    while (il < lmax.size && iu < umin.size) {
      val l = lmax(il)
      val u = umin(iu)
      if (l.getYmax <= u.getYmin) {
        il += 1
      } else if (u.getYmax <= l.getYmin) {
        iu += 1
      } else {
        connectInds(polyInds, polys.get(l.getP), polys.get(u.getP))
        if (l.getYmax <= u.getYmax) il += 1 else iu += 1
      }
    }
    closeInds(polyInds)
    
    val newPolys = new Array[ExtPoly](polys.size)
    val it = polys.entrySet.iterator
    while (it.hasNext) {
      val e = it.next
      val oldP = e.getKey
      val i = e.getValue
      if (oldP eq StripeExts.hugePoly) {
        assert(i == 0 && polyInds(i) == 0)
        newPolys(0) = StripeExts.hugePoly
      } else if (polyInds(i) == i) {
        val newP = new ExtPoly
        newP.area = oldP.area
        newP.x = oldP.x
        newP.y = oldP.y
        newPolys(i) = newP
      } else {
        val p = newPolys(polyInds(i))
        if (!(p eq StripeExts.hugePoly)) p.union(oldP)
      }
    }
    assert(newPolys(0) eq StripeExts.hugePoly)
    for (i <- 1 until newPolys.size) {
      val p = newPolys(i)
      assert(!(p eq StripeExts.hugePoly))
      if (p != null) {
        if (p.area >= minArea) {
          newPolys(i) = StripeExts.hugePoly
        } else if (i >= innerPolys) {
          errorLogger.reportMinAreaViolation(p.area, p.x, p.y, null)
        }
      }
    }
    
    for (i <- 0 until rmin.size) {
      val oldS = lmin(i)
      val newP = newPolys(polyInds(polys.get(oldS.getP)))
      rmin(i) = new ExtSegment(oldS.getYmin, oldS.getYmax, newP)
    }
    for (i <- 0 until rmax.size) {
      val oldS = umax(i)
      val newP = newPolys(polyInds(polys.get(oldS.getP)))
      rmax(i) = new ExtSegment(oldS.getYmin, oldS.getYmax, newP)
    }
    
    new StripeExts(l.getXmin, u.getXmax, rmin, rmax, l.getTotalArea + u.getTotalArea)
  }
  
  def addP(map: LinkedHashMap[ExtPoly,Int], p: ExtPoly) = {
    if (!map.containsKey(p)) map.put(p, map.size)
  }
  
  def initInds(size: Int): Array[Int] = {
    val inds = new Array[Int](size)
    for (i <- 0 until size) inds(i) = i
    inds
  }
  
  def connectInds(inds: Array[Int], i1: Int, i2: Int) = {

    var m1 = i1
    while (inds(m1) != m1) m1 = inds(m1)
    var m2 = i2
    while (inds(m2) != m2) m2 = inds(m2)
    val m = if (m1 < m2) m1 else m2

    m1 = i1
    while (inds(m1) != m1) {
      val k = m1
      m1 = inds(m1)
      inds(m1) = m
    }
    inds(m1) = m
    
    m2 = i2
    while (inds(m2) != m2) {
      val k = m2
      m2 = inds(m2)
      inds(m2) = m
    }
    inds(m2) = m
  }
  
  def closeInds(inds: Array[Int]) = {
    for (i <- 0 until inds.size) inds(i) = inds(inds(i))
  }
}

class StripeExts(xmin: Int, xmax: Int, minSegs: Array[ExtSegment], maxSegs: Array[ExtSegment], totalArea: Long) {
  assert(xmin <= xmax)
  
  def getXmin = xmin
  def getXmax = xmax
  def getMinSegs = minSegs
  def getMaxSegs = maxSegs
  def getTotalArea = totalArea
  
  def printIt = {
    println("Lower x="+xmin+":")
    for (s <- minSegs) s.printIt
    println("Upper x="+xmax+":")
    for (s <- maxSegs) s.printIt
  }
}

class ExtSegment(ymin: Int, ymax: Int, p: ExtPoly) {
  def getYmin = ymin
  def getYmax = ymax
  def getP = p
  
  def printIt = {
    print("y=["+ymin+","+ymax+"] ")
    if (p eq StripeExts.hugePoly) print("HUGE") else print("area="+p.area+" top=("+p.x+","+p.y+")")
    println
  }
}

class ExtPoly {
  var area = 0L
  var x = 0
  var y = 0
  
  def union(p: ExtPoly) = {
    area += p.area
    if (p.x > x || p.x == x && p.y > y) {
      x = p.x
      y = p.y
    }
  }
}
