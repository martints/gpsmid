/**
 * OSM2GpsMid 
 *  
 *
 *
 * Copyright (C) 2008
 */
package de.ueller.osmToGpsMid.model;

import de.ueller.osmToGpsMid.Configuration;
import java.util.List;


public class WayDescription extends EntityDescription{
	public int		minOnewayArrowScale;
	public int		minDescriptionScale;
	public int		lineColor;
	public int		lineStyle;
	public int		boardedColor;
	public boolean	isArea;
	public int		wayWidth;
	/** Travel Modes (motorcar, bicycle, etc.) supported by this WayDescription (1 bit per travel mode) */
	public byte		wayDescTravelModes;
	/** typical speed of this WayDescription for up to 8 travel modes */
	public int		typicalSpeed[] = new int[8];
	public int		noWaysOfType;
	public byte		forceToLayer;
	
	// line styles
	public final static int LINESTYLE_SOLID = 0x00;  // same as Graphics.SOLID
	public final static int LINESTYLE_DOTTED = 0x01; // same as Graphics.DOTTED;
	public final static int LINESTYLE_RAIL = 0x02;
	public final static int LINESTYLE_STEPS = 0x04;
	public final static int LINESTYLE_POWERLINE = 0x08;
	
	public WayDescription() {
		lineStyle = LINESTYLE_SOLID;
		boardedColor = 0;
		isArea = false;
		wayWidth = 2;
		wayDescTravelModes = 0;
		for (int i = 0; i < TravelModes.travelModeCount; i++) {
			typicalSpeed[i] = 5;
		}
		rulePriority = 0;
	}
	
	
}
