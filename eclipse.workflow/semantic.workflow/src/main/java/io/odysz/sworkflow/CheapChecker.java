package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg.Dataset;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapEvent.Evtype;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

public class CheapChecker implements Runnable {
	// private final HashMap<String, CheapWorkflow> wfs;

	private ICheapChecker customChker;
	private String conn;
	private Dataset ds;
	private String wfid;
	@SuppressWarnings("unused")
	private int ms;

	// public CheapChecker(HashMap<String,CheapWorkflow> wfs, ICheapChecker usrChker) {
	public CheapChecker(String conn, ICheapChecker usrChker) {
		// this.wfs = wfs;
		this.conn = conn;
		this.customChker = usrChker;
	}

	public CheapChecker(String conn, String wfid, int ms, Dataset ds) {
		// TODO Auto-generated constructor stub
		this.ds = ds;
		this.wfid = wfid;
		this.conn = conn;
		this.ms = ms;
	}

	@Override
	public void run() {
		try {
			if (customChker != null)
				customChker.check(conn);
			else checkTimeout();
		}
		catch (Exception ex) { ex.printStackTrace(); }
	}
	
	private void checkTimeout() throws SQLException, SemanticException {
		if (CheapEngin.debug)
			Utils.logi("CheapChecker - checking timeout ...");
		ArrayList<CheapEvent> evts = new ArrayList<CheapEvent>();
		
		// select TIMESTAMPDIFF(minute, opertime, now()) expMin, i.opertime, n.timeouts, n.timeoutRoute,
		// n.wfId, i.nodeId nodeId, i.taskId taskId 
		// from ir_prjnodes i join oz_wfnodes n on i.nodeId = n.nodeId and n.timeouts > 0
		// where TIMESTAMPDIFF(second, opertime, now()) > n.timeouts;
		String sql = ds.getSql(Connects.driverType(conn));
		SResultset rs = Connects.select(sql);
		rs.beforeFirst();
		while (rs.next()) {
			String nid = rs.getString("nodeId");
			CheapNode n = CheapEngin.getWf(wfid).getNode(nid);
			CheapNode go = CheapEngin.getWf(wfid).getNode(n.timeoutRoute().to);

			evts.add(new CheapEvent(n.wfId(), Evtype.timeout, n, go,
					rs.getString("taskId"), rs.getString("instId"),
					null, n.timeoutTxt()));
		}
		rs.close();

		for (CheapEvent evt : evts) {
			try {
				// timeout stepping 
				timeout(evt);
	 
				// call user handler
				// wfs.get(evt.wfId()).getNode(evt.currentNodeId()).timeoutHandler().onTimeout(evt);
				ICheapEventHandler handler = CheapEngin.getWf(wfid).getNode(evt.currentNodeId()).timeoutHandler();
				if (handler != null)
					handler.onTimeout(evt);
			} catch (Exception ex) {
				System.err.println("Timeout event ignored: ");
				System.err.println(evt.toString());
				System.err.println(ex.getMessage());
			}
		}
	}

	/**Handle timeout event: step event's current node to timeout node - timeout event not fired here.
	 * @param evt
	 * @throws SQLException
	 * @throws IOException
	 * @throws TransException 
	 */
	@SuppressWarnings("unused")
	private static void timeout(CheapEvent evt)
			throws SQLException, IOException, TransException {
		// current node id means current instance
		CheapApi wfapi = CheapApi.stepTimeout(evt.wfId(),
				(String)evt.taskId()); // string: event must already resulved.
		Update jreq = (Update) wfapi.commit(CheapEngin.checkUser).get("res"); // NOT NULLLL!!!!
		if (jreq != null) {
			ArrayList<String> sqls = new ArrayList<String>();
			jreq.commit(sqls, CheapEngin.checkUser);
		}
		
	}
}
