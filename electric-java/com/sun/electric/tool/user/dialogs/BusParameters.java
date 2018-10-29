/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BusParameters.java
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.TextUtils;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Bus Parameters" dialog.
 */
public class BusParameters extends EDialog
{
	/** key for library's bus variables. */	public static final Variable.Key BUS_VARIABLES = Variable.newKey("LIB_Bus_Variables");
	/** key for node's bus template. */		public static final Variable.Key NODE_BUS_TEMPLATE = Variable.newKey("NODE_Bus_Template");
	/** key for arc's bus template. */		public static final Variable.Key ARC_BUS_TEMPLATE = Variable.newKey("ARC_Bus_Template");
	/** key for export's bus template. */	public static final Variable.Key EXPORT_BUS_TEMPLATE = Variable.newKey("EXPORT_Bus_Template");

	private JList parametersList;
	private DefaultListModel parametersModel;
	Map<Library,String[]> libParameters;
	
	public static void showBusParametersDialog()
	{
		BusParameters dialog = new BusParameters(TopLevel.getCurrentJFrame());
		dialog.setVisible(true);
	}

	public static void makeBusParameter()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Highlight h = wnd.getHighlighter().getOneHighlight();
		if (h == null)
		{
			Job.getUserInterface().showErrorMessage("Select a node, arc, or export name first", "Nothing Selected");
			return;
		}
		ElectricObject owner = h.getElectricObject();
		if (owner == null || !(owner instanceof NodeInst || owner instanceof ArcInst || owner instanceof Export))
		{
			Job.getUserInterface().showErrorMessage("Select a node, arc, or export name first", "Incorrect Selection");
			return;
		}
		if (owner instanceof ArcInst)
		{
			if (h.getVarKey() != ArcInst.ARC_NAME)
			{
				Job.getUserInterface().showErrorMessage("Must select the arc's name", "Incorrect Selection");
				return;
			}
		}
		if (owner instanceof NodeInst)
		{
			if (h.getVarKey() != NodeInst.NODE_NAME)
			{
				Job.getUserInterface().showErrorMessage("Must select the node's name", "Incorrect Selection");
				return;
			}
		}
		new AddTemplate(owner);
	}

	/**
	 * Method for internally updating bus parameters.
	 * Can be called from internal electric routines.
	 * Added for ArchGen Plugin - BVE
	 */
	public static void updateBusParametersInt()
	{
		BusParameters foo = new BusParameters(null);
		foo.setVisible(false);
		new UpdateAllParameters(foo.libParameters, true);
	}
	
	/**
	 * Method for internally updating bus parameters on a single cell.
	 * Can be called from internal electric routines.
	 * Added for ArchGen Plugin - BVE
	 */
	public static void updateCellBusParameterInt(Cell cell, Library lib, EditingPreferences ep)
	{
		Map<Library,String[]> libParam = new HashMap<Library,String[]>();
		initializeLibParameters(libParam, null);
		updateCellParameters(cell, lib, libParam, ep);
	}
	
	/**
	 * Method to replace bus parameters in Electric variables.
	 * Can be called from internal Electric routines.
	 * Added for ArchGen Plugin - BVE
	 * @param varString the string with embedded variables.
	 * @return the string with bus parameters substituted.
	 */
	public static String replaceBusParameterInt(String varString)
	{
		// find library with variables in it
		Map<Library,String[]> libParam = new HashMap<Library,String[]>();
		initializeLibParameters(libParam, null);
		return replaceVariableInString(varString, null, libParam);
	}
	
	/**
	 * Creates a template with a suffix appended to the owners's name.
	 * @param owner
	 * @param suffix
	 */
	public static void addTemplateWithString(ElectricObject owner, String suffix)
	{
		new AddTemplate(owner, true, suffix);
	}
	
	/**
	 * Internal method for finding all bus parameters across all libraries.  Refactored to
	 * permit resuse.
	 * Added for ArchGen Plugin - BVE
	 * @param libParam
	 * @param libPopup
	 * @return the Library with parameters.
	 */
	private static Library initializeLibParameters(Map<Library,String[]> libParam, JComboBox libPopup)
	{
		Library bestLib = null;
		int mostParameters = 0;
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library lib = it.next();
			if (lib.isHidden()) continue;
			if(libPopup != null) libPopup.addItem(lib.getName());
			Variable var = lib.getVar(BUS_VARIABLES);
			String [] parameterList = new String[0];
			if (var != null) parameterList = (String [])var.getObject();
			libParam.put(lib, parameterList);
			if (parameterList.length > mostParameters)
			{
				bestLib = lib;
				mostParameters = parameterList.length;
			}
		}
		Library curLib = Library.getCurrent();
		String [] parameterList = libParam.get(curLib);
		if ((parameterList != null && parameterList.length > 0) || bestLib == null) bestLib = curLib;		
		return bestLib;
	}
	
	/** Creates new form Bus Parameters */
	private BusParameters(Frame parent)
	{
		super(parent, true);
		initComponents();

		// build display list for variables
		parametersModel = new DefaultListModel();
		parametersList = new JList(parametersModel);
		parametersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		variablesPane.setViewportView(parametersList);

		value.getDocument().addDocumentListener(new BusParametersDocumentListener(this));

		// find library with variables in it
		libParameters = new HashMap<Library,String[]>();
		// BVE - Refactored code into helper function
		Library bestLib = initializeLibParameters(libParameters, libraryPopup);

		parametersList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { variablesSelected(); }
		});
//		parametersList.addMouseListener(new MouseAdapter()
//		{
//			public void mouseClicked(MouseEvent evt) { variablesSelected(); }
//		});
		libraryPopup.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { libraryChanged(); }
		});
		libraryPopup.setSelectedItem(bestLib.getName());

		pack();
		finishInitialization();
	}

	protected void escapePressed() { doneActionPerformed(null); }

	/**
	 * Method called when the library popup is changed
	 * and the list of bus variables should be updated.
	 */
	private void libraryChanged()
	{
		parametersModel.clear();
		String libName = (String)libraryPopup.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		String [] parameterList = libParameters.get(lib);
		boolean gotSome = false;
		for(int i=0; i<parameterList.length; i++)
		{
			String variable = parameterList[i];
			int equalPos = variable.indexOf('=');
			if (equalPos < 0) continue;
			parametersModel.addElement(variable.substring(0, equalPos));
			gotSome = true;
		}
		if (gotSome)
		{
			parametersList.setSelectedIndex(0);
			variablesSelected();
		}
	}

	/**
	 * Method called when a variable has been selected
	 * and its value should be shown.
	 */
	private void variablesSelected()
	{
		String libName = (String)libraryPopup.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		String [] parameterList = libParameters.get(lib);
		int selectedIndex = parametersList.getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= parameterList.length) return;
		String varSelected = parameterList[selectedIndex];
		int equalPos = varSelected.indexOf('=');
		if (equalPos < 0) return;
		value.setText(varSelected.substring(equalPos+1));
	}

	/**
	 * Method called when a bus variable value has changed.
	 */
	private void valueChanged()
	{
		String libName = (String)libraryPopup.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		String [] parameterList = libParameters.get(lib);
		int selectedIndex = parametersList.getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= parameterList.length) return;
		String parSelected = parameterList[selectedIndex];
		int equalPos = parSelected.indexOf('=');
		if (equalPos < 0) return;
		parameterList[selectedIndex] = parSelected.substring(0, equalPos+1) + value.getText();
		new UpdateLibrary(lib, parameterList);
	}

	/**
	 * Class to handle special changes to changes to the variable value.
	 */
	private static class BusParametersDocumentListener implements DocumentListener
	{
		BusParameters dialog;

		BusParametersDocumentListener(BusParameters dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.valueChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.valueChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.valueChanged(); }
	}

	/**
	 * Class to update variables on a library.
	 */
	private static class UpdateLibrary extends Job
	{
		private Library lib;
		private String [] parameterList;

		private UpdateLibrary(Library lib, String [] parameterList)
		{
			super("Update Bus Parameters", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.lib = lib;
			this.parameterList = parameterList;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			lib.newVar(BUS_VARIABLES, parameterList, getEditingPreferences());
//			lib.setChanged();
			return true;
		}
	}

	/**
	 * Class to update parameters on all libraries.
	 */
	private static class UpdateAllParameters extends Job
	{
		private Map<Library,String[]> libParameters;

		private UpdateAllParameters(Map<Library,String[]> libParameters)
		{
			super("Update All Bus Parameters", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.libParameters = libParameters;
			startJob();
		}
		
		private UpdateAllParameters(Map<Library,String[]> libParameters, boolean doItNow)
		{
			super("Update All Bus Parameters", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.libParameters = libParameters;
			if (doItNow){
			   try {doIt();} catch (Exception e) {e.printStackTrace();}
			}else {
				startJob();
			}
		}

        @Override
		public boolean doIt() throws JobException
		{
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library lib = it.next();
				if (lib.isHidden()) continue;
				for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
				{
					Cell cell = cIt.next();
					// BVE - Old code refactored into helper function
					updateCellParameters(cell, lib, libParameters, getEditingPreferences());
				}
			}
			return true;
		}
	}

	/**
	 * Internal method for updating the bus parameters within a single cell.  Refactored to
	 * permit resuse.
	 * Added for ArchGen Plugin - BVE
	 * @param cell
	 * @param lib
	 * @param libParameters
	 */
	private static void updateCellParameters(Cell cell, Library lib, Map<Library,String[]> libParameters, EditingPreferences ep) {
		for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
		{
			NodeInst ni = nIt.next();
			Variable var = ni.getVar(NODE_BUS_TEMPLATE);
			if (var != null)
			{
				String newVarString = updateVariable(var, lib, libParameters);
				String arcName = ni.getName();
				if (!arcName.equalsIgnoreCase(newVarString))
					ni.setName(newVarString);
			}
		}
		for(Iterator<ArcInst> aIt = cell.getArcs(); aIt.hasNext(); )
		{
			ArcInst ai = aIt.next();
			Variable var = ai.getVar(ARC_BUS_TEMPLATE);
			if (var != null)
			{
				String newVarString = updateVariable(var, lib, libParameters);
				String arcName = ai.getName();
				if (!arcName.equalsIgnoreCase(newVarString))
					ai.setName(newVarString, ep);
			}
		}
		for(Iterator<Export> eIt = cell.getExports(); eIt.hasNext(); )
		{
			Export e = eIt.next();
			Variable var = e.getVar(EXPORT_BUS_TEMPLATE);
			if (var != null)
			{
				String newVarString = updateVariable(var, lib, libParameters);
				String exportName = e.getName();
				if (!exportName.equalsIgnoreCase(newVarString))
					e.rename(newVarString);
			}
		}
	}
	
	/**
	 * Internal method for replacing a bus parameter in a string.  Refactored to permit reuse.
	 * Added for ArchGen Plugin - BVE
	 * @param var
	 * @param lib
	 * @param libParameters
	 * @return the new Variable string.
	 */
	private static String replaceVariableInString(String var, Library lib, Map<Library,String[]> libParameters) {
		String varString = var;
		for(;;)
		{
			int dollarPos = varString.indexOf("$(");
			if (dollarPos < 0) break;
			int closePos = varString.indexOf(')', dollarPos);
			if (closePos < 0)
			{
				System.out.println("ERROR: Bus parameter '" + varString + "' is missing the close parenthesis");
				break;
			}
			String varName = varString.substring(dollarPos+2, closePos);

			String [] paramList = libParameters.get(lib);
			String paramValue = null;
			if (paramList != null) {
				paramValue = findParameterValue(paramList, varName);
			}
			if (paramValue == null)
			{
				for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
				{
					Library aLib = it.next();
					if (aLib == lib || aLib.isHidden()) continue;
					paramList = libParameters.get(aLib);
					paramValue = findParameterValue(paramList, varName);
					if (paramValue != null) break;
				}
				if (paramValue == null)
				{
					System.out.println("ERROR: Bus parameter '" + varName + "' is not defined");
					break;
				}
			}
			varString = varString.substring(0, dollarPos) + paramValue + varString.substring(closePos+1);
		}
		return varString;
	}
	
	private static String updateVariable(Variable var, Library lib, Map<Library,String[]> libParameters)
	{
		// first substitute variable names
		String varString = (String)var.getObject();
		// BVE - Old code refactored into helper function
		varString = replaceVariableInString(varString, lib, libParameters);

		// now that variables are substituted, handle arithmetic
		for(int i=0; i<varString.length(); i++)
		{
			char op = varString.charAt(i);
			if (op != '+' && op != '-' && op != '*' && op != '/') continue;

			// gather number before the operator
			int start = i;
			while (start > 0 && TextUtils.isDigit(varString.charAt(start-1))) start--;

			int end = i;
			while (end+1 < varString.length() && TextUtils.isDigit(varString.charAt(end+1))) end++;

			if (start < i && end > i)
			{
				// found numbers
				int startVal = TextUtils.atoi(varString.substring(start));
				int endVal = TextUtils.atoi(varString.substring(i+1));
				int res = 0;
				if (op == '+')
				{
					res = startVal + endVal;
				} else if (op == '-')
				{
					res = startVal - endVal;
				} else if (op == '*')
				{
					res = startVal * endVal;
				} else if (op == '/')
				{
					if (endVal != 0) res = startVal / endVal;
				}
				String newString = Integer.toString(res);
				varString = varString.substring(0, start) + newString + varString.substring(end+1);
				i = start + newString.length() - 1;
			}
		}
		return varString;
	}

	private static String findParameterValue(String [] parameterList, String varName)
	{
		for(int i=0; i<parameterList.length; i++)
		{
			String param = parameterList[i];
			int equalPos = param.indexOf('=');
			if (equalPos < 0) continue;
			if (varName.equalsIgnoreCase(param.substring(0, equalPos)))
				return param.substring(equalPos+1);
		}
		return null;
	}

	/**
	 * Class to create a bus template on an arc or export.
	 */
	private static class AddTemplate extends Job
	{
		private ElectricObject owner;
		private String templateString;

		private AddTemplate(ElectricObject owner)
		{
			super("Create Bus Parameter", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.owner = owner;
			this.templateString = "";
			startJob();
		}
		
		/**
		 * Constructor for adding a bus parameter template to an ElectricObject immediately or as a job.
		 * Added the ability to append a string to the template
		 * Added for ArchGen Plugin - BVE
		 * @param owner
		 * @param suffix
		 * @return
		 */
		private AddTemplate(ElectricObject owner, boolean doItNow, String initValue)
		{
			super("Create Bus Parameter", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.owner = owner;
			this.templateString = initValue;
			if (doItNow) {
			   try {doIt();} catch (Exception e) {e.printStackTrace();}
			}else {
				startJob();
			}
		}

		public boolean doIt() throws JobException
		{
			if (owner instanceof NodeInst)
			{
				// add template to node
				NodeInst ni = (NodeInst)owner;
				TextDescriptor td = ni.getTextDescriptor(NodeInst.NODE_NAME);
				double relSize = 1;
				if (!td.getSize().isAbsolute())
					relSize = td.getSize().getSize();
				td = td.withOff(td.getXOff(), td.getYOff() - relSize*1.5).withRelSize(relSize/2).withDispPart(TextDescriptor.DispPos.NAMEVALUE);
				if(!templateString.equals("")) {
					ni.newVar(NODE_BUS_TEMPLATE, ni.getName()+templateString, td);				
				}else {
					ni.newVar(NODE_BUS_TEMPLATE, ni.getName(), td);
				}
			} else if (owner instanceof ArcInst)
			{
				// add template to arc
				ArcInst ai = (ArcInst)owner;
				TextDescriptor td = ai.getTextDescriptor(ArcInst.ARC_NAME);
				double relSize = 1;
				if (!td.getSize().isAbsolute())
					relSize = td.getSize().getSize();
				td = td.withOff(td.getXOff(), td.getYOff() - relSize*1.5).withRelSize(relSize/2).withDispPart(TextDescriptor.DispPos.NAMEVALUE);
				if(!templateString.equals("")) {
					ai.newVar(ARC_BUS_TEMPLATE, ai.getName()+templateString, td);
				}else {
					ai.newVar(ARC_BUS_TEMPLATE, ai.getName(), td);
				}
			} else
			{
				// add template to export
				Export e = (Export)owner;
				TextDescriptor td = e.getTextDescriptor(Export.EXPORT_NAME);
				double relSize = 1;
				if (!td.getSize().isAbsolute())
					relSize = td.getSize().getSize();
				td = td.withOff(td.getXOff(), td.getYOff() - relSize*1.5).withRelSize(relSize/2).withDispPart(TextDescriptor.DispPos.NAMEVALUE);
				if(!templateString.equals("")) {
					e.newVar(EXPORT_BUS_TEMPLATE, e.getName()+templateString, td);
				}else {
					e.newVar(EXPORT_BUS_TEMPLATE, e.getName(), td);
				}
			}
			return true;
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        done = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        libraryPopup = new javax.swing.JComboBox();
        variablesPane = new javax.swing.JScrollPane();
        jLabel2 = new javax.swing.JLabel();
        value = new javax.swing.JTextField();
        update = new javax.swing.JButton();
        deleteVariable = new javax.swing.JButton();
        newVariable = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Bus Parameters");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        getAccessibleContext().setAccessibleName("Bus Parameters");
        done.setText("Done");
        done.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doneActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(done, gridBagConstraints);

        jLabel1.setText("Library:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(libraryPopup, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridheight = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(variablesPane, gridBagConstraints);

        jLabel2.setText("Parameters:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        value.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(value, gridBagConstraints);

        update.setText("Update All Templates");
        update.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(update, gridBagConstraints);

        deleteVariable.setText("Delete Parameter");
        deleteVariable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteVariableActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(deleteVariable, gridBagConstraints);

        newVariable.setText("New Parameter");
        newVariable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newVariableActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(newVariable, gridBagConstraints);

        jLabel3.setText("Parameter Value:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jSeparator1, gridBagConstraints);

        pack();
    }
    // </editor-fold>//GEN-END:initComponents

    private void newVariableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newVariableActionPerformed
		String libName = (String)libraryPopup.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		String [] parameterList = libParameters.get(lib);
		String newParName = Job.getUserInterface().askForInput("New Bus Parameter Name:", "Create New Bus Parameter", "");
		if (newParName == null) return;

		// make sure the name is unique
		int insertAfter = -1;
		for(int i=0; i<parameterList.length; i++)
		{
			int equalPos = parameterList[i].indexOf('=');
			if (equalPos < 0) continue;
			String varName = parameterList[i].substring(0, equalPos);
			if (varName.equalsIgnoreCase(newParName))
			{
				Job.getUserInterface().showErrorMessage("That bus parameter name already exists", "Duplicate Name");
				return;
			}
			if (varName.compareToIgnoreCase(newParName) < 0) insertAfter = i;
		}
		String [] newParameterList = new String[parameterList.length+1];
		int j = 0;
		for(int i=0; i<parameterList.length; i++)
		{
			if (i == insertAfter+1) newParameterList[j++] = newParName + "=1";
			newParameterList[j++] = parameterList[i];
		}
		if (parameterList.length == insertAfter+1) newParameterList[j++] = newParName + "=1";
		libParameters.put(lib, newParameterList);
		new UpdateLibrary(lib, newParameterList);
		libraryChanged();
    }//GEN-LAST:event_newVariableActionPerformed

	private void deleteVariableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteVariableActionPerformed
		String libName = (String)libraryPopup.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null) return;
		String [] parameterList = libParameters.get(lib);
		int selectedIndex = parametersList.getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= parameterList.length) return;
		String [] newParameterList = new String[parameterList.length-1];
		int j = 0;
		for(int i=0; i<parameterList.length; i++)
		{
			if (i != selectedIndex) newParameterList[j++] = parameterList[i];
		}
		libParameters.put(lib, newParameterList);
		new UpdateLibrary(lib, newParameterList);
		libraryChanged();
    }//GEN-LAST:event_deleteVariableActionPerformed

    private void updateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateActionPerformed
		new UpdateAllParameters(libParameters);
    }//GEN-LAST:event_updateActionPerformed

    private void doneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doneActionPerformed
		closeDialog(null);
    }//GEN-LAST:event_doneActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteVariable;
    private javax.swing.JButton done;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JComboBox libraryPopup;
    private javax.swing.JButton newVariable;
    private javax.swing.JButton update;
    private javax.swing.JTextField value;
    private javax.swing.JScrollPane variablesPane;
    // End of variables declaration//GEN-END:variables
}
