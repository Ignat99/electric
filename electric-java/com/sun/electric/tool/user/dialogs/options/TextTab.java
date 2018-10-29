/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TextTab.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.variable.MutableTextDescriptor;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TextWindow;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumMap;
import java.util.Iterator;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Class to handle the "Text" tab of the Preferences dialog.
 */
public class TextTab extends PreferencePanel
{
	private static final String EXTERNALEDITOR_HEADER = "External editor: ";
	private static final String EXTERNALEDITOR_NOTSET = "NOT SET";

	/** Creates new form TextTab */
	public TextTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
		EDialog.makeTextFieldSelectAllOnTab(textPointSize);
		EDialog.makeTextFieldSelectAllOnTab(textUnitSize);
		EDialog.makeTextFieldSelectAllOnTab(textCellSize);
		EDialog.makeTextFieldSelectAllOnTab(textGlobalScale);
		EDialog.makeTextFieldSelectAllOnTab(textWindowScale);
	}

	/** return the panel to use for user preferences. */
	@Override
	public JPanel getUserPreferencesPanel() { return text; }

	/** return the name of this preferences tab. */
	@Override
	public String getName() { return "Text"; }

    private final MutableTextDescriptor[] currentTextDescriptors = new MutableTextDescriptor[TextDescriptor.TextType.values().length];
//    private final EnumMap<TextDescriptor.TextType,MutableTextDescriptor> currentTextDescriptors =
//                new EnumMap<TextDescriptor.TextType,MutableTextDescriptor>(TextDescriptor.TextType.class);
	private MutableTextDescriptor currentTextDescriptor;
	private boolean textValuesChanging = false;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Text tab.
	 */
	@Override
	public void init()
	{
        EditingPreferences ep = getEditingPreferences();
		GraphicsPreferences gp = UserInterfaceMain.getGraphicsPreferences();
		for (Iterator<TextDescriptor.Position> it = TextDescriptor.Position.getPositions(); it.hasNext(); )
		{
			TextDescriptor.Position pos = it.next();
			textAnchor.addItem(pos);
		}

		// get current descriptors (gets changed by dialog)
        for (TextDescriptor.TextType textType: TextDescriptor.TextType.values()) {
            currentTextDescriptors[textType.ordinal()] = new MutableTextDescriptor(ep.getTextDescriptor(textType, true));
//            currentTextDescriptors.put(textType, new MutableTextDescriptor(ep.getTextDescriptor(textType, true)));
        }

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		String [] fontNames = ge.getAvailableFontFamilyNames();
		textFace.addItem("DEFAULT FONT");
		for(int i=0; i<fontNames.length; i++)
		{
			textDefaultFont.addItem(fontNames[i]);
			textFace.addItem(fontNames[i]);
			textCellFont.addItem(fontNames[i]);
		}
		textDefaultFont.setSelectedItem(gp.defaultFont);
		textCellFont.setSelectedItem(User.getDefaultTextCellFont());
		textCellSize.setText(Integer.toString(User.getDefaultTextCellSize()));
		String editor = EXTERNALEDITOR_HEADER;
		if (User.getDefaultTextExternalEditor().length() == 0) editor += EXTERNALEDITOR_NOTSET; else
			editor += User.getDefaultTextExternalEditor();
		textExternalEditor.setText(editor);

		textGlobalScale.setText(TextUtils.formatDouble(User.getGlobalTextScale() * 100));
		EditWindow wnd = EditWindow.getCurrent();
		textWindowScale.setText(wnd == null ? "" : TextUtils.formatDouble(wnd.getGlobalTextScale() * 100));
		textShowTempNames.setSelected(gp.isShowTempNames());

		textNodes.setSelected(true);
		textButtonChanged();

		textNodes.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textArcs.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textExports.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textPorts.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textAnnotation.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textInstances.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});
		textCellText.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textButtonChanged(); }
		});

		textPoints.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textUnits.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textItalic.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textBold.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textUnderline.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textFace.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textAnchor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textNewVisibleInsideCell.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { textValuesChanged(); }
		});
		textPointSize.getDocument().addDocumentListener(new TextSizeDocumentListener(this));
		textUnitSize.getDocument().addDocumentListener(new TextSizeDocumentListener(this));
	}

	/**
	 * Class to handle special changes to text sizes.
	 */
	private static class TextSizeDocumentListener implements DocumentListener
	{
		TextTab dialog;

		TextSizeDocumentListener(TextTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.textValuesChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.textValuesChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.textValuesChanged(); }
	}

	private void textValuesChanged()
	{
		if (textValuesChanging) return;
		if (textPoints.isSelected())
		{
			String sizeStr = textPointSize.getText().trim();
			if (sizeStr.length() > 0)
			{
				int size = TextUtils.isANumber(sizeStr) ? TextUtils.atoi(sizeStr) : 0;
				MutableTextDescriptor.Size s = MutableTextDescriptor.Size.newAbsSize(size);
				if (s != null) currentTextDescriptor.setAbsSize(size); else
				{
					System.out.println("Invalid size: " + sizeStr + " (Leaving it at " + currentTextDescriptor.getSize().getSize() + ")");
					SwingUtilities.invokeLater(new Runnable() {
						public void run() { loadCurrentDescriptorInfo(); }
					});
				}
			}
		} else
		{
			String sizeStr = textUnitSize.getText().trim();
			if (sizeStr.length() > 0)
			{
				double size = TextUtils.isANumber(sizeStr) ? TextUtils.atof(sizeStr) : 0;
				MutableTextDescriptor.Size s = MutableTextDescriptor.Size.newRelSize(size);
				if (s != null) currentTextDescriptor.setRelSize(size); else
				{
					System.out.println("Invalid size: " + sizeStr + " (Leaving it at " + currentTextDescriptor.getSize().getSize() + ")");
					SwingUtilities.invokeLater(new Runnable() {
						public void run() { loadCurrentDescriptorInfo(); }
					});
				}
			}
		}

		currentTextDescriptor.setItalic(textItalic.isSelected());
		currentTextDescriptor.setBold(textBold.isSelected());
		currentTextDescriptor.setUnderline(textUnderline.isSelected());

		currentTextDescriptor.setPos((TextDescriptor.Position)textAnchor.getSelectedItem());

		currentTextDescriptor.setInterior(textNewVisibleInsideCell.isSelected());

		int face = 0;
		if (textFace.getSelectedIndex() != 0)
		{
			TextDescriptor.ActiveFont af = TextDescriptor.ActiveFont.findActiveFont((String)textFace.getSelectedItem());
			if (af != null)
				face = af.getIndex();
		}
		currentTextDescriptor.setFace(face);
	}

	private void textButtonChanged()
	{
        TextDescriptor.TextType textType = null;
		if (textNodes.isSelected())
		{
			textType = TextDescriptor.TextType.NODE;
		} else if (textArcs.isSelected())
		{
			textType = TextDescriptor.TextType.ARC;
		} else if (textExports.isSelected())
		{
			textType = TextDescriptor.TextType.PORT;
		} else if (textPorts.isSelected())
		{
			textType = TextDescriptor.TextType.EXPORT;
		} else if (textAnnotation.isSelected())
		{
			textType = TextDescriptor.TextType.ANNOTATION;
		} else if (textInstances.isSelected())
		{
			textType = TextDescriptor.TextType.INSTANCE;
		} else if (textCellText.isSelected())
		{
			textType = TextDescriptor.TextType.CELL;
		}
		currentTextDescriptor = textType != null ? currentTextDescriptors[textType.ordinal()] : null;
//		currentTextDescriptor = textType != null ? currentTextDescriptors.get(textType) : null;
		loadCurrentDescriptorInfo();
	}

	private void loadCurrentDescriptorInfo()
	{
		textValuesChanging = true;
		TextDescriptor.Size size = currentTextDescriptor.getSize();
		if (size.isAbsolute())
		{
			textPoints.setSelected(true);
			textPointSize.setText(Integer.toString((int)size.getSize()));
			textUnitSize.setText("");
		} else
		{
			textUnits.setSelected(true);
			textUnitSize.setText(TextUtils.formatDouble(size.getSize()));
			textPointSize.setText("");
		}
		textItalic.setSelected(currentTextDescriptor.isItalic());
		textBold.setSelected(currentTextDescriptor.isBold());
		textUnderline.setSelected(currentTextDescriptor.isUnderline());

		textAnchor.setSelectedItem(currentTextDescriptor.getPos());
		textValuesChanging = false;

		textNewVisibleInsideCell.setSelected(currentTextDescriptor.isInterior());

		int face = currentTextDescriptor.getFace();
		if (face == 0)
		{
			textFace.setSelectedIndex(0);
		} else
		{
			String fontName = TextDescriptor.ActiveFont.findActiveFont(face).getName();
			ensureComboBoxFont(textFace, fontName);
			textFace.setSelectedItem(fontName);
		}
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Text tab.
	 */
	@Override
	public void term()
	{
		boolean editCellsChanged = false;
		boolean textCellsChanged = false;

        GraphicsPreferences gp = UserInterfaceMain.getGraphicsPreferences();
		String currentFontName = (String)textDefaultFont.getSelectedItem();
		if (!currentFontName.equalsIgnoreCase(gp.defaultFont))
		{
			UserInterfaceMain.setGraphicsPreferences(gp.withDefaultFont(currentFontName));
			editCellsChanged = true;
		}
        boolean showTempNames = textShowTempNames.isSelected();
        if (showTempNames != gp.isShowTempNames()) {
			UserInterfaceMain.setGraphicsPreferences(gp.withShowTempNames(showTempNames));
            editCellsChanged = true;
        }

		currentFontName = (String)textCellFont.getSelectedItem();
		if (!currentFontName.equalsIgnoreCase(User.getDefaultTextCellFont()))
		{
			User.setDefaultTextCellFont(currentFontName);
			textCellsChanged = true;
		}

		int currInt = TextUtils.atoi(textCellSize.getText());
		if (currInt != User.getDefaultTextCellSize())
		{
			User.setDefaultTextCellSize(currInt);
			textCellsChanged = true;
		}
		String externalEditor = textExternalEditor.getText();
		if (externalEditor.startsWith(EXTERNALEDITOR_HEADER)) externalEditor = externalEditor.substring(EXTERNALEDITOR_HEADER.length());
		if (externalEditor.equals(EXTERNALEDITOR_NOTSET)) externalEditor = "";
		if (!externalEditor.equals(User.getDefaultTextExternalEditor()))
			User.setDefaultTextExternalEditor(externalEditor);

		EditingPreferences ep = getEditingPreferences();
        for (TextDescriptor.TextType textType: TextDescriptor.TextType.values()) {
            ep = ep.withTextDescriptor(textType, TextDescriptor.newTextDescriptor(currentTextDescriptors[textType.ordinal()]));
//            ep = ep.withTextDescriptor(textType, TextDescriptor.newTextDescriptor(currentTextDescriptors.get(textType)));
        }
		setEditingPreferences(ep);

		double currentGlobalScale = TextUtils.atof(textGlobalScale.getText()) / 100;
		if (currentGlobalScale != User.getGlobalTextScale())
		{
			User.setGlobalTextScale(currentGlobalScale);
			editCellsChanged = true;
		}
		currentGlobalScale = TextUtils.atof(textWindowScale.getText()) / 100;
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd != null && currentGlobalScale != wnd.getGlobalTextScale())
		{
			wnd.setGlobalTextScale(currentGlobalScale);
			editCellsChanged = true;
		}

		if (textCellsChanged || editCellsChanged)
		{
			// redraw appropriate cells
			for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
			{
				WindowFrame wf = it.next();
				if (textCellsChanged && wf.getContent() instanceof TextWindow)
				{
					TextWindow tw = (TextWindow)wf.getContent();
					tw.updateFontInformation();
				}
				if (editCellsChanged && wf.getContent() instanceof EditWindow)
				{
					EditWindow w = (EditWindow)wf.getContent();
					w.fullRepaint();
				}
			}
		}
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	@Override
	public void reset()
	{
		setEditingPreferences(getEditingPreferences().withTextDescriptorsReset());

		if (!User.getFactoryDefaultTextCellFont().equals(User.getDefaultTextCellFont()))
			User.setDefaultTextCellFont(User.getFactoryDefaultTextCellFont());
		if (User.getFactoryDefaultTextCellSize() != User.getDefaultTextCellSize())
			User.setDefaultTextCellSize(User.getFactoryDefaultTextCellSize());
		if (!User.getFactoryDefaultTextExternalEditor().equals(User.getDefaultTextExternalEditor()))
			User.setDefaultTextExternalEditor(User.getFactoryDefaultTextExternalEditor());
		GraphicsPreferences gp = UserInterfaceMain.getGraphicsPreferences();
		UserInterfaceMain.setGraphicsPreferences(gp.withDefaultFont(GraphicsPreferences.FACTORY_DEFAULT_FONT).withShowTempNames(false));
		if (User.getFactoryGlobalTextScale() != User.getGlobalTextScale())
			User.setGlobalTextScale(User.getFactoryGlobalTextScale());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        textSizeGroup = new javax.swing.ButtonGroup();
        textTypeGroup = new javax.swing.ButtonGroup();
        text = new javax.swing.JPanel();
        top = new javax.swing.JPanel();
        jLabel41 = new javax.swing.JLabel();
        jLabel42 = new javax.swing.JLabel();
        textPointSize = new javax.swing.JTextField();
        textUnitSize = new javax.swing.JTextField();
        jLabel43 = new javax.swing.JLabel();
        textFace = new javax.swing.JComboBox();
        textItalic = new javax.swing.JCheckBox();
        textBold = new javax.swing.JCheckBox();
        textUnderline = new javax.swing.JCheckBox();
        textPoints = new javax.swing.JRadioButton();
        textUnits = new javax.swing.JRadioButton();
        jSeparator3 = new javax.swing.JSeparator();
        jLabel6 = new javax.swing.JLabel();
        textAnchor = new javax.swing.JComboBox();
        textNewVisibleInsideCell = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        textNodes = new javax.swing.JRadioButton();
        textArcs = new javax.swing.JRadioButton();
        textPorts = new javax.swing.JRadioButton();
        textExports = new javax.swing.JRadioButton();
        jPanel2 = new javax.swing.JPanel();
        textAnnotation = new javax.swing.JRadioButton();
        textInstances = new javax.swing.JRadioButton();
        textCellText = new javax.swing.JRadioButton();
        globals = new javax.swing.JPanel();
        jLabel44 = new javax.swing.JLabel();
        textDefaultFont = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        textGlobalScale = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        textWindowScale = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        textShowTempNames = new javax.swing.JCheckBox();
        textCells = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        textCellFont = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        textCellSize = new javax.swing.JTextField();
        textExternalEditor = new javax.swing.JLabel();
        textSetExternalEditor = new javax.swing.JButton();
        textClearExternalEditor = new javax.swing.JButton();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        text.setLayout(new java.awt.GridBagLayout());

        top.setBorder(javax.swing.BorderFactory.createTitledBorder("Default Style for New Text"));
        top.setLayout(new java.awt.GridBagLayout());

        jLabel41.setText("Which type of new text:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 4);
        top.add(jLabel41, gridBagConstraints);

        jLabel42.setText("Size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        top.add(jLabel42, gridBagConstraints);

        textPointSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        top.add(textPointSize, gridBagConstraints);

        textUnitSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        top.add(textUnitSize, gridBagConstraints);

        jLabel43.setText("Font:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        top.add(jLabel43, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        top.add(textFace, gridBagConstraints);

        textItalic.setText("Italic");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        top.add(textItalic, gridBagConstraints);

        textBold.setText("Bold");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        top.add(textBold, gridBagConstraints);

        textUnderline.setText("Underline");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        top.add(textUnderline, gridBagConstraints);

        textSizeGroup.add(textPoints);
        textPoints.setText("Points (max 63)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        top.add(textPoints, gridBagConstraints);

        textSizeGroup.add(textUnits);
        textUnits.setText("Units (max 127.75)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        top.add(textUnits, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 20);
        top.add(jSeparator3, gridBagConstraints);

        jLabel6.setText("Anchor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        top.add(jLabel6, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        top.add(textAnchor, gridBagConstraints);

        textNewVisibleInsideCell.setText("Invisible outside cell");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        top.add(textNewVisibleInsideCell, gridBagConstraints);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        textTypeGroup.add(textNodes);
        textNodes.setText("Node text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel1.add(textNodes, gridBagConstraints);

        textTypeGroup.add(textArcs);
        textArcs.setText("Arc text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel1.add(textArcs, gridBagConstraints);

        textTypeGroup.add(textPorts);
        textPorts.setText("Port text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel1.add(textPorts, gridBagConstraints);

        textTypeGroup.add(textExports);
        textExports.setText("Export text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel1.add(textExports, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        top.add(jPanel1, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        textTypeGroup.add(textAnnotation);
        textAnnotation.setText("Annotation text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel2.add(textAnnotation, gridBagConstraints);

        textTypeGroup.add(textInstances);
        textInstances.setText("Instance names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel2.add(textInstances, gridBagConstraints);

        textTypeGroup.add(textCellText);
        textCellText.setText("Cell text");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel2.add(textCellText, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        top.add(jPanel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        text.add(top, gridBagConstraints);

        globals.setBorder(javax.swing.BorderFactory.createTitledBorder("Everywhere:"));
        globals.setLayout(new java.awt.GridBagLayout());

        jLabel44.setText("Default font:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        globals.add(jLabel44, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        globals.add(textDefaultFont, gridBagConstraints);

        jLabel1.setText("Default global text scale");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        globals.add(jLabel1, gridBagConstraints);

        textGlobalScale.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 1);
        globals.add(textGlobalScale, gridBagConstraints);

        jLabel2.setText("percent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 1, 2, 4);
        globals.add(jLabel2, gridBagConstraints);

        jLabel4.setText("Global text scale in");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        globals.add(jLabel4, gridBagConstraints);

        textWindowScale.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 1);
        globals.add(textWindowScale, gridBagConstraints);

        jLabel5.setText("percent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 1, 4, 4);
        globals.add(jLabel5, gridBagConstraints);

        jLabel8.setText("the current window:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 4, 4);
        globals.add(jLabel8, gridBagConstraints);

        jLabel9.setText("for new windows:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 2, 4);
        globals.add(jLabel9, gridBagConstraints);

        textShowTempNames.setText("Show temporary node names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        globals.add(textShowTempNames, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        text.add(globals, gridBagConstraints);

        textCells.setBorder(javax.swing.BorderFactory.createTitledBorder("For Textual Cells:"));
        textCells.setLayout(new java.awt.GridBagLayout());

        jLabel7.setText("Font:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(jLabel7, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(textCellFont, gridBagConstraints);

        jLabel3.setText("Size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(jLabel3, gridBagConstraints);

        textCellSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(textCellSize, gridBagConstraints);

        textExternalEditor.setText("External editor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(textExternalEditor, gridBagConstraints);

        textSetExternalEditor.setText("Set");
        textSetExternalEditor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textSetExternalEditorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(textSetExternalEditor, gridBagConstraints);

        textClearExternalEditor.setText("Clear");
        textClearExternalEditor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textClearExternalEditorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        textCells.add(textClearExternalEditor, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        text.add(textCells, gridBagConstraints);

        getContentPane().add(text, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void textClearExternalEditorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textClearExternalEditorActionPerformed
		textExternalEditor.setText(EXTERNALEDITOR_HEADER + EXTERNALEDITOR_NOTSET);
    }//GEN-LAST:event_textClearExternalEditorActionPerformed

    private void textSetExternalEditorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textSetExternalEditorActionPerformed
		String fileName = OpenFile.chooseInputFile(FileType.ANY, "External editor",
			false, User.getWorkingDirectory(), false, null);
		if (fileName == null) return;
		textExternalEditor.setText(EXTERNALEDITOR_HEADER + fileName);
    }//GEN-LAST:event_textSetExternalEditorActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel globals;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel41;
    private javax.swing.JLabel jLabel42;
    private javax.swing.JLabel jLabel43;
    private javax.swing.JLabel jLabel44;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JPanel text;
    private javax.swing.JComboBox textAnchor;
    private javax.swing.JRadioButton textAnnotation;
    private javax.swing.JRadioButton textArcs;
    private javax.swing.JCheckBox textBold;
    private javax.swing.JComboBox textCellFont;
    private javax.swing.JTextField textCellSize;
    private javax.swing.JRadioButton textCellText;
    private javax.swing.JPanel textCells;
    private javax.swing.JButton textClearExternalEditor;
    private javax.swing.JComboBox textDefaultFont;
    private javax.swing.JRadioButton textExports;
    private javax.swing.JLabel textExternalEditor;
    private javax.swing.JComboBox textFace;
    private javax.swing.JTextField textGlobalScale;
    private javax.swing.JRadioButton textInstances;
    private javax.swing.JCheckBox textItalic;
    private javax.swing.JCheckBox textNewVisibleInsideCell;
    private javax.swing.JRadioButton textNodes;
    private javax.swing.JTextField textPointSize;
    private javax.swing.JRadioButton textPoints;
    private javax.swing.JRadioButton textPorts;
    private javax.swing.JButton textSetExternalEditor;
    private javax.swing.JCheckBox textShowTempNames;
    private javax.swing.ButtonGroup textSizeGroup;
    private javax.swing.ButtonGroup textTypeGroup;
    private javax.swing.JCheckBox textUnderline;
    private javax.swing.JTextField textUnitSize;
    private javax.swing.JRadioButton textUnits;
    private javax.swing.JTextField textWindowScale;
    private javax.swing.JPanel top;
    // End of variables declaration//GEN-END:variables

}
