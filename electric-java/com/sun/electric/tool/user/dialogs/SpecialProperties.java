/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SpecialProperties.java
 *      2572968, exp 6/5/5
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

package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.TextUtils.UnitScale;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Class to handle special dialogs for specific nodes.
 */
public class SpecialProperties
{
	/**
	 * Method to handle special dialogs that are associated with double-clicking on a node.
	 * @param wnd the EditWindow in which the click occurred.
	 * @param ni the NodeInst that was double-clicked.
	 * @return 1 if the double-click has been handled.
	 * 0 if text edit in-place should be done.
	 * -1 indicates that a general "node properties" dialog should be shown.
	 */
	public static int doubleClickOnNode(EditWindow wnd, NodeInst ni)
	{
		// if double-clicked on a schematic resistor, show special dialog
		if (ni.getProto() == Schematics.tech().resistorNode)
		{
			NodePropertiesDialog npd = new NodePropertiesDialog(wnd, ni, "Resistance",
				Schematics.SCHEM_RESISTANCE, TextDescriptor.Unit.RESISTANCE);
			if (npd.wantMore()) return -1;
			return 1;
		}

		// if double-clicked on a schematic capacitor, show special dialog
		if (ni.getProto() == Schematics.tech().capacitorNode)
		{
			NodePropertiesDialog npd = new NodePropertiesDialog(wnd, ni, "Capacitance",
				Schematics.SCHEM_CAPACITANCE, TextDescriptor.Unit.CAPACITANCE);
			if (npd.wantMore()) return -1;
			return 1;
		}

		// if double-clicked on a schematic inductor, show special dialog
		if (ni.getProto() == Schematics.tech().inductorNode)
		{
			NodePropertiesDialog npd = new NodePropertiesDialog(wnd, ni, "Inductance",
				Schematics.SCHEM_INDUCTANCE, TextDescriptor.Unit.INDUCTANCE);
			if (npd.wantMore()) return -1;
			return 1;
		}

		// if double-clicked on a schematic diode, show special dialog
		if (ni.getProto() == Schematics.tech().diodeNode)
		{
			NodePropertiesDialog npd = new NodePropertiesDialog(wnd, ni, "Diode area", Schematics.SCHEM_DIODE, null);
			if (npd.wantMore()) return -1;
			return 1;
		}

		// if double-clicked on a schematic transistor, show special dialog
		if (ni.getProto() == Schematics.tech().transistorNode ||
			ni.getProto() == Schematics.tech().transistor4Node)
		{
			PrimitiveNode.Function fun = ni.getFunction();
			if (fun == PrimitiveNode.Function.TRA4NPN || fun == PrimitiveNode.Function.TRA4PNP ||
				fun == PrimitiveNode.Function.TRANPN || fun == PrimitiveNode.Function.TRAPNP)
			{
				// show just the area value for NPN and PNP transistors
				NodePropertiesDialog npd = new NodePropertiesDialog(wnd, ni, "Transistor area", Schematics.ATTR_AREA, null);
				if (npd.wantMore()) return -1;
				return 1;
			}

			// show length and width for other transistors
			TransistorPropertiesDialog tpd = new TransistorPropertiesDialog(wnd, ni);
			if (tpd.wantMore()) return -1;
			return 1;
		}

		// if double-clicked on a schematic global, show special dialog
		if (ni.getProto() == Schematics.tech().globalNode)
		{
			GlobalPropertiesDialog gpd = new GlobalPropertiesDialog(wnd, ni);
			if (gpd.wantMore()) return -1;
			return 1;
		}
		return 0;
	}

	/**
	 * This class displays a dialog for handling special node properties.
	 */
	private static class NodePropertiesDialog extends EDialog
	{
		private JTextField value;
		private JComboBox combo;
		private boolean cancelHit;
		private boolean moreHit;

		/** Creates new special node properties object */
		private NodePropertiesDialog(EditWindow wnd, NodeInst ni, String title, Variable.Key key,
			TextDescriptor.Unit units)
		{
			super(null, true);

			TextUtils.UnitScale [] scales = TextUtils.UnitScale.getUnitScales();
			String [] theScales = new String[scales.length];
			for(int i=0; i<scales.length; i++) theScales[i] = scales[i].toString();

			double num = 0;
			TextUtils.UnitScale scale = TextUtils.UnitScale.NONE;
			Variable var = ni.getVar(key);
			if (var != null)
			{
				String val = var.getObject().toString();
				scale = UnitScale.findUnitScaleFromPostfix(val);
				String postFix = scale.getPostFix();
				if (postFix.length() > 0) val = val.substring(0, val.length()-postFix.length());
				num = TextUtils.atof(val);
			}
			initComponents(wnd, ni, title, TextUtils.formatDouble(num), theScales, scale.getName(), units);
		    cancelHit = moreHit = false;
			setVisible(true);
			toFront();

			// all done: see if value should be updated
			if (!cancelHit)
			{
				String newValue = value.getText();
				if (units != null)
				{
					int newScale = combo.getSelectedIndex();
					newValue += scales[newScale].getPostFix();
				}
				new ModifyNodeProperties(ni, key, newValue);
			}
		}

		protected void escapePressed() { exit(false); }

		private boolean wantMore() { return moreHit; }

		private void exit(boolean goodButton)
		{
			cancelHit = !goodButton;
			setVisible(false);
			dispose();
		}

		private void moreButton()
		{
			moreHit = true;
			exit(true);
		}

		private void initComponents(EditWindow wnd, NodeInst ni, String title, String initialValue,
			String [] theScales, String initialScale, TextDescriptor.Unit unit)
		{
			getContentPane().setLayout(new GridBagLayout());

			setTitle(title);
			setName("");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { exit(false); }
			});

			// special information
			value = new JTextField(initialValue);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			if (unit == null) gbc.gridwidth = 4; else
				gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = .5;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(value, gbc);
		    EDialog.makeTextFieldSelectAllOnTab(value);
			value.selectAll();

			if (unit != null)
			{
				combo = new JComboBox();
				int selected = 0;
				for(int k=0; k<theScales.length; k++)
				{
					String sca = theScales[k];
					if (sca.equalsIgnoreCase(initialScale)) selected = k;
					if (sca.length() == 0) combo.addItem(unit.getName()); else
						combo.addItem(sca + "-" + unit.getName().toLowerCase());
				}
				combo.setSelectedIndex(selected);
				gbc = new GridBagConstraints();
				gbc.gridx = 2;   gbc.gridy = 0;
				gbc.gridwidth = 2;
				gbc.anchor = GridBagConstraints.CENTER;
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.weightx = .5;
				gbc.insets = new Insets(4, 4, 4, 4);
				getContentPane().add(combo, gbc);
			}

			// OK, More, and Cancel
			JButton cancel = new JButton("Cancel");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 1;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(cancel, gbc);
			cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(false); }
			});

			JButton more = new JButton("More...");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 1;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(more, gbc);
			more.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { moreButton(); }
			});

			JButton ok = new JButton("OK");
			gbc = new GridBagConstraints();
			gbc.gridx = 3;   gbc.gridy = 1;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(ok, gbc);
			ok.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(true); }
			});
			getRootPane().setDefaultButton(ok);

			pack();

			// now make the dialog appear over a node
			Point ew = wnd.getLocationOnScreen();
			ScreenPoint locInWnd = wnd.databaseToScreen(ni.getAnchorCenterX(), ni.getAnchorCenterY());
			Point textfield = value.getLocation();
			Dimension textSize = value.getSize();
			setLocation(locInWnd.getIntX()+ew.x-(textfield.x+textSize.width/2),
				locInWnd.getIntY()+ew.y-(textfield.y+textSize.height/2+20));
		}
	}

	/**
	 * Class for saving changes in the special dialog.
	 */
	private static class ModifyNodeProperties extends Job
	{
		private NodeInst ni;
		private Variable.Key key;
		private String newValue, newValueLen;
		private CodeExpression.Code newCode, newCodeLen;
		private int newBits;

		private ModifyNodeProperties(NodeInst ni, Variable.Key key, String newValue)
		{
			super("Change Node Value", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ni = ni;
			this.key = key;
			this.newValue = newValue;
			this.newBits = -1;
			startJob();
		}

		private ModifyNodeProperties(NodeInst ni, Variable.Key key, String newValue, int newBits)
		{
			super("Change Node Value", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ni = ni;
			this.key = key;
			this.newValue = newValue;
			this.newBits = newBits;
			startJob();
		}

		private ModifyNodeProperties(NodeInst ni, String newWid, CodeExpression.Code newWidCode, String newLen, CodeExpression.Code newLenCode)
		{
			super("Change Node Value", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ni = ni;
			this.key = null;
			this.newValue = newWid;
			this.newCode = newWidCode;
			this.newValueLen = newLen;
			this.newCodeLen = newLenCode;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			if (key == null)
			{
				// update length/width on transistor
				Variable oldWid = ni.getVar(Schematics.ATTR_WIDTH);
                TextDescriptor wtd = oldWid != null ? oldWid.getTextDescriptor() : ep.getNodeTextDescriptor();
                Object newV = Variable.withCode(newValue, newCode);
				ni.newVar(Schematics.ATTR_WIDTH, newV, wtd);

				Variable oldLen = ni.getVar(Schematics.ATTR_LENGTH);
                TextDescriptor ltd = oldLen != null ? oldLen.getTextDescriptor() : ep.getNodeTextDescriptor();
                Object newVL = Variable.withCode(newValueLen, newCodeLen);
				ni.newVar(Schematics.ATTR_LENGTH, newVL, ltd);
			} else
			{
				// update single value on a node
				Variable oldVar = ni.getVar(key);
                TextDescriptor td = oldVar != null ? oldVar.getTextDescriptor() : ep.getNodeTextDescriptor();
                CodeExpression.Code newCode = oldVar != null ? oldVar.getCode() : CodeExpression.Code.NONE;
                Object newV = Variable.withCode(newValue, newCode);
				ni.newVar(key, newV, td);
				// set techbits if requested
				if (newBits != -1)
					ni.setTechSpecific(newBits);
			}
			return true;
		}
	}

	/**
	 * This class displays a dialog for handling "global" node properties.
	 */
	private static class GlobalPropertiesDialog extends EDialog
	{
		private JTextField value;
		private JComboBox combo;
		private boolean cancelHit;
		private boolean moreHit;

		/** Creates new special node properties object for "global"s */
		private GlobalPropertiesDialog(EditWindow wnd, NodeInst ni)
		{
			super(null, true);

			String gName = "";
			Variable var = ni.getVar(Schematics.SCHEM_GLOBAL_NAME);
			if (var != null)
				gName = var.getObject().toString();
			initComponents(wnd, ni, "Global Signal", gName);
			cancelHit = moreHit = false;
			setVisible(true);

			// all done: see if value should be updated
			if (!cancelHit)
			{
				String newValue = value.getText();

				PortCharacteristic ch = PortCharacteristic.findCharacteristic((String)combo.getSelectedItem());
				int newBits = ch.getBits();
				new ModifyNodeProperties(ni, Schematics.SCHEM_GLOBAL_NAME, newValue, newBits);
			}
		}

		protected void escapePressed() { exit(false); }

		private boolean wantMore() { return moreHit; }

		private void exit(boolean goodButton)
		{
			cancelHit = !goodButton;
			setVisible(false);
			dispose();
		}

		private void moreButton()
		{
			moreHit = true;
			exit(true);
		}

		private void initComponents(EditWindow wnd, NodeInst ni, String title, String initialValue)
		{
			getContentPane().setLayout(new GridBagLayout());

			setTitle(title);
			setName("");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { exit(false); }
			});

			// global information
			JLabel lab1 = new JLabel("Global signal name:");
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(lab1, gbc);

			value = new JTextField(initialValue);
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 0;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = .5;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(value, gbc);
		    EDialog.makeTextFieldSelectAllOnTab(value);
			value.selectAll();

			JLabel lab2 = new JLabel("Characteristics:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 1;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(lab2, gbc);

			combo = new JComboBox();
			List<PortCharacteristic> characteristics = PortCharacteristic.getOrderedCharacteristics();
			for(PortCharacteristic ch : characteristics)
				combo.addItem(ch.getName());
			PortCharacteristic ch = PortCharacteristic.findCharacteristic(ni.getTechSpecific());
			combo.setSelectedItem(ch.getName());
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 1;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = .5;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(combo, gbc);

			// OK, More, and Cancel
			JButton cancel = new JButton("Cancel");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(cancel, gbc);
			cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(false); }
			});

			JButton more = new JButton("More...");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 2;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(more, gbc);
			more.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { moreButton(); }
			});

			JButton ok = new JButton("OK");
			gbc = new GridBagConstraints();
			gbc.gridx = 3;   gbc.gridy = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(ok, gbc);
			ok.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(true); }
			});
			getRootPane().setDefaultButton(ok);

			pack();

			// now make the dialog appear over a node
			Point ew = wnd.getLocationOnScreen();
			ScreenPoint locInWnd = wnd.databaseToScreen(ni.getAnchorCenterX(), ni.getAnchorCenterY());
			Point textfield = value.getLocation();
			Dimension textSize = value.getSize();
			setLocation(locInWnd.getIntX()+ew.x-(textfield.x+textSize.width/2),
				locInWnd.getIntY()+ew.y-(textfield.y+textSize.height/2+20));
		}
	}

	/**
	 * This class displays a dialog for handling "transistor" node properties.
	 */
	private static class TransistorPropertiesDialog extends EDialog
	{
		private JTextField valueWid, valueLen;
		private JComboBox comboWid, comboLen;
		private boolean cancelHit;
		private boolean moreHit;

		/** Creates new special node properties object for transistors */
		private TransistorPropertiesDialog(EditWindow wnd, NodeInst ni)
		{
			super(null, true);

			String tWid = "";
			CodeExpression.Code cWid = CodeExpression.Code.NONE;
			Variable varWid = ni.getVar(Schematics.ATTR_WIDTH);
			if (varWid != null)
			{
				tWid = varWid.getObject().toString();
				cWid = varWid.getCode();
			}

			String tLen = "";
			CodeExpression.Code cLen = CodeExpression.Code.NONE;
			Variable varLen = ni.getVar(Schematics.ATTR_LENGTH);
			if (varLen != null)
			{
				tLen = varLen.getObject().toString();
				cLen = varLen.getCode();
			}

			initComponents(wnd, ni, "Transistor Properties", tWid, cWid, tLen, cLen);
			cancelHit = moreHit = false;
			setVisible(true);

			// all done: see if value should be updated
			if (!cancelHit)
			{
				String newWid = valueWid.getText();
				String newLen = valueLen.getText();
		        CodeExpression.Code newWidCode = (CodeExpression.Code)comboWid.getSelectedItem();
		        CodeExpression.Code newLenCode = (CodeExpression.Code)comboLen.getSelectedItem();

				new ModifyNodeProperties(ni, newWid, newWidCode, newLen, newLenCode);
			}
		}

		protected void escapePressed() { exit(false); }

		private boolean wantMore() { return moreHit; }

		private void exit(boolean goodButton)
		{
			cancelHit = !goodButton;
			setVisible(false);
			dispose();
		}

		private void moreButton()
		{
			moreHit = true;
			exit(true);
		}

		private void initComponents(EditWindow wnd, NodeInst ni, String title, String initialWid, CodeExpression.Code codeWid,
			String initialLen, CodeExpression.Code codeLen)
		{
			getContentPane().setLayout(new GridBagLayout());

			setTitle(title);
			setName("");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { exit(false); }
			});

			// transistor width information
			JLabel lab1 = new JLabel("Width:");
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(lab1, gbc);

			valueWid = new JTextField(initialWid);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 0;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(valueWid, gbc);
		    EDialog.makeTextFieldSelectAllOnTab(valueWid);
			valueWid.selectAll();

			comboWid = new JComboBox();
			for(Iterator<CodeExpression.Code> it = CodeExpression.Code.getCodes(); it.hasNext(); )
				comboWid.addItem(it.next());
			comboWid.setSelectedItem(codeWid);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 1;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(comboWid, gbc);

			// transistor length information
			JLabel lab2 = new JLabel("Length:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 2;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(lab2, gbc);

			valueLen = new JTextField(initialLen);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 2;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(valueLen, gbc);
		    EDialog.makeTextFieldSelectAllOnTab(valueLen);

			comboLen = new JComboBox();
			for(Iterator<CodeExpression.Code> it = CodeExpression.Code.getCodes(); it.hasNext(); )
				comboLen.addItem(it.next());
			comboLen.setSelectedItem(codeLen);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 3;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(comboLen, gbc);

			// OK, More, and Cancel
			JButton cancel = new JButton("Cancel");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 4;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(cancel, gbc);
			cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(false); }
			});

			JButton more = new JButton("More...");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 4;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(more, gbc);
			more.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { moreButton(); }
			});

			JButton ok = new JButton("OK");
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 4;
			gbc.anchor = GridBagConstraints.CENTER;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(ok, gbc);
			ok.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { exit(true); }
			});
			getRootPane().setDefaultButton(ok);

			pack();

			// now make the dialog appear over a node
			Point ew = wnd.getLocationOnScreen();
			ScreenPoint locInWnd = wnd.databaseToScreen(ni.getAnchorCenterX(), ni.getAnchorCenterY());
			Point textfield = valueWid.getLocation();
			Dimension textSize = valueWid.getSize();
			setLocation(locInWnd.getIntX()+ew.x-(textfield.x+textSize.width/2),
				locInWnd.getIntY()+ew.y-(textfield.y+textSize.height/2+20));
		}
	}

}
