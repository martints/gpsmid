/*
 * GpsMid - Copyright (c) 2009 Kai Krueger apmonkey at users dot sourceforge dot net 
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * See COPYING
 */
package de.ueller.midlet.gps.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import de.ueller.midlet.gps.Logger;
import de.ueller.midlet.gps.data.CompassProvider;
import de.ueller.midlet.gps.data.SocketGateway;

//#if polish.android
import android.content.Context;
import de.enough.polish.android.midlet.MidletBridge;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
//#endif

import de.enough.polish.util.Locale;

public class CellIdProvider {
	private static final int CELLMETHOD_NONE = 0;
	private static final int CELLMETHOD_SE = 1;
	private static final int CELLMETHOD_S60FP2 = 2;
	private static final int CELLMETHOD_MOTO = 3;
	private static final int CELLMETHOD_SOCKET = 4;
	private static final int CELLMETHOD_DEBUG = 5;
	private static final int CELLMETHOD_ANDROID = 6;
	private static final int CELLMETHOD_SAMSUNG = 7;
	
	private static CellIdProvider singelton;
	
	private static final Logger logger = Logger.getInstance(CellIdProvider.class,
			Logger.TRACE);
	
	private int cellRetrievelMethod = -1;
	
	GSMCell cachedCell = null;
	
	private CellIdProvider() {
		//#debug info
		logger.info("Trying to find a suitable cell id provider");
		//#if polish.android
		try {
			//#debug info
			logger.info("Trying to see if android method is available");
			GSMCell cell = obtainAndroidCell();
			if (cell != null) {
				cellRetrievelMethod = CELLMETHOD_ANDROID;
				//#debug info
				logger.info("   Yes, the Android method works");
				return;
			} else {
				//#debug info
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Retrieving CellID as Android failed", e);
			//Nothing to do here, just fall through to the next method
		}
		//#endif
		try {
			//#debug info
			logger.info("Trying to see if Sony-Ericcson method is available");
			GSMCell cell = obtainSECell();
			if (cell != null) {
				cellRetrievelMethod = CELLMETHOD_SE;
				//#debug info
				logger.info("   Yes, the Sony-Ericcsson method works");
				return;
			} else {
				//#debug info
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Retrieving CellID as a Sony-Ericsson failed", e);
			//Nothing to do here, just fall through to the next method
		}
		try {
			//#debug info
			logger.info("Trying to see if Motorola method is available");
			GSMCell cell = obtainMotoOrSamsungCell(false);
			if (cell != null) {
				logger.error(Locale.get("cellidprovider.MotorolaCellIDPleseCheck")/*Motorola CellID is experimental and may be wrong. Please check data before uploading*/);
				cellRetrievelMethod = CELLMETHOD_MOTO;
				//#debug info
				logger.info("   Yes, the Motorola method works");
				return;
			} else {
				//#debug info
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Retrieving CellID as a Motorola failed", e);
			//Nothing to do here, just fall through to the next method
		}
		try {
			//#debug info
			logger.info("Trying to see if Samsung method is available");
			GSMCell cell = obtainMotoOrSamsungCell(true);
			if (cell != null) {
				logger.error(Locale.get("cellidprovider.MotorolaCellIDPleseCheck")/*Motorola CellID is experimental and may be wrong. Please check data before uploading*/);
				cellRetrievelMethod = CELLMETHOD_SAMSUNG;
				//#debug info
				logger.info("   Yes, the Samsung method works");
				return;
			} else {
				//#debug info
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Retrieving CellID as a Samsung failed", e);
			//Nothing to do here, just fall through to the next method
		}
		try {
			//#debug info
			logger.info("Trying to see if there is a cellid server running on this device");
			GSMCell cell = obtainSocketCell();
			// FIXME 
			// cellRetrievelMethod = CELLMETHOD_SOCKET;
			if (cell != null) {
				cellRetrievelMethod = CELLMETHOD_SOCKET;
				logger.info("   Yes, there is a server running and we can get a cell from it");
				return;
			} else {
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Could not connect to socket", e);
			//Nothing to do here, just fall through to the next method
		}
		
		try {
			//#debug info
			logger.info("Trying to see if S60 3rd FP2 method is available");
			GSMCell cell = obtainS60FP2Cell();
			if (cell != null) {
				cellRetrievelMethod = CELLMETHOD_S60FP2;
				logger.info("   Yes, the S60 3rd FP2 method works");
				return;
			} else {
				logger.info("   No, need to use a different method");
			}
		} catch (Exception e) {
			logger.silentexception("Retrieving CellID as a Nokia S60 3rd FP2 failed", e);
		}
		cellRetrievelMethod = CELLMETHOD_NONE;
		//#debug info
		logger.error(Locale.get("cellidprovider.NoCellIDUsable")/*No method of retrieving CellID is valid, can not use CellID*/);
		
	}
	
	public synchronized static CellIdProvider getInstance() {
		if (singelton == null) {
			singelton = new CellIdProvider();
		}
		return singelton;
	}
	
	//#if polish.android
	private GSMCell obtainAndroidCell() {
		GSMCell cell = new GSMCell();
		
		TelephonyManager tm  = 
			(TelephonyManager) MidletBridge.instance.getSystemService(Context.TELEPHONY_SERVICE); 
		GsmCellLocation location = (GsmCellLocation) tm.getCellLocation();
		cell.cellID = location.getCid();
                cell.lac = location.getLac();

		String networkOperator = tm.getNetworkOperator();
		if (networkOperator != null && networkOperator.length() > 0) {
			try {
				cell.mcc = (short) Integer.parseInt(networkOperator.substring(0, 3));
				cell.mnc = (short) Integer.parseInt(networkOperator.substring(3));
			} catch (NumberFormatException e) {
			}
		}

		if (location == null) {
			//#debug debug
			logger.debug("No valid cell-id");
			return null;
		}
		
		return cell;
	}
	//#endif
	
	private GSMCell obtainSECell() {
		String cellidS = null;
		String mccS = null;
		String mncS = null;
		String lacS = null;
		GSMCell cell = new GSMCell();
		
		cellidS = System.getProperty("com.sonyericsson.net.cellid");
		mccS = System.getProperty("com.sonyericsson.net.cmcc");
		mncS = System.getProperty("com.sonyericsson.net.cmnc");
		lacS = System.getProperty("com.sonyericsson.net.lac");
		
		if ((cellidS == null) || (mccS == null) || (mncS == null) || (lacS == null)) {
			//#debug debug
			logger.debug("No valid cell-id");
			return null;
		}
		
		try {
			cell.cellID = Integer.parseInt(cellidS, 16);
			cell.mcc = (short) Integer.parseInt(mccS);
			cell.mnc = (short) Integer.parseInt(mncS);
			cell.lac = Integer.parseInt(lacS, 16);
		} catch (NumberFormatException nfe) {
			logger.silentexception("Failed to parse cell-id (cellid: " + cellidS +
					" mcc: " + mccS + " mnc: " + mncS + " lac: " + lacS, nfe);
			return null;
		}
		
		return cell;
	}
	
	private GSMCell obtainS60FP2Cell() {
		String cellidS = null;
		String mccS = null;
		String mncS = null;
		String lacS = null;
		GSMCell cell = new GSMCell();
		cellidS = System.getProperty("com.nokia.mid.cellid");
		/**
		 * The documentation claims that the country code is returned as
		 * two letter iso country code, but at least my phone Nokia 6220 seems
		 * to return the mcc instead, so assume this gives the mcc for the moment. 
		 */
		mccS = System.getProperty("com.nokia.mid.countrycode");
		mncS = System.getProperty("com.nokia.mid.networkid");
		if (mncS.indexOf(" ") > 0) {
			mncS = mncS.substring(0, mncS.indexOf(" "));
		}
		//System.getProperty("com.nokia.mid.networksignal");
		/*
		 * Lac is not currently supported for S60 devices
		 * The com.nokia.mid.lac comes from S40 devices.
		 * We include this here for the moment, in the hope
		 * that future software updates will include this into
		 * S60 as well.
		 * 
		 * The LAC is needed to uniquely identify cells, but openCellID
		 * seems to do a lookup ignoring LAC at first and only using it
		 * if there are no results. So for retreaving Cells, not having
		 * the LAC looks ok, but we won't be able to submit new cells
		 * with out the LAC
		 */
		lacS = System.getProperty("com.nokia.mid.lac");
		
		if ((cellidS == null) || (mccS == null) || (mncS == null)) {
			//#debug debug
			logger.debug("No valid cell-id");
			return null;
		}
		
		try {
			cell.cellID = Integer.parseInt(cellidS);
			cell.mcc = (short) Integer.parseInt(mccS);
			cell.mnc = (short) Integer.parseInt(mncS);
			if (lacS != null) {
				//#debug info
				logger.info("This Nokia device supports LAC! Please report this to GpsMid so that we can correctly use the LAC");
			}
		} catch (NumberFormatException nfe) {
			logger.silentexception("Failed to parse cell-id (cellid: " + cellidS +
					" mcc: " + mccS + " mnc: " + mncS + " lac: " + lacS, nfe);
			return null;
		}
		
		return cell;
	}
	
	private GSMCell obtainMotoOrSamsungCell(boolean samsung) {
		String cellidS = null;
		String mccS = null;
		String mncS = null;
		String lacS = null;
		String imsi = null;
		GSMCell cell = new GSMCell();
		if (samsung) {
			cellidS = System.getProperty("CELLID");
			lacS = System.getProperty("LAC");
		} else {
			cellidS = System.getProperty("CellID");
			lacS = System.getProperty("LocAreaCode");
		}
		
		/*
		 * This method of getting MNC and MCC seems
		 * highly problematic, as it will produce
		 * broken data when abroad or otherwise
		 * roaming outside the home network.
		 * I hope this won't cause corrupt data
		 * 
		 * Also, it seems that not all networks use
		 * the same format for the imsi. Some have
		 * a two digit mnc and some a three digit mnc
		 * So this method will fail in some countries.
		 */
		imsi  = System.getProperty("IMSI");
		if (imsi != null) {
			mccS  = imsi.substring(0,3);
			mncS  = imsi.substring(3,5);
		}
		if ((cellidS == null) || (mccS == null) || (mncS == null)) {
			//#debug debug
			logger.debug("No valid cell-id");
			return null;
		}
		
		try {
			cell.cellID = Integer.parseInt(cellidS);
			cell.mcc = (short) Integer.parseInt(mccS);
			cell.mnc = (short) Integer.parseInt(mncS);
			cell.lac = Integer.parseInt(lacS);
		} catch (NumberFormatException nfe) {
			logger.silentexception("Failed to parse cell-id (cellid: " + cellidS +
					" mcc: " + mccS + " mnc: " + mncS + " lac: " + lacS, nfe);
			return null;
		}
		
		return cell;
	}
	
	private GSMCell obtainSocketCell() {
		int retval;
		retval = SocketGateway.getSocketData(SocketGateway.TYPE_CELLID);
		if (retval == SocketGateway.RETURN_OK) {
			return SocketGateway.getCell();
		}
		if (cellRetrievelMethod == CELLMETHOD_SOCKET && retval == SocketGateway.RETURN_IOE) {
			/*
			 * The local helper daemon seems to have died.
			 * No point in trying to continue trying,
			 * as otherwise we will get an exception every time
			 */
			//cellRetrievelMethod = CELLMETHOD_NONE;
			return null;
		}
		return null;
	}
	
	private GSMCell obtainDebugCell() {
		/*
		 * This code is used for debugging cell-id data on the emulator
		 * by generating one of 16 random cell-ids 
		 */
		String cellidS = null;
		short mcc = 0;
		short mnc = 0;
		int lac = 0;
		
		Random r = new Random();
		int rr = r.nextInt(16) + 1;
//		System.out.println("RR: " +rr);
		switch (rr) {
		case 1:
			cellidS = "2627"; mcc = 234; mnc = 33; lac = 0x133;
			break;
		case 2:
			cellidS = "2628"; mcc = 234; mnc = 33; lac = 0x133;
			break;
		case 3:
			cellidS = "2629"; mcc = 234; mnc = 33; lac = 0x133;
			break;
		case 4:
			cellidS = "2620"; mcc = 234; mnc = 33; lac = 0x134;
			break;
		case 5:
			cellidS = "2619"; mcc = 234; mnc = 33; lac = 0x134;
			break;
		case 6:
			cellidS = "2629"; mcc = 234; mnc = 33; lac = 0x135;
			break;
		case 7:
			cellidS = "2649"; mcc = 234; mnc = 33; lac = 0x136;
			break;
		case 8:
			cellidS = "2659"; mcc = 234; mnc = 33; lac = 0x137;
			break;
		case 9:
			cellidS = "B1D1"; mcc = 310; mnc = 260; lac = 0xB455;
			break;
		case 10:
			cellidS = "79D9"; mcc = 310; mnc = 260; lac = 0x4D;
			break;
		
		case 11:
			cellidS = "3E92FFF"; mcc = 284; mnc = 3; lac = 0x3E9;
			break;
		case 12:
			cellidS = "1B0"; mcc = 250; mnc = 20; lac = 0x666D;
			break;
		case 13:
			cellidS = "23EC45A"; mcc = 234; mnc = 10; lac = 0x958C;
			break;
		case 14:
			cellidS = "8589A"; mcc = 234; mnc = 10; lac = 0x8139;
			break;
		case 15:
			cellidS = "85A67"; mcc = 234; mnc = 10; lac = 0x8139;
			break;
		case 16:
			cellidS = "151E"; mcc = 724; mnc = 5; lac = 0x552;
			break;
		}
		
		if (cellidS == null) {
			//#debug debug
			logger.debug("No valid cell-id");
			return null;
		}
		
		try {
			return new GSMCell(Integer.parseInt(cellidS, 16), mcc, mnc, lac);
		} catch (NumberFormatException nfe) {
			logger.silentexception("Failed to parse cell-id (cellid: " + cellidS +
					" mcc: " + mcc + " mnc: " + mnc + " lac: " + Integer.toHexString(lac), nfe);
			return null;
		}
	}
	
	public GSMCell obtainCachedCellID() {
		return cachedCell;
	}
	
	public GSMCell obtainCurrentCellId() throws Exception {
		
		//#debug info
		logger.info("Tring to retrieve cell-id");
		
		if (cellRetrievelMethod ==  CELLMETHOD_NONE) {
			//#debug info
			logger.info("Can't retrieve CellID, as there is no valid method available");
			return null;
		}

		if (cellRetrievelMethod == CELLMETHOD_SE) {
			cachedCell =  obtainSECell();
		}
		//#if polish.android
		if (cellRetrievelMethod == CELLMETHOD_ANDROID) {
			cachedCell =  obtainAndroidCell();
		}
		//#endif
		if (cellRetrievelMethod == CELLMETHOD_MOTO) {
			cachedCell =  obtainMotoOrSamsungCell(false);
		}
		if (cellRetrievelMethod == CELLMETHOD_SAMSUNG) {
			cachedCell =  obtainMotoOrSamsungCell(true);
		}
		if (cellRetrievelMethod == CELLMETHOD_S60FP2) {
			cachedCell = obtainS60FP2Cell();
		}
		if (cellRetrievelMethod == CELLMETHOD_SOCKET) {
			cachedCell = obtainSocketCell();
		}
		if (cellRetrievelMethod == CELLMETHOD_DEBUG) {
			cachedCell = obtainDebugCell();
		}
		//#debug debug
		logger.debug("Retrieved " + cachedCell);
		return cachedCell;
	}

}
