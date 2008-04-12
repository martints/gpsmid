/*
 * GpsMid - Copyright (c) 2007 Harald Mueller james22 at users dot sourceforge dot net 
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
 */
package de.ueller.midlet.gps;


import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

//#if polish.api.fileconnection
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
//#endif

//#if polish.api.nokia-ui
import com.nokia.mid.ui.DeviceControl;
//#endif

import de.ueller.gps.data.Configuration;
import de.ueller.gps.tools.HelperRoutines;




public class GpsMid extends MIDlet implements CommandListener{
	/** */
	private static GpsMid instance;
    /** A menu list instance */
    private static final String[] elements = { "Trace","Search","Setup","About","Log"};

    /** Soft button for exiting GpsMid. */
    private final Command EXIT_CMD = new Command("Exit", Command.EXIT, 2);

    /** Soft button for launching a client or sever. */
    private final Command OK_CMD = new Command("Ok", Command.SCREEN, 1);
    /** Soft button to go back from about screen. */
    private final Command BACK_CMD = new Command("Back", Command.BACK, 1);
    /** Soft button to show Debug Log. */
 //   private final Command DEBUG_CMD = new Command("", Command.BACK, 1);
    /** Soft button to go back from about screen. */
    private final Command CLEAR_DEBUG_CMD = new Command("Clear", Command.BACK, 1);

    /** A menu list instance */
    private final List menu = new List("GPSMid", Choice.IMPLICIT, elements, null);
//	private boolean	isInit=false;

    private final List loghist=new List("Log Hist",Choice.IMPLICIT);
	private String	root;
	private Configuration config;
//	#debug
	private Logger l;
	
	private OutputStreamWriter logFile;
	
	/**
	 * This Thread is used to periodically prod the display
	 * to keep the backlight illuminator if this is wanted
	 * by the user
	 */
	private Thread lightTimer;

private Trace trace=null;


	public GpsMid() {		
		instance = this;
		System.out.println("Init GpsMid");		
		l=new Logger(this);
		config = new Configuration();
		
		enableDebugFileLogging();
		
		menu.addCommand(EXIT_CMD);
		menu.addCommand(OK_CMD);
		menu.setCommandListener(this);
		loghist.addCommand(BACK_CMD);
		loghist.addCommand(CLEAR_DEBUG_CMD);
		loghist.setCommandListener(this);
		
//		
		new Splash(this);
//		RouteNodeTools.initRecordStore();
		startBackLightTimer();
	}
	
	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
//		#debug
		System.out.println("destroy GpsMid");
	}

	protected void pauseApp() {
//		#debug
		System.out.println("Pause GpsMid");
		if (trace != null){
			trace.pause();
		}
	// TODO Auto-generated method stub

	}

	protected void startApp() throws MIDletStateChangeException {
//		#debug
		System.out.println("Start GpsMid");
		if (trace == null){
			try {
				trace = new Trace(this,config);
//				trace.show();
			} catch (Exception e) {
				trace=null;
				e.printStackTrace();
			}
			} else {
				trace.resume();
//				trace.show();
		}
		}

	public void commandAction(Command c, Displayable d) {
        if (c == EXIT_CMD) {
            exit();

            return;
        }
        if (c == BACK_CMD) {
        	show();
        	return;
        }
        if (c == CLEAR_DEBUG_CMD){
        	loghist.deleteAll();
        }
        switch (menu.getSelectedIndex()) {
            case 0:
            	try {
            		if (trace == null){
            			trace = new Trace(this,config);
            			trace.show();
            		} else {
            			trace.resume();
            			trace.show();
            		}
				} catch (Exception e) {
					l.exception("Failed to display map " , e);
            		return;
				} 
                break;
            case 1:
        		try {
					if (trace == null){
						trace = new Trace(this,config);
					}
					GuiSearch search = new GuiSearch(trace);
					search.show();
				} catch (Exception e) {
					l.exception("Failed to display search screen " , e);
				}
            	break;
            case 2:
            	new GuiDiscover(this);
            	break;
            case 3:
				new Splash(this);
            	break;
            case 4:
				Display.getDisplay(this).setCurrent(loghist);
				break;
            default:
//            	#debug
                System.err.println("Unexpected choice...");

                break;
            }

//            isInit = true;


		
	}

	public void exit() {
		try {
			destroyApp(true);
		} catch (MIDletStateChangeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		notifyDestroyed();
	}
    /** Shows main menu of MIDlet on the screen. */
    void show() {
        Display.getDisplay(this).setCurrent(menu);
    }


	public void log(String msg){
		if (l != null){
			//#debug
			System.out.println(msg);
			/**
			 * Adding the log hist seems to cause very wierd problems
			 * even in the emulator. So leave this commented out
			 */
			//loghist.append(msg, null);
			
			if (logFile != null) {
				try {
					logFile.write(System.currentTimeMillis() + " " + msg + "\n");					
				} catch (IOException e) {
					//Nothing much we can do here, we are
					//already in the debugging routines.
					System.out.println("Failed to write to the log file: " + msg + " with error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
	}

	public Configuration getConfig() {
		return config;
	}

	public static GpsMid getInstance() {
		return instance;
	}
	
	public void enableDebugFileLogging() {
		//#if polish.api.fileconnection
		String url = config.getDebugRawLoggerUrl();
		if (config.getDebugRawLoggerEnable() && url != null) {
			try {
				url = url + "GpsMid_log_" + HelperRoutines.formatSimpleDateNow() + ".txt";
				Connection debugLogConn = Connector.open(url);
				if (debugLogConn instanceof FileConnection) {
					if (!((FileConnection)debugLogConn).exists()) {
						((FileConnection)debugLogConn).create();
					}
					logFile = new OutputStreamWriter(((FileConnection)debugLogConn).openOutputStream());
				}				
			} catch (IOException e) {
				l.exception("Couldn't connect to the debug log file", e);
				e.printStackTrace();
			}
		} else {
			logFile = null;
		}
		//#endif
	}
	
	public void startBackLightTimer() {		
		int backlight=config.getBacklight();
		if ((backlight & (1<<Configuration.BACKLIGHT_ON) )!=0 ) {
			// Warn the user if none of the methods
			// to keep backlight on was selected
			if( (backlight & 
					(
					 (1<<Configuration.BACKLIGHT_MIDP2)
					+(1<<Configuration.BACKLIGHT_NOKIA)
					+(1<<Configuration.BACKLIGHT_NOKIAFLASH)
					)
				 ) == 0
			) {				
				l.error("Backlight cannot be kept on when no 'with'-method is specified in Setup");
			}
			if (lightTimer == null) {
				lightTimer = new Thread(new Runnable() {
					private final Logger logger=Logger.getInstance(GpsMid.class,Logger.DEBUG);
					public void run() {
						try {
							boolean notInterupted = true;
							while(notInterupted) {							
								int backlight=config.getBacklight();
								// only when map is displayed or
								// option "only when map is displayed" is off 
								if ( (Trace.getInstance()!=null && Trace.getInstance().isShown())
								|| (backlight & (1<<Configuration.BACKLIGHT_MAPONLY)) ==0
								) {
									//Method to keep the backlight on
									//some MIDP2 phones
									if ((backlight & (1<<Configuration.BACKLIGHT_MIDP2) ) !=0) {
										Display.getDisplay(GpsMid.getInstance()).flashBacklight(6000);						
									//#if polish.api.nokia-ui
									//Method to keep the backlight on
									//on SE K750i and some other models
									} else if ((backlight & (1<<Configuration.BACKLIGHT_NOKIAFLASH) ) !=0) {  
										DeviceControl.flashLights(1);								
									//Method to keep the backlight on
									//on those phones that support the nokia-ui 
									} else if ((backlight & (1<<Configuration.BACKLIGHT_NOKIA) ) !=0) {
										DeviceControl.setLights(0, 100);
									//#endif		
									}
								}
								try {
									synchronized(this) {
										wait(5000);
									}
								} catch (InterruptedException e) {
									notInterupted = false;
								}
							}
						} catch (RuntimeException rte) {
							// Backlight prodding sometimes fails when minimizing the
							// application. Don't display an alert because of this
							logger.info("Blacklight prodding failed: " + rte.getMessage());
						} catch (NoClassDefFoundError ncdfe) {
							logger.error("Blacklight prodding failed, API not supported: " + ncdfe.getMessage());
						}
					}
				});
				lightTimer.setPriority(Thread.MIN_PRIORITY);
				lightTimer.start();
			}
		}
	}
	
	public void stopBackLightTimer() {
		if (lightTimer != null) {
			lightTimer.interrupt();
			try {
				lightTimer.join();
			} catch (Exception e) {
			
			}
			lightTimer = null;
		}
	}	
}

