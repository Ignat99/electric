/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellLists.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.extract.TransistorSearch;
import com.sun.electric.tool.generator.sclibrary.SCLibraryGen;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.MutableInteger;

import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JFrame;

/**
 * Class to handle the "Cell Lists" dialog.
 */
public class CellLists extends EDialog
{
       Cell curCell;
       private static int whichSwitch = 0;
       private static boolean onlyViewSwitch = false;
       private static View viewSwitch = View.SCHEMATIC;
       private static boolean alsoIconSwitch = false;
       private static boolean excOldVersSwitch = false;
       private static boolean excNewVersSwitch = false;
       private static boolean evaluateNumbers = true;
       private static int orderingSwitch = 0;
       private static int destinationSwitch = 0;

       /**
        * This method implements the command to create general Cell lists.
        */
       public static void generalCellListsCommand()
       {
               JFrame jf = TopLevel.getCurrentJFrame();
               CellLists dialog = new CellLists(jf, true);
               dialog.setVisible(true);
               dialog.toFront();
       }

       /** Creates new form Cell Lists */
       private CellLists(JFrame parent, boolean modal)
       {
               super(parent, modal);
               initComponents();
               getRootPane().setDefaultButton(ok);

               // make a popup of views
               for (View v : View.getOrderedViews())
               {
                       views.addItem(v.getFullName());
               }

               curCell = WindowFrame.getCurrentCell();
               onlyCellsUnderCurrent.setEnabled(curCell != null);

               switch (whichSwitch)
               {
                       case 0: allCells.setSelected(true);                   break;
                       case 1: onlyCellsUsedElsewhere.setSelected(true);     break;
                       case 2: onlyCellsNotUsedElsewhere.setSelected(true);  break;
                       case 3: onlyCellsUnderCurrent.setSelected(true);      break;
                       case 4: onlyPlaceholderCells.setSelected(true);       break;
               }
               onlyThisView.setSelected(onlyViewSwitch);
               views.setSelectedItem(viewSwitch.getFullName());
               views.setEnabled(onlyViewSwitch);
               alsoIconViews.setSelected(alsoIconSwitch);
               excludeOlderVersions.setSelected(excOldVersSwitch);
               excludeNewestVersions.setSelected(excNewVersSwitch);
               evaluateNumerically.setSelected(evaluateNumbers);
               switch (orderingSwitch)
               {
                       case 0: orderByName.setSelected(true);                break;
                       case 1: orderByDate.setSelected(true);                break;
                       case 2: orderByStructure.setSelected(true);           break;
               }
               switch (destinationSwitch)
               {
                       case 0: displayInMessages.setSelected(true);          break;
                       case 1: saveToDisk.setSelected(true);                 break;
               }
               finishInitialization();
       }

       protected void escapePressed() { cancel(null); }

       private static String makeCellLine(Cell cell, int maxlen, DRC.DRCPreferences dp)
       {
               String line = cell.noLibDescribe();
               if (maxlen < 0) line += "\t"; else
               {
                       for(int i=line.length(); i<maxlen; i++) line += " ";
               }

               // add the version number
               String versionString = TextUtils.toBlankPaddedString(cell.getVersion(), 5);
               line += versionString;
               if (maxlen < 0) line += "\t"; else line += "   ";

               // add the creation date
               Date creationDate = cell.getCreationDate();
               if (creationDate == null)
               {
                       if (maxlen < 0) line += "UNRECORDED"; else
                               line += "     UNRECORDED     ";
               } else
               {
                       line += TextUtils.formatDate(creationDate);
               }
               if (maxlen < 0) line += "\t"; else line += "   ";

               // add the revision date
               Date revisionDate = cell.getRevisionDate();
               if (revisionDate == null)
               {
                       if (maxlen < 0) line += "UNRECORDED"; else
                               line += "     UNRECORDED     ";
               } else
               {
                       line += TextUtils.formatDate(revisionDate);
               }
               if (maxlen < 0) line += "\t"; else line += "   ";

               // add the size
               if (cell.getView().isTextView())
               {
                       int len = 0;
                       String [] textLines = cell.getTextViewContents();
                       if (textLines != null) len = textLines.length;
                       if (maxlen < 0) line += len + " lines"; else
                       {
                               line += TextUtils.toBlankPaddedString(len, 8) + " lines   ";
                       }
               } else
               {
                       String width = TextUtils.formatDistance(cell.getBounds().getWidth(), cell.getTechnology());
                       if (maxlen >= 0)
                       {
                               while (width.length() < 7) width = " " + width;
                       }
                       String height = TextUtils.formatDistance(cell.getBounds().getHeight(), cell.getTechnology());
                       if (maxlen >= 0)
                       {
                               while (height.length() < 7) height += " ";
                       }
                       line += width + " x " + height;
               }
               if (maxlen < 0) line += "\t";

               // count the number of instances
               int total = 0;
               for(Iterator<NodeInst> it = cell.getInstancesOf(); it.hasNext(); )
               {
                       total++;
                       it.next();
               }
               if (maxlen < 0) line += total; else
               {
                       line += TextUtils.toBlankPaddedString(total, 4);
               }
               if (maxlen < 0) line += "\t"; else line += "   ";

               // show other factors about the cell
               if (cell.isAllLocked()) line += "L"; else line += " ";
               if (maxlen < 0) line += "\t"; else line += " ";
               if (cell.isInstancesLocked()) line += "I"; else line += " ";
               if (maxlen < 0) line += "\t"; else line += " ";
               if (SCLibraryGen.isStandardCell(cell)) line += "S"; else line += " ";
               if (maxlen < 0) line += "\t"; else line += " ";

               boolean goodDRC = false;
               int activeBits = DRC.getActiveBits(cell.getTechnology(), dp);
               Date lastGoodDate = DRC.getLastDRCDateBasedOnBits(cell, true, activeBits, true);
               // checking spacing drc
               if (!Job.getDebug() && lastGoodDate != null && cell.getRevisionDate().before(lastGoodDate)) goodDRC = true;
               if (goodDRC) line += "D"; else line += " ";
               // now check min area
               goodDRC = false;
               lastGoodDate = DRC.getLastDRCDateBasedOnBits(cell, false, activeBits, true);
               if (!Job.getDebug() && lastGoodDate != null && cell.getRevisionDate().before(lastGoodDate)) goodDRC = true;
               if (goodDRC) line += "A"; else line += " ";
               if (maxlen < 0) line += "\t"; else line += " ";

//              if (net_ncchasmatch(cell) != 0) addstringtoinfstr(infstr, x_("N")); else
//                      addstringtoinfstr(infstr, x_(" "));
               return line;
       }

       private static class SortByCellStructure implements Comparator<Cell>
       {
               public int compare(Cell c1, Cell c2)
               {
                       // first sort by cell size
                       Rectangle2D b1 = c1.getBounds();
                       Rectangle2D b2 = c2.getBounds();
                       int xs1 = (int)b1.getWidth();
                       int xs2 = (int)b2.getWidth();
                       if (xs1 != xs2) return(xs1-xs2);
                       int ys1 = (int)b1.getHeight();
                       int ys2 = (int)b2.getHeight();
                       if (ys1 != ys2) return(ys1-ys2);

                       // now sort by number of exports
                       int pc1 = c1.getNumPorts();
                       int pc2 = c2.getNumPorts();
                       pc1 = 0;
                       if (pc1 != pc2) return(pc1-pc2);

                       // now match the exports
//                      for(Iterator it = c1.getPorts(); it.hasNext(); )
//                      {
//                              PortProto pp1 = it.next();
//                              pp1.clearBit(portFlagBit);
//                      }
//                      for(Iterator it = c2.getPorts(); it.hasNext(); )
//                      {
//                              PortProto pp2 = it.next();
//
//                              // locate center of this export
//                              ni = &dummyni;
//                              initdummynode(ni);
//                              ni->proto = f2;
//                              ni->lowx = -xs1/2;   ni->highx = ni->lowx + xs1;
//                              ni->lowy = -ys1/2;   ni->highy = ni->lowy + ys1;
//                              portposition(ni, pp2, &x2, &y2);
//
//                              ni->proto = f1;
//                              for(pp1 = f1->firstportproto; pp1 != NOPORTPROTO; pp1 = pp1->nextportproto)
//                              {
//                                      portposition(ni, pp1, &x1, &y1);
//                                      if (x1 == x2 && y1 == y2) break;
//                              }
//                              if (pp1 == NOPORTPROTO) return(f1-f2);
//                              pp1->temp1 = 1;
//                      }
//                      for(pp1 = f1->firstportproto; pp1 != NOPORTPROTO; pp1 = pp1->nextportproto)
//                              if (pp1->temp1 == 0) return(f1-f2);
                       return(0);
               }
       }

       /**
        * This method implements the command to list (recursively) the nodes and arcs in this Cell.
        */
       public static void listNodesAndArcsInCellCommand()
       {
               Cell curCell = WindowFrame.needCurCell();
               if (curCell == null) return;

               // now look at every object recursively in this cell
               Map<NodeProto, MutableInteger> nodeCount = new HashMap<NodeProto, MutableInteger>();
       Map<ArcProto, MutableInteger> arcCount = new HashMap<ArcProto, MutableInteger>();
       addObjects(curCell, nodeCount, arcCount);

               // print the totals
               System.out.println("Contents of " + curCell + ":");
               Technology printtech = null;
               for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
               {
                       Technology curtech = it.next();
                       int totalVal = 0;
           // nodes
           for(Iterator<PrimitiveNode> nIt = curtech.getNodes(); nIt.hasNext(); )
                       {
                               PrimitiveNode np = nIt.next();
                               MutableInteger count = nodeCount.get(np);
                               if (count == null) continue;
                               if (curtech != printtech)
                               {
                                       System.out.println(curtech.getTechName() + " technology:");
                                       printtech = curtech;
                               }
                               System.out.println("\t" + TextUtils.toBlankPaddedString(count.intValue(), 6) + " " + np.describe(true) + " nodes");
                               totalVal += count.intValue();
                       }
           if (totalVal > 0)
               System.out.println(TextUtils.toBlankPaddedString(totalVal, 6) + " Total nodes for " + curtech.getTechName() + " technology");

           // arcs
           totalVal = 0;
           for(Iterator<ArcProto> nIt = curtech.getArcs(); nIt.hasNext(); )
                       {
               ArcProto ap = nIt.next();
                               MutableInteger count = arcCount.get(ap);
                               if (count == null) continue;
                               if (curtech != printtech)
                               {
                                       System.out.println(curtech.getTechName() + " technology:");
                                       printtech = curtech;
                               }
                               System.out.println("\t" + TextUtils.toBlankPaddedString(count.intValue(), 6) + " " + ap.describe() + " arcs");
                               totalVal += count.intValue();
                       }
           if (totalVal > 0)
               System.out.println(TextUtils.toBlankPaddedString(totalVal, 6) + " Total arcs for " + curtech.getTechName() + " technology");
       }

               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                       Library lib = it.next();
                       Library printlib = null;
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                               Cell cell = cIt.next();
                               MutableInteger count = nodeCount.get(cell);
                               if (count == null) continue;
                               if (lib != printlib)
                               {
                                       System.out.println(lib + ":");
                                       printlib = lib;
                               }
                               System.out.println(TextUtils.toBlankPaddedString(count.intValue(), 6) + " " + cell.describe(true) + " nodes");
                       }
               }
       }

       /**
        * Method to recursively examine cell "np" and update the number of
        * instantiated primitive nodeprotos in the "temp1" field of the nodeprotos.
        */
       private static void addObjects(Cell cell, Map<NodeProto, MutableInteger> nodeCount,
                                  Map<ArcProto, MutableInteger> arcCount)
       {
               for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
               {
                       NodeInst ni = it.next();
                       if (ni.isIconOfParent()) continue;
                       NodeProto np = ni.getProto();
                       MutableInteger count = nodeCount.get(np);
                       if (count == null)
                       {
                               count = new MutableInteger(0);
                               nodeCount.put(np, count);
                       }
                       count.increment();

                       if (!ni.isCellInstance()) continue;
                       Cell subCell = (Cell)np;

                       /* ignore recursive references (showing icon in contents) */
                       if (ni.isIconOfParent()) continue;
                       Cell cnp = subCell.contentsView();
                       if (cnp == null) cnp = subCell;
                       addObjects(cnp, nodeCount, arcCount);
               }

       // counting arcs
       for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
       {
                       ArcInst ai = it.next();
           ArcProto ap = ai.getProto();
            MutableInteger count = arcCount.get(ap);
                       if (count == null)
                       {
                               count = new MutableInteger(0);
                               arcCount.put(ap, count);
                       }
                       count.increment();
       }
   }

       /**
        * This method implements the command to list instances in this Cell.
        */
       public static void listCellInstancesCommand()
       {
               Cell curCell = WindowFrame.needCurCell();
               if (curCell == null) return;

               // count the number of instances in this cell
               Map<Cell, MutableInteger> nodeCount = new HashMap<Cell, MutableInteger>();
               for(Iterator<NodeInst> it = curCell.getNodes(); it.hasNext(); )
               {
                       NodeInst ni = it.next();
                       if (!ni.isCellInstance()) continue;
                       MutableInteger count = nodeCount.get(ni.getProto());
                       if (count == null)
                       {
                               count = new MutableInteger(0);
                               nodeCount.put((Cell)ni.getProto(), count);
                       }
                       count.increment();
               }

               // show the results
               boolean first = true;
               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                               Cell cell = cIt.next();
                               MutableInteger count = nodeCount.get(cell);
                               if (count == null) continue;
                               if (first)
                                       System.out.println("Instances appearing in " + curCell);
                               first = false;
                               String line = "   " + count.intValue() + " instances of " + cell + " at";
                               for(Iterator<NodeInst> nIt = curCell.getNodes(); nIt.hasNext(); )
                               {
                                       NodeInst ni = nIt.next();
                                       if (ni.getProto() != cell) continue;
                                       line += " (" + ni.getAnchorCenterX() + "," + ni.getAnchorCenterY() + ")";
                               }
                               System.out.println(line);
                       }
               }
               if (first)
                       System.out.println("There are no instances in " + curCell);
       }

       /**
        * This method implements the command to count the number of transistors
        * from this current Cell.
        */
       public static void numberOfTransistorsCommand()
       {
               Cell curCell = WindowFrame.needCurCell();
               if (curCell == null) return;
               TransistorSearch.countNumberOfTransistors(curCell);
       }

       /**
        * This method implements the command to list the usage of the current Cell.
        */
       public static void listCellUsageCommand(boolean recursive)
       {
               Cell c = WindowFrame.needCurCell();
               if (c == null) return;

               Map<Cell,Map<Cell, MutableInteger>> nodeCount = new HashMap<Cell,Map<Cell, MutableInteger>>();
               List<Cell> cellsToConsider = new ArrayList<Cell>();
               cellsToConsider.add(c);

               for(int i=0; i<cellsToConsider.size(); i++)
               {
                       Cell bottom = cellsToConsider.get(i);

                       // count the number of instances in this cell
                       for(Iterator<NodeInst> nIt = bottom.getInstancesOf(); nIt.hasNext(); )
                       {
                               NodeInst ni = nIt.next();
                               Cell top = ni.getParent();
                               if (recursive)
                               {
                                       if (!cellsToConsider.contains(top)) cellsToConsider.add(top);
                               }
                               Map<Cell, MutableInteger> tally = nodeCount.get(top);
                               if (tally == null)
                               {
                                       tally = new HashMap<Cell, MutableInteger>();
                                       nodeCount.put(top, tally);
                               }
                               MutableInteger instanceCount = tally.get(bottom);
                               if (instanceCount == null)
                               {
                                       instanceCount = new MutableInteger(0);
                                       tally.put(bottom, instanceCount);
                               }
                               instanceCount.increment();
                       }

                       // count the number of instances in this cell's icon
                       if (bottom.getView() == View.SCHEMATIC)
                       {
                               for(Cell iconCell : bottom.getCellsInGroup())
                               {
                                       if (iconCell.getView() != View.ICON) continue;
                                       for(Iterator<NodeInst> nIt = iconCell.getInstancesOf(); nIt.hasNext(); )
                                       {
                                               NodeInst ni = nIt.next();
                                               if (ni.isIconOfParent()) continue;
                                               Cell top = ni.getParent();
                                               if (recursive)
                                               {
                                                       if (!cellsToConsider.contains(top)) cellsToConsider.add(top);
                                               }
                                               Map<Cell, MutableInteger> tally = nodeCount.get(top);
                                               if (tally == null)
                                               {
                                                       tally = new HashMap<Cell, MutableInteger>();
                                                       nodeCount.put(top, tally);
                                               }
                                               MutableInteger instanceCount = tally.get(bottom);
                                               if (instanceCount == null)
                                               {
                                                       instanceCount = new MutableInteger(0);
                                                       tally.put(bottom, instanceCount);
                                               }
                                               int arraySize = ni.getNameKey().busWidth();
                                               if (arraySize > 1) instanceCount.setValue(instanceCount.intValue() + arraySize); else
                                                       instanceCount.increment();
                                       }
                               }
                       }
               }

               // show the results
               if (nodeCount.size() == 0)
               {
                       System.out.println("Cell " + c.describe(true) + " is not used anywhere");
                       return;
               }
               if (recursive) System.out.println("Cell " + c.describe(true) + " recursive usage:"); else
                       System.out.println("Cell " + c.describe(true) + " usage:");
               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                               Cell cell = cIt.next();
                               Map<Cell, MutableInteger> tally = nodeCount.get(cell);
                               if (tally == null) continue;
                               for(Cell bottom : tally.keySet())
                               {
                                       MutableInteger count = tally.get(bottom);
                                       System.out.println("  " + cell + " has " + count.intValue() + " instances of " + bottom);
                               }
                       }
               }
       }

   /**
        * This method implements the command to describe Design Summary like Cadence of the current Cell.
        */
   public static void designSummaryCommand(boolean onlySelected)
   {
       Cell curCell = WindowFrame.needCurCell();
       if (curCell == null) return;

       // Store layers
       TreeMap<Layer, MutableInteger> layerTable = new TreeMap<Layer, MutableInteger> ();
       TreeMap<Cell, MutableInteger> instanceTable = new TreeMap<Cell, MutableInteger> ();
       Iterator<ArcInst> arcIt = curCell.getArcs();
       Iterator<NodeInst> nodeIt = curCell.getNodes();
       
       if (onlySelected)
       {
    	   EditWindow wnd = EditWindow.getCurrent();
           if (wnd == null) return;
           
           // getting arcs first
           List<Geometric> list = wnd.getHighlightedEObjs(false, true);
           List<ArcInst> arcList = new ArrayList<ArcInst>(list.size());
           for (Geometric g : list)
        	   arcList.add((ArcInst)g);
           arcIt = arcList.iterator();
           
           // getting nodes now
           list = wnd.getHighlightedEObjs(true, false);
           List<NodeInst> nodeList = new ArrayList<NodeInst>(list.size());
           for (Geometric g : list)
        	   nodeList.add((NodeInst)g);
           nodeIt = nodeList.iterator();
           
           if (!nodeIt.hasNext() && !arcIt.hasNext())
           {
        	   System.out.println("No content selected in cell");
        	   return;
           }
       }
       
       // Print environment section
       System.out.println("                   Cell Environment");
       System.out.println("******************************************************");
       Library curLib = curCell.getLibrary();
       System.out.println("Cell:      " + curCell.noLibDescribe());
       System.out.println("Library:   " + curLib.getName());
       String editMode = "?";
       URL url = curLib.getLibFile();
       if (url != null)
       {
           File f = new File(url.getPath());
           if (f.canRead()) editMode = "Read";
           if (f.canWrite()) editMode = "Write";
           System.out.println("Path:      " + url.getPath());
           System.out.println("Edit Mode: " + editMode);
       } else
       {
           System.out.println("!!! No disk file associated with this library");
       }

       int selectCount = 0;
       // Retrieve all arc layers
       for(; arcIt.hasNext(); )
       {
           ArcInst ai = arcIt.next();
           ArcProto ap = ai.getProto();
           selectCount++;
           
           for(int idx = 0; idx < ap.getNumArcLayers(); idx++)
           {
               Layer layer = ap.getLayer(idx);
               MutableInteger.addToBag(layerTable, layer);
           }
       }
       System.out.println(selectCount + " arcs analyzed");

       // Retrieve node layers
       selectCount = 0;
       for(; nodeIt.hasNext(); )
       {
           NodeInst ni = nodeIt.next();

           if (ni.isCellInstance())
           {
               // Ignore recursive references (showing icon in contents)
               if (ni.isIconOfParent())
                   continue;

               Cell subcell = (Cell) ni.getProto();
               MutableInteger.addToBag(instanceTable, subcell);
           } else
           {
               NodeProto np = ni.getProto();
               PrimitiveNode pn = (PrimitiveNode) np;

               // Ignore generic components
               if (pn.getTechnology() instanceof Generic)
                   continue;

               Technology.NodeLayer[] nodeLayerList = pn.getNodeLayers();

               for (Technology.NodeLayer nl: nodeLayerList)
               {
                   Layer layer = nl.getLayer();
                   MutableInteger.addToBag(layerTable, layer);
               }
           }
           selectCount++;
       }
       System.out.println(selectCount + " nodes analyzed");
       System.out.println("******************************************************");

       // Print layer section
       System.out.println();
       System.out.println("                     Layer Usage");
       System.out.println("******************************************************");
       System.out.println("Count\tLayer");
       int totalVal = 0;
       for (Map.Entry<Layer, MutableInteger> e: layerTable.entrySet())
       {
           Layer layer = e.getKey();
           MutableInteger count = e.getValue();
           System.out.printf("%5s\t%s\n", count, layer.getFullName());
           totalVal += count.intValue();
       }
       System.out.println("------- Total: " + totalVal + " -------");
       System.out.println("******************************************************");

       System.out.println();
       System.out.println("                    Cell Instances");
       System.out.println("******************************************************");
       System.out.println("Count\tInstance");
       totalVal = 0;
       for (Map.Entry<Cell, MutableInteger> e: instanceTable.entrySet())
       {
           Cell subcell = e.getKey();
           MutableInteger count = e.getValue();
           System.out.printf("%5s\t%s\n", count, subcell.describe(false));
           totalVal += count.intValue();
       }
       System.out.println("------- Total: " + totalVal + " -------");
       System.out.println("******************************************************");
   }

       /**
        * This method implements the command to describe the current Cell.
        */
       public static void describeThisCellCommand()
       {
               Cell curCell = WindowFrame.needCurCell();
               if (curCell == null) return;
       new DescribeThisCellJob(curCell).startJob();
       }

   private static class DescribeThisCellJob extends Job {
       private Cell cell;
       private DRC.DRCPreferences dp = new DRC.DRCPreferences(false);

       public DescribeThisCellJob(Cell cell) {
           super("DescribeThisCell", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
           this.cell = cell;
       }

       @Override
       public boolean doIt() {
           int maxLen = cell.describe(false).length();
           printHeaderLine(maxLen);
           String line = makeCellLine(cell, maxLen, dp);
           System.out.println(line);

           // also give range of X and Y
           ERectangle bounds = cell.getBounds();
           Technology tech = cell.getTechnology();
           System.out.println("Cell runs from " + TextUtils.formatDistance(bounds.getMinX(), tech) + " <= X <= " +
               TextUtils.formatDistance(bounds.getMaxX(), tech) + " and " + TextUtils.formatDistance(bounds.getMinY(), tech) +
               " <= Y <= " + TextUtils.formatDistance(bounds.getMaxY(), tech));
           return true;
       }
   }

       private static void printHeaderLine(int maxLen)
       {
               String header = "Cell";
               for(int i=4; i<maxLen; i++) header += "-";
               header += "Version--------Creation date";
               header += "---------------Revision Date--------------Size-------Usage--L-I-S-D";
               System.out.println(header);
       }

       /** This method is called from within the constructor to
        * initialize the form.
        * WARNING: Do NOT modify this code. The content of this method is
        * always regenerated by the Form Editor.
        */
   // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
   private void initComponents() {
       java.awt.GridBagConstraints gridBagConstraints;

       whichCells = new javax.swing.ButtonGroup();
       ordering = new javax.swing.ButtonGroup();
       destination = new javax.swing.ButtonGroup();
       cancel = new javax.swing.JButton();
       ok = new javax.swing.JButton();
       jLabel1 = new javax.swing.JLabel();
       allCells = new javax.swing.JRadioButton();
       onlyCellsUsedElsewhere = new javax.swing.JRadioButton();
       onlyCellsNotUsedElsewhere = new javax.swing.JRadioButton();
       onlyCellsUnderCurrent = new javax.swing.JRadioButton();
       onlyPlaceholderCells = new javax.swing.JRadioButton();
       jSeparator1 = new javax.swing.JSeparator();
       jLabel2 = new javax.swing.JLabel();
       onlyThisView = new javax.swing.JCheckBox();
       views = new javax.swing.JComboBox();
       alsoIconViews = new javax.swing.JCheckBox();
       jSeparator2 = new javax.swing.JSeparator();
       jLabel3 = new javax.swing.JLabel();
       excludeOlderVersions = new javax.swing.JCheckBox();
       excludeNewestVersions = new javax.swing.JCheckBox();
       jSeparator3 = new javax.swing.JSeparator();
       jLabel4 = new javax.swing.JLabel();
       orderByName = new javax.swing.JRadioButton();
       orderByDate = new javax.swing.JRadioButton();
       orderByStructure = new javax.swing.JRadioButton();
       jSeparator4 = new javax.swing.JSeparator();
       jLabel5 = new javax.swing.JLabel();
       displayInMessages = new javax.swing.JRadioButton();
       saveToDisk = new javax.swing.JRadioButton();
       evaluateNumerically = new javax.swing.JCheckBox();

       getContentPane().setLayout(new java.awt.GridBagLayout());

       setTitle("Cell Lists");
       setName("");
       addWindowListener(new java.awt.event.WindowAdapter() {
           public void windowClosing(java.awt.event.WindowEvent evt) {
               closeDialog(evt);
           }
       });

       cancel.setText("Cancel");
       cancel.addActionListener(new java.awt.event.ActionListener() {
           public void actionPerformed(java.awt.event.ActionEvent evt) {
               cancel(evt);
           }
       });

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 25;
       gridBagConstraints.weightx = 0.5;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(cancel, gridBagConstraints);

       ok.setText("OK");
       ok.addActionListener(new java.awt.event.ActionListener() {
           public void actionPerformed(java.awt.event.ActionEvent evt) {
               ok(evt);
           }
       });

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 1;
       gridBagConstraints.gridy = 25;
       gridBagConstraints.weightx = 0.5;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(ok, gridBagConstraints);

       jLabel1.setText("Which cells:");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 0;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(jLabel1, gridBagConstraints);

       whichCells.add(allCells);
       allCells.setText("All cells");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 1;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 0, 4);
       getContentPane().add(allCells, gridBagConstraints);

       whichCells.add(onlyCellsUsedElsewhere);
       onlyCellsUsedElsewhere.setText("Only those used elsewhere");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 2;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 4);
       getContentPane().add(onlyCellsUsedElsewhere, gridBagConstraints);

       whichCells.add(onlyCellsNotUsedElsewhere);
       onlyCellsNotUsedElsewhere.setText("Only those not used elsewhere");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 3;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 4);
       getContentPane().add(onlyCellsNotUsedElsewhere, gridBagConstraints);

       whichCells.add(onlyCellsUnderCurrent);
       onlyCellsUnderCurrent.setText("Only those under current cell");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 4;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 4);
       getContentPane().add(onlyCellsUnderCurrent, gridBagConstraints);

       whichCells.add(onlyPlaceholderCells);
       onlyPlaceholderCells.setText("Only placeholder cells");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 5;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 4, 4);
       getContentPane().add(onlyPlaceholderCells, gridBagConstraints);

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 6;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       getContentPane().add(jSeparator1, gridBagConstraints);

       jLabel2.setText("View filter:");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 7;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(jLabel2, gridBagConstraints);

       onlyThisView.setText("Show only this view:");
       onlyThisView.addActionListener(new java.awt.event.ActionListener() {
           public void actionPerformed(java.awt.event.ActionEvent evt) {
               onlyThisViewActionPerformed(evt);
           }
       });

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 8;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 0, 4);
       getContentPane().add(onlyThisView, gridBagConstraints);

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 9;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(2, 40, 4, 4);
       getContentPane().add(views, gridBagConstraints);

       alsoIconViews.setText("Also include icon views");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 10;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 4);
       getContentPane().add(alsoIconViews, gridBagConstraints);

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 11;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       getContentPane().add(jSeparator2, gridBagConstraints);

       jLabel3.setText("Version filter:");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 12;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(jLabel3, gridBagConstraints);

       excludeOlderVersions.setText("Exclude older versions");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 13;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 0, 4);
       getContentPane().add(excludeOlderVersions, gridBagConstraints);

       excludeNewestVersions.setText("Exclude newest versions");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 14;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 4, 4);
       getContentPane().add(excludeNewestVersions, gridBagConstraints);

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 15;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       getContentPane().add(jSeparator3, gridBagConstraints);

       jLabel4.setText("Display ordering:");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 16;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(jLabel4, gridBagConstraints);

       ordering.add(orderByName);
       orderByName.setText("Order by name");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 17;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 0, 4);
       getContentPane().add(orderByName, gridBagConstraints);

       ordering.add(orderByDate);
       orderByDate.setText("Order by modification date");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 19;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 4);
       getContentPane().add(orderByDate, gridBagConstraints);

       ordering.add(orderByStructure);
       orderByStructure.setText("Order by skeletal structure");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 20;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 4, 4);
       getContentPane().add(orderByStructure, gridBagConstraints);

       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 21;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       getContentPane().add(jSeparator4, gridBagConstraints);

       jLabel5.setText("Destination:");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 22;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
       getContentPane().add(jLabel5, gridBagConstraints);

       destination.add(displayInMessages);
       displayInMessages.setText("Display in messages window");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 23;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(4, 20, 0, 4);
       getContentPane().add(displayInMessages, gridBagConstraints);

       destination.add(saveToDisk);
       saveToDisk.setText("Save to disk");
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 24;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 20, 4, 4);
       getContentPane().add(saveToDisk, gridBagConstraints);

       evaluateNumerically.setText("Evaluate Numbers when Sorting Names");
       evaluateNumerically.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
       evaluateNumerically.setMargin(new java.awt.Insets(0, 0, 0, 0));
       gridBagConstraints = new java.awt.GridBagConstraints();
       gridBagConstraints.gridx = 0;
       gridBagConstraints.gridy = 18;
       gridBagConstraints.gridwidth = 2;
       gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
       gridBagConstraints.insets = new java.awt.Insets(0, 40, 0, 4);
       getContentPane().add(evaluateNumerically, gridBagConstraints);

       pack();
   }// </editor-fold>//GEN-END:initComponents

       private void onlyThisViewActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_onlyThisViewActionPerformed
       {//GEN-HEADEREND:event_onlyThisViewActionPerformed
               boolean selected = onlyThisView.isSelected();
               views.setEnabled(selected);
       }//GEN-LAST:event_onlyThisViewActionPerformed

       private void cancel(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancel
       {//GEN-HEADEREND:event_cancel
               closeDialog(null);
       }//GEN-LAST:event_cancel

       private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
       {//GEN-HEADEREND:event_ok
       new GeneralCellListsJob(curCell, allCells.isSelected(),
               onlyCellsUnderCurrent.isSelected(), onlyCellsUsedElsewhere.isSelected(), onlyCellsNotUsedElsewhere.isSelected(),
               onlyThisView.isSelected(), (String)views.getSelectedItem(), alsoIconViews.isSelected(),
               excludeOlderVersions.isSelected(), excludeNewestVersions.isSelected(),
               orderByName.isSelected(), evaluateNumerically.isSelected(), orderByDate.isSelected(), orderByStructure.isSelected(),
               saveToDisk.isSelected()).startJob();
               closeDialog(null);
   }

   private static class GeneralCellListsJob extends Job {
       private final Cell curCell;
       private final boolean allCells;
       private final boolean onlyCellsUnderCurrent;
       private final boolean onlyCellsUsedElsewhere;
       private final boolean onlyCellsNotUsedElsewhere;
       private final boolean onlyThisView;
       private final String viewName;
       private final boolean alsoIconViews;
       private final boolean excludeOlderVersions;
       private final boolean excludeNewestVersions;
       private final boolean orderByName;
       private final boolean evaluateNumerically;
       private final boolean orderByDate;
       private final boolean orderByStructure;
       private final boolean saveToDisk;
       private final String saveNameFile;
       
       private DRC.DRCPreferences dp = new DRC.DRCPreferences(false);

       private GeneralCellListsJob(Cell curCell, boolean allCells,
               boolean onlyCellsUnderCurrent, boolean onlyCellsUsedElsewhere, boolean onlyCellsNotUsedElsewhere,
               boolean onlyThisView, String viewName, boolean alsoIconViews,
               boolean excludeOlderVersions, boolean excludeNewestVersions,
               boolean orderByName, boolean evaluateNumerically, boolean orderByDate, boolean orderByStructure,
               boolean saveToDisk) {
           super("GeneralCellLists", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
           this.curCell = curCell;
           this.allCells = allCells;
           this.onlyCellsUnderCurrent = onlyCellsUnderCurrent;
           this.onlyCellsUsedElsewhere = onlyCellsUsedElsewhere;
           this.onlyCellsNotUsedElsewhere = onlyCellsNotUsedElsewhere;
           this.onlyThisView = onlyThisView;
           this.viewName = viewName;
           this.alsoIconViews = alsoIconViews;
           this.excludeOlderVersions = excludeOlderVersions;
           this.excludeNewestVersions = excludeNewestVersions;
           this.orderByName = orderByName;
           this.evaluateNumerically = evaluateNumerically;
           this.orderByDate = orderByDate;
           this.orderByStructure = orderByStructure;
           this.saveToDisk = saveToDisk;
    	   saveNameFile = (saveToDisk) ? OpenFile.chooseOutputFile(FileType.READABLEDUMP, null, "celllist.txt") : null;
       }

       @Override
       public boolean doIt() {
           // get cell and port markers
           Set<Cell> cellsSeen = new HashSet<Cell>();

           // mark cells to be shown
           if (allCells)
           {
               // mark all cells for display
               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                   Library lib = it.next();
                   for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                   {
                       Cell cell = cIt.next();
                       cellsSeen.add(cell);
                   }
               }
           } else
           {
               // mark no cells for display, filter according to request
               if (onlyCellsUnderCurrent)
               {
                   // mark those that are under this
                   recursiveMark(curCell, cellsSeen);
               } else if (onlyCellsUsedElsewhere)
               {
                   // mark those that are in use
                   for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
                   {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                           Cell cell = cIt.next();
                           Cell iconCell = cell.iconView();
                           if (iconCell == null) iconCell = cell;
                           if (cell.getInstancesOf().hasNext() || iconCell.getInstancesOf().hasNext())
                               cellsSeen.add(cell);
                       }
                   }
               } else if (onlyCellsNotUsedElsewhere)
               {
                   // mark those that are not in use
                   for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
                   {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                           Cell cell = cIt.next();
                           Cell iconCell = cell.iconView();
                           if (iconCell != null)
                           {
                               // has icon: acceptable if the only instances are examples
                               if (cell.getInstancesOf().hasNext()) continue;
                               boolean found = false;
                               for(Iterator<NodeInst> nIt = iconCell.getInstancesOf(); nIt.hasNext(); )
                               {
                                   NodeInst ni = nIt.next();
                                   if (ni.isIconOfParent()) { found = true;   break; }
                               }
                               if (found) continue;
                           } else
                           {
                               // no icon: reject if this has instances
                               if (cell.isIcon())
                               {
                                   // this is an icon: reject if instances are not examples
                                   boolean found = false;
                                   for(Iterator<NodeInst> nIt = cell.getInstancesOf(); nIt.hasNext(); )
                                   {
                                       NodeInst ni = nIt.next();
                                       if (ni.isIconOfParent()) { found = true;   break; }
                                   }
                                   if (found) continue;
                               } else
                               {
                                   if (cell.getInstancesOf().hasNext()) continue;
                               }
                           }
                           cellsSeen.add(cell);
                       }
                   }
               } else
               {
                   // mark placeholder cells
                   for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
                   {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                           Cell cell = cIt.next();
                           Variable var = cell.getVar("IO_true_library");
                           if (var != null) cellsSeen.add(cell);
                       }
                   }
               }
           }

           // filter views
           if (onlyThisView)
           {
               View v = View.findView(viewName);
               if (v != null)
               {
                   for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
                   {
                       Library lib = it.next();
                       for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                       {
                           Cell cell = cIt.next();
                           if (cell.getView() != v)
                           {
                               if (cell.isIcon())
                               {
                                   if (alsoIconViews) continue;
                               }
                               cellsSeen.remove(cell);
                           }
                       }
                   }
               }
           }

           // filter versions
           if (excludeOlderVersions)
           {
               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                   Library lib = it.next();
                   for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                   {
                       Cell cell = cIt.next();
                       if (cell.getNewestVersion() != cell) cellsSeen.remove(cell);
                   }
               }
           }
           if (excludeNewestVersions)
           {
               for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
               {
                   Library lib = it.next();
                   for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
                   {
                       Cell cell = cIt.next();
                       if (cell.getNewestVersion() == cell) cellsSeen.remove(cell);
                   }
               }
           }

           // now make a list and sort it
           List<Cell> cellList = new ArrayList<Cell>();
           for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
           {
               Library lib = it.next();
               if (lib.isHidden()) continue;
               for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
               {
                   Cell cell = cIt.next();
                   if (cellsSeen.contains(cell)) cellList.add(cell);
               }
           }
           if (cellList.size() == 0) System.out.println("No cells match this request"); else
           {
               if (orderByName)
               {
                   if (evaluateNumerically)
                   {
                       Collections.sort(cellList);
                   } else
                   {
                       Collections.sort(cellList, new TextUtils.CellsByName());
                   }
               } else if (orderByDate)
               {
                   Collections.sort(cellList, new TextUtils.CellsByDate());
               } else if (orderByStructure)
               {
                   Collections.sort(cellList, new SortByCellStructure());
               }

               // finally show the results
               if (saveToDisk)
               {
                   if (saveNameFile == null) System.out.println("Cannot write cell listing"); else
                   {
                       FileOutputStream fileOutputStream = null;
                       try {
                           fileOutputStream = new FileOutputStream(saveNameFile);
                       } catch (FileNotFoundException e) {}
                       BufferedOutputStream bufStrm = new BufferedOutputStream(fileOutputStream);
                       DataOutputStream dataOutputStream = new DataOutputStream(bufStrm);
                       try
                       {
                           DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
                           String header = "List of cells created on " + df.format(new Date()) + "\n";
                           dataOutputStream.write(header.getBytes(), 0, header.length());
                           header = "Cell\tVersion\tCreation date\tRevision Date\tSize\tUsage\tLock\tInst-lock\tCell-lib\tDRC\tNCC\n";
                           dataOutputStream.write(header.getBytes(), 0, header.length());
                           for(Cell cell : cellList)
                           {
                               String line =  makeCellLine(cell, -1, dp) + "\n";
                               dataOutputStream.write(line.getBytes(), 0, line.length());
                           }
                           dataOutputStream.close();
                           System.out.println("Wrote " + saveNameFile);
                       } catch (IOException e)
                       {
                           System.out.println("Error closing " + saveNameFile);
                       }
                   }
               } else
               {
                   int maxLen = 0;
                   for(Cell cell : cellList)
                   {
                       maxLen = Math.max(maxLen, cell.noLibDescribe().length());
                   }
                   maxLen = Math.max(maxLen+2, 7);
                   printHeaderLine(maxLen);
                   Library lib = null;
                   for(Cell cell : cellList)
                   {
                       if (cell.getLibrary() != lib)
                       {
                           lib = cell.getLibrary();
                           System.out.println("======== LIBRARY " + lib.getName() + ": ========");
                       }
                       System.out.println(makeCellLine(cell, maxLen, dp));
                   }
               }
           }
           return true;
       }

       /**
        * Method to recursively walk the hierarchy from "np", marking all cells below it.
        */
       private static void recursiveMark(Cell cell, Set<Cell> cellsSeen)
       {
           if (cellsSeen.contains(cell)) return;
           cellsSeen.add(cell);
           for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
           {
               NodeInst ni = it.next();
               if (!ni.isCellInstance()) continue;
               Cell subCell = (Cell)ni.getProto();
               recursiveMark(subCell, cellsSeen);
               Cell contentsCell = subCell.contentsView();
               if (contentsCell != null) recursiveMark(contentsCell, cellsSeen);
           }
       }
       }//GEN-LAST:event_ok

       /** Closes the dialog */
       private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
       {
               // remember settings
               if (allCells.isSelected()) whichSwitch = 0; else
               if (onlyCellsUsedElsewhere.isSelected()) whichSwitch = 1; else
               if (onlyCellsNotUsedElsewhere.isSelected()) whichSwitch = 2; else
               if (onlyCellsUnderCurrent.isSelected()) whichSwitch = 3; else
               if (onlyPlaceholderCells.isSelected()) whichSwitch = 4;

               onlyViewSwitch = onlyThisView.isSelected();
               viewSwitch = View.findView((String)views.getSelectedItem());
               alsoIconSwitch = alsoIconViews.isSelected();
               excOldVersSwitch = excludeOlderVersions.isSelected();
               excNewVersSwitch = excludeNewestVersions.isSelected();
               evaluateNumbers = evaluateNumerically.isSelected();

               if (orderByName.isSelected()) orderingSwitch = 0; else
               if (orderByDate.isSelected()) orderingSwitch = 1; else
               if (orderByStructure.isSelected()) orderingSwitch = 2;

               if (displayInMessages.isSelected()) destinationSwitch = 0; else
               if (saveToDisk.isSelected()) destinationSwitch = 1;

               setVisible(false);
               dispose();
       }//GEN-LAST:event_closeDialog

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JRadioButton allCells;
   private javax.swing.JCheckBox alsoIconViews;
   private javax.swing.JButton cancel;
   private javax.swing.ButtonGroup destination;
   private javax.swing.JRadioButton displayInMessages;
   private javax.swing.JCheckBox evaluateNumerically;
   private javax.swing.JCheckBox excludeNewestVersions;
   private javax.swing.JCheckBox excludeOlderVersions;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JSeparator jSeparator1;
   private javax.swing.JSeparator jSeparator2;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JSeparator jSeparator4;
   private javax.swing.JButton ok;
   private javax.swing.JRadioButton onlyCellsNotUsedElsewhere;
   private javax.swing.JRadioButton onlyCellsUnderCurrent;
   private javax.swing.JRadioButton onlyCellsUsedElsewhere;
   private javax.swing.JRadioButton onlyPlaceholderCells;
   private javax.swing.JCheckBox onlyThisView;
   private javax.swing.JRadioButton orderByDate;
   private javax.swing.JRadioButton orderByName;
   private javax.swing.JRadioButton orderByStructure;
   private javax.swing.ButtonGroup ordering;
   private javax.swing.JRadioButton saveToDisk;
   private javax.swing.JComboBox views;
   private javax.swing.ButtonGroup whichCells;
   // End of variables declaration//GEN-END:variables
}

