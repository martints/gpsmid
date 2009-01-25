package de.ueller.midlet.gps.routing;
import java.io.IOException;
import java.util.Vector;

import de.ueller.gps.data.Configuration;
import de.ueller.gps.tools.intTree;
import de.ueller.gpsMid.mapData.RouteBaseTile;
import de.ueller.gpsMid.mapData.Tile;
import de.ueller.midlet.gps.Logger;
import de.ueller.midlet.gps.Trace;
import de.ueller.midlet.gps.data.MoreMath;
import de.ueller.midlet.gps.data.PositionMark;
import de.ueller.midlet.gps.data.Way;




public class Routing implements Runnable {
	private Thread processorThread;
	public boolean bestTime=true;
	private final Vector nodes = new Vector();
	private final intTree open = new intTree();
	private final intTree closed = new intTree();
	private Runtime runtime = Runtime.getRuntime();

	private final static Logger logger = Logger.getInstance(Routing.class,Logger.ERROR);
	private final RouteBaseTile tile;
	private final Tile[] tiles;
	private RouteNode routeFrom;
	private RouteNode routeTo;
	private final Trace parent;
	private int bestTotal;
	private long nextUpdate;
	private float estimateFac=1.40f;
	private int oomCounter=0;
	private int expanded;
	
	public Routing(Tile[] tile,Trace parent) throws IOException {
		this.parent = parent;
		this.tile = (RouteBaseTile) tile[4];
		this.tiles = tile;
		estimateFac=(Configuration.getRouteEstimationFac()/10f)+0.8f;
	}
	
	private GraphNode search(RouteNode target) throws Exception {
		GraphNode currentNode;
		int successorCost;
		Vector children = new Vector();
		expanded=0;
		while (!(nodes.isEmpty())) {
			currentNode = (GraphNode) nodes.firstElement();
			if(closed.get(currentNode.state.toId) != null) { // to avoid having to remove
				nodes.removeElementAt(0);// improved nodes from nodes
				continue;
			}
			if (!(currentNode.total == bestTotal)) {
				setBest(currentNode.total,currentNode.costs);
			} 
			if (currentNode.state.toId == target.id) 
				return currentNode;
			children.removeAllElements();

			expanded++;
			// Fetch all connections.
			Connection successor[];

//			// try to free up some Memory if necessary.
//			// TODO: this has to be reviewed.
//			if (runtime.freeMemory() < 25000) {
//				System.gc();
//				//#debug error
//				System.err.println("gc() only " +  runtime.freeMemory() + " exp=" + expanded +  " open " + open.size() + "  closed " + closed.size());
//			}
//			if (runtime.freeMemory() < 25000) {
//				tile.cleanup(2);
//				System.gc();
//				//#debug error
//				System.err.println("cleanup(2) + gc() " +  runtime.freeMemory() + " exp=" + expanded +  " open " + open.size() + "  closed " + closed.size());
//			}
//			if (runtime.freeMemory() < 25000) {
//				// load from resource only the Connections to this node
//				// avoid loading unused Connection because memory-expensive.
//				System.err.println("load only single " +  runtime.freeMemory() + " exp=" + expanded);
//				successor=currentNode.state.to.getConnections(tile);
//			} else {
//				// load all Connection to this Tile and return the connections to this node
//				// this avoid the usages of skip(int) because its CPU-expensive.
//				successor=tile.getConnections(currentNode.state.toId.shortValue(),tile);
//			}
//			//#debug error
//			System.out.println("Begin load connections MEM " +  runtime.freeMemory() + " exp=" + expanded +  " open " + open.size() + "  closed " + closed.size());
			try {
				tile.cleanup(50);
				successor=tile.getConnections(currentNode.state.toId,tile,bestTime);
			} catch (OutOfMemoryError e) {
				oomCounter++;
				tile.cleanup(0);
				System.gc();
				//#debug error
				logger.debug("after cleanUp : " + runtime.freeMemory());
//				successor=currentNode.state.to.getConnections(tile);
				estimateFac += 0.02f;
				successor=tile.getConnections(currentNode.state.toId,tile,bestTime);
				//#debug error
				logger.debug("after load single Conection : " + runtime.freeMemory());
			}
			if (successor == null){
				successor=new Connection[0];
			}

			for (int cl=0;cl < successor.length;cl++){
				Connection nodeSuccessor=successor[cl];
				int dTurn=currentNode.fromBearing-nodeSuccessor.startBearing;
				int turnCost=getTurnCost(dTurn);
				successorCost = currentNode.costs + nodeSuccessor.cost+turnCost;
				GraphNode openNode = null;
				GraphNode theNode = null;
				GraphNode closedNode =  (GraphNode) closed.get(nodeSuccessor.toId);
				if (closedNode == null) {
					openNode = (GraphNode) open.get(nodeSuccessor.toId);
				}
				theNode = (openNode != null) ? openNode : closedNode;
				// in open or closed				
				if (theNode != null) {
					if (successorCost < theNode.costs) {
						if (closedNode != null) {
							open.put(nodeSuccessor.toId, theNode);
							closed.remove(nodeSuccessor.toId);
						} else {
							int dist = theNode.distance;
							theNode = new GraphNode(nodeSuccessor, currentNode, successorCost, dist, currentNode.fromBearing);
							open.put(nodeSuccessor.toId, theNode); 
						} 
						theNode.costs = successorCost;
						theNode.total = theNode.costs + theNode.distance;
						theNode.parent = currentNode; 
						theNode.fromBearing=currentNode.state.endBearing;
						children.addElement(theNode);
					}
				// not in open or closed
				} else { 
					int estimation;
					GraphNode newNode;
					estimation = estimate(currentNode.state,nodeSuccessor, target);
					newNode = new GraphNode(nodeSuccessor, currentNode, successorCost, estimation, currentNode.fromBearing);
					open.put(nodeSuccessor.toId, newNode);
//					parent.getRouteNodes().addElement(new RouteHelper(newNode.state.to.lat,newNode.state.to.lon,"t"+expanded));
//					evaluated++;
					children.addElement(newNode);
				}
			}
			open.remove(currentNode.state.toId);
			closed.put(currentNode.state.toId, currentNode);
			nodes.removeElementAt(0);
			addToNodes(children); // update nodes
		} 
		parent.receiveMessage("no Solution found");
		return null;

	}
	
	private void setBest(int total,int actual) {
		bestTotal=total;
		long now=System.currentTimeMillis();
		if (now > nextUpdate){
		if (bestTime){
			parent.receiveMessage("" + (bestTotal/600) 
					+ "min " + (100*actual/total)
					+ "% m:" + runtime.freeMemory()/1000 
					+ "k s:" + oomCounter+"/"+expanded+"/"+open.size());
		} else {
			parent.receiveMessage("" + (bestTotal/1000f) 
					+ "km " + (100*actual/total)
					+ "% m:" + runtime.freeMemory()/1000 
					+ "k s:" + oomCounter+"/"+expanded);
		}
		nextUpdate=now + 1000;
		}
	}

	private void addToNodes(Vector children) {
		for (int i = 0; i < children.size(); i++) { 
			GraphNode newNode = (GraphNode) children.elementAt(i);
			long newTotal = newNode.total;
			long newCosts = newNode.costs;
			boolean done = false;
			int idx = bsearch(0, nodes.size()-1, newTotal, newCosts);
			nodes.insertElementAt(newNode, idx); 
		}
	}

	private int bsearch(int l, int h, long tot, long costs){
		int lo = l;
		int hi = h;
		while(lo<=hi) {
			int cur = (lo+hi)/2;
			long ot = ((GraphNode)nodes.elementAt(cur)).total;
			if((tot < ot) || (tot == ot && costs >= ((GraphNode) nodes.elementAt(cur)).costs)) 
				hi = cur - 1;
			else lo = cur + 1;
		} 
		return lo; //insert before lo 
	} 

	
	private int getTurnCost(int turn) {
		int adTurn=Math.abs(turn*2);
		if (adTurn > 150){
			return 20;
		} else if (adTurn > 120){
			return 15;
		} else if (adTurn > 60){
			return 10;
		} else if (adTurn > 30){
			return 5;
		} else {
			return 0;			
		}
	}
	/**
	 * @param nodeSuccessor
	 * @param target
	 * @return
	 */
	private int estimate(Connection from,Connection to, RouteNode target) {
//		if (noHeuristic){
//			return 0;
//		}
		int dTurn=from.endBearing-to.startBearing;
		int turnCost=getTurnCost(dTurn);
		RouteNode toNode=getRouteNode(to.toId);
		if (toNode == null){
			//#debug error
			logger.info("RouteNode ("+to.toId+") = null" );
			return (10000000);
		}
		if (target == null){
			throw new Error("Target is NULL");
		}
		int dist = MoreMath.dist(toNode.lat,toNode.lon,target.lat,target.lon);
		if (bestTime) {
			if (dist > 100000){
				   // estimate 100 Km/h (28 m/s) as average speed 
				   return (int) (((dist/2.8f)+turnCost)*estimateFac);
				}
			if (dist > 50000){
				   // estimate 80 Km/h (22 m/s) as average speed 
				   return (int) (((dist/2.2f)+turnCost)*estimateFac);
				}
			if (dist > 10000){
				   // estimate 60 Km/h (17 m/s) as average speed 
				   return (int) (((dist/1.7f)+turnCost)*estimateFac);
				}
			if (dist > 5000){
				   // estimate 45 Km/h (12 m/s) as average speed 
				   return (int) (((dist/1.2f)+turnCost)*estimateFac);
				}
			// estimate 30 Km/h (8 m/s) as average speed 
			return (int) (((dist/2.2f)+turnCost)*estimateFac);
		} else {
			return (int) ((dist*1.1f + turnCost)*estimateFac);
		}
	}

	/**
	 * @param to
	 * @return
	 */
	private RouteNode getRouteNode(int id) {
		if (id == Integer.MAX_VALUE){
			return routeTo;
		}
		return tile.getRouteNode(id);
	} 

	public void solve (float fromLat,float fromLon,float toLat,float toLon) {

		try {
			// search ways new  
//			for (int i=0; i< 4;i++){
//				tiles[i].
//			}
			// end search ways new
			routeFrom = tile.getRouteNode(null,fromLat,fromLon);
			
			if (routeFrom == null){
				parent.receiveMessage("No startpoint found");
			} 
			routeTo = tile.getRouteNode(null,toLat,toLon);
			if (routeTo == null){
				parent.receiveMessage("No targetpoint found");
			} 
			if (routeFrom != null && routeTo != null){
				processorThread = new Thread(this,"Routing");
				processorThread.setPriority(Thread.NORM_PRIORITY);
				processorThread.start();
			} else {
				parent.setRoute(null);
			}
		} catch (Exception e) {
			parent.receiveMessage("Routing Ex " + e.getMessage());
			parent.setRoute(null);
		}
	}
	public void solve (PositionMark fromMark,PositionMark toMark) {

		logger.info("Calculating route from " + fromMark + " to " + toMark);

		try {
			if (toMark == null){
				parent.receiveMessage("Please set target first");
				parent.setRoute(null);
				return;
			} 
			
			RouteNode startNode=new RouteNode();
			startNode.lat=fromMark.lat;
			startNode.lon=fromMark.lon;

			if (fromMark.e == null){
				// if there is no element at the from Mark, then search it from 
				// the data.
				parent.receiveMessage("search for start element");
				parent.searchElement(fromMark);
				if (fromMark.e == null){
					parent.receiveMessage("No Way found for start point");
				} 
			}
			if (toMark.e == null){
				// if there is no element in the to Mark, fill it from tile-data
				parent.receiveMessage("search for target element");
				parent.searchElement(toMark);
				if (toMark.e == null){
					parent.receiveMessage("search for routable way close by the target");
					parent.searchNextRoutableWay(toMark);
					if (toMark.e == null){
						parent.receiveMessage("No Way found for target point");
						return;
					}
				}
			}
			
			logger.info("Calculating route from " + fromMark + " to " + toMark);
			
			if (fromMark.e instanceof Way){
				// search the next route node in all accessible directions. Then create 
				// connections that lead form the start point to the next route nodes.
				parent.receiveMessage("create from Connections");
				Way w=(Way) fromMark.e;
				int nearestSegment=getNearestSeg(w, startNode.lat, startNode.lon, fromMark.nodeLat,fromMark.nodeLon);
				if (! w.isOneway()){
					
//					parent.getRouteNodes().addElement(new RouteHelper(fromMark.nodeLat[nearestSegment],fromMark.nodeLon[nearestSegment],"oneWay sec"));
					RouteNode rn=findPrevRouteNode(nearestSegment-1, startNode.lat, startNode.lon, fromMark.nodeLat,fromMark.nodeLon);
					if (rn != null){
//						parent.getRouteNodes().addElement(new RouteHelper(rn.lat,rn.lon,"next back"));
						// TODO: fill in bearings and cost
						Connection initialState=new Connection(rn,0,(byte)0,(byte)0);
						GraphNode firstNode=new GraphNode(initialState,null,0,0,(byte)0);
						open.put(initialState.toId, firstNode);
						nodes.addElement(firstNode);						
					}
				} 
				RouteNode rn=findNextRouteNode(nearestSegment, startNode.lat, startNode.lon, fromMark.nodeLat,fromMark.nodeLon);
				if (rn != null){
					// TODO: fill in bearings and cost
					Connection initialState=new Connection(rn,0,(byte)0,(byte)0);
					GraphNode firstNode=new GraphNode(initialState,null,0,0,(byte)0);
					open.put(initialState.toId, firstNode);
					nodes.addElement(firstNode);						
				}				
			}


			// same for the endpoint
			
			routeTo=new RouteNode();
			routeTo.id=Integer.MAX_VALUE;
			routeTo.conSize=0;
			routeTo.lat=toMark.lat;
			routeTo.lon=toMark.lon;
			parent.receiveMessage("create to Connections");
			Way w=(Way) toMark.e;
			int nearestSeg = getNearestSeg(w,toMark.lat, toMark.lon, toMark.nodeLat, toMark.nodeLon);
			RouteTileRet nodeTile=new RouteTileRet();
			if (! w.isOneway()){
				RouteNode prefNode = findPrevRouteNode(nearestSeg, toMark.lat, toMark.lon, toMark.nodeLat, toMark.nodeLon);
				// TODO: fill in bearings and cost
				Connection newCon=new Connection(routeTo,0,(byte)0,(byte)0);
				tile.getRouteNode(prefNode.lat, prefNode.lon, nodeTile);
				nodeTile.tile.addConnection(prefNode,newCon,bestTime);
			}
			RouteNode nextNode = findNextRouteNode(nearestSeg, toMark.lat, toMark.lon, toMark.nodeLat, toMark.nodeLon);
			// TODO: fill in bearings and cost
			Connection newCon=new Connection(routeTo,0,(byte)0,(byte)0);
			tile.getRouteNode(nextNode.lat, nextNode.lon, nodeTile);
			nodeTile.tile.addConnection(nextNode,newCon,bestTime);
			if (routeTo != null){
				parent.cleanup();
				System.gc();
				//#debug error
				logger.info("free mem: "+runtime.freeMemory());
				processorThread = new Thread(this);
				processorThread.setPriority(Thread.NORM_PRIORITY);
				processorThread.start();
			} else {
				parent.setRoute(null);
			}
		} catch (Exception e) {
			parent.receiveMessage("Routing Ex " + e.getMessage());
			//#debug error
			e.printStackTrace();
			parent.setRoute(null);
		}
	}
		

	private int getNearestSeg(Way w,float lat, float lon,float[] lats,float[] lons){
			float minDistSq=Float.MAX_VALUE;
			int startAt=0;
			int max=lats.length -1;
			for (int u=0;u<max;u++){
				  float distSq = MoreMath.ptSegDistSq(
						  lats[u],
						  lons[u],
						  lats[u+1],
						  lons[u+1],
						  lat,
						  lon);
//				  logger.debug("dist:" + distSq + "  minDist:" + minDistSq);
				  if (distSq < minDistSq){
//					  logger.debug("index " + (u+1) + " is actual min");
					  minDistSq=distSq;
					  startAt=u+1;
				  }
			}
		return startAt;
	}
	
	private RouteNode findNextRouteNode(int begin,float lat, float lon,float[] lats,float[] lons){
		RouteNode rn=null;
		for (int v=begin;v < lats.length; v++){
			//#debug debug
			logger.debug("search point "+ lats[v] +"," + lons[v]);
			rn=tile.getRouteNode(lats[v], lons[v]);
			if (rn !=null){return rn;}
		} 
		return null;
	}
	private RouteNode findPrevRouteNode(int end,float lat, float lon,float[] lats,float[] lons){
		RouteNode rn=null;
		for (int v=end;v >= 0; v--){
			//#debug debug
			logger.debug("search point "+ lats[v] +"," + lons[v]);
			rn=tile.getRouteNode(lats[v], lons[v]);
			if (rn !=null){return rn;}
		} 
		return null;
	}
	
	private final Vector solve () {
		try {
			GraphNode solution=search(routeTo);
			if (bestTime){
				parent.receiveMessage("Route found: " + (bestTotal/600) + "min");
			} else {
				parent.receiveMessage("Route found: " + (bestTotal/1000f) + "km");
			}
			nodes.removeAllElements();
			open.removeAll();
			closed.removeAll();
			tile.cleanup(-1);
			Vector sequence = getSequence(solution);
//			logger.info("Ready with route discovery");
			return sequence;
		} catch (Exception e) {
			parent.receiveMessage("Routing Ex " + e.getMessage());
			//#debug error
			e.printStackTrace();
			return null;
		}
	}
	private Vector getSequence(GraphNode n) { 
		Vector result;
		if (n == null) {
			result = new Vector();
		} else { 
			result = getSequence (n.parent);
			ConnectionWithNode c=new ConnectionWithNode(getRouteNode(n.state.toId),n.state);
			result.addElement(c);
		} 
		return result; 
	}

	public void run() {
		try {
			//#debug error
			logger.info("Start Routing thread");
			Vector solve = solve();
			parent.setRoute(solve);
		} catch (NullPointerException npe) {
			parent.setRoute(null);
			parent.receiveMessage(npe.getMessage());
			logger.fatal("Routing thread crashed unexpectadly with error " +  npe.getMessage());			
			npe.printStackTrace();
			
		} catch (Exception e) {
			parent.setRoute(null);
			parent.receiveMessage(e.getMessage());
			//#debug error
			logger.fatal("Routing thread crashed unexpectadly with error " +  e.getMessage());
			//#debug			
			e.printStackTrace();
		} catch (Error e1){
			parent.setRoute(null);
			parent.receiveMessage(e1.getMessage());
			e1.printStackTrace();
		}
		
	} 



}
