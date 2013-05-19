/**
 * This file is part of OSM2GpsMid 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published by
 * the Free Software Foundation.
 *
 * Copyright (C) 2007 Harald Mueller
 * Copyright (C) 2010 Kai Krueger
 */
package de.ueller.osmToGpsMid;

import java.awt.geom.Area;
import java.io.InputStream; 
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import de.ueller.osmToGpsMid.model.Bounds;
import de.ueller.osmToGpsMid.model.Hash;
import de.ueller.osmToGpsMid.model.Node;
import de.ueller.osmToGpsMid.model.Relation;
import de.ueller.osmToGpsMid.model.Storage;
import de.ueller.osmToGpsMid.model.TurnRestriction;
import de.ueller.osmToGpsMid.model.Way;

public abstract class OsmParser {

	private static class NodeHash implements Hash<Node, Node> {

		@Override
		public int getHashCode(Node k) {
			return (int) k.id;
		}

		@Override
		public boolean equals(Node k, Node t) {
			return k.id == t.id;
		}
	}

	private static class Id2EntityHash implements Hash<Long, Node> {

		@Override
		public int getHashCode(Long k) {
			return (int)k.longValue();
		}

		@Override
		public boolean equals(Long k, Node t) {
			return t.id == k.longValue();
		}
	}


	/**
	 * Maps id to already read nodes. Key: Long Value: Node
	 */
	//protected HashMap<Long, Node> nodes = new HashMap<Long, Node>(80000, 0.60f);
	protected final Storage<Node> nodesStorage = new Storage<Node>(new NodeHash());
    protected Map<Long, Node> nodes = nodesStorage.foreignKey(new Id2EntityHash());
	protected HashMap<Long, Way> ways = new HashMap<Long, Way>();
	protected HashMap<Long, Relation> relations = new HashMap<Long, Relation>();
	protected HashMap<Long, TurnRestriction> turnRestrictions = new HashMap<Long, TurnRestriction>();
	protected List<TurnRestriction> turnRestrictionsWithViaWays = new ArrayList<TurnRestriction>();
	private Node[] delayingNodes;
	public int trafficSignalCount = 0;
	private final Bounds[] bounds;
	private final Configuration configuration;
	
	protected int wayIns;

	/**
	 * @param i
	 *            InputStream from which planet file is read
	 * @param c
	 *            Configuration which supplies the bounds
	 */
	public OsmParser(InputStream i, Configuration c) {
		this.configuration = c;
		Vector<Bounds> tmpBounds = c.getBounds();
		if (tmpBounds == null) {
			this.bounds = new Bounds[0];
		} else {
			this.bounds = tmpBounds.toArray(new Bounds[tmpBounds.size()]);
		}
		System.out.println(parserType() + " parser with bounds started...");
		init(i);
	}

	protected abstract String parserType();

	protected abstract void init(InputStream i);

	protected boolean nodeInArea(float lat, float lon) {
		boolean inBounds = false;
		Area area = configuration.getArea();
		if (area != null && area.contains(lat, lon)) {
			inBounds = true;
		} 
		for (Bounds b : bounds) {
			if (b.isIn(lat, lon)) {
				inBounds = true;
				break;
			}
		} 
		if ((area == null) && (bounds.length == 0)) {
			inBounds = true;
		}

		return inBounds;
	}

	/**
	 * @param viaNodeOrWayRef
	 * @param turnRestriction
	 */
	public void addTurnRestriction(long viaNodeOrWayRef,
			TurnRestriction turnRestriction) {
		if (!turnRestrictions.containsKey(new Long(viaNodeOrWayRef))) {
			turnRestrictions.put(new Long(viaNodeOrWayRef), turnRestriction);
			// System.out.println("Put turn restrictions at " +
			// viaNodeOrWayRef);
		} else {
			TurnRestriction baseTurnRestriction = (TurnRestriction) turnRestrictions
					.get(new Long(viaNodeOrWayRef));
			while (baseTurnRestriction.nextTurnRestrictionAtThisNode != null) {
				baseTurnRestriction = baseTurnRestriction.nextTurnRestrictionAtThisNode;
			}
			baseTurnRestriction.nextTurnRestrictionAtThisNode = turnRestriction;
			// System.out.println("Multiple turn restrictions at " +
			// viaNodeOrWayRef);
		}
	}

	/**
	 * @param w
	 */
	public void addWay(Way w) {
		// polish.api.bigstyles
		// If oneway=-1, reverse the way
		if (w.isOneWayMinusOne()) {
			//System.out.println("Reversing a oneway=-1 way, orig: " + w);
			Way tmp_w = new Way(w, true);
			w = tmp_w;
			w.setAttribute("oneway","yes");
			//System.out.println("Reversed a oneway=-1 way, result: " + w);
		}

		short t = w.getType(configuration);
		/**
		 * We seem to have a bit of a mess with respect to type -1 and 0. Both
		 * are used to indicate invalid type it seems.
		 */
		if (w.isValid() /* && t > 0 */) {
			w.trimPath();
			w.determineWayRouteModes();
			if (w.isAccessForAnyRouting()) {
				LegendParser.tileScaleLevelContainsRoutableWays[w.getZoomlevel(configuration)] = true;
			}
			if (ways.get(w.id) != null) {
				/**
				 * This way is already in data storage. This results from
				 * splitting a single osm way into severals GpsMid ways. We can
				 * simply invent an id in this case, as we currently don't use
				 * them for anything other than checking if an id is valid for
				 * use in relations
				 */
				ways.put(new Long(-1 * wayIns), w);
			} else {
				ways.put(w.id, w);
			}
			wayIns++;
		}
	}

	/**
	 * @param r
	 */
	public void addRelation(Relation r) {
		relations.put(r.id, r);
	}
	
	/**
	 * @param w
	 */
	public void addNode(Node n) {
		// polish.api.bigstyles
		short t = n.getType(configuration);
		/**
		 * We seem to have a bit of a mess with respect to type -1 and 0. Both
		 * are used to indicate invalid type it seems.
		 */
		if (true /* n.isValid() * && t > 0 */) {
			//w.determineWayRouteModes();
			if (nodes.get(n.id) != null) {
				System.out.println ("Error: couldn't store node, already there, id: " + n.id);
			} else {
				nodes.put(n.id, n);
			}
		}
	}

	public void removeWay(Way w) {
		ways.remove(w.id);
	}

	public Collection<Node> getNodes() {
		return nodesStorage;
	}
	
	/**
	 * WARNING: This function may return null, after dropHashMap has been called
	 * @return
	 */
	public Map<Long,Node> getNodeHashMap() {
		return nodes; 
	}

	public Collection<Way> getWays() {
		return ways.values();
	}

	public Collection<Relation> getRelations() {
		return relations.values();
	}

	public HashMap<Long, TurnRestriction> getTurnRestrictionHashMap() {
		return turnRestrictions;
	}

	public Node[] getDelayingNodes() {
		return delayingNodes;
	}

	public void freeUpDelayingNodes() {
		delayingNodes = null;
	}

	public void setDelayingNodes(Node[] nodes) {
		delayingNodes = nodes;
	}

	public List<TurnRestriction> getTurnRestrictionsWithViaWays() {
		return turnRestrictionsWithViaWays;
	}

	public HashMap<Long, Way> getWayHashMap() {
		return ways;
	}

	public void removeNodes(Collection<Node> nds) {
		for (Node n : nds) {
			nodes.remove(new Long(n.id));
		}
	}

	/**
	 * 
	 */
	public void resize() {
		//System.gc();
		//System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
		System.out.println("Resizing nodes HashMap");
		nodesStorage.shrink(0.85f);
		relations = new HashMap<Long, Relation>(relations);
		//System.gc();
		//System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
		printMemoryUsage(1);
	}

	/**
	 * Print memory usage.
	 * 
	 * @param numberOfGarbageLoops
	 *            Number of times to call the garbage colector and print the
	 *            memory usage again.
	 */
	public static void printMemoryUsage(int numberOfGarbageLoops) {
		DecimalFormat df =   new DecimalFormat  (",###");
		System.out.print("---> Used memory: "
				+ df.format((Runtime.getRuntime().totalMemory() - Runtime.getRuntime()
						.freeMemory()) / 1024) + " KB / "
				+ df.format(Runtime.getRuntime().maxMemory() / 1024) + " KB");
		for (int i = 0; i < numberOfGarbageLoops; i++) {
			System.gc();
			System.out.print(" --> gc: "
					+ df.format((Runtime.getRuntime().totalMemory() - Runtime
							.getRuntime().freeMemory()) / 1024) + " KB");
			try {
				if (i + 1 < numberOfGarbageLoops) {
					Thread.sleep(100);
				}
			} catch (InterruptedException ex) {
			}
		}
		System.out.println("");
	}

}
