/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RoutingTab.java
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
package com.sun.electric.tool.user.dialogs.options;

import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.RoutingFrame;
import com.sun.electric.tool.routing.RoutingFrame.RoutingParameter;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.menus.MenuCommands;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

/**
 * Class to handle the "Routing" tab of the Preferences dialog.
 */
public class RoutingTab extends PreferencePanel
{
	private PreferencesFrame parent;
	private Map<RoutingParameter,JComponent> currentParameters;
    private RoutingFrame.RoutingPrefs routingOptions;

	/** Creates new form RoutingTab */
	public RoutingTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		this.parent = parent;
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(sogMaxArcWidth);
	    EDialog.makeTextFieldSelectAllOnTab(sogComplexityLimit);
	    EDialog.makeTextFieldSelectAllOnTab(sogMaxDistance);
	    EDialog.makeTextFieldSelectAllOnTab(sogRerunComplexityLimit);
	    EDialog.makeTextFieldSelectAllOnTab(sogForcedProcessorCount);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return routing; }

	/** return the name of this preferences tab. */
	public String getName() { return "Routing"; }

	private ArcProto initRoutDefArc;
	private static boolean oneProcessorWarning = false;
	private static boolean twoProcessorWarning = false;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Routing tab.
	 */
	public void init()
	{
		// initialize for the stitcher that is running
		boolean initRoutMimicOn = Routing.isMimicStitchOn();
		boolean initRoutAutoOn = Routing.isAutoStitchOn();
		if (!initRoutMimicOn && !initRoutAutoOn) routNoStitcher.setSelected(true); else
		{
			if (initRoutMimicOn) routMimicStitcher.setSelected(true); else
				routAutoStitcher.setSelected(true);
		}

		// the sea-of-gates section
		sogMaxArcWidth.setText(TextUtils.formatDistance(Routing.getSeaOfGatesMaxWidth()));
		sogComplexityLimit.setText(Integer.toString(Routing.getSeaOfGatesComplexityLimit()));
		sogMaxDistance.setText(Integer.toString(Routing.getSeaOfGatesMaxDistance()));
		sogGlobalRouting.setSelected(Routing.isSeaOfGatesUseGlobalRouting());
		sogSpineRouting.setSelected(Routing.isSeaOfGatesEnableSpineRouting());
		sogRerunFailedRoutes.setSelected(Routing.isSeaOfGatesRerunFailedRoutes());
		sogRunOnConnectedRoutes.setSelected(Routing.isSeaOfGatesRunOnConnectedRoutes());
		sogContactSubcellAction.addItem(SoGContactsStrategy.SOGCONTACTSATTOPLEVEL); // 
		sogContactSubcellAction.addItem(SoGContactsStrategy.SOGCONTACTSUSEEXISTINGSUBCELLS);
		sogContactSubcellAction.addItem(SoGContactsStrategy.SOGCONTACTSALWAYSINSUBCELLS);
		sogContactSubcellAction.addItem(SoGContactsStrategy.SOGCONTACTSFORCESUBCELLS);
		sogContactSubcellAction.setSelectedIndex(Routing.getSeaOfGatesContactPlacementAction());
		sogRerunComplexityLimit.setText(Integer.toString(Routing.getSeaOfGatesRerunComplexityLimit()));
		sogForcedProcessorCount.setText(Integer.toString(Routing.getSeaOfGatesForcedProcessorCount()));
		sogParallel.setSelected(Routing.isSeaOfGatesUseParallelRoutes());
		sogParallel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { sogParallelChanged(); }
		});
		sogParallelDij.setSelected(Routing.isSeaOfGatesUseParallelFromToRoutes());
		sogParallelDij.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { sogParallelChanged(); }
		});

		routTechnology.setSelectedItem(Technology.getCurrent().getTechName());
		routOverrideArc.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { overrideChanged(); }
		});
		String prefArcName = Routing.getPreferredRoutingArc();
		initRoutDefArc = null;
		if (prefArcName.length() > 0)
		{
			initRoutDefArc = ArcProto.findArcProto(prefArcName, Technology.getCurrent());
			routOverrideArc.setSelected(true);
		} else
		{
			routOverrideArc.setSelected(false);
		}
		overrideChanged();
		if (initRoutDefArc != null)
		{
			routTechnology.setSelectedItem(initRoutDefArc.getTechnology().getTechName());
			routDefaultArc.setSelectedItem(initRoutDefArc.getName());
		}

		// auto routing section
		routAutoCreateExports.setSelected(Routing.isAutoStitchCreateExports());

		// mimic routing section
		routMimicPortsMustMatch.setSelected(Routing.isMimicStitchMatchPorts());
		routMimicPortsWidthMustMatch.setSelected(Routing.isMimicStitchMatchPortWidth());
		routMimicNumArcsMustMatch.setSelected(Routing.isMimicStitchMatchNumArcs());
		routMimicNodeSizesMustMatch.setSelected(Routing.isMimicStitchMatchNodeSize());
		routMimicNodeTypesMustMatch.setSelected(Routing.isMimicStitchMatchNodeType());
		routMimicNoOtherArcs.setSelected(Routing.isMimicStitchNoOtherArcsSameDir());
		routMimicOnlyNewTopology.setSelected(Routing.isMimicStitchOnlyNewTopology());
		routMimicInteractive.setSelected(Routing.isMimicStitchInteractive());
        routMimicKeepPins.setSelected(Routing.isMimicStitchPinsKept());

        // experimental routing section
        RoutingFrame[] algorithms = RoutingFrame.getRoutingAlgorithms();
        for(RoutingFrame rf : algorithms)
        	routExperimental.addItem(rf.getAlgorithmName());
        routExperimental.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { getAlgorithmParameters(); setupForAlgorithm(); }
		});
        routingOptions = new RoutingFrame.RoutingPrefs(false);

		// show parameters for current algorithm
		setupForAlgorithm();
	}

	private void setupForAlgorithm()
	{
		for(int i=experimental.getComponentCount()-1; i >= 2; i--)
			experimental.remove(i);
		experimental.updateUI();
		currentParameters = new HashMap<RoutingParameter,JComponent>();

		String algName = (String)routExperimental.getSelectedItem();
        RoutingFrame[] algorithms = RoutingFrame.getRoutingAlgorithms();
        RoutingFrame whichOne = null;
		for(RoutingFrame an : algorithms)
		{
			if (algName.equals(an.getAlgorithmName())) { whichOne = an;   break; }
		}
		if (whichOne != null)
		{
			List<RoutingParameter> allParams = whichOne.getParameters();
			if (allParams != null)
			{
				// load the parameters
				int yPos = 1;
				JSeparator sep = new JSeparator();
				GridBagConstraints gbc = new GridBagConstraints();
				gbc.gridx = 0;   gbc.gridy = yPos;
				gbc.gridwidth = 2;
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.insets = new Insets(4, 10, 4, 10);
				experimental.add(sep, gbc);
				yPos++;

				for(RoutingParameter pp : allParams)
				{
                    Object value = routingOptions.getParameter(pp);
					if (pp.getType() == RoutingParameter.TYPEBOOLEAN)
					{
						JCheckBox cb = new JCheckBox(pp.getName());
						cb.setSelected(((Boolean)value).booleanValue());
						gbc = new GridBagConstraints();
						gbc.gridx = 0;   gbc.gridy = yPos;
						gbc.anchor = GridBagConstraints.WEST;
						gbc.gridwidth = 2;
						gbc.insets = new Insets(1, 4, 1, 4);
						experimental.add(cb, gbc);
						currentParameters.put(pp, cb);
					} else
					{
						JLabel lab = new JLabel(pp.getName());
						gbc = new GridBagConstraints();
						gbc.gridx = 0;   gbc.gridy = yPos;
						gbc.anchor = GridBagConstraints.WEST;
						gbc.insets = new Insets(1, 4, 1, 4);
						experimental.add(lab, gbc);

						String init = null;
						if (pp.getType() == RoutingParameter.TYPEINTEGER)
						{
							init = value.toString();
						} else if (pp.getType() == RoutingParameter.TYPESTRING)
						{
							init = value.toString();
						} else if (pp.getType() == RoutingParameter.TYPEDOUBLE)
						{
							init = TextUtils.formatDouble(((Double)value).doubleValue());
						}
						JTextField txt = new JTextField(init);
						txt.setColumns(init.length()*2);
						gbc = new GridBagConstraints();
						gbc.gridx = 1;   gbc.gridy = yPos;
						gbc.anchor = GridBagConstraints.WEST;
						gbc.insets = new Insets(1, 4, 1, 4);
						experimental.add(txt, gbc);
						currentParameters.put(pp, txt);
					}

					yPos++;
				}
			}
		}
		parent.pack();
	}

	private void overrideChanged()
	{
		boolean enableRest = routOverrideArc.isSelected();

		// set other fields
		routTechnology.setEnabled(enableRest);
		routTechLabel.setEnabled(enableRest);
		routDefaultArc.setEnabled(enableRest);
		routArcLabel.setEnabled(enableRest);
	}

	/**
	 * Method called when either of the parallel sea-of-gate checkboxes changes.
	 * Checks this against the number of processors and warns if not possible.
	 */
	private void sogParallelChanged()
	{
		int numberOfProcessors = Runtime.getRuntime().availableProcessors();
		boolean twoPerRoute = sogParallelDij.isSelected();
		boolean parallelRouting = sogParallel.isSelected();
		if ((twoPerRoute || parallelRouting) && numberOfProcessors == 1 && !oneProcessorWarning)
		{
			oneProcessorWarning = true;
			Job.getUserInterface().showInformationMessage("This computer has only one processor and cannot do parallel routing.",
				"Not Enough Processors");
			return;
		}
		if ((twoPerRoute && parallelRouting) && numberOfProcessors == 2 && !twoProcessorWarning)
		{
			twoProcessorWarning = true;
			Job.getUserInterface().showInformationMessage(
				"This computer has only two processors and so it can do either\n"+
				"   'two processors per route' or 'multiple routes in parallel' but not both.\n"+
				"It is recommended that two-processor machines use 'two processors per route'.",
				"Not Enough Processors");
			return;
		}
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Routing tab.
	 */
	public void term()
	{
		boolean curMimic = routMimicStitcher.isSelected();
		if (curMimic != Routing.isMimicStitchOn()) {
			Routing.setMimicStitchOn(curMimic);
            MenuCommands.menuBar().updateAllButtons();
        }
		boolean curAuto = routAutoStitcher.isSelected();
		if (curAuto != Routing.isAutoStitchOn()) {
			Routing.setAutoStitchOn(curAuto);
            MenuCommands.menuBar().updateAllButtons();
        }

		double curSOGMaxWid = TextUtils.atofDistance(sogMaxArcWidth.getText());
		if (curSOGMaxWid != Routing.getSeaOfGatesMaxWidth())
			Routing.setSeaOfGatesMaxWidth(curSOGMaxWid);
		int curSoGMInt = TextUtils.atoi(sogComplexityLimit.getText());
		if (curSoGMInt != Routing.getSeaOfGatesComplexityLimit())
			Routing.setSeaOfGatesComplexityLimit(curSoGMInt);

		curSoGMInt = TextUtils.atoi(sogMaxDistance.getText());
		if (curSoGMInt != Routing.getSeaOfGatesMaxDistance())
			Routing.setSeaOfGatesMaxDistance(curSoGMInt);
		
		boolean useGR = sogGlobalRouting.isSelected();
		if (useGR != Routing.isSeaOfGatesUseGlobalRouting())
			Routing.setSeaOfGatesUseGlobalRouting(useGR);

		boolean useSR = sogSpineRouting.isSelected();
		if (useSR != Routing.isSeaOfGatesEnableSpineRouting())
			Routing.setSeaOfGatesEnableSpineRouting(useSR);

		boolean reRun = sogRerunFailedRoutes.isSelected();
		if (reRun != Routing.isSeaOfGatesRerunFailedRoutes())
			Routing.setSeaOfGatesRerunFailedRoutes(reRun);

		boolean runConnected = sogRunOnConnectedRoutes.isSelected();
		if (runConnected != Routing.isSeaOfGatesRunOnConnectedRoutes())
			Routing.setSeaOfGatesRunOnConnectedRoutes(runConnected);

		int useSubcellsForContacts = sogContactSubcellAction.getSelectedIndex();
		if (useSubcellsForContacts != Routing.getSeaOfGatesContactPlacementAction())
			Routing.setSeaOfGatesContactPlacementAction(useSubcellsForContacts);

		curSoGMInt = TextUtils.atoi(sogRerunComplexityLimit.getText());
		if (curSoGMInt != Routing.getSeaOfGatesRerunComplexityLimit())
			Routing.setSeaOfGatesRerunComplexityLimit(curSoGMInt);

		curSoGMInt = TextUtils.atoi(sogForcedProcessorCount.getText());
		if (curSoGMInt != Routing.getSeaOfGatesForcedProcessorCount())
			Routing.setSeaOfGatesForcedProcessorCount(curSoGMInt);

		boolean curSOGParallel = sogParallel.isSelected();
		if (curSOGParallel != Routing.isSeaOfGatesUseParallelRoutes())
			Routing.setSeaOfGatesUseParallelRoutes(curSOGParallel);
		curSOGParallel = sogParallelDij.isSelected();
		if (curSOGParallel != Routing.isSeaOfGatesUseParallelFromToRoutes())
			Routing.setSeaOfGatesUseParallelFromToRoutes(curSOGParallel);

		ArcProto ap = null;
		if (routOverrideArc.isSelected())
		{
			String techName = (String)routTechnology.getSelectedItem();
			Technology tech = Technology.findTechnology(techName);
			if (tech != null)
			{
				String curArcName = (String)routDefaultArc.getSelectedItem();
				ap = tech.findArcProto(curArcName);
			}
		}
		if (ap != initRoutDefArc)
		{
			String newArcName = "";
			if (ap != null) newArcName = ap.getTechnology().getTechName() + ":" + ap.getName();
			Routing.setPreferredRoutingArc(newArcName);
		}

		boolean cur = routMimicPortsMustMatch.isSelected();
		if (cur != Routing.isMimicStitchMatchPorts())
			Routing.setMimicStitchMatchPorts(cur);

		cur = routMimicPortsWidthMustMatch.isSelected();
		if (cur != Routing.isMimicStitchMatchPortWidth())
			Routing.setMimicStitchMatchPortWidth(cur);

		cur = routMimicNumArcsMustMatch.isSelected();
		if (cur != Routing.isMimicStitchMatchNumArcs())
			Routing.setMimicStitchMatchNumArcs(cur);

		cur = routMimicNodeSizesMustMatch.isSelected();
		if (cur != Routing.isMimicStitchMatchNodeSize())
			Routing.setMimicStitchMatchNodeSize(cur);

		cur = routMimicNodeTypesMustMatch.isSelected();
		if (cur != Routing.isMimicStitchMatchNodeType())
			Routing.setMimicStitchMatchNodeType(cur);

		cur = routMimicNoOtherArcs.isSelected();
		if (cur != Routing.isMimicStitchNoOtherArcsSameDir())
			Routing.setMimicStitchNoOtherArcsSameDir(cur);

		cur = routMimicOnlyNewTopology.isSelected();
		if (cur != Routing.isMimicStitchOnlyNewTopology())
			Routing.setMimicStitchOnlyNewTopology(cur);

		cur = routMimicInteractive.isSelected();
		if (cur != Routing.isMimicStitchInteractive())
			Routing.setMimicStitchInteractive(cur);

		cur = routMimicKeepPins.isSelected();
		if (cur != Routing.isMimicStitchPinsKept())
			Routing.setMimicStitchPinsKept(cur);

		cur = routAutoCreateExports.isSelected();
		if (cur != Routing.isAutoStitchCreateExports())
			Routing.setAutoStitchCreateExports(cur);

		// load values into temp parameters
		getAlgorithmParameters();

		// set parameters in experimental algorithms
        putPrefs(routingOptions);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (Routing.isFactoryMimicStitchOn() != Routing.isMimicStitchOn())
			Routing.setMimicStitchOn(Routing.isFactoryMimicStitchOn());
		if (Routing.isFactoryAutoStitchOn() != Routing.isAutoStitchOn())
			Routing.setAutoStitchOn(Routing.isFactoryAutoStitchOn());
		if (!Routing.getFactoryPreferredRoutingArc().equals(Routing.getPreferredRoutingArc()))
			Routing.setPreferredRoutingArc(Routing.getFactoryPreferredRoutingArc());

		if (Routing.getFactorySeaOfGatesMaxWidth() != Routing.getSeaOfGatesMaxWidth())
			Routing.setSeaOfGatesMaxWidth(Routing.getFactorySeaOfGatesMaxWidth());
		if (Routing.getFactorySeaOfGatesComplexityLimit() != Routing.getSeaOfGatesComplexityLimit())
			Routing.setSeaOfGatesComplexityLimit(Routing.getFactorySeaOfGatesComplexityLimit());
		if (Routing.getFactorySeaOfGatesMaxDistance() != Routing.getSeaOfGatesMaxDistance())
			Routing.setSeaOfGatesMaxDistance(Routing.getFactorySeaOfGatesMaxDistance());
		if (Routing.isFactorySeaOfGatesUseGlobalRouting() != Routing.isSeaOfGatesUseGlobalRouting())
			Routing.setSeaOfGatesUseGlobalRouting(Routing.isFactorySeaOfGatesUseGlobalRouting());
		if (Routing.isFactorySeaOfGatesEnableSpineRouting() != Routing.isSeaOfGatesEnableSpineRouting())
			Routing.setSeaOfGatesEnableSpineRouting(Routing.isFactorySeaOfGatesEnableSpineRouting());
		if (Routing.isFactorySeaOfGatesRerunFailedRoutes() != Routing.isSeaOfGatesRerunFailedRoutes())
			Routing.setSeaOfGatesRerunFailedRoutes(Routing.isFactorySeaOfGatesRerunFailedRoutes());
		if (Routing.isFactorySeaOfGatesRunOnConnectedRoutes() != Routing.isSeaOfGatesRunOnConnectedRoutes())
			Routing.setSeaOfGatesRunOnConnectedRoutes(Routing.isFactorySeaOfGatesRunOnConnectedRoutes());
		if (Routing.getFactorySeaOfGatesContactPlacementAction() != Routing.getSeaOfGatesContactPlacementAction())
			Routing.setSeaOfGatesContactPlacementAction(Routing.getFactorySeaOfGatesContactPlacementAction());		
		if (Routing.getFactorySeaOfGatesRerunComplexityLimit() != Routing.getSeaOfGatesRerunComplexityLimit())
			Routing.setSeaOfGatesRerunComplexityLimit(Routing.getFactorySeaOfGatesRerunComplexityLimit());

		if (Routing.isFactorySeaOfGatesUseParallelRoutes() != Routing.isSeaOfGatesUseParallelRoutes())
			Routing.setSeaOfGatesUseParallelRoutes(Routing.isFactorySeaOfGatesUseParallelRoutes());
		if (Routing.isFactorySeaOfGatesUseParallelFromToRoutes() != Routing.isSeaOfGatesUseParallelFromToRoutes())
			Routing.setSeaOfGatesUseParallelFromToRoutes(Routing.isFactorySeaOfGatesUseParallelFromToRoutes());
		if (Routing.getFactorySeaOfGatesForcedProcessorCount() != Routing.getSeaOfGatesForcedProcessorCount())
			Routing.setSeaOfGatesForcedProcessorCount(Routing.getFactorySeaOfGatesForcedProcessorCount());

		if (Routing.isFactoryMimicStitchInteractive() != Routing.isMimicStitchInteractive())
			Routing.setMimicStitchInteractive(Routing.isFactoryMimicStitchInteractive());
		if (Routing.isFactoryMimicStitchPinsKept() != Routing.isMimicStitchPinsKept())
			Routing.setMimicStitchPinsKept(Routing.isFactoryMimicStitchPinsKept());
		if (Routing.isFactoryMimicStitchMatchPorts() != Routing.isMimicStitchMatchPorts())
			Routing.setMimicStitchMatchPorts(Routing.isFactoryMimicStitchMatchPorts());
		if (Routing.isFactoryMimicStitchMatchPortWidth() != Routing.isMimicStitchMatchPortWidth())
			Routing.setMimicStitchMatchPortWidth(Routing.isFactoryMimicStitchMatchPortWidth());
		if (Routing.isFactoryMimicStitchMatchNumArcs() != Routing.isMimicStitchMatchNumArcs())
			Routing.setMimicStitchMatchNumArcs(Routing.isFactoryMimicStitchMatchNumArcs());
		if (Routing.isFactoryMimicStitchMatchNodeSize() != Routing.isMimicStitchMatchNodeSize())
			Routing.setMimicStitchMatchNodeSize(Routing.isFactoryMimicStitchMatchNodeSize());
		if (Routing.isFactoryMimicStitchMatchNodeType() != Routing.isMimicStitchMatchNodeType())
			Routing.setMimicStitchMatchNodeType(Routing.isFactoryMimicStitchMatchNodeType());
		if (Routing.isFactoryMimicStitchNoOtherArcsSameDir() != Routing.isMimicStitchNoOtherArcsSameDir())
			Routing.setMimicStitchNoOtherArcsSameDir(Routing.isFactoryMimicStitchNoOtherArcsSameDir());
		if (Routing.isFactoryMimicStitchOnlyNewTopology() != Routing.isMimicStitchOnlyNewTopology())
			Routing.setMimicStitchOnlyNewTopology(Routing.isFactoryMimicStitchOnlyNewTopology());

		if (Routing.isFactoryAutoStitchCreateExports() != Routing.isAutoStitchCreateExports())
			Routing.setAutoStitchCreateExports(Routing.isFactoryAutoStitchCreateExports());

		// reset parameters in experimental algorithms
        putPrefs(new RoutingFrame.RoutingPrefs(true));
	}

	private void getAlgorithmParameters()
	{
		if (currentParameters == null) return;

		// get the parameters
		for(RoutingParameter pp : currentParameters.keySet())
		{
			JComponent txt = currentParameters.get(pp);
            Object value;
            switch (pp.getType()) {
                case RoutingParameter.TYPEINTEGER:
                    value = Integer.valueOf(TextUtils.atoi(((JTextField)txt).getText()));
                    break;
                case RoutingParameter.TYPESTRING:
                    value = String.valueOf(((JTextField)txt).getText());
                    break;
                case RoutingParameter.TYPEDOUBLE:
                    value = Double.valueOf(TextUtils.atof(((JTextField)txt).getText()));
                    break;
                case RoutingParameter.TYPEBOOLEAN:
                    value = Boolean.valueOf(((JCheckBox)txt).isSelected());
                    break;
                default:
                    throw new AssertionError();
            }
            routingOptions = routingOptions.withParameter(pp, value);
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        routStitcher = new javax.swing.ButtonGroup();
        routing = new javax.swing.JPanel();
        left = new javax.swing.JPanel();
        auto = new javax.swing.JPanel();
        routAutoCreateExports = new javax.swing.JCheckBox();
        mimic = new javax.swing.JPanel();
        jLabel70 = new javax.swing.JLabel();
        routMimicPortsMustMatch = new javax.swing.JCheckBox();
        routMimicInteractive = new javax.swing.JCheckBox();
        routMimicNumArcsMustMatch = new javax.swing.JCheckBox();
        routMimicNodeSizesMustMatch = new javax.swing.JCheckBox();
        routMimicNodeTypesMustMatch = new javax.swing.JCheckBox();
        routMimicNoOtherArcs = new javax.swing.JCheckBox();
        routMimicPortsWidthMustMatch = new javax.swing.JCheckBox();
        routMimicKeepPins = new javax.swing.JCheckBox();
        routMimicOnlyNewTopology = new javax.swing.JCheckBox();
        all = new javax.swing.JPanel();
        routTechLabel = new javax.swing.JLabel();
        routDefaultArc = new javax.swing.JComboBox();
        routNoStitcher = new javax.swing.JRadioButton();
        routAutoStitcher = new javax.swing.JRadioButton();
        routMimicStitcher = new javax.swing.JRadioButton();
        routTechnology = new javax.swing.JComboBox();
        routArcLabel = new javax.swing.JLabel();
        routOverrideArc = new javax.swing.JCheckBox();
        right = new javax.swing.JPanel();
        experimental = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        routExperimental = new javax.swing.JComboBox();
        seaOfGates = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        sogMaxArcWidth = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        sogComplexityLimit = new javax.swing.JTextField();
        sogParallel = new javax.swing.JCheckBox();
        sogParallelDij = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        sogForcedProcessorCount = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        sogGlobalRouting = new javax.swing.JCheckBox();
        sogRerunFailedRoutes = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        sogRerunComplexityLimit = new javax.swing.JTextField();
        sogSpineRouting = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        sogMaxDistance = new javax.swing.JTextField();
        sogRunOnConnectedRoutes = new javax.swing.JCheckBox();
        sogContactSubcellAction = new javax.swing.JComboBox();

        setTitle("Tool Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        routing.setLayout(new java.awt.GridBagLayout());

        left.setLayout(new java.awt.GridBagLayout());

        auto.setBorder(javax.swing.BorderFactory.createTitledBorder("Auto Stitcher"));
        auto.setLayout(new java.awt.GridBagLayout());

        routAutoCreateExports.setText("Create exports where necessary");
        routAutoCreateExports.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 4);
        auto.add(routAutoCreateExports, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        left.add(auto, gridBagConstraints);

        mimic.setBorder(javax.swing.BorderFactory.createTitledBorder("Mimic Stitcher"));
        mimic.setLayout(new java.awt.GridBagLayout());

        jLabel70.setText("Restrictions (when non-interactive):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 1, 4);
        mimic.add(jLabel70, gridBagConstraints);

        routMimicPortsMustMatch.setText("Ports must match");
        routMimicPortsMustMatch.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicPortsMustMatch, gridBagConstraints);

        routMimicInteractive.setText("Interactive mimicking");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        mimic.add(routMimicInteractive, gridBagConstraints);

        routMimicNumArcsMustMatch.setText("Number of existing arcs must match");
        routMimicNumArcsMustMatch.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicNumArcsMustMatch, gridBagConstraints);

        routMimicNodeSizesMustMatch.setText("Node sizes must match");
        routMimicNodeSizesMustMatch.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicNodeSizesMustMatch, gridBagConstraints);

        routMimicNodeTypesMustMatch.setText("Node types must match");
        routMimicNodeTypesMustMatch.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicNodeTypesMustMatch, gridBagConstraints);

        routMimicNoOtherArcs.setText("No other arcs in the same direction");
        routMimicNoOtherArcs.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicNoOtherArcs, gridBagConstraints);

        routMimicPortsWidthMustMatch.setText("Bus ports must have same width");
        routMimicPortsWidthMustMatch.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        mimic.add(routMimicPortsWidthMustMatch, gridBagConstraints);

        routMimicKeepPins.setText("Keep pins");
        routMimicKeepPins.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        mimic.add(routMimicKeepPins, gridBagConstraints);

        routMimicOnlyNewTopology.setText("Ignore if already connected elsewhere");
        routMimicOnlyNewTopology.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 4, 4);
        mimic.add(routMimicOnlyNewTopology, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        left.add(mimic, gridBagConstraints);

        all.setBorder(javax.swing.BorderFactory.createTitledBorder("Stitching Routers"));
        all.setLayout(new java.awt.GridBagLayout());

        routTechLabel.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        all.add(routTechLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        all.add(routDefaultArc, gridBagConstraints);

        routStitcher.add(routNoStitcher);
        routNoStitcher.setText("No stitcher running");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        all.add(routNoStitcher, gridBagConstraints);

        routStitcher.add(routAutoStitcher);
        routAutoStitcher.setText("Auto-stitcher running");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        all.add(routAutoStitcher, gridBagConstraints);

        routStitcher.add(routMimicStitcher);
        routMimicStitcher.setText("Mimic-stitcher running");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 4);
        all.add(routMimicStitcher, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        all.add(routTechnology, gridBagConstraints);

        routArcLabel.setText("Arc:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 4, 4);
        all.add(routArcLabel, gridBagConstraints);

        routOverrideArc.setText("Use this arc in stitching routers:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 1, 4);
        all.add(routOverrideArc, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 0.5;
        left.add(all, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        routing.add(left, gridBagConstraints);

        right.setLayout(new java.awt.GridBagLayout());

        experimental.setBorder(javax.swing.BorderFactory.createTitledBorder("Experimental Routers"));
        experimental.setLayout(new java.awt.GridBagLayout());

        jLabel5.setText("Routing algorithm:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        experimental.add(jLabel5, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        experimental.add(routExperimental, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        right.add(experimental, gridBagConstraints);

        seaOfGates.setBorder(javax.swing.BorderFactory.createTitledBorder("Sea-of-Gates Router"));
        seaOfGates.setLayout(new java.awt.GridBagLayout());

        jLabel2.setText("Maximum arc width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(jLabel2, gridBagConstraints);

        sogMaxArcWidth.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(sogMaxArcWidth, gridBagConstraints);

        jLabel3.setText("Search complexity limit:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(jLabel3, gridBagConstraints);

        sogComplexityLimit.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(sogComplexityLimit, gridBagConstraints);

        sogParallel.setText("Do multiple routes in parallel");
        sogParallel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        seaOfGates.add(sogParallel, gridBagConstraints);

        sogParallelDij.setText("Use two processors per route");
        sogParallelDij.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        seaOfGates.add(sogParallelDij, gridBagConstraints);

        jLabel4.setText("If there are multiple processors available:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 1, 4);
        seaOfGates.add(jLabel4, gridBagConstraints);

        sogForcedProcessorCount.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(sogForcedProcessorCount, gridBagConstraints);

        jLabel6.setText("Forced processor count:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(jLabel6, gridBagConstraints);

        sogGlobalRouting.setText("Do Global Routing");
        sogGlobalRouting.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        seaOfGates.add(sogGlobalRouting, gridBagConstraints);

        sogRerunFailedRoutes.setText("Rerun routing with failed routes");
        sogRerunFailedRoutes.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        seaOfGates.add(sogRerunFailedRoutes, gridBagConstraints);

        jLabel7.setText("Rerun complexity limit:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 4, 4);
        seaOfGates.add(jLabel7, gridBagConstraints);

        sogRerunComplexityLimit.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 2, 4);
        seaOfGates.add(sogRerunComplexityLimit, gridBagConstraints);

        sogSpineRouting.setText("Do Spine Routing");
        sogSpineRouting.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        seaOfGates.add(sogSpineRouting, gridBagConstraints);

        jLabel1.setText("Maximum search factor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(jLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        seaOfGates.add(sogMaxDistance, gridBagConstraints);

        sogRunOnConnectedRoutes.setText("Run even on connected routes");
        sogRunOnConnectedRoutes.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        seaOfGates.add(sogRunOnConnectedRoutes, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        seaOfGates.add(sogContactSubcellAction, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        right.add(seaOfGates, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        routing.add(right, gridBagConstraints);

        getContentPane().add(routing, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel all;
    private javax.swing.JPanel auto;
    private javax.swing.JPanel experimental;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel70;
    private javax.swing.JPanel left;
    private javax.swing.JPanel mimic;
    private javax.swing.JPanel right;
    private javax.swing.JLabel routArcLabel;
    private javax.swing.JCheckBox routAutoCreateExports;
    private javax.swing.JRadioButton routAutoStitcher;
    private javax.swing.JComboBox routDefaultArc;
    private javax.swing.JComboBox routExperimental;
    private javax.swing.JCheckBox routMimicInteractive;
    private javax.swing.JCheckBox routMimicKeepPins;
    private javax.swing.JCheckBox routMimicNoOtherArcs;
    private javax.swing.JCheckBox routMimicNodeSizesMustMatch;
    private javax.swing.JCheckBox routMimicNodeTypesMustMatch;
    private javax.swing.JCheckBox routMimicNumArcsMustMatch;
    private javax.swing.JCheckBox routMimicOnlyNewTopology;
    private javax.swing.JCheckBox routMimicPortsMustMatch;
    private javax.swing.JCheckBox routMimicPortsWidthMustMatch;
    private javax.swing.JRadioButton routMimicStitcher;
    private javax.swing.JRadioButton routNoStitcher;
    private javax.swing.JCheckBox routOverrideArc;
    private javax.swing.ButtonGroup routStitcher;
    private javax.swing.JLabel routTechLabel;
    private javax.swing.JComboBox routTechnology;
    private javax.swing.JPanel routing;
    private javax.swing.JPanel seaOfGates;
    private javax.swing.JTextField sogComplexityLimit;
    private javax.swing.JComboBox sogContactSubcellAction;
    private javax.swing.JTextField sogForcedProcessorCount;
    private javax.swing.JCheckBox sogGlobalRouting;
    private javax.swing.JTextField sogMaxArcWidth;
    private javax.swing.JTextField sogMaxDistance;
    private javax.swing.JCheckBox sogParallel;
    private javax.swing.JCheckBox sogParallelDij;
    private javax.swing.JTextField sogRerunComplexityLimit;
    private javax.swing.JCheckBox sogRerunFailedRoutes;
    private javax.swing.JCheckBox sogRunOnConnectedRoutes;
    private javax.swing.JCheckBox sogSpineRouting;
    // End of variables declaration//GEN-END:variables

}
