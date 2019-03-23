package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
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

		public VirtualNode(CheapWorkflow wf, CheapNode startNode)
				throws SQLException, TransException {
			super(wf, "virt-" + startNode.nid, "start", "invisible", null, 0, null, null, null);
			this.toStartNode = startNode;
			super.routes = new HashMap<String, CheapRoute>(1);
			super.routes.put(Req.start.name(), new CheapRoute(super.nid,
					Req.start.name(), startNode.nid, Req.start.name(), 0) {});
		}

		@Override
		public CheapNode findRoute(String req) throws SemanticException {
//			if (req == Req.start)
				return toStartNode;
//			else throw new SemanticException("Can't step from virutal node to start node on req %s", req.name());
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
	private HashMap<String, CheapRoute> routes;

	private CheapRoute timeoutRoute;
	private ICheapEventHandler timeoutHandler;

	private ICheapEventHandler eventHandler;
	private String prevNodes;
	private CheapLogic arriveCondt;
	
	private String rights;
	/**Default rights if the right is configured as all next nodes without relationship to user, roles, ... */

	/**
	 * @param wf
	 * @param nid
	 * @param ncode
	 * @param nname
	 * @param prevNodes
	 * @param timeout
	 * @param ntimeoutRoute
	 * @param nonEvents
	 * @param rightsView right view definition, arg[0] = next-node-id, arg[1] = user-id
	 * @throws SQLException
	 * @throws TransException
	 */
	CheapNode(CheapWorkflow wf, String nid, String ncode, String nname,
			String prevNodes, int timeout, String timeoutRoute,
			String onEvents, String rightsView) throws SQLException, TransException {
		this.wf = wf;
		this.nid = nid;
		this.ncode = ncode;
		this.nname = nname;
		this.routes = loadRoutes(nid);
		this.prevNodes = prevNodes;
		this.arriveCondt = new CheapLogic(prevNodes);
		this.eventHandler = createHandler(onEvents);

		if (timeout > 0)
			this.timeoutRoute = new CheapRoute(timeout, timeoutRoute);
		if (timeout > 0) {
			// String[] timeoutRt = new String[2];
			this.timeoutHandler = parseTimeoutRoute(timeoutRoute);
		}
		
		this.rights = rightsView;
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
				.rs(CheapEngin.trcs.basictx());
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

	public ICheapEventHandler timeoutHandler() {
		return timeoutHandler;
	}

	public ICheapEventHandler onEventHandler() {
		return eventHandler;
	}

	public String getReqText(Req req) {
		return null;
	}

	public boolean isArrived(CheapNode currentNode) {
		return arriveCondt.isArrive(currentNode.nodeId());
	}

	public CheapNode findRoute(String cmd) throws SemanticException {
		if (routes.containsKey(cmd))
			return wf.getNode(routes.get(cmd).to);
		else return null;
	}

	public String arrivCondt() { return prevNodes; }

	public Set<String> rights(CheapNode nextNode, String cmd, IUser usr) throws SQLException {
//		if (this instanceof VirtualNode)
//			// FIXME What about the user can't start this workflow?
//			return routes.keySet();
//		else
		if (rights != null) {
			String vw = String.format(rights, nextNode.nid, usr.uid());
			SResultset rs = Connects.select(CheapEngin.trcs.basiconnId(), vw, Connects.flag_nothing);

			rs.beforeFirst();
			HashSet<String> set = new HashSet<String>();
			while (rs.next()) {
				set.add(rs.getString("to"));
			}
			return set;
		}
		else {
			return routes.keySet();
		}
	}

}
