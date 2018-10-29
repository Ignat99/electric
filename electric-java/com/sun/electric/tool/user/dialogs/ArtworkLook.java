/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ArtworkLook.java
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.ClientOS;

import java.awt.Color;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

/**
 * Class to handle the "Artwork Look" dialog.
 */
public class ArtworkLook extends EModelessDialog implements HighlightListener
{
	private ColorPatternPanel.Info li;
	private List<Geometric> artworkObjects;
	private ColorPatternPanel colorPatternPanel;
	private static ArtworkLook theDialog;

	/**
	 * Method to display a dialog for controlling the appearance of selected artwork primitives.
	 */
	public static void showArtworkLookDialog()
	{
		// see if there is a piece of artwork selected
		List<Geometric> artObjects = findSelectedArt();
		if (artObjects.size() == 0)
		{
			System.out.println("Selected object must be from the Artwork technology");
			return;
		}

		if (ClientOS.isOSLinux()) {
            // On Linux, if a dialog is built, closed using setVisible(false),
            // and then requested again using setVisible(true), it does
            // not appear on top. I've tried using toFront(), requestFocus(),
            // but none of that works.  Instead, I brute force it and
            // rebuild the dialog from scratch each time.
            if (theDialog != null) theDialog.dispose();
            theDialog = null;
        }
		if (theDialog == null)
		{
            JFrame jf = null;
            if (TopLevel.isMDIMode()) jf = TopLevel.getCurrentJFrame();
			theDialog = new ArtworkLook(jf, artObjects);
		} else
		{
			theDialog.showArtworkObjects(artObjects);
		}

        if (!theDialog.isVisible())
		{
        	theDialog.pack();
        	theDialog.ensureProperSize();
            theDialog.setVisible(true);
		}
		theDialog.toFront();
	}

	private static List<Geometric> findSelectedArt()
	{
		List<Geometric> artworkObjects = new ArrayList<Geometric>();

		// find all pieces of artwork selected
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd == null) return artworkObjects;
		List<Geometric> objects = wnd.getHighlighter().getHighlightedEObjs(true, true);
		for(Geometric geom : objects)
		{
			if (geom instanceof NodeInst)
			{
				NodeInst ni = (NodeInst)geom;
				if (!ni.isCellInstance() &&
					ni.getProto().getTechnology() == Artwork.tech())
						artworkObjects.add(ni);
			} else if (geom instanceof ArcInst)
			{
				ArcInst ai = (ArcInst)geom;
				if (ai.getProto().getTechnology() == Artwork.tech()) artworkObjects.add(ai);
			}
		}
		return artworkObjects;
	}

	/** Creates new form ArtworkLook */
	public ArtworkLook(Frame parent, List<Geometric> artObjects)
	{
		super(parent);
		initComponents();
		getRootPane().setDefaultButton(ok);

		// make the color/pattern panel
		colorPatternPanel = new ColorPatternPanel(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;      gbc.gridy = 0;
		gbc.gridwidth = 3;  gbc.gridheight = 1;
		gbc.weightx = 1;    gbc.weighty = 1;
		gbc.insets = new java.awt.Insets(4, 4, 4, 4);
		getContentPane().add(colorPatternPanel, gbc);
		pack();

		Technology tech = Technology.getCurrent();
		Color [] map = tech.getTransparentLayerColors();
		colorPatternPanel.setColorMap(map);

		showArtworkObjects(artObjects);
		finishInitialization();
		Highlighter.addHighlightListener(this);
	}

	protected void escapePressed() { cancel(null); }

	private void showArtworkObjects(List<Geometric> artObjects)
	{
		artworkObjects = artObjects;
		if (artworkObjects.size() == 0) li = null; else
		{
			EGraphics graphics = Artwork.tech().makeGraphics(artworkObjects.get(0));
			if (graphics == null)
                graphics = Artwork.tech().defaultLayer.getFactoryGraphics();
			li = new ColorPatternPanel.Info(graphics);
		}
		colorPatternPanel.setColorPattern(li);
	}

	/**
	 * Reloads the dialog when Highlights change
	 */
	public void highlightChanged(Highlighter which)
	{
		if (!isVisible()) return;
		List<Geometric> artObjects = findSelectedArt();
		showArtworkObjects(artObjects);
	}

	/**
	 * Called when by a Highlighter when it loses focus. The argument
	 * is the Highlighter that has gained focus (may be null).
	 * @param highlighterGainedFocus the highlighter for the current window (may be null).
	 */
	public void highlighterLostFocus(Highlighter highlighterGainedFocus)
	{
		if (!isVisible()) return;
		List<Geometric> artObjects = findSelectedArt();
		showArtworkObjects(artObjects);
	}

	private void applyDialog()
	{
		if (li == null) return;
        EGraphics graphics = li.updateGraphics(li.graphics);
		if (graphics != li.graphics)
		{
			int transparent = graphics.getTransparentLayer();
			Color newColor = graphics.getColor();
			int index = -1;
			if (transparent != 0 || newColor != Color.BLACK)
			{
				if (transparent > 0) index = EGraphics.makeIndex(transparent); else
					index = EGraphics.makeIndex(newColor);
			}

			// set the stipple pattern if specified
			Integer [] pat = null;
			if (graphics.isPatternedOnDisplay())
			{
				// set the pattern
				int [] pattern = graphics.getPattern();
				pat = new Integer[17];
				for(int i=0; i<16; i++)
					pat[i] = new Integer(pattern[i]);
				pat[16] = new Integer(graphics.getOutlined().getIndex());
			}

			// change the objects
			new ApplyChanges(artworkObjects, index, pat);
		}
	}

	/**
	 * Class to update graphics on an artwork node or arc.
	 */
	private static class ApplyChanges extends Job
	{
		private List<Geometric> artworkObjects;
		private int index;
		private Integer [] pat;

		protected ApplyChanges(List<Geometric> artworkObjects, int index, Integer [] pat)
		{
			super("Change Artwork Appearance", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.artworkObjects = artworkObjects;
			this.index = index;
			this.pat = pat;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			for(Geometric eObj : artworkObjects)
			{
				if (index == -1)
				{
					if (eObj.getVarValue(Artwork.ART_COLOR, Integer.class) != null)
						eObj.delVar(Artwork.ART_COLOR);
				} else
				{
					eObj.newVar(Artwork.ART_COLOR, new Integer(index), ep);
				}
				if (pat != null)
				{
					// set the pattern
					eObj.newVar(Artwork.ART_PATTERN, pat, ep);
				} else
				{
					if (eObj.getVar(Artwork.ART_PATTERN) != null)
						eObj.delVar(Artwork.ART_PATTERN);
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
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        apply = new javax.swing.JButton();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Artwork Color and Pattern");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });

        cancel.setText("Cancel");
        cancel.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cancel(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gridBagConstraints);

        ok.setText("OK");
        ok.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                ok(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        apply.setText("Apply");
        apply.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                applyActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(apply, gridBagConstraints);

        pack();
    }
    // </editor-fold>//GEN-END:initComponents

    private void applyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyActionPerformed
		applyDialog();
    }//GEN-LAST:event_applyActionPerformed

	private void cancel(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancel
	{//GEN-HEADEREND:event_cancel
		closeDialog(null);
	}//GEN-LAST:event_cancel

	private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
	{//GEN-HEADEREND:event_ok
		applyDialog();
		closeDialog(null);
	}//GEN-LAST:event_ok

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		Highlighter.removeHighlightListener(this);
		dispose();
		theDialog = null;
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton apply;
    private javax.swing.JButton cancel;
    private javax.swing.JButton ok;
    // End of variables declaration//GEN-END:variables
}
