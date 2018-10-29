/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: f206.java
 * Written by Ajanta Chakraborty.
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.simulation.test;

public class f206 extends Equipment {

    String s = new String("null");

    /** Creates a new instance of power suppy */
    public f206(String name) {
        super(name);
    }

    void testConnection() {
        write("help");
        s = read(2000).trim();
        //s = s.substring(0,s.length()-1);
        System.out.println("help " + s);
        try { Thread.sleep(100); } catch (InterruptedException e) { }
        write("ini");
        try { Thread.sleep(100); } catch (InterruptedException e) { }
    }//end testConnection

    void initialize() {
        write("ini");
        try { Thread.sleep(100); } catch (InterruptedException e) { }
    }//end initialize

    void move(float x, float y, float z, float u, float v, float w) {
        write("mov x" + x + " y" + y + " z" + z + " u" + u + " v" + v + " w"
                + w);
        write("mov?");
        s = read(20).trim();
        System.out.println("move complete " + s);
    }//end moveY

    void moveZ(float val) {
        write("mov z " + val);
        write("mov?");
        s = read(2000).trim();
        System.out.println("move complete " + s);
    }//end moveZ

    void moveX(float val) {
        write("mov x " + val);
        write("mov?");
        s = read(20).trim();
        System.out.println("move complete " + s);
    }//end moveX

    void moveY(float val) {
        write("mov y " + val);
        write("mov?");
        s = read(20).trim();
        System.out.println("move complete " + s);
    }//end moveY

    void moveU(float val) {
        write("mov u " + val);
        write("mov?");
        s = read(20).trim();
        System.out.println("move complete " + s);
    }//end moveU

    void moveV(float val) {
        write("mov v " + val);
        try { Thread.sleep(100); } catch (InterruptedException e) { }
        write("mov?");
        s = read(2000).trim();
        System.out.println("move complete " + s);
        try { Thread.sleep(100); } catch (InterruptedException e) { }
    }//end moveV

    void moveW(float val) {
        write("mov y " + val);
        write("mov?");
        s = read(20).trim();
        System.out.println("move complete " + s);
    }//end moveW

    public static void main(String args[]) {
        f206 pos = new f206("f206");
        pos.testConnection();
        pos.move(1f, 1f, 1f, 3, 4, 0);
    }//end main

}//end class
