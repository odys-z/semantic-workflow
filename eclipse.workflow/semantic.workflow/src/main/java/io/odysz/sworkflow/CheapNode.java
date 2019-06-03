package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.common.LangExt;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg.Dataset;
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

		public CheapRoute(int timeout, String timeoutRoute) throws SemanticException {
			if (timeout <= 0)
				throw new SemanticException("Timeout Route consturctor can't been called if timeout is not defined");
		}

		public CheapRoute(String from, String cmd, String to, String text, int sort) {
			this.from = from;
			this.to= to;
			this.txt = text;
			this.timeoutsnd = 0;
			this.cmd = cmd;
			this.sort = sort;
		}
		
		/////// JSON Helpers /////////////////////////////////////////////////////////////
		@Override
		public String toString() {
			return LangExt.toString(new String[] {from, to, txt, cmd, String.valueOf(timeoutsnd), String.valueOf(sort)});
		}
		
		public CheapRoute(String js) {
			String[] jss = LangExt.toArray(js);
			this.from = jss[0];
			this.to= jss[1];
			this.txt = jss[2];
			this.cmd = jss[3];
			this.timeoutsnd = Integer.valueOf(jss[4]);
			this.sort = Integer.valueOf(jss[5]);
		}
	}

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
		SemanticObject s = CheapEngin.trcs
				.select(WfMeta.cmdTabl)
				.col(WfMeta.cmdCmd, "cmd")
				.col(WfMeta.cmdTxt, "txt")
				.col(WfMeta.cmdRoute, "route")
				.col(WfMeta.cmdSort, "sort")
				.where("=", WfMeta.nid, "'" + nodeId + "'")
				.rs(CheapEngin.basictx);
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
		SResultset rs = Connects.select(CheapEngin.trcs.basiconnId(), vw, Connects.flag_nothing);
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
		Dataset ds = CheapEngin.ritConfigs.get(dskey);
		if (ds == null)
			ds = CheapEngin.ritConfigs.get("ds-allcmd");
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
				throw new CheapException(CheapException.ERR_WF_Rights, "wf %s, user %s, node %s, task: %s",
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
