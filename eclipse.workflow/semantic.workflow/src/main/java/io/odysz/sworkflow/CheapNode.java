package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.module.rs.SResultset;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.x.TransException;

public class CheapNode {
	public static class CheapRoute {
		String from;
		String to;
		String txt;
		/** -1 for not a timeout route */
		int timeoutsnd = -1;
		String cmd; 
		int sort;

		public CheapRoute(int timeout, String timeoutRoute) throws SemanticException {
			if (timeout <= 0)
				throw new SemanticException("Timeout Route consturctor can't been called if timeout is not defined");
			// TODO Auto-generated constructor stub
		}

		public CheapRoute(String from, String cmd, String to, String text, int sort) {
			this.from = from;
			this.to= to;
			this.txt = text;
			this.timeoutsnd = 0;
			this.cmd = cmd;
			this.sort = sort;
		}
	}

	public static class VirtualNode extends CheapNode {
		private CheapNode toStartNode;

		VirtualNode(CheapWorkflow wf, String nid, String ncode, String text, String prevNodes, int timeout,
				String timeoutRoute,
				// HashMap<Req, CheapRoute> routes,
				String onEvents) throws SQLException, TransException {
			super(wf, "virt-" + nid, "start", "invisible", prevNodes, timeout, timeoutRoute, onEvents);
		}

		public VirtualNode(CheapWorkflow wf, CheapNode startNode)
				throws SQLException, TransException {
			super(wf, "virt-" + startNode.nid, "start", "invisible", null, 0, null, null);
			this.toStartNode = startNode;
		}

		@Override
		public CheapNode findRoute(Req req) throws SemanticException {
			if (req == Req.start)
				return toStartNode;
			else throw new SemanticException("Can't step from virutal node to start node on req %s", req.name());
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
	private HashMap<String, CheapRoute> route_lazy;

	private CheapRoute timeoutRoute;
	private ICheapEventHandler timeoutHandler;

	private ICheapEventHandler eventHandler;
	private String prevNodes;
	private CheapLogic arriveCondt;

	CheapNode(CheapWorkflow wf, String nid, String ncode, String nname,
			String prevNodes, int timeout, String timeoutRoute,
			String onEvents) throws SQLException, TransException {
		this.wf = wf;
		this.nid = nid;
		this.ncode = ncode;
		this.nname = nname;
		this.route_lazy = loadRoutes(nid);
		this.prevNodes = prevNodes;
		this.arriveCondt = new CheapLogic(prevNodes);
		this.eventHandler = createHandler(onEvents);

		if (timeout > 0)
			this.timeoutRoute = new CheapRoute(timeout, timeoutRoute);
		if (timeout > 0) {
			// String[] timeoutRt = new String[2];
			this.timeoutHandler = parseTimeoutRoute(timeoutRoute);
		}
	}

	private HashMap<String, CheapRoute> loadRoutes(String nodeId) throws TransException, SQLException {
		HashMap<String, CheapRoute> routs = new HashMap<String, CheapRoute>();
		SResultset rs = (SResultset) CheapEngin.trcs
				.select(WfMeta.cmdTabl)
				.col(WfMeta.cmdCmd, "cmd")
				.col(WfMeta.cmdTxt, "txt")
				.col(WfMeta.cmdRoute, "route")
				.col(WfMeta.cmdSort, "sort")
				.where("=", WfMeta.nid, "'" + nodeId + "'")
				.rs(CheapEngin.trcs.basiContext());
		rs.beforeFirst();
		while (rs.next()) {
			String cmd = rs.getString("cmd");
			routs.put(cmd, new CheapRoute(nodeId,
					cmd, rs.getString("route"),
					rs.getString("txt"), rs.getInt("sort", 0)));
		}
		return routs;
	}

	private ICheapEventHandler createHandler(String onEvent) {
		if(onEvent != null) {
			try {
				ICheapEventHandler handler = (ICheapEventHandler) Class.forName(onEvent.trim()).newInstance();
				return handler;
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				e.printStackTrace();
				return new CheapHandler();
			}
		}
		return new CheapHandler();
	}

	private ICheapEventHandler parseTimeoutRoute(String timeoutRoute) {
		if (timeoutRoute == null)
			return null;
		String[] timess = timeoutRoute.split(":");
		if (timess == null || timess.length < 2)
			return null;
		if (timess.length == 2)
			return new CheapHandler();
		return createHandler(timess[2]);
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

	public CheapNode findRoute(Req req) throws SemanticException {
		if (route_lazy.containsKey(req))
			return wf.getNode(route_lazy.get(req).to);
		else return null;
	}


	public String prevNodes() { return prevNodes; }

}
