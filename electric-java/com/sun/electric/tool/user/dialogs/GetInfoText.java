/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GetInfoText.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.output.Verilog;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.menus.EMenuBar;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.ClientOS;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 * Class to handle the Text "Properties" dialog.
 */
public class GetInfoText extends EModelessDialog implements HighlightListener, DatabaseChangeListener {
	private static GetInfoText theDialog = null;
	private CachedTextInfo cti;
	private TextPropertiesFocusListener dialogFocusListener;

	/**
	 * Class to hold information about the text being manipulated.
	 */
	private static class CachedTextInfo
	{
		private Highlight shownText;
		private String initialText;
		private Variable var;
		private Variable.Key varKey;
		private TextDescriptor td;
		private ElectricObject owner;
		private String description;
		private boolean instanceName;
		private boolean multiLineCapable;

		/**
		 * Method to load the field variables from a Highlight.
		 * @param h the Highlight of text.
		 */
		CachedTextInfo(Highlight h)
		{
			shownText = h;
			description = "Unknown text";
			initialText = "";
			td = null;
			owner = shownText.getElectricObject();
			instanceName = multiLineCapable = false;
			NodeInst ni = null;
			if (owner instanceof NodeInst)
			{
				ni = (NodeInst)owner;
			}
			varKey = shownText.getVarKey();
			if (varKey != null)
			{
				if (ni != null && ni.isInvisiblePinWithText() && varKey == Artwork.ART_MESSAGE)
					multiLineCapable = true;
				var = owner.getParameterOrVariable(varKey);
				if (var != null)
				{
					Object obj = var.getObject();
					if (obj instanceof Object[])
					{
						// unwind the array elements by hand
						Object[] theArray = (Object[]) obj;
						initialText = "";
						for (int i = 0; i < theArray.length; i++)
						{
							if (i != 0) initialText += "\n";
							initialText += theArray[i];
						}
						multiLineCapable = true;
					} else
					{
						initialText = var.getPureValue(-1);
					}
					description = owner.getFullDescription(var);
				} else if (varKey == NodeInst.NODE_NAME)
				{
					description = "Name of " + ni.getProto();
					varKey = NodeInst.NODE_NAME;
					initialText = ni.getName();
				} else if (varKey == ArcInst.ARC_NAME)
				{
					ArcInst ai = (ArcInst)owner;
					description = "Name of " + ai.getProto();
					varKey = ArcInst.ARC_NAME;
					initialText = ai.getName();
				} else if (varKey == NodeInst.NODE_PROTO)
				{
					description = "Name of cell instance " + ni.describe(true);
					varKey = NodeInst.NODE_PROTO;
					initialText = ni.getProto().describe(true);
					instanceName = true;
				} else if (varKey == Export.EXPORT_NAME)
				{
					Export pp = (Export)owner;
					description = "Name of export " + pp.getName();
					varKey = Export.EXPORT_NAME;
					initialText = pp.getName();
				}
			}
			td = owner.getTextDescriptor(varKey);
		}

		/**
		 * Method to tell whether the highlighted text is the name of a cell instance.
		 * These cannot be edited by in-line editing.
		 * @return true if the highlighted text is the name of a cell instance.
		 */
		public boolean isInstanceName() { return instanceName; }

		/**
		 * Method to tell whether the highlighted text can be expressed with
		 * more than 1 line of text.
		 * This only applies to text on Invisible Pins (annotation text).
		 * @return true if the highlighted text can have more than 1 line.
		 */
		public boolean isMultiLineCapable() { return multiLineCapable; }
	}

	/**
	 * Method to show the Text Properties dialog.
	 */
	public static void showDialog() {
		if (ClientOS.isOSLinux()) {
			// JKG 07Apr2006:
			// On Linux, if a dialog is built, closed using setVisible(false),
			// and then requested again using setVisible(true), it does
			// not appear on top. I've tried using toFront(), requestFocus(),
			// but none of that works.  Instead, I brute force it and
			// rebuild the dialog from scratch each time.
			if (theDialog != null)
			{
				theDialog.removeWindowFocusListener(theDialog.dialogFocusListener);
				theDialog.dispose();
			}
			theDialog = null;
		}
		if (theDialog == null)
		{
			JFrame jf = null;
			if (TopLevel.isMDIMode()) jf = TopLevel.getCurrentJFrame();
			theDialog = new GetInfoText(jf);
		}
		theDialog.loadTextInfo();
		if (!theDialog.isVisible())
		{
			theDialog.pack();
			theDialog.ensureProperSize();
			theDialog.setVisible(true);
		}
		theDialog.toFront();
	}

	/**
	 * Creates new form Text Get-Info
	 */
	private GetInfoText(Frame parent)
	{
		super(parent);
		initComponents();
		getRootPane().setDefaultButton(ok);
		
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Dimension screenDimension = env.getMaximumWindowBounds().getSize();
		Insets insets = getRootPane().getInsets();
		int width = screenDimension.width - insets.left - insets.right;
		int height = screenDimension.height - insets.top - insets.bottom;
		getRootPane().setMinimumSize(new Dimension(width/4, height/4));
		getRootPane().setPreferredSize(new Dimension(width/2, height*2/3));
		getRootPane().setMaximumSize(new Dimension(width, height));
		
		UserInterfaceMain.addDatabaseChangeListener(this);
		Highlighter.addHighlightListener(this);
		dialogFocusListener = new TextPropertiesFocusListener();
		addWindowFocusListener(dialogFocusListener);

		loadTextInfo();
		finishInitialization();
	}

	private class TextPropertiesFocusListener implements WindowFocusListener
	{
		public synchronized void windowGainedFocus(WindowEvent e)
		{
			theText.requestFocus();
		}

		public void windowLostFocus(WindowEvent e) {}
	}

	/**
	 * Reloads the dialog when Highlights change
	 */
	public void highlightChanged(Highlighter which)
	{
		if (!isVisible()) return;
		loadTextInfo();
	}

	/**
	 * Called when by a Highlighter when it loses focus. The argument
	 * is the Highlighter that has gained focus (may be null).
	 * @param highlighterGainedFocus the highlighter for the current window (may be null).
	 */
	public void highlighterLostFocus(Highlighter highlighterGainedFocus) {
		if (!isVisible()) return;
		loadTextInfo();
	}

	public void databaseChanged(DatabaseChangeEvent e) {
		if (!isVisible()) return;

		// update dialog
		if (cti != null && e.objectChanged(cti.owner))
			loadTextInfo();
	}

	private void loadTextInfo() {
		// must have a single text selected
		Highlight textHighlight = null;
		EditWindow curWnd = EditWindow.getCurrent();
		int textCount = 0;
		if (curWnd != null) {
			for (Highlight h : curWnd.getHighlighter().getHighlights()) {
				if (!h.isHighlightText()) continue;
				// ignore export text
				if (h.getVarKey() == Export.EXPORT_NAME) continue;
				textHighlight = h;
				textCount++;
			}
		}
		if (textCount > 1) textHighlight = null;
		boolean enabled = (textHighlight == null) ? false : true;

		EDialog.focusClearOnTextField(theText);

		// enable or disable everything
		for (int i = 0; i < getComponentCount(); i++) {
			Component c = getComponent(i);
			c.setEnabled(enabled);
		}
		if (!enabled) {
			header.setText("No Text Selected");
			evaluation.setText(" ");
			theText.setText("");
			// for some reason, the following line causes keyboard input to get ignored on SUSE Linux 9.1
//			theText.setEnabled(false);
			cti = null;
			textPanel.setTextDescriptor(null, null);
			attrPanel.setVariable(null, null);
			ok.setEnabled(false);
			apply.setEnabled(false);
			multiLine.setEnabled(false);
			return;
		}

		// cache information about the Highlight
		cti = new CachedTextInfo(textHighlight);

		// enable buttons
		ok.setEnabled(true);
		apply.setEnabled(true);

		header.setText(cti.description);
		theText.setText(cti.initialText);
		theText.setEditable(true);

		// if multiline text, make it a TextArea, otherwise it's a TextField
		if (cti.initialText.indexOf('\n') != -1) {
			// if this is the name of an object it should not be multiline
			if (cti.shownText != null && (cti.varKey == NodeInst.NODE_NAME || cti.varKey == ArcInst.ARC_NAME)) {
				multiLine.setEnabled(false);
				multiLine.setSelected(false);
			} else {
				multiLine.setEnabled(true);
				multiLine.setSelected(true);
			}
		} else {
			// if this is the name of an object it should not be multiline
			if (cti.shownText != null && (cti.varKey == NodeInst.NODE_NAME || cti.varKey == ArcInst.ARC_NAME)) {
				multiLine.setEnabled(false);
			} else {
				multiLine.setEnabled(true);
			}
			multiLine.setSelected(false);
		}

		// if the var is code, evaluate it
		evaluation.setText(" ");
		if (cti.var != null) {
			if (cti.var.isCode()) {
				evaluation.setText("Evaluation: " + cti.var.describe(-1));
			}
		}

		// set the text edit panel
		textPanel.setTextDescriptor(cti.varKey, cti.owner);
		attrPanel.setVariable(cti.varKey, cti.owner);

		// do this last so everything gets packed right
		changeTextComponent(cti.initialText, multiLine.isSelected());

		EDialog.focusOnTextField(theText);

		// if this is a cell instance name, disable editing
		if (cti.varKey == NodeInst.NODE_PROTO)
		{
			theText.setEditable(false);
			theText.setEnabled(false);
			multiLine.setEnabled(false);
		}
	}

	protected void escapePressed() { cancelActionPerformed(null); }

	/**
	 * Method to edit text in place.
	 */
	public static void editTextInPlace()
	{
		// there must be a current edit window
		EditWindow curWnd = EditWindow.getCurrent();
		if (curWnd == null) return;

		// must have a single text selected
		Highlight theHigh = null;
		int textCount = 0;
		for (Highlight h : curWnd.getHighlighter().getHighlights())
		{
			if (!h.isHighlightText()) continue;
			theHigh = h;
			textCount++;
		}
		if (textCount > 1) theHigh = null;
		if (theHigh == null) return;

		// grab information about the highlighted text
		CachedTextInfo cti = new CachedTextInfo(theHigh);
		if (cti.isInstanceName())
		{
			showDialog();
			return;
		}

		// get text description
		Font theFont = curWnd.getFont(cti.td);
		if (theFont == null)
		{
			// text too small to draw (or edit), show the dialog
			showDialog();
			return;
		}
		Point2D [] points = Highlighter.describeHighlightText(curWnd, cti.owner, cti.varKey);
		long lowX=0, highX=0, lowY=0, highY=0;
		for(int i=0; i<points.length; i++)
		{
			ScreenPoint pt = curWnd.databaseToScreen(points[i]);
			if (i == 0)
			{
				lowX = highX = pt.getX();
				lowY = highY = pt.getY();
			} else
			{
				if (pt.getX() < lowX) lowX = pt.getX();
				if (pt.getX() > highX) highX = pt.getX();
				if (pt.getY() < lowY) lowY = pt.getY();
				if (pt.getY() > highY) highY = pt.getY();
			}
		}
		if (cti.td.getDispPart() != TextDescriptor.DispPos.VALUE && (cti.var == null || cti.var.getLength() == 1))
		{
			GlyphVector gv = curWnd.getGlyphs(cti.initialText, theFont);
			Rectangle2D glyphBounds = gv.getLogicalBounds();
			lowX = highX - (int)glyphBounds.getWidth();
		}

		new EditInPlaceListener(cti, curWnd, theFont, lowX, lowY);
	}

	/**
	 * Class for in-line editing a single-line piece of text.
	 * The class exists so that it can grab the focus.
	 */
	static public class EIPTextField extends JTextField
	{
		EIPTextField(String text) { super(text); }

		public void paint(Graphics g)
		{
			requestFocus();
			super.paint(g);
		}
	}

	/**
	 * Class for in-line editing a multiple-line piece of text.
	 * The class exists so that it can grab the focus.
	 */
	static public class EIPEditorPane extends JEditorPane
	{
		EIPEditorPane(String text) { super("text/plain", text); }

		public void paint(Graphics g)
		{
			requestFocus();
			super.paint(g);
		}
	}

	/**
	 * Class to handle edit-in-place of text.
	 */
	public static class EditInPlaceListener implements WindowFrame.ElectricEventListener
	{
		private CachedTextInfo cti;
		private EditWindow wnd;
		private WindowFrame.ElectricEventListener oldListener;
		private JTextComponent tc;
		private EMenuBar.Instance mb;
		private UndoManager undo;

		public EditInPlaceListener(CachedTextInfo cti, EditWindow wnd, Font theFont, long lowX, long lowY)
		{
			this.cti = cti;
			this.wnd = wnd;

			// make the field bigger
			if (cti.isMultiLineCapable() || (cti.var != null && cti.var.getLength() > 1))
			{
				EIPEditorPane ep = new EIPEditorPane(cti.initialText);
				tc = ep;
			} else
			{
				EIPTextField tf = new EIPTextField(cti.initialText);
				tf.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent evt) { closeEditInPlace(); }
				});
				tc = tf;
			}
			Document doc = tc.getDocument();

			// Listen for undo and redo events
			undo = new UndoManager();
			doc.addUndoableEditListener(new UndoableEditListener()
			{
				public void undoableEditHappened(UndoableEditEvent evt) { undo.addEdit(evt.getEdit()); }
			});

			tc.addKeyListener(this);
			tc.setSize(figureSize());
			tc.setLocation((int)lowX, (int)lowY);
			tc.setBorder(new EmptyBorder(0,0,0,0));
			if (theFont != null) tc.setFont(theFont);
			tc.selectAll();

			wnd.addInPlaceTextObject(this);
			tc.setVisible(true);
			tc.repaint();

			oldListener = WindowFrame.getListener();
			WindowFrame.setListener(this);

			TopLevel top = TopLevel.getCurrentJFrame();
			mb = top.getTheMenuBar();
			mb.setIgnoreTextEditKeys(true);
		}

		/**
		 * Method to return the JTextComponent associated with this listener.
		 * @return the  JTextComponent associated with this listener.
		 */
		public JTextComponent getTextComponent() { return tc; }

		/**
		 * Method to undo the last change to the in-place edit session.
		 */
		public void undo()
		{
			try
			{
				if (undo.canUndo()) undo.undo();
			} catch (CannotUndoException e) {}
		}

		/**
		 * Method to redo the last change to the in-place edit session.
		 */
		public void redo()
		{
			try
			{
				if (undo.canRedo()) undo.redo();
			} catch (CannotUndoException e) {}
		}

		/**
		 * Method to determine the size of the in-place edit.
		 * @return the size of the in-place edit.
		 */
		private Dimension figureSize()
		{
			Font theFont = wnd.getFont(cti.td);
			double size = EditWindow.getDefaultFontSize();
			if (cti.td != null) size = cti.td.getTrueSize(wnd);
			if (size <= 0) size = 1;
			size = theFont.getSize();

			String[] textArray = tc.getText().split("\\n", -1);
			double totalHeight = 0;
			double totalWidth = 0;
			for (int i=0; i<textArray.length; i++)
			{
				String str = textArray[i];
				GlyphVector gv = wnd.getGlyphs(str, theFont);
				Rectangle2D glyphBounds = gv.getLogicalBounds();
				totalHeight += size;
				if (glyphBounds.getWidth() > totalWidth) totalWidth = glyphBounds.getWidth();
			}
			if (textArray.length > 1) totalHeight *= 2;
			return new Dimension((int)(totalWidth +0.5*size), (int)(totalHeight + 0.2*size)); // + 20% in width and 10% in height
		}

		public void closeEditInPlace()
		{
			// gather the current text and store it on the owner
			String currentText = tc.getText();
			if (!currentText.equals(cti.initialText))
			{
				String[] textArray = currentText.split("\\n");
				ArrayList<String> textList = new ArrayList<String>();
				for (int i=0; i<textArray.length; i++)
				{
					String str = textArray[i];
					if (SimulationTool.getPreserveVerilogFormating() && // Pref set
					   cti.td.getPos() == TextDescriptor.Position.RIGHT && // Left justified text
					   (cti.varKey == Verilog.VERILOG_PARAMETER_KEY ||
						cti.varKey == Verilog.VERILOG_CODE_KEY ||
						cti.varKey == Verilog.VERILOG_EXTERNAL_CODE_KEY ||
						cti.varKey == Verilog.VERILOG_DECLARATION_KEY)) {
						textList.add(str);
					} else
					{
						if (str.trim().length() == 0) continue;
						textList.add(str);
					}
				}
				textArray = new String[textList.size()];
				for (int i=0; i<textList.size(); i++)
				{
					String str = textList.get(i);
					textArray[i] = str;
				}
				if (textArray.length > 0)
				{
					// generate job to change text
					new ChangeText(cti.owner, cti.varKey, textArray);
				}
			}

			// close the in-place text editor
			tc.removeKeyListener(this);
			WindowFrame.setListener(oldListener);
			wnd.removeInPlaceTextObject(this);
			wnd.repaint();
			wnd.requestFocus();
			mb.setIgnoreTextEditKeys(false);
		}

		// the MouseListener events
		public void mouseEntered(MouseEvent evt) {}
		public void mouseExited(MouseEvent evt) {}
		public void mousePressed(MouseEvent evt) { closeEditInPlace(); }
		public void mouseReleased(MouseEvent evt) {}
		public void mouseClicked(MouseEvent evt) {}

		// the MouseMotionListener events
		public void mouseMoved(MouseEvent evt) {}
		public void mouseDragged(MouseEvent evt) {}

		// the MouseWheelListener events
		public void mouseWheelMoved(MouseWheelEvent evt) {}

		// the KeyListener events
		public void keyPressed(KeyEvent evt) {}
		public void keyReleased(KeyEvent evt)
		{
			int chr = evt.getKeyCode();
			if (chr == KeyEvent.VK_ESCAPE)
			{
				tc.setText(cti.initialText);
				closeEditInPlace();
				return;
			}
			tc.setSize(figureSize());
		}
		public void keyTyped(KeyEvent evt) {}
        public void databaseChanged(DatabaseChangeEvent e) {}
	}

	private static class ChangeText extends Job {
		private ElectricObject owner;
		private Variable.Key key;
		private String[] newText;

		private ChangeText(ElectricObject owner, Variable.Key key, String[] newText) {
			super("Modify Text", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.owner = owner;
			this.key = key;
			this.newText = newText;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			if (key == null) return false;
            EditingPreferences ep = getEditingPreferences();
			if (key == Export.EXPORT_NAME)
			{
				Export pp = (Export)owner;
				pp.rename(newText[0]);
			} else if (key == NodeInst.NODE_NAME)
			{
				((NodeInst)owner).setName(newText[0]);
			} else if (key == ArcInst.ARC_NAME)
			{
				((ArcInst)owner).setName(newText[0], ep);
			} else if (owner instanceof Cell && owner.isParam(key))
			{
				Cell.CellGroup cellGroup = ((Cell)owner).getCellGroup();
                if (newText.length > 1)
                {
                    Variable param = ((Cell)owner).getParameter(key);
                    cellGroup.updateParam((Variable.AttrKey)key, newText, param.getUnit());
                } else
                {
                    // change variable text
                    cellGroup.updateParamText((Variable.AttrKey)key, newText[0]);
                }
			} else
			{
				if (newText.length > 1)
				{
					owner.updateVar(key, newText, ep);
				} else
				{
					// change variable text
					owner.updateVarText(key, newText[0], ep);
				}
			}
			return true;
		}
	}

	/**
	 * This method is called from within the constructor to
	 * initialize the form.
	 */
	private void initComponents()
	{
		GridBagConstraints gridBagConstraints;

		cancel = new JButton();
		ok = new JButton();
		header = new JLabel();
		apply = new JButton();
		evaluation = new JLabel();
		theText = new JTextField();
		textPanel = new TextInfoPanel(false, false);
		attrPanel = new TextAttributesPanel(false);
		multiLine = new JCheckBox();

		getContentPane().setLayout(new GridBagLayout());
		getRootPane().setDefaultButton(ok);

		setTitle("Text Properties");
		setName("");
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) { closeDialog(evt); }
		});

        
		header.setText("");
		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 2;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.anchor = GridBagConstraints.WEST;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		getContentPane().add(header, gridBagConstraints);

		changeTextComponent("", false);

		multiLine.setText("Multi-Line Text");
		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 2;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		gridBagConstraints.anchor = GridBagConstraints.EAST;
		getContentPane().add(multiLine, gridBagConstraints);
		multiLine.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) { multiLineStateChanged(); }
		});

		evaluation.setText(" ");
		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 2;
		gridBagConstraints.gridwidth = 3;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(2, 4, 2, 4);
		getContentPane().add(evaluation, gridBagConstraints);

		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 3;
		gridBagConstraints.gridwidth = 3;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(2, 4, 2, 4);
		getContentPane().add(textPanel, gridBagConstraints);

		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 4;
		gridBagConstraints.gridwidth = 3;
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(2, 4, 2, 4);
		getContentPane().add(attrPanel, gridBagConstraints);

		cancel.setText("Cancel");
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { cancelActionPerformed(evt); }
		});
		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 5;
		gridBagConstraints.weightx = 0.1;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		getContentPane().add(cancel, gridBagConstraints);

		apply.setText("Apply");
		apply.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { applyActionPerformed(evt); }
		});

		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 1;
		gridBagConstraints.gridy = 5;
		gridBagConstraints.weightx = 0.1;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		getContentPane().add(apply, gridBagConstraints);

		ok.setText("OK");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { okActionPerformed(evt); }
		});

		gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 2;
		gridBagConstraints.gridy = 5;
		gridBagConstraints.weightx = 0.1;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		getContentPane().add(ok, gridBagConstraints);

		pack();
	}

	private void multiLineStateChanged() {
		// set text box type
		changeTextComponent(theText.getText(), multiLine.isSelected());
	}

	private void changeTextComponent(String currentText, boolean multipleLines) {

		if (cti == null || cti.shownText == null) return;

		getContentPane().remove(theText);

		if (currentText == null) currentText = "";

		if (multipleLines) {
			// multiline text, change to text area
			theText = new JTextArea();
			String[] text = currentText.split("\\n");
			int size = 1;
			if (text.length > size) size = text.length;
			((JTextArea)theText).setRows(size);
			((JTextArea)theText).setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

			// add listener to increase the number of rows if needed
			theText.addKeyListener(new KeyListener() {
				public void keyPressed(KeyEvent e) {}
				public void keyTyped(KeyEvent e) {}
				public void keyReleased(KeyEvent e) {
					JTextArea area = (JTextArea)theText;
					area.setRows(area.getLineCount());
					pack();
					ensureProperSize();
				}
			});
		} else {
			theText = new JTextField();
			if (currentText.matches(".*?\\n.*")) {
				currentText = currentText.substring(0, currentText.indexOf('\n'));
			}
		}
		theText.setText(currentText);

		GridBagConstraints gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.gridwidth = 3;
		gridBagConstraints.fill = GridBagConstraints.BOTH;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.weighty = 1.0;
		gridBagConstraints.insets = new Insets(4, 4, 4, 4);
		getContentPane().add(theText, gridBagConstraints);

		pack();
	}

	private void applyActionPerformed(ActionEvent evt)
	{
		if (cti.shownText == null) return;

		// tell sub-panels to update if they have changed
		textPanel.applyChanges(true);
		attrPanel.applyChanges();

		boolean changed = false;

		// see if text changed
		String currentText = theText.getText();
		if (!currentText.equals(cti.initialText)) changed = true;

		if (changed)
		{
			String[] textArray = currentText.split("\\n");
			ArrayList<String> textList = new ArrayList<String>();
			for (int i=0; i<textArray.length; i++) {
				String str = textArray[i];
				if (SimulationTool.getPreserveVerilogFormating() && // Pref set
					cti.td.getPos() == TextDescriptor.Position.RIGHT && // Left justified text
					(cti.varKey == Verilog.VERILOG_PARAMETER_KEY ||
				 	cti.varKey == Verilog.VERILOG_CODE_KEY ||
				 	cti.varKey == Verilog.VERILOG_EXTERNAL_CODE_KEY ||
				 	cti.varKey == Verilog.VERILOG_DECLARATION_KEY))
				{
				 	textList.add(str);
				} else
				{
					if (str.trim().length() == 0) continue;
					textList.add(str);
				}
			}

			textArray = new String[textList.size()];
			for (int i=0; i<textList.size(); i++) {
				String str = textList.get(i);
				textArray[i] = str;
			}

			if (textArray.length > 0) {
				// generate job to change text
				new ChangeText(cti.owner, cti.varKey, textArray);
				cti.initialText = currentText;
			}
		}
	}

	private void okActionPerformed(ActionEvent evt) {
		applyActionPerformed(evt);
		closeDialog(null);
	}

	private void cancelActionPerformed(ActionEvent evt) {
		closeDialog(null);
	}

	/**
	 * Closes the dialog
	 */
	private void closeDialog(WindowEvent evt) {
		super.closeDialog();
	}

	private JButton apply;
	private JButton cancel;
	private JLabel evaluation;
	private JLabel header;
	private JButton ok;
	private JTextComponent theText;
	private TextInfoPanel textPanel;
	private TextAttributesPanel attrPanel;
	private JCheckBox multiLine;
}
