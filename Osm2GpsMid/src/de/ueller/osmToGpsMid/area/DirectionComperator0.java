package de.ueller.osmToGpsMid.area;

import java.util.Comparator;

public class DirectionComperator0 implements Comparator<Vertex> {

	public static final DirectionComperator0 INSTANCE = new DirectionComperator0();
	
	public DirectionComperator0() {
		super();
	}

	@Override
	public int compare(Vertex o1, Vertex o2) {
		float diff = o1.getX() - o2.getX();
		if (diff > 0.0f)
			return 1;
		else if (diff < 0.0f)
			return -1;
		else
			return 0;
	}

}
