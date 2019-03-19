package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;

public class CheapNode {

	public static class CheapRoute {
		String from;
		String to;
		String txt;
		/** -1 for not a timeout route */
		int timeoutsnd = -1;
		Req cmd; 
		int sort;

		public CheapRoute(int timeout, String timeoutRoute) throws SemanticException {
			if (timeout <= 0)
				throw new SemanticException("Timeout Route consturctor can been called if timeout is not defined");
			// TODO Auto-generated constructor stub
		}

		public CheapRoute(String from, String cmd, String to) {
			TO BE CONTINUED
			// TODO Auto-generated constructor stub
		}
	}

	public static class VirtualNode extends CheapNode {
		private CheapNode startNode;

		VirtualNode(CheapWorkflow wf, String nid, String ncode, String nname, String prevNodes, int timeout,
				String timeoutRoute, HashMap<Req, CheapRoute> routes, String onEvents)
				throws SQLException, SemanticException {
			super(wf, nid, ncode, nname, prevNodes, timeout, timeoutRoute, routes, onEvents);
		}

		public VirtualNode(CheapWorkflow wf, CheapNode startNode)
				throws SemanticException, SQLException {
			super(wf, "#virt", "virt", "invisible", null, 0, null, null, null);
			this.startNode = startNode;
		}

	}

	private CheapWorkflow wf;
	private String nid;
	private String ncode;
	private String nname;
	/**
	 * [cmd-code, code-name-array], for communicate with client (e.g. req
	 * findroute).<br>
	 * Created according to route when needed.
	 */
	private HashMap<Req, CheapRoute> route_lazy;

	private CheapRoute timeoutRoute;
	private ICheapEventHandler timeoutHandler;

	private ICheapEventHandler eventHandler;
	private String prevNodes;
	private CheapLogic arriveCondt;

	CheapNode(CheapWorkflow wf, String nid, String ncode, String nname,
			String prevNodes, int timeout, String timeoutRoute,
			HashMap<Req, CheapRoute> routes, String onEvents) throws SQLException, SemanticException {
		this.wf = wf;
		this.nid = nid;
		this.ncode = ncode;
		this.nname = nname;
		this.route_lazy = routes;
		this.prevNodes = prevNodes;
		this.arriveCondt = new CheapLogic(prevNodes);
		this.eventHandler = parseEvent(onEvents);

		if (timeout > 0)
			this.timeoutRoute = new CheapRoute(timeout, timeoutRoute);
		if (timeout > 0) {
			// String[] timeoutRt = new String[2];
			this.timeoutHandler = parseEvent(timeoutRoute);
		}
	}


	private ICheapEventHandler parseEvent(String onEvent) {
		if(onEvent != null) {
			try {
				ICheapEventHandler handler = (ICheapEventHandler) Class.forName(onEvent.trim()).newInstance();
				return handler;
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				e.printStackTrace();
				return new CheapHandler();
			}
		}
		return null;
	}

	/**
	 * @param strRoute e.g. next:f01,back:f02:com.ir.eventhandler
	 * @return route map
	 * @throws SQLException
	private static HashMap<Req, CheapRoute> parseRoute(String strRoute) throws SQLException {
		String[] ss = strRoute == null ? null : strRoute.split(",");
		if (ss == null)
			return null;

		HashMap<Req, CheapRoute> rt = new HashMap<Req, CheapRoute>(ss.length);
		for (String r : ss) {
			String[] rss = r.split(":");
			if (rss == null || rss.length <= 2 || rss[0] == null || rss[1] == null) {
				System.err.println("Ignoring Route Config: " + r);
				continue;
			}
			Req req = Req.valueOf(rss[0].trim());
			// [req, [cmd, text, event-handler]]
			rt.put(req, new CheapRoute(rss[1].trim(), rss[2].trim(), rss.length > 3 ? rss[3].trim() : null));
		}
		return rt;
	}
	 */

	public String nodeId() { return nid; }
	public String wfId() { return wf.wfId; }

	public CheapRoute timeoutRoute() {
		// return route != null && route.containsKey(Req.timeout) ? route.get(Req.timeout)[0] : null;
		return timeoutRoute;
	}

//	public String timeoutTxt() {
//		// return timeoutRoute == null ? null : timeoutRoute[1];
//		return route != null && route.containsKey(Req.timeout) ? route.get(Req.timeout)[1] : null;
//	}

	public ICheapEventHandler timeoutHandler() {
		return timeoutHandler;
	}

//	public ICheapEventHandler stepEventHandler(Req req) throws SQLException {
//		if (route_lazy != null && route_lazy.containsKey(req)) {
//			String[] rt = route.get(req);
//
//			if (rt.length > 2 && rt[2] != null)
//				try {
//					return (ICheapEventHandler) Class.forName(rt[2].trim()).newInstance();
//				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
//					Utils.warn("Node (nid = %s) can't initiate an event handler: %s", nid, rt[2]);
//				}
//		}
//		return null;
//	}

	public ICheapEventHandler onEventHandler() {
		// return eventHandler == null ? null : eventHandler.get(arrive);
		return eventHandler;
	}


	public String getReqText(Req req) {
		return null;
	}


	public boolean isArrived(CheapNode currentNode) {
		return arriveCondt.isArrive(currentNode.nodeId());
	}

	public CheapNode findRoute(Req req) {
		if (route_lazy.containsKey(req))
			return wf.getNode(route_lazy.get(req).to);
		else return null;
	}


	public String prevNodes() { return prevNodes; }

}
