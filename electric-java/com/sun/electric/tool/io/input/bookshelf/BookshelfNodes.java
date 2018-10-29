/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BookshelfNodes.java
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
package com.sun.electric.tool.io.input.bookshelf;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.input.bookshelf.BookshelfNets.BookshelfNet;
import com.sun.electric.util.CollectionFactory;
import com.sun.electric.util.TextUtils;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Felix Schmidt
 * 
 */
public class BookshelfNodes implements BookshelfInputParser<Void> {

	private String nodesFile;

	public BookshelfNodes(String nodesFile) {
		this.nodesFile = nodesFile;
	}

	public Void parse() throws IOException {

		Job.getUserInterface().setProgressNote("Parse Nodes File");

		BufferedReader rin;
		try
		{
			File file = new File(nodesFile);
			FileReader freader = new FileReader(file);
			rin = new BufferedReader(freader);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot find Bookshelf Nodes file: " + nodesFile);
			return null;
		}

		String line;
		while ((line = rin.readLine()) != null) {
			if (line.startsWith("   ")) {
				StringTokenizer tokenizer = new StringTokenizer(line, " ");
				int i = 0;
				String name = "";
				double height = 0;
				double width = 0;
				boolean isTerminal = false;
				while (tokenizer.hasMoreTokens()) {
					if (i == 0) {
						name = tokenizer.nextToken();
					} else if (i == 1) {
						width = TextUtils.atof(tokenizer.nextToken());
					} else if (i == 2) {
						height = TextUtils.atof(tokenizer.nextToken());
					} else if (i == 3) {
						if (tokenizer.nextToken().toLowerCase().equals("terminal")) {
							isTerminal = true;
						}
					} else {
						tokenizer.nextToken();
					}
					i++;
				}
				new BookshelfNode(name, width, height, isTerminal);
			}
		}
		return null;
	}

	public static class BookshelfNode {
		private String name;
		private double width;
		private double height;
		private double x, y;
		private boolean terminal;
		private List<BookshelfPin> pins;
		private Cell prototype;
		private NodeInst instance;
		private static Map<String, BookshelfNode> nodeMap = new LinkedHashMap<String, BookshelfNode>();
		private int weight;

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name
		 *            the name to set
		 */
		public void setName(String name) {
			this.name = name;
		}

		/**
		 * @return the width
		 */
		public double getWidth() {
			return width;
		}

		/**
		 * @param width
		 *            the width to set
		 */
		public void setWidth(double width) {
			this.width = width;
		}

		/**
		 * @return the height
		 */
		public double getHeight() {
			return height;
		}

		/**
		 * @param height
		 *            the height to set
		 */
		public void setHeight(double height) {
			this.height = height;
		}

		/**
		 * @return the X coordinate
		 */
		public double getX() {
			return x;
		}

		/**
		 * @return the Y coordinate
		 */
		public double getY() {
			return y;
		}

		/**
		 * @param x
		 *            the X coordinate to set
		 * @param y
		 *            the Y coordinate to set
		 */
		public void setLocation(double x, double y) {
			this.x = x;
			this.y = y;
		}

		/**
		 * @return the Cell prototype
		 */
		public Cell getPrototype() {
			return prototype;
		}

		/**
		 * @param instance the actual NodeInst.
		 */
		public void setInstance(NodeInst instance) {
			this.instance = instance;
		}

		/**
		 * @return the actual NodeInst.
		 */
		public NodeInst getInstance() {
			return instance;
		}

		/**
		 * @param prototype
		 *            the Cell prototype
		 */
		public void setPrototype(Cell prototype) {
			this.prototype = prototype;
		}

		/**
		 * @param name
		 * @param width
		 * @param height
		 */
		public BookshelfNode(String name, double width, double height, boolean terminal) {
			this.name = name;
			this.width = width;
			this.height = height;
			this.x = this.y = 0;
			this.setTerminal(terminal);
			this.pins = CollectionFactory.createArrayList();
			nodeMap.put(name, this);
		}

		/**
		 * Find a BookshelfNode from its name.
		 * 
		 * @param name
		 *            the name of the BookshelfNode.
		 * @return the BookshelfNode with that name (null if not found).
		 */
		public static BookshelfNode findNode(String name) {
			return nodeMap.get(name);
		}

		/**
		 * Return a list of all BookshelfNodes.
		 * 
		 * @return a list of all BookshelfNodes.
		 */
		public static Collection<BookshelfNode> getAllNodes() {
			return nodeMap.values();
		}

		/**
		 * @param pins
		 *            the pins to set
		 */
		public void setPins(List<BookshelfPin> pins) {
			this.pins = pins;
		}

		/**
		 * @return the pins
		 */
		public List<BookshelfPin> getPins() {
			return pins;
		}

		/**
		 * @param terminal
		 *            the terminal to set
		 */
		public void setTerminal(boolean terminal) {
			this.terminal = terminal;
		}

		/**
		 * @return the terminal
		 */
		public boolean isTerminal() {
			return terminal;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			
			//object_name        width         height        terminal
			builder.append(this.name + " ");
			builder.append(String.valueOf(this.width) + " ");
			builder.append(String.valueOf(this.height));
			if(this.terminal) {
				builder.append(" terminal");	
			}

			return builder.toString();
		}

		/**
		 * @param weight the weight to set
		 */
		public void setWeight(int weight) {
			this.weight = weight;
		}

		/**
		 * @return the weight
		 */
		public int getWeight() {
			return weight;
		}
	}

	public static class BookshelfPin {
		private Point2D location;
		private BookshelfNet net;
		private String nodeName;

		/**
		 * @param location
		 */
		public BookshelfPin(Point2D location, BookshelfNet net, String nodeName) {
			super();
			this.location = location;
			this.setNet(net);
			this.nodeName = nodeName;
		}

		/**
		 * @param location
		 *            the location to set
		 */
		public void setLocation(Point2D location) {
			this.location = location;
		}

		/**
		 * @return the location
		 */
		public Point2D getLocation() {
			return location;
		}

		/**
		 * @param net
		 *            the net to set
		 */
		public void setNet(BookshelfNet net) {
			this.net = net;
		}

		/**
		 * @return the net
		 */
		public BookshelfNet getNet() {
			return net;
		}

		/**
		 * @param nodeName
		 *            the nodeName to set
		 */
		public void setNodeName(String nodeName) {
			this.nodeName = nodeName;
		}

		/**
		 * @return the nodeName
		 */
		public String getNodeName() {
			return nodeName;
		}
	}
}
