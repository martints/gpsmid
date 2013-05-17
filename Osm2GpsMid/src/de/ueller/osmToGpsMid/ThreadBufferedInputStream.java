/**
 * This file is part of OSM2GpsMid 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published by
 * the Free Software Foundation.
 *
 * Copyright (C) 2008  Kai Krueger
 */
package de.ueller.osmToGpsMid;

import java.io.IOException;
import java.io.InputStream;

public class ThreadBufferedInputStream extends InputStream implements Runnable {
	private static final int BUFFER_SIZE = 1024*1024;
	
	private InputStream is;
	private byte[][] buffer;
	private int bufferReadIdx;
	private int bufferWriteIdx;
	private int readIdx;
	private int writeIdx;
	private boolean writeSwappReady;
	private boolean readSwappReady;
	private int readLength;
	private Thread workerThread;
	private boolean eof;
	private boolean eofIn;
	
	public ThreadBufferedInputStream(InputStream in) {
		is = in;
		buffer = new byte[2][];
		buffer[0] = new byte[BUFFER_SIZE];
		buffer[1] = new byte[BUFFER_SIZE];
		writeSwappReady = false;
		readSwappReady = false;
		bufferReadIdx = 0;
		bufferWriteIdx = 1;
		readLength = 0;
		readIdx = 0;
		writeIdx = 0;
		eof = false;
		eofIn = false;
		workerThread = new Thread(this, "ThreadBuffered-reader");
		workerThread.start();
	}
	
	private final boolean retrieveNextBuffer() {
		synchronized (this) {
			if (eof) {
				return false;
			}
			readSwappReady = true;
			if (writeSwappReady) {
				swapBuffers();
			}
			else {
				try {
					wait();						
				} catch (InterruptedException e) {
					System.out.println("Something went horribly wrong " + e.getMessage());
					System.exit(3);
				}
			}
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	@Override
	public int read() throws IOException {
		if (readLength <= readIdx ) {
			if (! retrieveNextBuffer()) {
				return -1;
			}
		}
		byte res = buffer[bufferReadIdx][readIdx++];
		return res;
	}
	
	public int read(byte[] buf, int off, int len) {
		if (readLength <= readIdx ) {
			if (! retrieveNextBuffer()) {
				return -1;
			}
		}
		int noRead = len;
		if (noRead + readIdx > readLength) {
			noRead = readLength - readIdx;
		}
		System.arraycopy(buffer[bufferReadIdx], readIdx, buf, off, noRead);
		readIdx += noRead;
		return noRead;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		int noRead = 0;
		while (noRead != -1) {
			try {
				noRead = is.read(buffer[bufferWriteIdx],writeIdx,BUFFER_SIZE - writeIdx);
				if (noRead == -1) {
					eofIn = true;					
					//System.out.println("finished reading is");					
				}
			} catch (IOException e) {
				System.out.println("Something went horribly wrong " + e.getMessage());
				System.exit(2);
			}
			
			if (!eofIn) {
				writeIdx += noRead;
			}
			if (writeIdx >= BUFFER_SIZE || eofIn) {
				synchronized (this) {
					writeSwappReady = true;
					if (readSwappReady) {
						swapBuffers();
					} else {
						try {
							wait();
						} catch (InterruptedException e) {
							System.out.println("Something went horribly wrong " + e.getMessage());
							System.exit(3);
						}
					}
				}
			}
		}		
	}
	
	private void swapBuffers () {
		readLength = writeIdx;
		readSwappReady = false;
		writeSwappReady = false;
		readIdx = 0;
		writeIdx= 0;
		int tmp = bufferReadIdx;
		bufferReadIdx = bufferWriteIdx;
		bufferWriteIdx = tmp;
		
		if (eofIn) {
			eof = true;
		}
		notifyAll();
	}

}
