/**
 * OSM2GpsMid 
 *  
 *
 * @version $Revision: 1.3 $ ($Name:  $)
 *
 * Copyright (C) 2007 Harald Mueller
 */
package de.ueller.osmToGpsMid.area;

import java.awt.geom.Rectangle2D;
import java.util.Comparator;

public class BoundedOutline 
		extends Outline {

	private float minX;
	private float minY;
	private float maxX;
	private float maxY;

	public BoundedOutline(Outline o) {
		setWayId(o.getWayId());
		
		minX = Float.POSITIVE_INFINITY;
		maxX = Float.NEGATIVE_INFINITY;
		minY = Float.POSITIVE_INFINITY;
		maxY = Float.NEGATIVE_INFINITY;
		for (Vertex v : o.getVertexList()) {
			append(v);
		}
	}

	public BoundedOutline() {
	}

	public Vertex findFirstVertexInside(Triangle triangle, Comparator<Vertex> comp, Vertex first) {
    	Rectangle2D r = triangle.getBoundingBox();
    	
    	if ((r.getMaxX() >= minX) && (r.getMinX() <= maxX)
    			&& (r.getMaxY() >= minY) && (r.getMinY() <= maxY)) {
    		return super.findFirstVertexInside(triangle, comp, first);
    	}
    	return first;
    }

	@Override
	public void append(Vertex v) {
		if (v.getX() > maxX) {
			maxX = v.getX();
		}
		if (v.getX() < minX) {
			minX = v.getX();
		}
		if (v.getY() > maxY) {
			maxY = v.getY();
		}
		if (v.getY() < minY) {
			minY = v.getY();
		}
		super.append(v);
	}
	
	public void clean() {
		minX = Float.POSITIVE_INFINITY;
		maxX = Float.NEGATIVE_INFINITY;
		minY = Float.POSITIVE_INFINITY;
		maxY = Float.NEGATIVE_INFINITY;
		super.clean();
	}

	@Override
	public void prepend(Vertex v) {
		if (v.getX() > maxX) {
			maxX = v.getX();
		}
		if (v.getX() < minX) {
			minX = v.getX();
		}
		if (v.getY() > maxY) {
			maxY = v.getY();
		}
		if (v.getY() < minY) {
			minY = v.getY();
		}
		super.prepend(v);
	}

	@Override
	public void remove(Vertex v) {
		// TODO: Change the border if required
		super.remove(v);
	}
	
	
}
