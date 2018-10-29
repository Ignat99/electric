/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ExternalEditing.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.ui;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.TextUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Class to handle editing text cells in an external editor.
 */
public class ExternalEditing
{
	private String cellName, fileName;
	private long lastModified;
	private static List<ExternalEditing> activeExternalEditors = new ArrayList<ExternalEditing>();

	ExternalEditing(String cellName, String fileName)
	{
		this.cellName = cellName;
		this.fileName = fileName;
		File f = new File(fileName);
		lastModified = f.lastModified();
		activeExternalEditors.add(this);
	}

	void finishedEditing()
	{
		activeExternalEditors.remove(this);
	}

	public static void updateEditors()
	{
		for(ExternalEditing ee : activeExternalEditors)
		{
			File f = new File(ee.fileName);
			long lastModified = f.lastModified();
			if (ee.lastModified < lastModified)
			{
				// file has been updated
				System.out.println("Cell " + ee.cellName + " changed in the external text editor");
				ee.updateFromExternalEditor(false);
				ee.lastModified = lastModified;
			}
		}
	}

	/**
	 * Method to edit the current text-cell in an external editor.
	 * Invoked from the "Edit / Text / Edit Text Cell Externally" command.
	 */
	public static void editExternally()
	{
		// find the current text-edit window
		TextWindow tw = null;
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf != null && wf.getContent() instanceof TextWindow)
			tw = (TextWindow)wf.getContent();
		if (tw == null)
		{
			Job.getUserInterface().showErrorMessage("You must be editing a text cell before editing it externally", "No Text To Edit");
			return;
		}
		Cell cell = tw.getCell();

		// find the external text editor program
		String externalEditor = User.getDefaultTextExternalEditor();
		if (externalEditor.length() == 0)
		{
			Job.getUserInterface().showErrorMessage("No external text editor is defined.  Use the Display/Text Preferences to set one",
				"No External Text Editor Set");
			return;
		}

		// make a temporary file to edit
        String fileName = cell.getName() + "tmp"; // prefix in File.createTempFile must be longer than 2
		File f = null;
        try
        {
            f = File.createTempFile(fileName, getExtension(cell));
        } catch (Exception e)
        {
			Job.getUserInterface().showErrorMessage("Error creating temporary file: " + fileName + " (" + e.getMessage() + ")",
				"Error Creating Temporary Text File");
        }
		if (f == null) return;

		// save the text cell to the temporary file
		fileName = f.getPath();
		if (!tw.writeTextCell(fileName))
		{
			// error writing temporary file
			Job.getUserInterface().showErrorMessage("Could not write temporary file " + fileName,
				"Error Writing Temporary Text File");
			return;
		}

		// now edit the file externally
		(new EditExternally(cell.describe(false), externalEditor, fileName)).start();
	}

	/**
	 * Method to edit a given text-cell in an external editor.
	 * Invoked from the explorer window.
	 */
	public static void editTextCellExternally(Cell cell)
	{
		// find the external text editor program
		String externalEditor = User.getDefaultTextExternalEditor();
		if (externalEditor.length() == 0)
		{
			Job.getUserInterface().showErrorMessage("No external text editor is defined.  Use the Display/Text Preferences to set one",
				"No Text Editor Set");
			return;
		}

		// make a temporary file to edit
		File f = null;
        String fileName = cell.getName() + "tmp"; // prefix in File.createTempFile must be longer than 2
        try
        {
            f = File.createTempFile(fileName, getExtension(cell));
        } catch (Exception e)
        {
			Job.getUserInterface().showErrorMessage("Error creating temporary file: " + fileName + " (" + e.getMessage() + ")",
				"Error Creating Temporary Text File");
        }
		if (f == null) return;

		// save the text cell to the temporary file
		fileName = f.getPath();
        String[] lines = cell.getTextViewContents();
        
        try
        {
            PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(f)));
            if (lines != null)
            {
	            for(int i=0; i<lines.length; i++)
	                printWriter.println(lines[i]);
            }
            printWriter.close();
        } catch (IOException e)
        {
			Job.getUserInterface().showErrorMessage("Could not write temporary file " + fileName,
				"Error Writing Temporary Text File");
            return;
        }

        // now edit the file externally
		(new EditExternally(cell.describe(false), externalEditor, fileName)).start();
	}

	private static String getExtension(Cell cell)
	{
		if (cell.getView() == View.VERILOG) return ".v";
		if (cell.getView() == View.DOC) return ".doc";
		if (cell.getView() == View.VHDL) return ".vhdl";
		return ".tmp";
	}

	/**
	 * Class to do external text editing in a separate thread, allowing Electric to continue to run.
	 */
	private static class EditExternally extends Thread
	{
		private ExternalEditing ee;
		private String externalEditor;

		EditExternally(String cellName, String externalEditor, String fileName)
		{
			ee = new ExternalEditing(cellName, fileName);
			this.externalEditor = externalEditor;
		}

		public void run()
		{
			String commandString;
			if (ClientOS.isOSWindows())
			{
				commandString = "cmd /c \"" + externalEditor + "\" " + ee.fileName;
			} else if (ClientOS.isOSMac())
			{
				// MacOS box only allows the selection of *.app programs.
				int index = externalEditor.indexOf(".app"); // like TextEdit.app
				if (index != -1)
				{
					String rootName = externalEditor.substring(0, index);
					int ind2 = rootName.lastIndexOf("/");
					if (ind2 != -1) // remove all /
						rootName = rootName.substring(ind2, rootName.length());
					commandString = externalEditor + "/Contents/MacOS/" + rootName + " " + ee.fileName;
				}
				else
					commandString = externalEditor + " " + ee.fileName;
			} else
			{
				commandString = externalEditor + " " + ee.fileName;
			}
			try
			{
				Process p = Runtime.getRuntime().exec(commandString);
				try
				{
					p.waitFor();
				} catch (InterruptedException e)
				{
					System.out.println("External text editor interrupted: " + e.getMessage());
				}
			} catch (IOException e)
			{
				System.out.println("Error running command: " + commandString + " (" + e.getMessage() + ")");
			}

			// now retrieve the temporary text file
	        SwingUtilities.invokeLater(new Runnable() {
	            public void run() { ee.endExternalEditing(); }
	        });
		}
	}

	/**
	 * Method invoked after external text editing is finished.
	 * Retrieves the file and saves it in the text window (if it is being edited)
	 * or in the Cell (if not being edited).
	 * @param fileName the name of the temporary file being edited.
	 * @param cellName the name of the Cell being edited.
	 */
	private void endExternalEditing()
	{
		// remove from list of active external editors
		finishedEditing();

    	updateFromExternalEditor(true);
	}

	private void updateFromExternalEditor(boolean deleteTempFile)
	{
		Cell cell = (Cell)Cell.findNodeProto(cellName);
    	if (cell == null)
    	{
			Job.getUserInterface().showErrorMessage("Could not find the text cell " + cellName +
				" so external edits are being ignored", "Error Recovering Temporary Text File");
    		return;
    	}
    	for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
    	{
    		WindowFrame wf = it.next();
    		if (wf.getContent().getCell() == cell)
    		{
    			new UpdateTextWindow(fileName, cell, deleteTempFile);
    	        return;
    		}
    	}
    	new UpdateTextCell(fileName, cell, deleteTempFile);
	}

	/**
     * Class to retrieve a text window after editing it externally.
     */
    private static class UpdateTextWindow extends Job
    {
        private String fileName;
        private Cell cell;
        private boolean deleteTempFile;

        private UpdateTextWindow(String fileName, Cell cell, boolean deleteTempFile)
        {
            super("Update text window after external editing", User.getUserTool(), Job.Type.CLIENT_EXAMINE, null, null, Job.Priority.USER);
            this.fileName = fileName;
            this.cell = cell;
            this.deleteTempFile = deleteTempFile;
            startJob();
        }

        public boolean doIt() throws JobException
        {
        	for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
        	{
        		WindowFrame wf = it.next();
        		if (wf.getContent().getCell() == cell)
        		{
        			TextWindow tw = (TextWindow)wf.getContent();
        			tw.readTextCell(fileName);
        			tw.goToLineNumber(1);

        			if (deleteTempFile)
        			{
	        			File f = new File(fileName);
	        	        if (!f.delete())
	        	            System.out.println("WARNING: Failed to delete temporary file " + fileName);
        			}
        	        break;
        		}
        	}
            return true;
        }
    }

	/**
     * Class to retrieve a text cell after editing it externally.
     */
    private static class UpdateTextCell extends Job
    {
        private String fileName;
        private Cell cell;
        private boolean deleteTempFile;

        private UpdateTextCell(String fileName, Cell cell, boolean deleteTempFile)
        {
            super("Update text cell after external editing", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.fileName = fileName;
            this.cell = cell;
            this.deleteTempFile = deleteTempFile;
            startJob();
        }

        @Override
        public boolean doIt() throws JobException
        {
            EditingPreferences ep = getEditingPreferences();
            List<String> linesInFile = new ArrayList<String>();
            URL fileURL = TextUtils.makeURLToFile(fileName);
            InputStream stream = TextUtils.getURLStream(fileURL);
            if (stream == null)
            {
            	Job.getUserInterface().showErrorMessage("Could not find temporary file: " + fileURL.getFile(),
            		"Error Retrieving Temporary File");
                return true;
            }
            try
            {
                fileURL.openConnection();
                InputStreamReader is = new InputStreamReader(stream);
        		LineNumberReader lineReader = new LineNumberReader(is);
        		for(;;)
        		{
        			String buf = lineReader.readLine();
        			if (buf == null) break;
        			linesInFile.add(buf);
        		}
            } catch (IOException e)
            {
            	Job.getUserInterface().showErrorMessage("Could not read back temporary file: " + fileURL.getFile() +
            		" (" + e.getMessage() + ")", "Error Retrieving Temporary File");
                return true;
            }

            String[] lines = new String[linesInFile.size()];
            for(int i=0; i<linesInFile.size(); i++) lines[i] = linesInFile.get(i);
            cell.setTextViewContents(lines, ep);

			if (deleteTempFile)
			{
    			File f = new File(fileName);
    	        if (!f.delete())
    	            System.out.println("WARNING: Failed to delete temporary file " + fileName);
			}
            return true;
        }
    }

}
