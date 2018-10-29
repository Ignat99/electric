/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDSReader.java
 * Input/output tool: GDS input
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io;

import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.tool.Job;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class does low-level reading of GDS files.
 */
public class GDSReader
{
	// data declarations
	private static class DatatypeSymbol {}
	private static final DatatypeSymbol TYPEERR    = new DatatypeSymbol();
	private static final DatatypeSymbol TYPENONE   = new DatatypeSymbol();
	private static final DatatypeSymbol TYPEFLAGS  = new DatatypeSymbol();
	private static final DatatypeSymbol TYPESHORT  = new DatatypeSymbol();
	private static final DatatypeSymbol TYPELONG   = new DatatypeSymbol();
	private static final DatatypeSymbol TYPEFLOAT  = new DatatypeSymbol();
	private static final DatatypeSymbol TYPEDOUBLE = new DatatypeSymbol();
	private static final DatatypeSymbol TYPESTRING = new DatatypeSymbol();

	private final static double twoTo32 = makePower(2, 32);
	private final static double twoToNeg56 = 1.0 / makePower (2, 56);

	private DataInputStream dataInputStream;
	private DatatypeSymbol  valuetype;
	private long            fileLength;
	private String          filePath;
	private short           dataWord;
	private byte            recordType, dataType;
	private long            byteCount;
	private int             recordCount;
	private int             tokenFlags;
	private int             tokenValue16;
	private int             tokenValue32;
	private double          tokenValueDouble;
	private String          tokenString;
	private GSymbol         theToken;

	/**
	 * Class to define the types of objects found in a GDS file.
	 */
	public static class GSymbol
	{
		private static List<GSymbol> symbols = new ArrayList<GSymbol>();

		private GSymbol(int value)
		{
			while (symbols.size() <= value) symbols.add(null);
			symbols.set(value, this);
		}

		private static GSymbol findSymbol(int value)
		{
			if (value >= 0 && value < symbols.size()) return symbols.get(value);
			return null;
		}
	}
	public static final GSymbol GDS_HEADER       = new GSymbol(0);
	public static final GSymbol GDS_BGNLIB       = new GSymbol(1);
	public static final GSymbol GDS_LIBNAME      = new GSymbol(2);
	public static final GSymbol GDS_UNITS        = new GSymbol(3);
	public static final GSymbol GDS_ENDLIB       = new GSymbol(4);
	public static final GSymbol GDS_BGNSTR       = new GSymbol(5);
	public static final GSymbol GDS_STRNAME      = new GSymbol(6);
	public static final GSymbol GDS_ENDSTR       = new GSymbol(7);
	public static final GSymbol GDS_BOUNDARY     = new GSymbol(8);
	public static final GSymbol GDS_PATH         = new GSymbol(9);
	public static final GSymbol GDS_SREF         = new GSymbol(10);
	public static final GSymbol GDS_AREF         = new GSymbol(11);
	public static final GSymbol GDS_TEXTSYM      = new GSymbol(12);
	public static final GSymbol GDS_LAYER        = new GSymbol(13);
	public static final GSymbol GDS_DATATYPSYM   = new GSymbol(14);
	public static final GSymbol GDS_WIDTH        = new GSymbol(15);
	public static final GSymbol GDS_XY           = new GSymbol(16);
	public static final GSymbol GDS_ENDEL        = new GSymbol(17);
	public static final GSymbol GDS_SNAME        = new GSymbol(18);
	public static final GSymbol GDS_COLROW       = new GSymbol(19);
	public static final GSymbol GDS_TEXTNODE     = new GSymbol(20);
	public static final GSymbol GDS_NODE         = new GSymbol(21);
	public static final GSymbol GDS_TEXTTYPE     = new GSymbol(22);
	public static final GSymbol GDS_PRESENTATION = new GSymbol(23);
//	public static final GSymbol GDS_SPACING      = new GSymbol(24);
	public static final GSymbol GDS_STRING       = new GSymbol(25);
	public static final GSymbol GDS_STRANS       = new GSymbol(26);
	public static final GSymbol GDS_MAG          = new GSymbol(27);
	public static final GSymbol GDS_ANGLE        = new GSymbol(28);
//	public static final GSymbol GDS_UINTEGER     = new GSymbol(29);
//	public static final GSymbol GDS_USTRING      = new GSymbol(30);
	public static final GSymbol GDS_REFLIBS      = new GSymbol(31);
	public static final GSymbol GDS_FONTS        = new GSymbol(32);
	public static final GSymbol GDS_PATHTYPE     = new GSymbol(33);
	public static final GSymbol GDS_GENERATIONS  = new GSymbol(34);
	public static final GSymbol GDS_ATTRTABLE    = new GSymbol(35);
//	public static final GSymbol GDS_STYPTABLE    = new GSymbol(36);
//	public static final GSymbol GDS_STRTYPE      = new GSymbol(37);
	public static final GSymbol GDS_ELFLAGS      = new GSymbol(38);
//	public static final GSymbol GDS_ELKEY        = new GSymbol(39);
//	public static final GSymbol GDS_LINKTYPE     = new GSymbol(40);
//	public static final GSymbol GDS_LINKKEYS     = new GSymbol(41);
	public static final GSymbol GDS_NODETYPE     = new GSymbol(42);
	public static final GSymbol GDS_PROPATTR     = new GSymbol(43);
	public static final GSymbol GDS_PROPVALUE    = new GSymbol(44);
	public static final GSymbol GDS_BOX          = new GSymbol(45);
	public static final GSymbol GDS_BOXTYPE      = new GSymbol(46);
	public static final GSymbol GDS_PLEX         = new GSymbol(47);
	public static final GSymbol GDS_BGNEXTN      = new GSymbol(48);
	public static final GSymbol GDS_ENDEXTN      = new GSymbol(49);
//	public static final GSymbol GDS_TAPENUM      = new GSymbol(50);
//	public static final GSymbol GDS_TAPECODE     = new GSymbol(51);
//	public static final GSymbol GDS_STRCLASS     = new GSymbol(52);
//	public static final GSymbol GDS_NUMTYPES     = new GSymbol(53);
	public static final GSymbol GDS_IDENT        = new GSymbol(54);
	public static final GSymbol GDS_REALNUM      = new GSymbol(55);
	public static final GSymbol GDS_SHORT_NUMBER = new GSymbol(56);
	public static final GSymbol GDS_NUMBER       = new GSymbol(57);
	public static final GSymbol GDS_FLAGSYM      = new GSymbol(58);
	public static final GSymbol GDS_FORMAT       = new GSymbol(59);
	public static final GSymbol GDS_MASK         = new GSymbol(60);
	public static final GSymbol GDS_ENDMASKS     = new GSymbol(61);

	/**
	 * Creates a new instance of GDSReader.
	 */
	public GDSReader(String filePath, DataInputStream dataInputStream, long fileLength)
	{
		this.filePath = filePath;
		this.dataInputStream = dataInputStream;
		this.fileLength = fileLength;
		byteCount = 0;
		recordCount = 0;
	}

	/**
	 * Method to read the header of the next GDS object.
	 * Depending on the nature of the object, there may need to be additional calls to this to get the "parameters" of the GDS object.
	 * @throws Exception if the GDS file is corrupt.
	 */
	public void getToken()
		throws Exception
	{
		if (recordCount == 0)
		{
			valuetype = readRecord();
		} else
		{
			if (valuetype == TYPEFLAGS)
			{
				tokenFlags = getWord();
				theToken = GDS_FLAGSYM;
				return;
			}
			if (valuetype == TYPESHORT)
			{
				tokenValue16 = getWord();
				theToken = GDS_SHORT_NUMBER;
				return;
			}
			if (valuetype == TYPELONG)
			{
				tokenValue32 = getInteger();
				theToken = GDS_NUMBER;
				return;
			}
			if (valuetype == TYPEFLOAT)
			{
				tokenValueDouble = getFloat();
				theToken = GDS_REALNUM;
				return;
			}
			if (valuetype == TYPEDOUBLE)
			{
				tokenValueDouble = getDouble();
				theToken = GDS_REALNUM;
				return;
			}
			if (valuetype == TYPESTRING)
			{
				tokenString = getString();
				theToken = GDS_IDENT;
				return;
			}
			if (valuetype == TYPEERR) handleError("Invalid GDS II datatype");
		}
	}

	/**
	 * Method to tell the type of GDS object that was just read by "getToken()".
	 * @return the type of GDS object that was just read by "getToken()".
	 */
	public GSymbol getTokenType() { return theToken; }

	/**
	 * Method to tell how many bytes remain in the GDS object being read.
	 * For simple GDS objects, there are no data bytes.
	 * For more complex GDS objects, data follows and additional calls to "getToken()"
	 * will read those values.
	 * @return the number of bytes remaining in the GDS object being read.
	 */
	public int getRemainingDataCount() { return recordCount; }

	public int getFlagsValue() { return tokenFlags; }

	public int getShortValue() { return tokenValue16; }

	public int getIntValue() { return tokenValue32; }

	public double getDoubleValue() { return tokenValueDouble; }

	public String getStringValue() { return tokenString; }

	/**
	 * Low-level method to return the first 16 bits of the GDS object header that was just read by "getToken()".
	 * @return the first 16 bits of the GDS object header that was just read by "getToken()".
	 */
	public short getLastDataWord() { return dataWord; }

	/**
	 * Low-level method to return the 8-bit record type in the GDS object header that was just read by "getToken()".
	 * @return the 8-bit record type in the GDS object header that was just read by "getToken()".
	 */
	public byte getLastRecordType() { return recordType; }

	/**
	 * Low-level method to return the 8-bit data type in the GDS object header that was just read by "getToken()".
	 * @return the 8-bit data type in the GDS object header that was just read by "getToken()".
	 */
	public byte getLastDataType() { return dataType; }

	/**
	 * Class to report inconsistent GDS.
	 */
	public static class GDSException extends Exception
	{
		private String msg;

		public GDSException(String msg) { this.msg = msg; }

		public String getMessage() { return msg; }
	}

	/**
	 * Error handler for GDS inconsistency.
	 * @param msg the problem with the GDS.
	 * @throws GDSException
	 */
	public void handleError(String msg)
		throws GDSException
	{
		String message = "Error: " + msg + " at byte " + byteCount + " in '" + filePath + "'";
		throw new GDSException(message);
	}

	private DatatypeSymbol readRecord()
		throws Exception
	{
		dataWord = (short)getWord();
		recordCount = dataWord - 2;
		recordType = (byte)(getByte() & 0xFF);
		theToken = GSymbol.findSymbol(recordType);
		dataType = getByte();
		switch (dataType)
		{
			case 0:  return TYPENONE;
			case 1:  return TYPEFLAGS;
			case 2:  return TYPESHORT;
			case 3:  return TYPELONG;
			case 4:  return TYPEFLOAT;
			case 5:  return TYPEDOUBLE;
			case 6:  return TYPESTRING;
		}
		return TYPEERR;
	}

	private float getFloat()
		throws Exception
	{
		int reg = getByte() & 0xFF;
		int sign = 1;
		if ((reg & 0x00000080) != 0) sign = -1;
		reg = reg & 0x0000007F;

		// generate the exponent, currently in Excess-64 representation
		int binary_exponent = (reg - 64) << 2;
		reg = (getByte() & 0xFF) << 16;
		int dataword = getWord();
		reg = reg + dataword;
		int shift_count = 0;

		// normalize the mantissa
		while ((reg & 0x00800000) == 0)
		{
			reg = reg << 1;
			shift_count++;
		}

		// this is the exponent + normalize shift - precision of mantissa
		binary_exponent = binary_exponent - shift_count - 24;

		if (binary_exponent > 0)
		{
			return (float)(sign * reg * makePower(2, binary_exponent));
		}
		if (binary_exponent < 0)
			return (float)(sign * reg / makePower(2, -binary_exponent));
		return sign * reg;
	}

	private static double makePower(int val, int power)
	{
		return Math.pow(val, power);
	}

	private double getDouble()
		throws Exception
	{
		// first byte is the exponent field (hex)
		int register1 = getByte() & 0xFF;

		// plus sign bit
		double realValue = 1;
		if ((register1 & 0x00000080) != 0) realValue = -1.0;

		// the hex exponent is in excess 64 format
		register1 = register1 & 0x0000007F;
		int exponent = register1 - 64;

		// bytes 2-4 are the high ordered bits of the mantissa
		register1 = (getByte() & 0xFF) << 16;
		int dataword = getWord();
		register1 = register1 + dataword;

		// next word completes the mantissa (1/16 to 1)
		int long_integer = getInteger();
		int register2 = long_integer;

		// now normalize the value
		if (register1 != 0 || register2 != 0)
		{
			// check for 0 in the high-order nibble
			while ((register1 & 0x00F00000) == 0)
			{
				// shift the 2 registers by 4 bits
				register1 = (register1 << 4) + (register2>>28);
				register2 = register2 << 4;
				exponent--;
			}
		} else
		{
			// true zero
			return 0;
		}

		// now create the mantissa (fraction between 1/16 to 1)
		realValue *= (register1 * twoTo32 + register2) * twoToNeg56;
		if (exponent > 0)
		{
			double pow =  makePower(16, exponent);
			realValue *= pow;
		} else
		{
			if (exponent < 0)
			{
				double pow =  makePower(16, -exponent);
				realValue /= pow;
			}
		}
		return realValue;
	}

	private String getString()
		throws Exception
	{
		StringBuffer sb = new StringBuffer();
		while (recordCount != 0)
		{
			char letter = (char)getByte();
			if (letter != 0) sb.append(letter);
		}
		return sb.toString();
	}

	private int getInteger()
		throws Exception
	{
		int highWord = getWord();
		int lowWord = getWord();
		return (highWord << 16) | lowWord;
	}

	private int getWord()
		throws Exception
	{
		int highByte = getByte() & 0xFF;
		int lowByte = getByte() & 0xFF;
		return (highByte << 8) | lowByte;
	}

	/**
	 * Method to read the next byte of data from the GDS input stream.
	 * @return the next byte of data from the GDS input stream.
	 * @throws Exception
	 */
	public byte getByte()
		throws Exception
	{
		byte b = dataInputStream.readByte();
		updateProgressDialog(1);
		recordCount--;
		return b;
	}

	/**
	 * Method to read the date/time values (in library and cell headers).
	 * @return the date/time as a string.
	 * @throws Exception
	 */
	public String determineTime()
		throws Exception
	{
		String [] time_array = new String[7];
		for (int i = 0; i < 6; i++)
		{
			if (theToken != GDSReader.GDS_SHORT_NUMBER) handleError("Date value is not a valid number");

			if (i == 0 && getShortValue() < 1900)
			{
				// handle Y2K date issues
				if (getShortValue() > 60) tokenValue16 += 1900; else
					tokenValue16 += 2000;
			}
			time_array[i] = Integer.toString(tokenValue16);
			getToken();
		}
		return time_array[1] + "-" + time_array[2] + "-" + time_array[0] + " at " +
			time_array[3] + ":" + time_array[4] + ":" + time_array[5];
	}

	private void updateProgressDialog(int bytesRead)
	{
		byteCount += bytesRead;
		if (fileLength == 0) return;
		long pct = byteCount * 100L / fileLength;
		UserInterface ui = Job.getUserInterface();
		if (ui != null)
			ui.setProgressValue((int)pct);
	}
}
