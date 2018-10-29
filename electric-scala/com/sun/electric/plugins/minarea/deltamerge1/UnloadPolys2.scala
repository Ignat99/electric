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

class UnloadPolys2 {
  var polyPool: Polys = null
  var arcPool: Arc2 = null

  var inpA = new Array[Int](1)
  var inpC: Int = 0

  var x: Int = 0

  var mainArc: Arc2 = null
  var top: Arc2 = null
  val stack = new java.util.ArrayList[Arc2]()
  var v = UnloadPolys.W
  var nv = UnloadPolys.B

  var rotated: Boolean = false

  def loop(inpS: DataInputStream, rotated: Boolean): java.lang.Iterable[PolyTree] = {
    this.rotated = rotated
    mainArc = newArc()
    mainArc.e(UnloadPolys.IN).y = Int.MinValue
    mainArc.e(UnloadPolys.OUT).y = Int.MaxValue
    mainArc.e(UnloadPolys.IN).t = mainArc.e(UnloadPolys.OUT)
    mainArc.e(UnloadPolys.OUT).b = mainArc.e(UnloadPolys.IN)
        
    while (getLine(inpS)) {
//            printInp();
      scanLine();
    }
    assert(mainArc.e(UnloadPolys.IN).t == mainArc.e(UnloadPolys.OUT) && mainArc.e(UnloadPolys.OUT).b == mainArc.e(UnloadPolys.IN))
    val result: java.util.Collection[PolyTree] = if (mainArc.sons != null) Collections.unmodifiableCollection(mainArc.sons) else Collections.emptyList()
    dispArc(mainArc)
    result
  }

  def getLine(inpS: DataInputStream): Boolean = {
    resetInp()
    val eof = inpS.readBoolean()
    if  (!eof) {
      return false
    }

    x = inpS.readInt()
    val count = inpS.readInt()
    while (count > inpA.length) {
      val newInpA = new Array[Int](inpA.length*2)
      System.arraycopy(inpA, 0, newInpA, 0, inpA.length)
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

    var al = top.e(v)

    var y0 = 0
    var d = 0
    var inpPos = 0
    while (inpPos < inpC) {
      if (Math.abs(d) == 2) {
        d >>= 1
      } else {
        val inpVal = inpA(inpPos)
        inpPos += 1
        y0 = inpVal >> 1
        d = if ((inpVal & 1) != 0) 1 else -1
        while (inpPos < inpC && (inpA(inpPos) >> 1) == y0) {
          if ((inpA(inpPos) & 1) != 0) {
            d += 1
          } else {
            d -= 1
          }
          inpPos += 1
        }
      }
      val y1 = y0
      val d1 = d
      assert(d1 == 1 || d1 == -1)
      
      {
        val inpVal = inpA(inpPos)
        inpPos += 1
        y0 = inpVal >> 1
        d = if ((inpVal & 1) != 0) 1 else -1
        while (inpPos < inpC && (inpA(inpPos) >> 1) == y0) {
          if ((inpA(inpPos) & 1) != 0) {
            d += 1
          } else {
            d -= 1
          }
          inpPos += 1
        }
        assert(d == 1 || d == -1 || d == 2 || d == -2)
      }
      val y2 = y0
      val d2 = d
      

      assert(v + nv == 1)
      while (y1 >= top.e(nv).y) {
        al = top.e(nv)
        POP();
      }
      assert (al.getV == v)
      assert(top.e(v).y <= y1 && y1 < top.e(nv).y)
      while (al.t != top.e(nv) && y1 >= al.t.ow.e(v).y) {
        al = al.t.ow.e(v)
      }
      while (al.t != top.e(nv) && y1 >= al.t.y) {
        al = al.t.ow.e(nv)
        PUSH( al.ow )
        while (al.t != top.e(nv) && y1 >= al.t.ow.e(v).y) {
          al = al.t.ow.e(v)
        }
      }
      assert(al.getV == v)
      assert(top.e(v).y <= y1 && y1 < top.e(nv).y)
      var ar = al.t
      assert(al.getV == v && ar.getV == nv)
      if (y1 > al.y) {
        val pl = newPolys()
        pl.y = y1
        pl.next = pl
        val at = newArc()
        at.e(nv).y = y1
        at.e(v).y = y1
        at.pol = pl
        GLUE(al, at.e(nv), v)
        GLUE(at.e(nv), at.e(v), nv)
        GLUE(at.e(v), ar, v)
        al = at.e(v)
      }
      assert(y1 == al.y)
      assert(ar == al.t)
      assert(al == ar.b)
      
      assert(y2 <= ar.y)
      if ( y2 < ar.y /*|| Math.abs(d) == 2*/) {
        al.y = y2
        val pl = newPolys()
        pl.y = y2
        pl.next = pl
        al.ow.pol = CAT( al.ow.pol, v, pl)
      } else {
        var aln = al.b
        var arn = ar.t

        GLUE(aln, arn, nv)
        if (al.ow == ar.ow) {
          assert(al == top.e(v) && ar == top.e(nv))
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
        } else if (al == top.e(v)) {
          top.pol = CAT(top.pol, v, ar.ow.pol)
          REPLACE(ar.ow.e(v),top.e(v),v)
          arn = aln.t
          val sons = ar.ow.sons
          dispArc(ar.ow)
          POP()
          top.addSons(sons)
        } else if (ar == top.e(nv)) {
          top.pol = CAT(top.pol, nv, al.ow.pol)
          REPLACE(al.ow.e(nv), top.e(nv), nv)
          aln = arn.b
          val sons = al.ow.sons
          dispArc(al.ow)
          POP()
          top.addSons(sons)
        } else {
          al.ow.pol = CAT(al.ow.pol, v, ar.ow.pol)
          REPLACE(ar.ow.e(v), al, v)
          arn = aln.t
          val sons = ar.ow.sons
          dispArc(ar.ow)
          al.ow.addSons(sons)
          PUSH(al.ow)
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

  def GLUE(al: Arc2End, ar: Arc2End, v: Int) = {
    assert(al.getV == v)
    al.t = ar
    assert(ar.getV == 1 - v)
    ar.b = al
  }
  
  def REPLACE(so: Arc2End, sn: Arc2End, v: Int) = {
    val nv = 1 - v
    assert (so.getV == v && sn.getV == v && so.b.getV == nv && so.t.getV == nv)
    sn.y = so.y
    so.b.t = sn
    so.t.b = sn
    sn.b = so.b
    sn.t = so.t
  }

  def PUSH(a: Arc2) = {
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

  def newArc(): Arc2 = {
    var result: Arc2 = null
    if (arcPool == null) {
      result = new Arc2()
    } else {
      result = arcPool
      arcPool = arcPool.next
    }
//        result.next = next;
    result;
  }

  def dispArc(p: Arc2) = {
    p.next = arcPool
    arcPool = p
    p.sons = null
  }
}

class Arc2 {
  var next: Arc2 = null
  val e = new Array[Arc2End](2)
  e(UnloadPolys.OUT) = new Arc2End(this, UnloadPolys.OUT)
  e(UnloadPolys.IN) = new Arc2End(this, UnloadPolys.IN)
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

class Arc2End(owner: Arc2, v: Int) {
  def ow = owner
  def getV = v
  var y = 0
  var b: Arc2End = null
  var t: Arc2End = null
}
