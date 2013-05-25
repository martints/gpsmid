/**
 * OSM2GpsMid  
 *  
 *
 * @version $Revision: 1.3 $ ($Name:  $)
 *
 * Copyright (C) 2007 Harald Mueller
 */
package de.ueller.osmToGpsMid.area;

public class BoundedTriangle 
		extends Triangle {
	private final float minX;
	private final float maxX;
	private final float minY;
	private final float maxY;
	

	public BoundedTriangle(Vertex n1, Vertex n2, Vertex n3) {
		super(n1, n2, n3);
		
		minX = min(n1.getX(), n2.getX(), n3.getX());
		maxX = max(n1.getX(), n2.getX(), n3.getX());
		minY = min(n1.getY(), n2.getY(), n3.getY());
		maxY = max(n1.getY(), n2.getY(), n3.getY());
	}


	private float min(float x, float x2, float x3) {
		float result = x;
		if (x2 < result) {
			result = x2;
		}
		if (x3 < result) {
			return x3;
		} else {
			return result;
		}
	}

	private float max(float x, float x2, float x3) {
		float result = x;
		if (x2 > result) {
			result = x2;
		}
		if (x3 > result) {
			return x3;
		} else {
			return result;
		}
	}

	public boolean isVertexInside(Vertex n){
		if (n == getVert()[0] && n == getVert()[1] && n == getVert()[2]) {
			return false;
		}
		
		if ((n.getX() <= minX) || (n.getX() >= maxX)
				|| (n.getY() <= minY) || (n.getY() >= maxY)) {
			return false;
		}
		
		return super.isVertexInside(n);
	}
}
