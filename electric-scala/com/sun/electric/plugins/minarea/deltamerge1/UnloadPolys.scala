/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UnloadPolys.java
 * Written by Dmitry Nadezhin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import java.io.DataInputStream
import java.util.Collections

object UnloadPolys {
  val IN = 1
  val OUT = 0
  val B = OUT
  val W = IN
}

class UnloadPolys {
  var polyPool: Polys = null
  var arcPool: Arc = null

  var inpA = new Array[Int](1)
  var inpC: Int = 0

  var x: Int = 0

  var mainArc: Arc = null
  var top: Arc = null
  val stack = new java.util.ArrayList[Arc]()
  var v = UnloadPolys.W
  var nv = UnloadPolys.B

  var rotated: Boolean = false

  def loop(inpS: DataInputStream, rotated: Boolean): java.lang.Iterable[PolyTree] = {
    this.rotated = rotated
    mainArc = newArc()
    mainArc.y(UnloadPolys.IN) = Int.MinValue
    mainArc.y(UnloadPolys.OUT) = Int.MaxValue
    mainArc.t(UnloadPolys.IN) = mainArc
    mainArc.b(UnloadPolys.OUT) = mainArc
        
    while (getLine(inpS)) {
//            printInp();
      scanLine();
    }
    assert(mainArc.t(UnloadPolys.IN) == mainArc && mainArc.b(UnloadPolys.OUT) == mainArc)
    val result: java.util.Collection[PolyTree] = if (mainArc.sons != null) Collections.unmodifiableCollection(mainArc.sons) else Collections.emptyList()
    dispArc(mainArc)
    result
  }

  def getLine(inpS: DataInputStream): Boolean = {
    resetInp()
    val eof = inpS.readBoolean()
    if  (!eof) {
      return false;
    }

    x = inpS.readInt()
    val count = inpS.readInt()
    while (count > inpA.length) {
      val newInpA = new Array[Int](inpA.length*2)
      System.arraycopy(inpA, 0, newInpA, 0, inpA.length);
      inpA = newInpA
    }
    while (inpC < count) {
      inpA(inpC) = inpS.readInt()
      inpC += 1
    }
    return true
  }

  /*    +->----!   OUT	*/
  /*    | main !		*/
  /*    |      !   W	*/
  /*    |  #-<-!.. IN	*/
  /*    |  |***! .	*/
  /*    |  |***! .	*/
  /*    |  |***! . B	*/
  /*    |  +->-!.# OUT	*/
  /*    |      ! poly	*/
  /*    +-<----!   IN	*/

  def scanLine() = {
    stack.clear()
    top = mainArc
    v = UnloadPolys.W
    nv = UnloadPolys.B
    /*   top->y[v] < top->y[!v]   */

    var al = top

    var y = 0
    var d = 0
    var inpPos = 0
    while (inpPos < inpC) {
      if (Math.abs(d) == 2) {
        d >>= 1
      } else {
        val inpVal = inpA(inpPos)
        inpPos += 1
        y = inpVal >> 1
        d = if ((inpVal & 1) != 0) 1 else -1
        while (inpPos < inpC && (inpA(inpPos) >> 1) == y) {
          if ((inpA(inpPos) & 1) != 0) {
            d += 1
          } else {
            d -= 1
          }
          inpPos += 1
        }
      }
      assert(d == 1 || d == -1)

      assert(v + nv == 1)
      while (y >= top.y(nv)) {
        al = top
        POP();
      }
      assert(top.y(v) <= y && y < top.y(nv))
      while (al.t(v) != top && y >= al.t(v).y(v)) {
        al = al.t(v)
      }
      while (al.t(v) != top && y >= al.t(v).y(nv)) {
        al = al.t(v)
        PUSH( al )
        while (al.t(v) != top && y >= al.t(v).y(v)) {
          al = al.t(v)
        }
      }
      assert(top.y(v) <= y && y < top.y(nv))
      var ar = al.t(v)
      if (y > al.y(v)) {
        val pl = newPolys()
        pl.y = y
        pl.next = pl
        val at = newArc()
        at.y(nv) = y
        at.y(v) = y
        at.pol = pl
        GLUE(al, at, v)
        GLUE(at, at, nv)
        GLUE(at, ar, v)
        al = at
      }
      assert(y == al.y(v))
      assert(ar == al.t(v))
      assert(al == ar.b(nv))
      
      {
        val inpVal = inpA(inpPos)
        inpPos += 1
        y = inpVal >> 1
        d = if ((inpVal & 1) != 0) 1 else -1
        while (inpPos < inpC && (inpA(inpPos) >> 1) == y) {
          if ((inpA(inpPos) & 1) != 0) {
            d += 1
          } else {
            d -= 1
          }
          inpPos += 1
        }
        assert(d == 1 || d == -1 || d == 2 || d == -2)
      }
      
      assert(y <= ar.y(nv))
      if ( y < ar.y(nv) /*|| Math.abs(d) == 2*/) {
        al.y(v) = y
        val pl = newPolys()
        pl.y = y
        pl.next = pl
        al.pol = CAT( al.pol, v, pl)
      } else {
        var aln = al.b(v)
        var arn = ar.t(nv)

        GLUE(aln, arn, nv)
        if (al == ar) {
          assert(al == top)
          top.pol.x = x;
          val t = outTree(top.pol)
          if (top.sons != null) {
            val it = top.sons.iterator
            while (it.hasNext) {
              val s = it.next
              t.addSonLowLevel(s)
            }
          }
//                    out(top.pol,v);
          dispArc(top)
          POP()
          top.addSon(t)
        } else if (al == top) {
          top.pol = CAT(top.pol, v, ar.pol)
          REPLACE(ar,top,v)
          arn = aln.t(nv)
          val sons = ar.sons
          dispArc(ar)
          POP()
          top.addSons(sons)
        } else if (ar == top) {
          top.pol = CAT(top.pol, nv, al.pol)
          REPLACE(al, top, nv)
          aln = arn.b(v)
          val sons = al.sons
          dispArc(al)
          POP()
          top.addSons(sons)
        } else {
          al.pol = CAT(al.pol, v, ar.pol)
          REPLACE(ar, al, v)
          arn = aln.t(nv)
          val sons = ar.sons
          dispArc(ar)
          al.addSons(sons)
          PUSH(al)
        }
        al = aln
        ar = arn
      }
    }
  }
    
  def outTree(pl: Polys): PolyTree = {
    var pg = pl
    var n = 0;
    do {
      pg = pg.next
      n += 1
    } while (pg != pl)
    val pts = new Array[Int](n*4)
    if (rotated) {
      var k = pts.length
      do {
        k -= 1
        pts(k) = pg.y
        k -= 1
        pts(k) = pg.x
        k -= 1
        pts(k) = pg.next.y
        k -= 1
        pts(k) = pg.x
        pg = pg.next
      } while (pg != pl)
      assert(k == 0)
    } else {
      var k = 0
      do {
        pts(k) = pg.x
        k += 1
        pts(k) = pg.y
        k += 1
        pts(k) = pg.x
        k += 1
        pts(k) += pg.next.y
        k += 1
        pg = pg.next
      } while (pg != pl)
      assert(k == pts.length)
    }
    new PolyTree(pts)
  }

  def GLUE(al: Arc, ar: Arc, v: Int) = {
    al.t(v) = ar
    ar.b(1 - v) = al
  }
  
  def REPLACE(so: Arc, sn: Arc, v: Int) = {
    val nv = 1 - v
    sn.y(v) = so.y(v)
    so.b(v).t(nv) = sn
    so.t(v).b(nv) = sn
    sn.b(v) = so.b(v)
    sn.t(v) = so.t(v)
  }

  def PUSH(a: Arc) = {
    stack.add(top)
    top = a
    nv = v
    v = 1 - v
  }

  def POP() = {
    nv = v
    v = 1 - v
    top = stack.remove(stack.size() - 1)
  }

  def CAT(pl: Polys, v: Int, pg: Polys): Polys = {
    (if (v == UnloadPolys.IN) pg else pl).x = x;
    val pt = pl.next
    pl.next = pg.next
    pg.next = pt
    if (v == UnloadPolys.IN) pl else pg
  }

  def resetInp() = { inpC = 0 }

  def newPolys(): Polys = {
    var result: Polys = null
    if (polyPool == null) {
      result = new Polys()
    } else {
      result = polyPool
      polyPool = polyPool.next
    }
    result
  }

  def newArc(): Arc = {
    var result: Arc = null
    if (arcPool == null) {
      result = new Arc()
    } else {
      result = arcPool
      arcPool = arcPool.next
    }
//        result.next = next;
    result;
  }

  def dispArc(p: Arc) = {
    p.next = arcPool
    arcPool = p
    p.sons = null
  }
}

class Polys {
  var next: Polys = null
  var x, y: Int = 0
}
 
class Arc {
  var next: Arc = null
  val y = new Array[Int](2)
  val b = new Array[Arc](2)
  val t = new Array[Arc](2)
  var sons: java.util.List[PolyTree] = null
  var pol: Polys = null

  def addSon(son: PolyTree) = {
    if (sons == null) {
      sons = new java.util.ArrayList[PolyTree]()
    }
    sons.add(son);
  }

  def addSons(newSons: java.util.List[PolyTree]): Unit = {
    if (newSons == null || newSons.isEmpty)
      return;
    if (sons == null) {
      sons = new java.util.ArrayList[PolyTree]()
    }
    sons.addAll(newSons);
  }
}

class PolyTree(poly: Array[Int]) {
  var sons: java.util.List[PolyTree] = null
  
  def getPoly: Array[Int] = poly
  def getSons: java.lang.Iterable[PolyTree] = if (sons != null) sons else Collections.emptyList()
  
  def addSonLowLevel(son: PolyTree) = {
    if (sons == null)
      sons = new java.util.ArrayList[PolyTree]();
    sons.add(son)
  }
  
  
  def getAreaOfPoly(): Long = {
    var area = 0L
    var x0 = poly(0)
    var y0 = poly(1)
    var i = 1
    while(i*2 < poly.length) {
      val x1 = poly(i*2)
      val y1 = poly(i*2 + 1)
      area += (x1 - x0).asInstanceOf[Long]*(y1 + y0).asInstanceOf[Long]
      x0 = x1
      y0 = y1
      i += 1
    }
    area += (poly(0) - x0).asInstanceOf[Long]*(poly(1) + y0).asInstanceOf[Long]
    Math.abs(area)/2
  }
}

