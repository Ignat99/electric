/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LayoutText.java
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.WindowFrame;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.util.Iterator;

/**
 * Class to handle the "Layout Text" dialog.
 */
public class LayoutText extends EDialog
{
	private static int lastSize = 12;
	private static double lastScale = 1;
	private static double lastSeparation = 0;
	private static boolean lastItalic = false;
	private static boolean lastBold = false;
	private static boolean lastUnderline = false;
	private static boolean lastInvertDots = false;
	private static String lastFont = User.getDefaultFont();
	private static String lastLayer = null;
	private static String lastMessage = null;

	/** Creates new form Layout Text */
	public LayoutText(Frame parent)
	{
		super(parent, true);
		initComponents();
        getRootPane().setDefaultButton(ok);

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(textSize);
	    EDialog.makeTextFieldSelectAllOnTab(textScale);
	    EDialog.makeTextFieldSelectAllOnTab(dotSeparation);

	    Technology tech = Technology.getCurrent();
		textSize.setText(Integer.toString(lastSize));
		textScale.setText(TextUtils.formatDouble(lastScale));
		dotSeparation.setText(TextUtils.formatDistance(lastSeparation, tech));

		textItalic.setSelected(lastItalic);
		textBold.setSelected(lastBold);
		textUnderline.setSelected(lastUnderline);
		invertDots.setSelected(lastInvertDots);

		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode np = it.next();
			if (np.getFunction() == PrimitiveNode.Function.NODE)
				textLayer.addItem(np.getName());
		}
		if (lastLayer != null)
			textLayer.setSelectedItem(lastLayer);

		if (lastMessage != null)
			textMessage.setText(lastMessage);

		Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
		for(int i=0; i<fonts.length; i++)
			textFont.addItem(fonts[i].getFontName());
		if (lastFont != null)
			textFont.setSelectedItem(lastFont);

		// have fields update the message display
		textFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateMessageField(); }
		});
		textItalic.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateMessageField(); }
		});
		textBold.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateMessageField(); }
		});
		textSize.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateMessageField(); }
		});
		finishInitialization();
	}

	protected void escapePressed() { cancel(null); }

	/**
	 * Method called when a dialog field has changed, and the message area must be redisplayed.
	 */
	private void updateMessageField()
	{
		String fontName = (String)textFont.getSelectedItem();
		int fontStyle = Font.PLAIN;
		if (textItalic.isSelected()) fontStyle |= Font.ITALIC;
		if (textBold.isSelected()) fontStyle |= Font.BOLD;
		int size = TextUtils.atoi(textSize.getText());
		Font theFont = new Font(fontName, fontStyle, size);
		if (theFont != null)
			textMessage.setFont(theFont);
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        textSize = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        textScale = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        dotSeparation = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        textFont = new javax.swing.JComboBox();
        textItalic = new javax.swing.JCheckBox();
        textBold = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        textLayer = new javax.swing.JComboBox();
        jLabel6 = new javax.swing.JLabel();
        textUnderline = new javax.swing.JCheckBox();
        textMessage = new javax.swing.JTextArea();
        invertDots = new javax.swing.JCheckBox();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Make Layout Text");
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
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
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
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        jLabel1.setText("Size (max 63):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        textSize.setColumns(8);
        textSize.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textSize, gridBagConstraints);

        jLabel2.setText("Scale factor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        textScale.setColumns(8);
        textScale.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textScale, gridBagConstraints);

        jLabel3.setText("Dot separation (units):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel3, gridBagConstraints);

        dotSeparation.setColumns(8);
        dotSeparation.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(dotSeparation, gridBagConstraints);

        jLabel4.setText("Font:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textFont, gridBagConstraints);

        textItalic.setText("Italic");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textItalic, gridBagConstraints);

        textBold.setText("Bold");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textBold, gridBagConstraints);

        jLabel5.setText("Layer:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel5, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textLayer, gridBagConstraints);

        jLabel6.setText("Message:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel6, gridBagConstraints);

        textUnderline.setText("Underline");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        getContentPane().add(textUnderline, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(textMessage, gridBagConstraints);

        invertDots.setText("Reverse-video");
        invertDots.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        invertDots.setMargin(new java.awt.Insets(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(invertDots, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void cancel(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancel
	{//GEN-HEADEREND:event_cancel
		closeDialog(null);
	}//GEN-LAST:event_cancel

	private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
	{//GEN-HEADEREND:event_ok
		grabDialogValues();

		Cell curCell = WindowFrame.needCurCell();
		if (curCell == null) return;

		// determine the primitive to use for the layout
		NodeProto primNode = Technology.getCurrent().findNodeProto(lastLayer);
		if (primNode == null)
		{
			System.out.println("Cannot find " + lastLayer + " primitive");
			return;
		}

		// get the raster
		int yOffset = 0;
		String [] strings = lastMessage.split("\n");
		for(int i=0; i<strings.length; i++)
		{
			String str = strings[i];
			Raster ras = renderText(str, lastFont, lastSize, lastItalic, lastBold, lastUnderline);
			if (ras == null)
			{
				System.out.println("Cannot generate a raster for the text '" + str + "'");
				return;
			}
			DataBufferByte dbb = (DataBufferByte)ras.getDataBuffer();
			byte [] samples = dbb.getData();

			// create the layout text
			new CreateLayoutText(curCell, primNode, lastScale, lastSeparation, ras.getWidth(), ras.getHeight(),
				yOffset, lastInvertDots, samples);
			yOffset += ras.getHeight();
		}


		closeDialog(null);
	}//GEN-LAST:event_ok

	/**
	 * Method to convert text to an array of pixels.
	 * This is used for text rendering, as well as for creating "layout text" which is placed as geometry in the circuit.
	 * @param msg the string of text to be converted.
	 * @param fontName the name of the font to use.
	 * @param tSize the size of the font to use.
	 * @param italic true to make the text italic.
	 * @param bold true to make the text bold.
	 * @param underline true to underline the text.
	 * @param invertDots true to invert dot placement.
	 * @return a Raster with the text bits.
	 */
	private static Raster renderText(String msg, String fontName, int tSize, boolean italic, boolean bold, boolean underline)
	{
        int fontStyle = Font.PLAIN;
        if (italic) fontStyle |= Font.ITALIC;
        if (bold) fontStyle |= Font.BOLD;
        Font font = new Font(fontName, fontStyle, tSize);
        if (font == null) {
            System.out.println("Could not find font "+font+" to render text: "+msg);
            return null;
        }

        // convert the text to a GlyphVector
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, msg);
        LineMetrics lm = font.getLineMetrics(msg, frc);

        // figure bounding box of text
        Rectangle2D rasRect = gv.getLogicalBounds();
        int width = (int)rasRect.getWidth();
        int height = (int)(lm.getHeight()+0.5);
        if (width <= 0 || height <= 0) return null;
        fontStyle = font.getStyle();

        if (underline) height++;
        Rectangle2D rasBounds = new Rectangle2D.Double(0, lm.getAscent()-lm.getLeading(), width, height);

		// if the new image is larger than what is saved, must rebuild
			// create a new text buffer
			BufferedImage textImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g2 = textImage.createGraphics();

		// now render it
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g2.setColor(new Color(255,255,255));
		g2.drawGlyphVector(gv, (float)-rasBounds.getX(), lm.getAscent()-lm.getLeading());
		if (underline)
			g2.drawLine(0, height-1, width-1, height-1);

		// return the bits
		return textImage.getData();
    }

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		grabDialogValues();
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

	private void grabDialogValues()
	{
		lastSize = TextUtils.atoi(textSize.getText());
		lastScale = TextUtils.atof(textScale.getText());
	    Technology tech = Technology.getCurrent();
		lastSeparation = TextUtils.atofDistance(dotSeparation.getText(), tech);
		lastItalic = textItalic.isSelected();
		lastBold = textBold.isSelected();
		lastUnderline = textUnderline.isSelected();
		lastInvertDots = invertDots.isSelected();
		lastLayer = (String)textLayer.getSelectedItem();
		lastFont = (String)textFont.getSelectedItem();
		lastMessage = textMessage.getText();
	}

	/**
	 * Class to create a cell in a new thread.
	 */
	private static class CreateLayoutText extends Job
	{
		private Cell curCell;
		private NodeProto primNode;
		private double lastScale;
		private double lastSeparation;
		private int wid, hei, yOffset;
		private boolean invertDots;
		private byte [] samples;

		protected CreateLayoutText(
			Cell curCell,
			NodeProto primNode,
			double lastScale,
			double lastSeparation,
			int wid, int hei,
			int yOffset,
			boolean invertDots,
			byte [] samples)
		{
			super("Create Layout Text", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.curCell = curCell;
			this.primNode = primNode;
			this.lastScale = lastScale;
			this.lastSeparation = lastSeparation;
			this.wid = wid;
			this.hei = hei;
			this.yOffset = yOffset;
			this.invertDots = invertDots;
			this.samples = samples;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			double width = lastScale - lastSeparation;
			double height = lastScale - lastSeparation;
			int samp = 0;
			for(int y=0; y<hei; y++)
			{
				for(int x=0; x<wid; x++)
				{
					if (samples[samp++] == 0 != invertDots) continue;
					Point2D center = new Point2D.Double(x*lastScale, -(y+yOffset)*lastScale);
					NodeInst.newInstance(primNode, ep, center, width, height, curCell);
				}
			}
			return true;
		}
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancel;
    private javax.swing.JTextField dotSeparation;
    private javax.swing.JCheckBox invertDots;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JButton ok;
    private javax.swing.JCheckBox textBold;
    private javax.swing.JComboBox textFont;
    private javax.swing.JCheckBox textItalic;
    private javax.swing.JComboBox textLayer;
    private javax.swing.JTextArea textMessage;
    private javax.swing.JTextField textScale;
    private javax.swing.JTextField textSize;
    private javax.swing.JCheckBox textUnderline;
    // End of variables declaration//GEN-END:variables

}
