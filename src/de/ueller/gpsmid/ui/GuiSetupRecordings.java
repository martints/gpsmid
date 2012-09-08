/*
 * GpsMid - Copyright (c) 2011 GpsMid project
 * See COPYING
 */

package de.ueller.gpsmid.ui;

import javax.microedition.lcdui.*;

import de.enough.polish.util.Locale;

import de.ueller.gpsmid.data.Configuration;

/**
 * Setup screen for GPS recording related settings.
 * This was just pulled out from the overcrowded class GuiDiscover.
 */
public class GuiSetupRecordings extends Form implements CommandListener {
	private final ChoiceGroup choiceGpxRecordRuleMode;
	private final ChoiceGroup choiceWptInTrack;
	private final ChoiceGroup gpxOptsGroup;
	private final TextField tfGpxRecordMinimumSecs;
	private final TextField tfGpxRecordMinimumDistanceMeters;
	private final TextField tfGpxRecordAlwaysDistanceMeters;
	//#if false
	private final ChoiceGroup otherOpts;
	//#endif

	// Commands
	private static final Command CMD_SAVE = new Command(Locale.get("generic.Save")/*Save*/, 
			GpsMidMenu.OK, 1);
	private static final Command CMD_CANCEL = new Command(Locale.get("generic.Cancel")/*Cancel*/, 
			GpsMidMenu.BACK, 2);
	
	// Other
	private final GpsMidDisplayable parent;
	
	
	public GuiSetupRecordings(GpsMidDisplayable parent) {
		//Prepare recording options selection menu
		super(Locale.get("guidiscover.RecordingRules")/*Recording Rules*/);
		this.parent = parent;

		String [] recModes = new String[2];
		recModes[0] = Locale.get("guidiscover.adaptivetospeed")/*adaptive to speed*/;
		recModes[1] = Locale.get("guidiscover.manualrules")/*manual rules:*/;
		choiceGpxRecordRuleMode = new ChoiceGroup(Locale.get("guidiscover.RecordTrackpoints")/*Record Trackpoints*/, 
				Choice.EXCLUSIVE, recModes ,null);
		tfGpxRecordMinimumSecs = new TextField(Locale.get("guidiscover.MinimumSeconds")/*Minimum seconds between trackpoints*/, 
				"0", 3, TextField.DECIMAL);
		tfGpxRecordMinimumDistanceMeters = new TextField(Locale.get("guidiscover.MinimumMeters")/*Minimum meters between trackpoints*/, 
				"0", 3, TextField.DECIMAL);
		tfGpxRecordAlwaysDistanceMeters = new TextField(Locale.get("guidiscover.AlwaysRecord")/*Always record when exceeding these meters between trackpoints*/, 
				"0", 3, TextField.DECIMAL);
		
		String [] wptFlag = new String[1];
		wptFlag[0] = Locale.get("guidiscover.WpAlsoInTrack")/*Also put waypoints in track*/;
		choiceWptInTrack = new ChoiceGroup(Locale.get("guidiscover.WaypointsInTrack")/*Waypoints in track*/, 
				Choice.MULTIPLE, wptFlag, null);
		choiceWptInTrack.setSelectedIndex(0, Configuration.getCfgBitSavedState(
				Configuration.CFGBIT_WPTS_IN_TRACK));
		String [] gpxNameOpts = new String[2];
		boolean[] selGpxName = new boolean[2];
		gpxNameOpts[0] = Locale.get("guidiscover.AskTrackNameStart")/*Ask track name at start of recording*/;
		gpxNameOpts[1] = Locale.get("guidiscover.AskTrackNameEnd")/*Ask track name at end of recording*/;
		selGpxName[0] = Configuration.getCfgBitSavedState(Configuration.CFGBIT_GPX_ASK_TRACKNAME_START);
		selGpxName[1] = Configuration.getCfgBitSavedState(Configuration.CFGBIT_GPX_ASK_TRACKNAME_STOP);
		
		gpxOptsGroup = new ChoiceGroup(Locale.get("guidiscover.TrackNaming")/*Track Naming*/, 
				Choice.MULTIPLE, gpxNameOpts ,null);
		gpxOptsGroup.setSelectedFlags(selGpxName);

		choiceGpxRecordRuleMode.setSelectedIndex(Configuration.getGpxRecordRuleMode(), true);
		/* Minimum seconds between trackpoints */
		tfGpxRecordMinimumSecs.setString(
			getCleanFloatString( (float)(Configuration.getGpxRecordMinMilliseconds()) / 1000, 3 )
		);

		/* Minimum meters between trackpoints */
		tfGpxRecordMinimumDistanceMeters.setString(
			getCleanFloatString( (float)(Configuration.getGpxRecordMinDistanceCentimeters()) / 100, 3 )
		);

		/* Meters between trackpoints that will always create a new trackpoint */
		tfGpxRecordAlwaysDistanceMeters.setString(
			getCleanFloatString( (float)(Configuration.getGpxRecordAlwaysDistanceCentimeters()) / 100, 3 )
		);

		append(choiceGpxRecordRuleMode);
		append(tfGpxRecordMinimumSecs);
		append(tfGpxRecordMinimumDistanceMeters);
		append(tfGpxRecordAlwaysDistanceMeters);
		append(gpxOptsGroup);
		append(choiceWptInTrack);

		//#if false
		String [] other = new String[1];
		other[0] = Locale.get("guisetupgui.PredefWpts") /*Predefined way points*/;
		otherOpts = new ChoiceGroup(Locale.get("guisetupgui.OtherOpt") /*Other options:*/, 
				Choice.MULTIPLE, other, null);
		otherOpts.setSelectedIndex(0,
				Configuration.getCfgBitSavedState(Configuration.CFGBIT_WAYPT_OFFER_PREDEF));
		
		append(otherOpts);
		//#endif

		addCommand(CMD_CANCEL);
		addCommand(CMD_SAVE);
		setCommandListener(this);
	}

	/** Converts float f to string and cuts off unnecessary trailing chars and digits.
	 * @param f Float to convert
	 * @param maxlen Maximum length of text field where string will be put
	 * @return f converted to string
	 */
	private String getCleanFloatString(float f, int maxlen) {
		StringBuffer sb = new StringBuffer();
		// convert float to string
		sb.append(Float.toString(f));
		// limit to maximum length of TextField
		sb.setLength(maxlen);
		boolean hasDecimalPoint = sb.toString().indexOf(".") != -1;
		// cut unnecessary trailing chars and digits
		while ((sb.length() > 1) && ((sb.charAt(sb.length() - 1) == '.')
				|| ((sb.charAt(sb.length() - 1) == '0') && hasDecimalPoint))) {
			if (sb.charAt(sb.length() - 1) == '.') {
				hasDecimalPoint = false;
			}
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	public void commandAction(Command c, Displayable d) {
		if (c == CMD_CANCEL) {
			parent.show();
			return;
		}

		if (c == CMD_SAVE) {
			String rule;
			// Save Record Rules to Config
			Configuration.setGpxRecordRuleMode(choiceGpxRecordRuleMode.getSelectedIndex());
			rule = tfGpxRecordMinimumSecs.getString();
			Configuration.setGpxRecordMinMilliseconds(
					rule.length() == 0 ? 0 : (int) (1000 * Float.parseFloat(rule))
			);
			rule = tfGpxRecordMinimumDistanceMeters.getString();
			Configuration.setGpxRecordMinDistanceCentimeters(
					rule.length() == 0 ? 0 : (int) (100 * Float.parseFloat(rule))
			);
			rule = tfGpxRecordAlwaysDistanceMeters.getString();
			Configuration.setGpxRecordAlwaysDistanceCentimeters(
					rule.length() == 0 ? 0 : (int) (100 * Float.parseFloat(rule))
			);
			// Save "waypoints in track" flag to config
			Configuration.setCfgBitSavedState(Configuration.CFGBIT_WPTS_IN_TRACK,
					choiceWptInTrack.isSelected(0));

			boolean[] selGpxName = new boolean[2];
			gpxOptsGroup.getSelectedFlags(selGpxName);
			Configuration.setCfgBitSavedState(Configuration.CFGBIT_GPX_ASK_TRACKNAME_START, selGpxName[0]);
			Configuration.setCfgBitSavedState(Configuration.CFGBIT_GPX_ASK_TRACKNAME_STOP, selGpxName[1]);
			//#if false
			Configuration.setCfgBitSavedState(Configuration.CFGBIT_WAYPT_OFFER_PREDEF,
					otherOpts.isSelected(0));
			//#endif
			parent.show();
		}
	}
	
	public void show() {
		GpsMid.getInstance().show(this);
	}

}
