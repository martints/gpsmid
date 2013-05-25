/**
 * This file is part of OSM2GpsMid 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published by
 * the Free Software Foundation.
 *
 * Copyright (C) 2010 Harald Mueller
 */

package de.ueller.osmToGpsMid.area;

import java.util.ArrayList; 
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;
import de.ueller.osmToGpsMid.MyMath;
import de.ueller.osmToGpsMid.model.Bounds;
import de.ueller.osmToGpsMid.model.Node;

public class Area {
	public static final boolean DEBUG = true;

	private List<Outline>	outlineList	= new ArrayList<Outline>();
	private List<Outline>	holeList	= new ArrayList<Outline>();
	List<Triangle> triangleList = null;
	static DebugViewer			viewer		= null;

	public double maxdist = 0d;
	//double limitdist = 25000d;
	// mapmid format errors
	//double limitdist = 50000d;
	double limitdist = 32000d;
	//double limitdist = 1250000d;
	//double limitdist = 10000d;

	public Area () {
		if (DEBUG) {
			outlineList	= new CopyOnWriteArrayList<Outline>();
			holeList	= new CopyOnWriteArrayList<Outline>();
		} else {
			outlineList	= new ArrayList<Outline>();
			holeList	= new ArrayList<Outline>();
		}
		
	}
	
	public void addOutline(Outline p) {
		if (p.isValid()) {
			outlineList.add(p);
//			p.calcNextPrev();
		}
	}

	public void addHole(Outline p) {
		if (p.isValid()) {
			holeList.add(p);
		}
	}

	private List<Outline> cleanupOutlines(List<Outline> src) {
		List<Outline> outlineTempList = new LinkedList<Outline>();
		while (!src.isEmpty()) {
			Outline outline = src.remove(0);
			if (DEBUG) {
				viewer.setActiveOutline(outline);
			}
			if (!outline.isClosed()) {
				outline.connectPartWays(src);
			}
			// use only closed and valid (i.e. more than two points) outlines
			if (outline.isClosed() && outline.isValid()) {
				outline.calcNextPrev();
				outlineTempList.add(outline);
			}
		}
		return outlineTempList;
	}
	
	public List<Triangle> triangulate() {
		if (DEBUG) {
			if (viewer == null) {
				viewer = new DebugViewer(this);
			} else {
				viewer.setArea(this);
			}
			repaint();
		}
		
		List<Triangle> ret;
		if (DEBUG) {
			ret = new CopyOnWriteArrayList<Triangle>();
		} else {			
			ret = new ArrayList<Triangle>(1);
		}
		triangleList = ret;
		
		// if there are more ways than one are used to build the outline, try to construct one outline for that
		outlineList = cleanupOutlines(outlineList);
		// the same for the holes
		holeList = cleanupOutlines(holeList);
		
		int dir = 0;
		repaint();
		int loop = 0;
		while (outlineList.size() > 0) {
			Outline outline = outlineList.remove(0);
			if (DEBUG) {
				viewer.setActiveOutline(outline);
				repaint();
			}
			
			if (! outline.isValid()) {
				continue;
			}

			outline.calcNextPrev();
			//System.err.println("Starting to do the cutOneEar thing");
			while ((outline.vertexCount() > 3)
						|| ((outline.vertexCount() > 2) && !holeList.isEmpty())) {
				loop++;
				if (loop % 5000 == 0) {
					System.err.println("Triangulating outline "
							   + outline.getWayId() + " looped "
							   + loop + " times");
				}
				if (loop > 4000000) {
					System.err.println("Break because of infinite loop for outline " + outline.getWayId());
					System.err.println("  see http://www.openstreetmap.org/browse/way/" + outline.getWayId());
					break;
				}
				Triangle t = cutOneEar(outline, holeList, dir);
				splitTriangleIfNeeded(new Triangle(t), ret, 0);
				dir = (dir + 1) % 4;
			}
			if (outline.vertexCount() == 3) {
				List<Vertex> v = outline.getVertexList();
				Triangle t = new Triangle(v.get(0), v.get(1), v.get(2));
				splitTriangleIfNeeded(t, ret, 0);
			}
			//System.err.println("Finished doing the cutOneEar thing");
		}
		//System.out.println(ret);
		//System.out.println("loops :" + loop);
		//System.err.println("Starting to optimize");
		optimize();
		
		if (DEBUG) {
			viewer.done();
		} else {
			((ArrayList<Triangle>)ret).trimToSize();
		}
		//System.err.println("Finished optimizing"); 
		return ret;

	}
	private void splitTriangleIfNeeded(Triangle t, List<Triangle> ret, int recurselevel) {
		// check the size; if a line is too long, split the triangle
		Node n0 = t.getVert()[0].getNode();
		Node n1 = t.getVert()[1].getNode();
		Node n2 = t.getVert()[2].getNode();
		double dist0 = MyMath.dist(n0, n1);
		double dist1 = MyMath.dist(n1, n2);
		double dist2 = MyMath.dist(n2, n0);
		if (dist0 > limitdist ||
			    dist1 > limitdist ||
			    dist2 > limitdist) {

			if (recurselevel > 80) {
				System.out.println("WARNING: Recurselevel > 80, giving up splitting triangle " + t);
				ret.add(t);
				return;
			}

			Triangle t1 = new Triangle(t.getVert()[0], t.getVert()[1], t.getVert()[2]);
			Triangle t2 = new Triangle(t.getVert()[0], t.getVert()[1], t.getVert()[2]);
			int longest = 0;
			double longestDist = 0d;
			Node newNode = null;
			if (dist0 > longestDist) {
				longestDist = dist0;
				longest = 0;
			}
			if (dist1 > longestDist) {
				longestDist = dist1;
				longest = 1;
			}
			if (dist2 > longestDist) {
				longestDist = dist2;
				longest = 2;
			}
			//System.out.println("Splitting triangle " + t + ", dist= " + longestDist);
			//System.out.println("Longest edge: " + longest);

			switch(longest) {
			case 0: 
				newNode = n0.midNode(n1, FakeIdGenerator.makeFakeId());
				t1.getVert()[1] = new Vertex(newNode,null);
				t2.getVert()[0] = new Vertex(newNode,null);
				break;
			case 1: 
				newNode = n1.midNode(n2, FakeIdGenerator.makeFakeId());
				t1.getVert()[2] = new Vertex(newNode,null);
				t2.getVert()[1] = new Vertex(newNode,null);
				break;
			case 2: 
				newNode = n2.midNode(n0, FakeIdGenerator.makeFakeId());
				t1.getVert()[0] = new Vertex(newNode,null);
				t2.getVert()[2] = new Vertex(newNode,null);
				break;
			}
			splitTriangleIfNeeded(t1, ret, recurselevel + 1);
			//System.out.println("Split or add triangle t2: " + t2);
			splitTriangleIfNeeded(t2, ret, recurselevel + 1);
		} else {
			//System.out.println("Adding side " + side + " of triangle");
			ret.add(t);
		}
	}
	private void optimize() {
		for (Triangle t:triangleList) {
			t.opt = false;
		}
//		while (true) {
			Iterator<Triangle> it = triangleList.iterator();
			while (it.hasNext()) {
				Triangle t1 = it.next();
				if (t1.getVert()[0].getNode() == t1.getVert()[1].getNode() 
						|| t1.getVert()[0].getNode() == t1.getVert()[2].getNode()
						|| t1.getVert()[1].getNode() == t1.getVert()[2].getNode()) {
					it.remove();
//					System.out.println("remove degenerated Triangle");
				}
//				if (! t1.opt) {
//					for (Triangle t2:triangleList) {
//						if (t1.equalVert(t2) == 2) {
//							optimize(t1,t2);
//						}
//					}
//				}
			}
//		}
	}

	/**
	 * 
	 */
	private final void repaint() {
		if (DEBUG) {
			viewer.repaint();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			// System.out.println("Area.repaint()");
		}
	}

	private Triangle cutOneEar(Outline outline, List<Outline> holeList, int dir) {
		Vertex n = outline.getMin(dir);
		while (true) {
			BoundedTriangle triangle = new BoundedTriangle(n, n.getNext(), n.getPrev());
			Vertex vertexInside = findFirstVertexInside(outline, triangle, dir);
			if (DEBUG) {
				viewer.setCurrentPosition(triangle, vertexInside);
				
				repaint();
			}
			
			if (vertexInside == null) {
				// this is an ear with nothing in it so cut the ear
				outline.remove(n);
				return triangle;
				
			// at least one edge is inside this ear. Is it from the outline,
		    // that is is the outline intersecting itself?
			} else if (vertexInside.partOf(outline)) {
				
				// node of the outline is in the ear so we have to cut the outline into two parts
				// one will handled now and the other goes to the stack
				outline.clean();
				Vertex nt = n;
				// create a fresh copy of the old outline starting from outer edge of the expected ear
				while (nt != vertexInside) {
					outline.append(nt);
					nt = nt.getNext();
				}
				// go ahead the edge that was found inside the test triangle
				outline.append(vertexInside);
				Outline newOutline = new Outline();
				newOutline.setWayId(outline.getWayId());
				while (nt != n) {
					newOutline.append(nt);
					nt = nt.getNext();
				}
				newOutline.append(n);
				if (newOutline.isValid()) {
					addOutline(newOutline);
					newOutline.calcNextPrev();
				}
				
				// reinititialize outline;
				outline.calcNextPrev();			
			// The Vertex is from some hole, connect the hole to the
			// outline and continue
			} else {
				Outline hole = vertexInside.getOutline();
				if (! hole.getVertexList().contains(vertexInside)) {
					throw new RuntimeException("The internal state is broken!");
				}
				// now we have an edge of a hole inside the rectangle
				// lets join the hole with the outline and have a next try
				repaint();
				// reinititialize outline;
				outline.calcNextPrev();
				boolean clockWise = outline.isClockWiseFast();
				outline.clean();
				Vertex nt = n;
				if (clockWise) {
					do {
						outline.append(nt);
						nt = nt.getNext();
					} while (nt != n);
				} else {
					do {
						outline.append(nt);
						nt = nt.getPrev();
					} while (nt != n);
				}
				repaint();
				outline.append(n.clone());
				repaint();
				// the following makes triangulation
				// of Finnish sea fail after 75 000 triangles with:

				/* Triangulating outline 4611686018427401182 looped 75000 times
				   Something went wrong when trying to triangulate relation 
				   http://www.openstreetmap.org/browse/relation/4611686018427388922 I'll attempt to ignore this relation
				      java.util.NoSuchElementException
				   at java.util.ArrayList$Itr.next(ArrayList.java:757)
				   at java.util.Collections.min(Collections.java:624)
				   at de.ueller.osmToGpsMid.area.Outline.getLonMin(Outline.java:174)
				   at de.ueller.osmToGpsMid.area.Outline.isClockWiseFast(Outline.java:290)
				   at de.ueller.osmToGpsMid.area.Area.cutOneEar(Area.java:326)
				   at de.ueller.osmToGpsMid.area.Area.triangulate(Area.java:131)
				   at de.ueller.osmToGpsMid.Relations.processRelations(Relations.java:316)
				   at de.ueller.osmToGpsMid.Relations.<init>(Relations.java:48)
				   at de.ueller.osmToGpsMid.BundleGpsMid.run(BundleGpsMid.java:516)
				   at java.lang.Thread.run(Thread.java:679) */

				//clockWise = hole.isClockWiseFast();
				hole.calcNextPrev();
				nt = vertexInside;
				if (hole.isClockWise()) {
					do {
						outline.append(nt);
						nt = nt.getPrev();
						// repaint();
					} while (nt != vertexInside);
				} else {
					do {
						outline.append(nt);
						nt = nt.getNext();
						// repaint();
					} while (nt != vertexInside);
					
				}
				outline.append(vertexInside.clone());
				holeList.remove(hole);
				
				// reinititialize outline;
				outline.calcNextPrev();
			}
		}
	}

//	private Vertex findEdgeInside(Outline outline, Triangle triangle) {
//		Vertex leftmost = null;
//		Vertex n = outline.findVertexInside(triangle);
//		if (leftmost == null) {
//			leftmost = n;
//		} else {
//			if (n.getX() < leftmost.getX()) {
//				leftmost = n;
//			}
//		}
//		for (Outline p : holeList) {
//			n = p.findVertexInside(triangle);
//			if (leftmost == null) {
//				leftmost = n;
//			} else {
//				if (n != null && n.getX() < leftmost.getX()) {
//					leftmost = n;
//				}
//			}
//		}
//		return leftmost;
//	}

	private Vertex findFirstVertexInside(Outline outline, Triangle triangle, int dir) {
		Comparator<Vertex> comp;
		switch (dir) {
		case 0:
			    comp = DirectionComperator0.INSTANCE;
		case 1:
			    comp = DirectionComperator1.INSTANCE;
		case 2:
			    comp = DirectionComperator2.INSTANCE;
		default:
			    comp = DirectionComperatorX.INSTANCE;
		}

		Vertex ret = outline.findFirstVertexInside(triangle, comp, null);
		for (Outline p : holeList) {
		    ret = p.findFirstVertexInside(triangle, comp, ret);
		}
		return ret;
	}
	
	public Bounds extendBounds(Bounds b) {
		if (b == null) {
			b = new Bounds();
		}
		for (Outline o:outlineList) {
			o.extendBounds(b);
		}
		for (Outline o:holeList) {
			o.extendBounds(b);
		}
		if (triangleList != null) {
			for (Triangle t: triangleList) {
				t.extendBound(b);
			}
		}

		return b;
	}
	
	public List<Outline> getOutlineList() {
		return outlineList;
	}

	public List<Outline> getHoleList() {
		return holeList;
	}

}
