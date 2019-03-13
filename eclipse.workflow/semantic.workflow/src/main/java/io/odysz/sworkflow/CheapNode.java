package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;

import io.odysz.common.Utils;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Act;
import io.odysz.sworkflow.EnginDesign.Event;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfProtocol;

@SuppressWarnings("unused")
public class CheapNode {
	static class wfInstance {
		static String bussRec = "baseProcessDataId";
	}

	private CheapWorkflow wf;
	private String nid;
	private String ncode;
	private String nname;
	/** [req-cmd, [ cmd, text, event-handler-name ] ] */
	private HashMap<Req, String[]> route;
	/**
	 * [cmd-code, code-name-array], for communicate with client (e.g. req
	 * findroute).<br>
	 * Created according to route when needed.
	 */
	private HashMap<Req, String[]> route_lazy;

	private int timeoutmm;

	private ICheapEventHandler timeoutHandler;

	private HashSet<String> roles;
	private HashMap<Integer, ICheapEventHandler> onEvents;

	CheapNode(CheapWorkflow wf, String nid, String ncode, String nname, String route, String onEvents, int timeout,
			String timeoutRoute, String roleIds) throws SQLException {
		this.wf = wf;
		this.nid = nid;
		this.ncode = ncode;
		this.nname = nname;
		this.route = parseRoute(route);
		this.onEvents = parseEvent(onEvents);
		this.roles = parseRoles(roleIds);

		this.timeoutmm = timeout;
		if (timeout > 0) {
			// String[] timeoutRt = new String[2];
			this.timeoutHandler = parseTimeout(timeoutRoute, this.route);
		}
	}

	private static ICheapEventHandler parseTimeout(String timeoutDef, HashMap<Req, String[]> route) {
		if (timeoutDef == null || timeoutDef.trim().length() == 0) {
			return null;
		}

		// 0: nodeId, 1: cmd text, [2: event-handler-name]
		String[] tss = timeoutDef.split(":");
		try {
			// route[0] = tss[0].trim(); // nodeId
			// route[1] = tss[1].trim(); // cmd text

			// optional handler name
			ICheapEventHandler handler;
			if (tss.length > 2 && tss[2].trim().length() > 0) {
				if (route == null)
					route = new HashMap<Req, String[]>(1);
				route.put(Req.timeout, new String[] { tss[0].trim(), tss[1].trim() });
				handler = (ICheapEventHandler) Class.forName(tss[2].trim()).newInstance();

			} else
				handler = null;

			return handler;
		} catch (Exception ex) {
			System.err.println("Can't parse timout config: " + timeoutDef);
			return null;
		}
	}

	private static HashSet<String> parseRoles(String roleIds) {
		String[] rss = roleIds == null ? null : roleIds.split(",");
		if (rss != null) {
			HashSet<String> roles = new HashSet<String>(rss.length);
			for (String ss : rss)
				roles.add(ss);
			return roles;
		}
		return null;
	}

	private static HashMap<Integer, ICheapEventHandler> parseEvent(String strEvt) {
		String[] ssEvt = strEvt == null || strEvt.trim().length() == 0 ? null : strEvt.split(",");
		if (ssEvt != null) {
			HashMap<Integer, ICheapEventHandler> ehandlers = new HashMap<Integer, ICheapEventHandler>(ssEvt.length);
			for (String evt : ssEvt) {
				String[] ss = evt.split(":");
				if (ss != null && ss.length > 1) {
					try {
						ICheapEventHandler handler = (ICheapEventHandler) Class.forName(ss[1].trim()).newInstance();
						ehandlers.put(Event.parse(ss[0].trim()), handler);
					} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
						Utils.warn("Can't parse event handler configuration: %s", strEvt);
						e.printStackTrace();
					}
				}
			}
			return ehandlers;
		}
		return null;
	}

	/**
	 * @param strRoute
	 *            e.g. next:f01,back:f02:com.ir.eventhandler
	 * @return
	 * @throws SQLException
	 */
	private static HashMap<Req,String[]> parseRoute(String strRoute) throws SQLException {
		String[] ss = strRoute == null ? null : strRoute.split(",");
		if (ss == null)
			return null;

		HashMap<Req, String[]> rt = new HashMap<Req, String[]>(ss.length);
		for (String r : ss) {
			String[] rss = r.split(":");
			if (rss == null || rss.length <= 2 || rss[0] == null || rss[1] == null) {
				System.err.println("Ignoring Route Config: " + r);
				continue;
			}
			Req req = Req.valueOf(rss[0].trim());
			// [req, [cmd, text, event-handler]]
			rt.put(req, new String[] { rss[1].trim(), rss[2].trim(), rss.length > 3 ? rss[3].trim() : null });
		}
		return rt;
	}

	CheapNode getRoute(String req) throws SQLException {
		Req r = Req.valueOf(req);
		return getRoute(r);
	}

	public CheapNode getRoute(Req req) throws SQLException {
		if (route != null && route.containsKey(req))
			return wf.getNode(route.get(req)[0]);
		else
			return null;
	}

	String getReqName(Req req) throws SQLException {
		return route == null ? null : route.get(req)[1];
	}

	String nodeName() {
		return nname;
	}

	public String nodeId() {
		return nid;
	}

	public String wfId() {
		return wf.wfId;
	}

	Act getAct(int arrive) {
		return null;
	}

	HashSet<String> roles() {
		return roles;
	}

	String rolestr() {
		String rls = null;
		for (String r : roles)
			if (roles != null)
				rls += "," + r;
			else
				rls = r;
		return rls;
	}

	public SemanticObject formatAllRoutes(IUser usr) throws SQLException {
		try {
			wf.checkRights(usr, this, null);
			SemanticObject r = convert(route);
			return r.code(WfProtocol.ok);
		} catch (CheapException ce) {
			SemanticObject r = convert(route);
			return r.code(CheapException.ERR_WF);
		}
	}

	public SemanticObject convert(HashMap<Req, String[]> kvs) throws SQLException {
		if (route_lazy == null) {
			if (kvs != null) {
				route_lazy = new HashMap<Req, String[]>(kvs.size());
				for (Req k : kvs.keySet()) {
					String[] kv = kvs.get(k);
					if (kv == null)
						continue;
					route_lazy.put(k, kv);
				}
			} else
				route_lazy = new HashMap<Req, String[]>(0);
		}
		return new SemanticObject().put(WfProtocol.routes, route_lazy);
	}

	public String timeoutRoute() {
		// return timeoutRoute == null ? null : timeoutRoute[0];
		return route != null && route.containsKey(Req.timeout) ? route.get(Req.timeout)[0] : null;
	}

	public String timeoutTxt() {
		// return timeoutRoute == null ? null : timeoutRoute[1];
		return route != null && route.containsKey(Req.timeout) ? route.get(Req.timeout)[1] : null;
	}

	public ICheapEventHandler timeoutHandler() {
		return timeoutHandler;
	}

	public ICheapEventHandler stepEventHandler(Req req) throws SQLException {
		if (route != null && route.containsKey(req)) {
			String[] rt = route.get(req);

			if (rt.length > 2 && rt[2] != null)
				try {
					return (ICheapEventHandler) Class.forName(rt[2].trim()).newInstance();
				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
					Utils.warn("Node (nid = %s) can't initiate an event handler: %s", nid, rt[2]);
				}
		}
		return null;
	}

	public ICheapEventHandler onEventHandler(int arrive) {
		return onEvents == null ? null : onEvents.get(arrive);
	}

}
