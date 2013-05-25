/**
 * OSM2GpsMid 
 *  
 *
 * @version $Revision: 1.3 $ ($Name:  $)
 *
 * Copyright (C) 2007 Harald Mueller
 */
package de.ueller.osmToGpsMid.area;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author martin
 *
 */
public class AreaTest {

	private static long id = 0L;
	
	public static void main(String[] args)
			throws IOException {
		BufferedReader inputReader = new BufferedReader(new InputStreamReader(new FileInputStream("testoutline.vert")));
		Area area = new Area();
		try {
			area.addOutline(readOutline(inputReader));
			System.out.println("Outline: DONE!");
			while (inputReader.ready()) {
				area.addHole(readOutline(inputReader));
			}
			System.out.println("Holes: DONE!");
		} finally {
			inputReader.close();
		}
		
		System.out.println("Total vertices: " + id);
		long startTime = System.currentTimeMillis();
		int size = area.triangulate().size();
		
		
		System.out.println("Triangluation took: " + (System.currentTimeMillis() - startTime) + "ms for " + size + " triangles");
		System.out.println("Triangles: " + area.triangleList.size());
	}

	private static Outline readOutline(BufferedReader inputReader)
			throws IOException {
		Outline result = new Outline();
		
		String line = inputReader.readLine();
		while ((line != null) && (line.length() > 0) && (line.charAt(0) != '=')) {
			String[] coords = line.trim().split(", ");
			result.append(new Vertex(Float.parseFloat(coords[0].trim()), Float.parseFloat(coords[1].trim()), id++));
			line = inputReader.readLine();
		}
		result.append(result.getVertexList().get(0));
		
		return result;
	}

}
