/**
 * OSM2GpsMid 
 *  
 *
 * @version $Revision$ ($Name$)
 *
 * Copyright (C) 2007 Harald Mueller
 */
package de.ueller.osmToGpsMid.area;

import java.awt.Color; 
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;
import javax.swing.event.MouseInputListener;

import de.ueller.osmToGpsMid.model.Bounds;
import de.ueller.osmToGpsMid.model.Node;


/**
 * @author hmu
 *
 */
public class DebugViewer
		extends JFrame {
	private static final long serialVersionUID = -2785651417345285948L;

	private static final boolean WAIT_FOR_CLICK = false;
	
	private static DebugViewer instance = null;
	
	public static DebugViewer getInstanz(Area a){
		if (instance == null){
			instance = new DebugViewer(a);
		} else {
			instance.setArea(a);
		}
		return instance;
	}

	private int xs=1200;
	private int ys=1000;
	private Area a;
	private float zoomFactor;
	private float offsetY,offsetX;
	public ArrayList<Triangle> alt=null;
	private Outline activeOutline = null;
	private Triangle currentTriangle = null;
	private Vertex currentInsideVertex = null;
	private volatile BufferedImage backBuffer = null;
	
	
	private final MouseInputListener mouseListener = new MouseInputListener() {
		
		private int startX;
		private int startY;

		@Override
		public void mouseMoved(MouseEvent e) {
			// Ignore
		}
		
		@Override
		public void mouseDragged(MouseEvent e) {
			pan(e.getX() - startX, e.getY() - startY);
			startX = e.getX();
			startY = e.getY();			
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			// Ignore
			clickSemaphore.release();
		}

		@Override
		public void mousePressed(MouseEvent e) {
			startX = e.getX();
			startY = e.getY();			
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			// Ignore
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			// Ignore
		}

		@Override
		public void mouseExited(MouseEvent e) {
			// Ignore
		}
	};
	
	private final MouseWheelListener mouseWheelListener = new MouseWheelListener() {
		
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if (e.getWheelRotation() < 0) {
				zoom(1.05f);
			} else {
				zoom(0.95f);
			}
		}
	};

	private final Semaphore clickSemaphore = new Semaphore(1);
	
	/**
	 * 
	 */
	public DebugViewer(Area a) {
		super("Triangulator Test");
		setSize(xs, ys);
		setVisible(true);
		setArea(a);
		getContentPane().addMouseListener(mouseListener);
		getContentPane().addMouseMotionListener(mouseListener);
		getContentPane().addMouseWheelListener(mouseWheelListener);
		KeyListener keyListener = new KeyListener() {
			
			@Override
			public void keyTyped(KeyEvent e) {
				clickSemaphore.release();
			}
			
			@Override
			public void keyReleased(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}
		};
		getContentPane().addKeyListener(keyListener);
  		addKeyListener(keyListener);
	}

	@Override
	public void paint(Graphics g) {
		if (ys != getSize().height) {
			ys = getSize().height;
			xs = getSize().height;
			recalcView();
		}
		if (backBuffer == null) {
			recreateBackbuffer();
		}
		Graphics2D g2=(Graphics2D) g;
		synchronized (this) {
			g2.drawImage(backBuffer, 0, 0, null);
		}
		try {
			g2.setColor(Color.cyan);
			if (activeOutline != null) {
				drawOutline(g2, activeOutline,0);
			}
			if (currentTriangle != null){
				drawTriangle(g2, currentTriangle, new Color(255,0,0,40), Color.RED);
			}
			Color cAlt = new Color(255,255,0,40);
			if (alt != null){
				for (Triangle t:alt) {
					drawTriangle(g2, t, cAlt, Color.BLACK);
				}
			}
			if (currentInsideVertex != null){
				Point ei = toScreen(currentInsideVertex.getNode());
				g2.setColor(Color.magenta);
				g2.fillOval(ei.x - 5, ei.y - 5, 10, 10);
			}
		} catch (Exception e) {
			System.out.println("error while painting " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
	/**
	 * 
	 */
	private synchronized void recreateBackbuffer() {
		BufferedImage backBuffer_ = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = (Graphics2D) backBuffer_.getGraphics();
		backBuffer = backBuffer_;
		g2.setBackground(Color.WHITE);
		g2.clearRect(0, 0, getWidth(), getHeight());
		
		g2.setColor(Color.DARK_GRAY);
		if (activeOutline != null) {
			drawOutline(g2, activeOutline,0);
		}
		for (Outline o : a.getOutlineList()){
			drawOutline(g2, o,0);
		}
		for (Outline o : a.getHoleList()){
			drawOutline(g2, o,0);
		}
		if (a.triangleList != null) {
			for (Triangle t : a.triangleList){
				drawTriangle(t, g2);
			}
		}
		
	}

	/**
	 * @param g2
	 * @param t
	 * @param cf
	 * @param co
	 */
	private void drawTriangle(Graphics2D g2, Triangle t, Color cf, Color co) {
		g2.setColor(cf);
		Point p;
		Vertex[] vert = t.getVert();
		Polygon po = new Polygon();
		for (int i = 0; i < 3; i++){
			p = toScreen(vert[i].getNode());
			po.addPoint(p.x, p.y);
		}
		g2.fillPolygon(po);
		g2.setColor(co);
		g2.drawPolygon(po);
	}
	/**
	 * @param g2
	 * @param o
	 */
	private void drawOutline(Graphics2D g2, Outline o,int xoff) {
		if (o==null || o.getVertexList().size()==0){
			return;
		}
//		System.out.println("DebugViewer.drawOutline()");
		Point s=null;
		Node n=null;
		Vertex vl=null;
		int i=0;
		for (Vertex v:o.getVertexList()){
			n=v.getNode();
			Point e=toScreen(n);
			if (s != null){
				g2.drawLine(s.x + xoff, s.y, e.x + xoff, e.y);
				if (xoff != 0){
					g2.drawString("" + i++, s.x + xoff + 3, s.y - 3);
				}
			}
			s=e;
			vl=v;
		}
		Point e=toScreen(vl.getNode());
		//close polygon from last endpoint to startpoint 
		g2.drawLine(s.x, s.y, e.x, e.y);
	}
	
	private Point toScreen(Node n) {
		int x = (int)(20+(n.lon-offsetX)*zoomFactor);
		int y = ys-(int)((n.lat-offsetY)*zoomFactor + 20);
//		System.out.println("DebugViewer.toScreen() " + x + " " +y);
		return new Point(x,y);
	}
	
	/**
	 * @param a the a to set
	 */
	public void setArea(Area a) {
		this.a = a;
		recalcView();
	}

	public void recalcView(){
		Bounds b=a.extendBounds(null);
		if ( alt != null){
			for (Triangle t: alt){
				t.extendBound(b);
			}
		}
		float fx = (xs - 50) / (b.maxLat - b.minLat);
		float fy = (ys - 50) / (b.maxLon - b.minLon);
		if (fx > fy){
			zoomFactor = fy;
		} else {
			zoomFactor = fx;
		}
		offsetY = b.minLat;
		offsetX = b.minLon;
		backBuffer = null;
	}

	protected void zoom(float g) {
		zoomFactor *= g;
		backBuffer = null;
		repaint();
	}
	
	protected void pan(int dx, int dy) {
		offsetX -= dx / zoomFactor;
		offsetY += dy / zoomFactor;
		backBuffer = null;
		repaint();
	}

	public void setActiveOutline(Outline outline) {
		activeOutline = outline;
	}
	
	public void setCurrentPosition(Triangle triangle, Vertex vertexInside) {
		currentTriangle = triangle;
		currentInsideVertex = vertexInside;
	}

	private void drawTriangle(Triangle t, Graphics2D g2) {
		drawTriangle(g2, t, new Color(0,255,0,50), Color.BLACK);
	}
	
	public synchronized void triangleAdded(Triangle t) {
		if (backBuffer != null) {
			drawTriangle(t, backBuffer.createGraphics());
		}
	}
	


	public void done() {
	}

	public void waitToContinue() {
		try {
			if (WAIT_FOR_CLICK) {
				clickSemaphore.acquire();
			} else {
					Thread.sleep(10);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param newOutline
	 */
	public synchronized void addOutline(Outline newOutline) {
		if (backBuffer != null) {
			Graphics2D g2 = backBuffer.createGraphics();
			g2.setColor(Color.DARK_GRAY);
			drawOutline(g2, newOutline, 0);
		}
	}

}
