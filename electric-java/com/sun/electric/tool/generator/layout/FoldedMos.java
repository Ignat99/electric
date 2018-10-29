/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FoldedMos.java
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
package com.sun.electric.tool.generator.layout;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.tool.generator.layout.TechType.MosInst;
import com.sun.electric.tool.Job;

/**
 * first cut at a folded transistor generator. Transistors are rotated 90
 * degrees counter clockwise so that gates are vertical.
 * 
 * FoldedMos is abstract. Instantiate FoldedNmos or FoldedPmos instead.
 */
public abstract class FoldedMos {
	// -------------------------- public types -------------------------------
	/**
	 * Users use GateSpace objects to tell the FoldedMos constructors to leave
	 * additional space between diffusion contacts and gates and between
	 * adjacent gates. There are a total of nbFolds * (nbSeries-1) such spaces.
	 * 
	 * <p>
	 * The FoldedMos constructor builds FoldedMos transistors from left to
	 * right. Just before it adds a new poly gate or a new diffusion contact
	 * (except for the first diffusion contact) it calls GateSpace.getExtraSpace
	 * to find out the amount of "extra space" it should leave between this new
	 * object and the preceeding object.
	 */
	public interface GateSpace {
		/**
		 * The getExtraSpace() method returns the desired amount of "extra
		 * space" to leave between the specified objects.
		 * 
		 * <p>
		 * First, we define "extra space". The "normal" distance from the center
		 * of a minimum sized diffusion contact and the center of a minimum
		 * width transistor gate is 4 lambda. For this normal spacing we define
		 * the "extra space" to be 0 lambda. However if the width of the
		 * transistor is less than 5 lambda, then the distance between the
		 * centers of the diffusion contact and the gate must be 4.5 lambda. In
		 * that case we define the extra space to be .5 lambda.
		 * 
		 * <p>
		 * Similarly if the distance between the centers of two adjacent series
		 * gates is 5 lambda then the "extraSpace" is 0 lambda. However if the
		 * distance between the centers of two adjacent series gates is 6 lambda
		 * then the "extra space" is 1 lambda.
		 * 
		 * @param requiredExtraSpace
		 *            the extra space required by the design rules. Normally
		 *            this is 0 lambda. However, if the gate width is less than
		 *            5 lambda, the requiredExtraSpace between the diffusion
		 *            contact and the gate is .5 lambda.
		 * @param foldNdx
		 *            the index of this fold. This will range between 0 and
		 *            nbFolds-1
		 * @param nbFolds
		 *            the number of folds in this FoldedMos
		 * @param spaceNdx
		 *            the index of this space within this fold. This will range
		 *            between 0 and nbGates. If spaceNdx==0 or spaceNdx==nbGates
		 *            then getSpace must return the distance between a diffusion
		 *            contact and a gate. Otherwise getSpace must return the
		 *            distance between gates spaceNdx-1 and spaceNdx.
		 * @param nbGates
		 *            the total number of gates in this FoldedMos.
		 * @return the desired extra space. Note that the returned extra space
		 *         must be greater than or equal to requiredExtraSpace to avoid
		 *         DRC errors.
		 */
		double getExtraSpace(double requiredExtraSpace, int foldNdx,
				int nbFolds, int spaceNdx, int nbGates);
	}

	// ---------------------------- private data --------------------------------

	private static final GateSpace useMinSp= new GateSpace() {
		public double getExtraSpace(double requiredExtraSpace, int foldNdx,
				int nbFolds, int spaceNdx, int nbGates) {
			return requiredExtraSpace;
		}
	};

	private PortInst[] diffVias;

	private MosInst[] moss;

	private PortInst[] internalDiffs;

	private double difContWid;

	private double gateWidth;

	private double physWidth;

	private double mosY;

	private TechType tech;
    
    private final EditingPreferences ep;

	// -------------------- protected and private methods ---------------------

    FoldedMos(EditingPreferences ep) {
        this.ep = ep;
    }
    
	// after rotating 90 degrees counter clock wise
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	private boolean isPmos() {
		return this instanceof FoldedPmos;
	}

	// This method is necessary to ensure that the edges of all
	// diffusion arcs are on the .5 lambda grid to avoid CIF resolution
	// errors. The method assumes that diff widths are integral and
	// attempts to position the ArcInst endpoints on a .5 lambda grid.
	//
	// The centers of ports may not be on .5 lambda grid for two
	// reasons. First, the x coordinates of the centers of diffusion
	// ports of a MOS transistor are ALWAYS off grid when the transistor
	// is on grid. Second, the y coordinate of the centers of diffusion
	// ports of a MOS transistor are off grid when the transistor width
	// is .5 plus an integer.
	//
	// The first problem requires the rounding of the x coordinate onto
	// the .5 lambda grid. The second problem is handled by using the y
	// coordinate of the contact.
	private void newDiffArc(ArcProto diff, double y, PortInst p1, PortInst p2) {
		double x1= tech.roundToGrid(LayoutLib.roundCenterX(p1));
		double x2= tech.roundToGrid(LayoutLib.roundCenterX(p2));

		NodeInst ni1= p1.getNodeInst();
		NodeInst ni2= p2.getNodeInst();
		Poly poly1= ni1.getShapeOfPort(p1.getPortProto());
		Poly poly2= ni2.getShapeOfPort(p2.getPortProto());
		// For CMOS90, Diff node's ports are always zero in size, so when
		// diff node and diffCon node are at different y positions, you
		// cannot always create a single arc. In CMOS90, as long as the arc is a
		// multiple
		// of 0.2 wide and the end points are at a grid multiple of 0.1, there
		// are no grid problems, so we can just use a track router
		if (!poly1.contains(x1, y) || !poly2.contains(x2, y)) {
			LayoutLib.newArcInst(diff, ep, LayoutLib.DEF_SIZE, p1, p2);
		} else {
			LayoutLib
					.newArcInst(diff, ep, LayoutLib.DEF_SIZE, p1, x1, y, p2, x2, y);
		}
	}
	// If necessary, add metal-1 to guarantee that metal-1 meets minimum area
	// requirements.
	private void addM1ForMinArea(PortInst diffContPort, double difContHei, char justify) {
		double diffOverM1 = (tech.getDiffContWidth()-tech.getDiffCont_m1Width())/2;
		double metalH = difContHei - diffOverM1*2;
		double metalW = tech.getDiffCont_m1Width();
		double area = (metalH * metalW);
		if (area>=tech.getM1MinArea()) return;
		
		// Insufficient metal-1.  Add some!
		Cell cell = diffContPort.getNodeInst().getParent();		
		
		// How high should m1 be? Round up to nearest .2 lambda.
		double reqH = tech.getM1MinArea() / metalW;
		reqH = Math.ceil(reqH / .2) * .2;

		double x = diffContPort.getCenter().getX();
		double y = diffContPort.getCenter().getY();
		if (justify=='T') {
			// align tops of metal-1 and diffusion contact
			double yHi = y + metalH/2 - metalW/2;
			PortInst piHi = 
				LayoutLib.newNodeInst(tech.m1pin(), ep, x, yHi, 
						              LayoutLib.DEF_SIZE, 
						              LayoutLib.DEF_SIZE, 
						              0, cell).getOnlyPortInst();
			LayoutLib.newArcInst(tech.m1(), ep, metalW, diffContPort, piHi);

			double yLo = yHi - reqH + metalW;
			PortInst piLo = 
				LayoutLib.newNodeInst(tech.m1pin(), ep, x, yLo, 
						              LayoutLib.DEF_SIZE, 
						              LayoutLib.DEF_SIZE, 
						              0, cell).getOnlyPortInst();
			LayoutLib.newArcInst(tech.m1(), ep, metalW, diffContPort, piLo);
		} else {
			// align bottoms of metal-1 and diffusion contact
			double yLo = y - metalH/2 + metalW/2;
			PortInst piLo = 
				LayoutLib.newNodeInst(tech.m1pin(), ep, x, yLo, 
						              LayoutLib.DEF_SIZE, 
						              LayoutLib.DEF_SIZE, 
						              0, cell).getOnlyPortInst();
			LayoutLib.newArcInst(tech.m1(), ep, metalW, diffContPort, piLo);
			
			double yHi = y + reqH - metalW;
			PortInst piHi = 
				LayoutLib.newNodeInst(tech.m1pin(), ep, x, yHi, 
						              LayoutLib.DEF_SIZE, 
						              LayoutLib.DEF_SIZE, 
						              0, cell).getOnlyPortInst();
			LayoutLib.newArcInst(tech.m1(), ep, metalW, diffContPort, piHi);
		}
	}

	/**
	 * Construct a MOS transistor that has been folded to fit within a certain
	 * height. The gates always vertical: no rotation is supported.
	 * 
	 * <p>
	 * Edge alignment to grid is a subtle issue. Limitations in the CIF format
	 * require all edges to lie on the 0.5 lambda grid. The user of FoldedMos is
	 * responsible for positioning the outer boundaries of the FoldedMos on
	 * grid. To do this she must ensure that x is on grid, that gateWid is a
	 * multiple of 0.5 lambda. Furthermore, if gateWid is less than the width of
	 * a minimum-sized diffusion contact (5 lambda) then y must be on grid. If
	 * gateWid is greater than the width of a minimum-sized diffusion contact
	 * then (y - gateWid)/2) must be on grid.
	 * 
	 * <p>
	 * This constructor is responsible for positioning the internal pieces on
	 * the 0.5 lambda grid. Most significant to the user is the positioning of
	 * the MOS transistors when gateWidth is less than the width of a minimum
	 * sized diffusion contact. In that case this constructor assumes the user
	 * will place the edges of the diffusion contact on grid and moves the MOS
	 * transistors slightly up or down to get the MOS transistor on the same
	 * grid.
	 * 
	 * @param type
	 *            'N' or 'P' for NMOS or PMOS
	 * @param x
	 *            the middle of the left most diffusion contact
	 * @param y
	 *            the "middle" of the FoldedMos. If gateWidth is less than the
	 *            width of the minimum sized diffusion contact this will be the
	 *            middle of the diffusion contact. Otherwise this is the middle
	 *            of the MOS transistor.
	 * @param nbFolds
	 *            the number of folds. Each "fold" consists of a left diffusion
	 *            contact, nbSeries transistors stacked in series, and a right
	 *            diffusion contact. Adjacent folds share diffusion contacts.
	 * @param nbSeries
	 *            the number of transistors stacked in series for each fold
	 * @param gateWidth
	 *            the width of each gate
	 * @param gateSpace
	 *            allows the user to specify the space between diffusion
	 *            contacts and gates and between adjacent gates. null means use
	 *            the minimum spacing required by the design rules.
	 * @param justifyDiffCont
	 *            FoldedMos always makes diffusion contacts a multiple of 5
	 *            lambda wide. If the gateWidth is not a multiple of 5 lambda
	 *            wide then this argument specifies how the diffusion contact
	 *            should be positioned within the diffusion. The choices are
	 *            'T', or 'B' to move the contact to the top or bottom.
	 *            Centering the contact was eliminated because if the transistor
	 *            width was .5 + an integer then the diffusion contact edges
	 *            would not be on the same .5 lambda grid as any of the edges of
	 *            the transistor thereby leading to CIF resolution errors.
	 * @param f
	 *            the facet that will contain this FoldedMos
	 * @param tech
	 */
	FoldedMos(char type, double x, double y, int nbFolds, int nbSeries,
			  double gateWidth, GateSpace gateSpace, char justifyDiffCont,
			  Cell f, TechType tech, EditingPreferences ep) {
		error(type != 'P' && type != 'N',
				"FoldedMos: type must be 'P' or 'N': " + type);
		this.tech = tech;
        this.ep = ep;
		this.gateWidth= gateWidth;
		physWidth= Math.max(tech.getDiffContWidth(), gateWidth);
		diffVias= new PortInst[nbFolds + 1];
		moss= new MosInst[nbFolds * nbSeries];
		internalDiffs= new PortInst[nbFolds * (nbSeries - 1)];
		if (gateSpace == null)
			gateSpace= useMinSp;

		PrimitiveNode diffCont= isPmos() ? tech.pdm1() : tech.ndm1();
		ArcProto diff= isPmos() ? tech.pdiff() : tech.ndiff();
		NodeProto difNod= isPmos() ? tech.pdNode() : tech.ndNode();

		// double foldPitch = 8 + (nbSeries - 1) * (3 + 2);
		int diffNdx= 0, mosNdx= 0, internalDiffNdx= 0;

		// Contact only needs to be multiple of 5 lambda high. Because
		// diffusion contact is always justified up or down and because it
		// is always an integral number of lambdas high, it's edges and
		// center are on the same .5 lambda grid as the transistor edges.
		double difConIncr= tech.getDiffContIncr();
		difContWid= Math.max(tech.getDiffContWidth(),
				((int) (gateWidth / difConIncr)) * difConIncr);
		double difContSlop= Math.max(0, (gateWidth - difContWid) / 2);
		double difContY= y;
		switch (justifyDiffCont) {
		case 'T':
			difContY+= difContSlop;
			break;
		case 'B':
			difContY-= difContSlop;
			break;
        case 'C':
            difContWid = gateWidth;
            break;
		default:
			error(true, "FoldedMos: justifyDiffCont must be 'T', or 'B'");
		}

		// If the MOS width is less than the minimum width of a diffusion
		// contact then the diffusion contact determines the boundary of
		// the transistor. In this case the user will place the top and
		// bottom of the diffusion contact on a 0.5 lambda grid.
		// Therefore we need to shift the MOS transistor so that its edges
		// are also on a 0.5 lambda grid. I will do this by shifting the
		// MOS transistor down until its bottom edge is on grid.
		mosY= y;
		if (gateWidth < tech.getDiffContWidth()) {
			double mosBotY= y - gateWidth / 2;
			double misAlign= Math.IEEEremainder(mosBotY, 0.5);
			mosY-= misAlign;
		}

		double extraDiffPolySpace = 
			gateWidth >= tech.getDiffContWidth() ? (
			    0.
			) : (
				tech.getGateToDiffContSpaceDogBone() - tech.getGateToDiffContSpace()
			);
		double viaToMosPitch = tech.getDiffContWidth()/2 + 
		                       tech.getGateToDiffContSpace() +
		                       tech.getGateLength()/2;
		double mosToMosPitch = tech.getGateLength() + tech.getGateToGateSpace();

		PortInst prevPort= null;
		for (int i= 0;; i++) {
			// put down diffusion contact
			PortInst newPort = 
				LayoutLib.newNodeInst(diffCont, ep, x, difContY,
				                      LayoutLib.DEF_SIZE, 
				                      difContWid, 0, f).getOnlyPortInst();
			
			// Add redundant diffusion as a hint of the metal-1 wire size to 
			// use to connect to this diffusion contact. Diffusion contact is
                // always on grid.
			ArcInst redundantAi = LayoutLib.newArcInst(diff, ep, tech.getDiffCont_m1Width(), newPort,
					             newPort);
            if (justifyDiffCont == 'C') {
            redundantAi.setHeadExtended(false);
            redundantAi.setTailExtended(false);
            }
			
			addM1ForMinArea(newPort, difContWid, justifyDiffCont);
			
			diffVias[diffNdx++]= newPort;

			// connect to previous port
			if (prevPort != null) {
				newDiffArc(diff, difContY, prevPort, newPort);
			}
			prevPort= newPort;

			if (i >= nbFolds)
				break; // exit after inserting last diff contact

			// series transistors
			for (int j= 0; j < nbSeries; j++) {
				double extraSp= gateSpace.getExtraSpace(
						j == 0 ? extraDiffPolySpace : 0, i, nbFolds, j,
						nbSeries);
				if (j == 0 && extraSp != 0) {
					// Fill diff notch from center of diff contact to left end
					// of MOS. Round up diff node width to multiple of lambda
					// or else center will be off .5 lambda grid.
					double w= Math.ceil(extraSp);
					NodeInst dn= LayoutLib.newNodeInst(difNod, ep, x + w / 2, mosY,
							w, gateWidth, 0, f);
					newDiffArc(diff, difContY, prevPort, dn.getOnlyPortInst());
				}

				x+= (j == 0 ? viaToMosPitch : mosToMosPitch) + extraSp;
				MosInst m= isPmos() ? tech.newPmosInst(x, mosY, gateWidth, 
						tech.getGateLength(), f, ep) : tech.newNmosInst(x, mosY,
						gateWidth, tech.getGateLength(), f, ep);
				moss[mosNdx++]= m;

				newDiffArc(diff, difContY, prevPort, m.leftDiff());
				prevPort= m.rightDiff();

				if (j != 0) {
					internalDiffs[internalDiffNdx++]= m.leftDiff();
				}
			}
			double extraSp= gateSpace.getExtraSpace(extraDiffPolySpace, i,
					nbFolds, nbSeries, nbSeries);
			x+= viaToMosPitch + extraSp;
			if (extraSp != 0) {
				// fill diff notch from right end of MOS to center of diff cont
				double w= Math.ceil(extraSp);
				NodeInst dn= LayoutLib.newNodeInst(difNod, ep, x - w / 2, mosY, w,
						gateWidth, 0, f);
				newDiffArc(diff, difContY, prevPort, dn.getOnlyPortInst());
			}
		}
	}

	// ----------------------------- public methods ------------------------------

	/** return the width of each transistor's gate */
	public double getGateWidth() {
		return gateWidth;
	}

	/**
	 * when the gate is narrower than the diffusion contact return the diffusion
	 * contact width, otherwise return the gate width
	 */
	public double getPhysWidth() {
		return physWidth;
	}

	/**
	 * Get the Y coordinate of the centers of the MOS transistors. This may be
	 * different from the Y coordinate passed into the constructor. When the MOS
	 * transistor is narrower than the diffusion contact the MOS transistor must
	 * be been shifted up or down to get its edges on grid
	 */
	public double getMosCenterY() {
		return mosY;
	}

	/**
	 * The diffusion contact's width increases with the gateWidth but is only
	 * large enough to surround the diffusion contact cuts
	 */
	public double getDiffContWidth() {
		return difContWid;
	}

	public int nbSrcDrns() {
		return diffVias.length;
	}

	public PortInst getSrcDrn(int col) {
		return diffVias[col];
	}

	public int nbGates() {
		return moss.length;
	}

	public PortInst getGate(int mosNdx, char pos) {
		error(pos != 'T' && pos != 'B', "pos must be 'T' or 'B': " + pos);
		return pos == 'T' ? moss[mosNdx].topPoly() : moss[mosNdx].botPoly();
	}

	public int nbInternalSrcDrns() {
		return internalDiffs.length;
	}

	/**
	 * "Internal diffusions" are the diffusions between two series transistors.
	 * The user may wish to connect these with "generic:Uinversal" arcs in order
	 * to fool Electric's NCC into paralleling transistor stacks of series
	 * transistors.
	 */
	public PortInst getInternalSrcDrn(int col) {
		return internalDiffs[col];
	}

}
