package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.common.LangExt;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfgV11.Dataset;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.x.TransException;

public class CheapNode {
	public static class CheapRoute {
		/**From node Id required by the command */
		String from;
		/**Target node Id stepped by the command */
		String to;
		/**Command route's text */
		String txt;
		/** -1 for not a timeout route */
		int timeoutsnd = -1;
		String cmd; 
		int sort;

		public CheapRoute(String from, String cmd, String to, String text, int sort) {
			this.from = from;
			this.to= to;
			this.txt = text;
			this.timeoutsnd = 0;
			this.cmd = cmd;
			this.sort = sort;
		}
		
		/**Convert to json like string
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return LangExt.toString(new String[] {from, to, txt, cmd, String.valueOf(timeoutsnd), String.valueOf(sort)});
		}
	}

	/**A virtual node used before a stepping to a starting node.
	 * @author odys-z@github.com
	 */
	public static class VirtualNode extends CheapNode {
		public static final String prefix = "virt-";
		public static final String virtualName = "invisible";

		private CheapNode toStartNode;

		public VirtualNode(CheapWorkflow wf, CheapNode startNode)
				throws SQLException, TransException {
			super(wf, prefix + startNode.nid, "start", virtualName, null, 0, null, null, null);
			this.toStartNode = startNode;
			super.routes = new HashMap<String, CheapRoute>(1);
			super.routes.put(Req.start.name(), new CheapRoute(super.nid,
					Req.start.name(), startNode.nid, Req.start.name(), 0) {});
		}

		@Override
		public CheapNode findRoute(String req) throws SemanticException {
			return toStartNode;
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

		if (timeout > 0 && !LangExt.isblank(timeoutRoute)) {
			// String[] timeoutRt = new String[2];
			Object[] rh = parseTimeoutRoute(nid, timeoutRoute);
			this.timeoutRoute = (CheapRoute) rh[0];
			this.timeoutHandler = (ICheapEventHandler) rh[1];
		}
		
		this.rights = rightsView;
	}

	private HashMap<String, CheapRoute> loadRoutes(String nodeId) throws TransException, SQLException {
		HashMap<String, CheapRoute> routs = new HashMap<String, CheapRoute>();
		SemanticObject s = CheapEnginv1.trcs
				.select(WfMeta.cmdTabl)
				.col(WfMeta.cmdCmd, "cmd")
				.col(WfMeta.cmdTxt, "txt")
				.col(WfMeta.cmdRoute, "route")
				.col(WfMeta.cmdSort, "sort")
				.where("=", WfMeta.nid, "'" + nodeId + "'")
				.rs(CheapEnginv1.basictx);
		SResultset rs = (SResultset) s.rs(0);
		rs.beforeFirst();
		while (rs.next()) {
			String cmd = rs.getString("cmd");
			routs.put(cmd, new CheapRoute(nodeId,
					cmd, rs.getString("route"),
					rs.getString("txt"), rs.getInt("sort", 0)));
		}
		return routs;
	}

	private static ICheapEventHandler createHandler(String cls) {
		if(cls != null) {
			try {
				ICheapEventHandler handler = (ICheapEventHandler) Class.forName(cls.trim()).newInstance();
				return handler;
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				e.printStackTrace();
				return new CheapHandler();
			}
		}
		return new CheapHandler();
	}

	/**Parse tiemoutRoute string, in format of [target node]:[text]:[handler],
	 * <br>e.g. "t01.01:time out:io.oz.sample.handler",<br>
	 * where <i>handler</i> is an implementation of {@link ICheapEventHandler} which can be ignored
	 * - timeout routing is already handled, nothing can do if there is no special business handling.<br>
	 * See <a href='https://odys-z.github.io/notes/cheapengin/about.html#cheap-config-oz_wfnodes-timeoutroute'>
	 * How to configure tiemout route</a>
	 * @param from from node id - the node will timeout
	 * @param timeoutRoute e.g. "t01.01:time out:io.oz.sample.handler"
	 * @return 0: {@link CheapRoute} object; 1: {@link ICheapEventHandler} if configured
	 */
	public static Object[] parseTimeoutRoute(String from, String timeoutRoute) {
		if (timeoutRoute == null)
			return null;
		String[] timess = timeoutRoute.split(":");
		if (timess == null || timess.length < 2)
			return null;
		if (timess.length == 2)
			return new Object[] {
				new CheapRoute(from, Req.timeout.name(), timess[0], timess[1], 9999),
				new CheapHandler()};
		return new Object[] {
				new CheapRoute(from, Req.timeout.name(), timess[0], timess[1], 9999),
				createHandler(timess[2])};
	}

	public String nodeId() { return nid; }
	public String wfId() { return wf.wfId; }
	public String nname() { return nname; }
	public String ncode() { return ncode; }

	public CheapRoute timeoutRoute() { return timeoutRoute; }

	public ICheapEventHandler timeoutHandler() { return timeoutHandler; }

	public ICheapEventHandler onEventHandler() { return eventHandler; }

	public boolean isArrived(CheapNode currentNode) {
		return arriveCondt.isArrive(currentNode.nodeId());
	}

	public CheapNode findRoute(String cmd) throws SemanticException {
		if (routes.containsKey(cmd))
			return wf.getNode(routes.get(cmd).to);
		else return null;
	}

	public String arrivCondt() { return prevNodes; }

	/**Get the node's command filtered by the nodes.cmdRights (view sql).
	 * @param trcs
	 * @param usrId
	 * @param taskId
	 * @return right map [cmd, cmd-txt]
	 * @throws SQLException
	 * @throws SemanticException
	 */
	public HashMap<String, String> rights(CheapTransBuild trcs, String usrId, String taskId)
			throws SQLException, SemanticException {
		String dskey = rightSk();

		// args: [%1$s] wfid, [%2$s] node-id, [%3$s] user-id, [%4$s] task-id
		String vw = String.format(rightDs(dskey, trcs), wfId(), nid, usrId, taskId);
		SResultset rs = Connects.select(CheapEnginv1.trcs.basiconnId(), vw, Connects.flag_nothing);
		rs.beforeFirst();
		HashMap<String, String> map = new HashMap<String, String>();
		while (rs.next()) { 
			// TODO add contract document to xml
			// 1: cmd, 2: nodeId, 3, roleId, 4, userId
			map.put(rs.getString(1), rs.getString(2));
		}
		return map;
	}

	String rightSk() {
		if (!LangExt.isblank(rights)) 
			return rights;
		else return "ds-allcmd";
	}

	/**Get sql configured in workflow-meta.xml/table="rigth-ds"
	 * @param dskey
	 * @param trcs for getting correct driver type
	 * @return configured sql template
	 * @throws SemanticException
	 * @throws SQLException
	 */
	public static String rightDs(String dskey, CheapTransBuild trcs) throws SemanticException, SQLException {
		Dataset ds = CheapEnginv1.ritConfigs.get(dskey);
		if (ds == null)
			ds = CheapEnginv1.ritConfigs.get("ds-allcmd");
		String sql = ds.getSql(Connects.driverType(trcs.basiconnId()));
		return sql;
	}

	/**Check user rights for req.
	 * @param trcs
	 * @param usr
	 * @param req 
	 * @param cmd
	 * @param taskId
	 * @throws SQLException Database accessing failed
	 * @throws SemanticException 
	 */
	public void checkRights(CheapTransBuild trcs, IUser usr, Req req, String cmd, String taskId)
			throws SemanticException {
		if (usr instanceof CheapRobot)
			return;
		try {
			HashMap<String, String> rts = rights(trcs, usr.uid(), taskId);
			if (req == Req.start && rts.size() == 0
				|| req != Req.start && !rts.keySet().contains(cmd))
				throw new CheapException(CheapException.ERR_WF_Rights,
						"No rights for the requst.\nwf: %s, user: %s, node: %s, task: %s",
						wf.wfId, usr.uid(), nid, taskId);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new CheapException(CheapException.ERR_WF_INTERNAL,
					"CheapNode#checkRights() internal error: %s\nwf %s, user %s, node %s, task: %s, cmd: %s",
					e.getMessage(), wf.wfId, usr.uid(), nid, taskId, cmd);
		}
	}
	
	/////// JSON Protocol helper //////////////////////////////////////////////
	@Override
	public String toString() {
		return LangExt.toString(new String[] {wf.wfId, wf.wfName, nid, ncode, nname});
								// LangExt.toString((HashMap<String, ?>)routes)});
	}
	
	public CheapNode(String nodeStr) throws SQLException, TransException {
		String[] ss = LangExt.toArray(nodeStr);
		wf = new CheapWorkflow(ss[0], ss[1]);
		nid = ss[2];
		ncode = ss[3];
		nname = ss[4];
	}

	public String timeoutTxt() {
		// TODO Auto-generated method stub
		return null;
	}
}
