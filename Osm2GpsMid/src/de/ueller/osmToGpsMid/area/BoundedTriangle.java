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

public class BoundedTriangle 
		extends Triangle {
	

	private Rectangle2D.Float boundingBox;

	public BoundedTriangle(Vertex n1, Vertex n2, Vertex n3) {
		super(n1, n2, n3);
		
		float minX = min(n1.getX(), n2.getX(), n3.getX());
		float maxX = max(n1.getX(), n2.getX(), n3.getX());
		float minY = min(n1.getY(), n2.getY(), n3.getY());
		float maxY = max(n1.getY(), n2.getY(), n3.getY());
		boundingBox = new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
	}



	public boolean isVertexInside(Vertex n){
		if (n == getVert()[0] && n == getVert()[1] && n == getVert()[2]) {
			return false;
		}
		
		if (! boundingBox.contains(n.getX(), n.getY())) {
			return false;
		}
		
		return super.isVertexInside(n);
	}
	
	public Rectangle2D getBoundingBox() {
		return boundingBox;
	}
}
