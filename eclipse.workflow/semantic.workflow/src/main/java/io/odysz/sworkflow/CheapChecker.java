package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg.Dataset;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapEvent.Evtype;

/**A default timeout checker.
 * @author odys-z@github.com
 */
public class CheapChecker implements Runnable, ICheapChecker {
	private ICheapChecker customChker;
	private String conn;
	String wfid;
	private Dataset ds;
	private int ms;

	public CheapChecker(String conn, ICheapChecker usrChker) {
		this.conn = conn;
		this.customChker = usrChker;
	}

	public CheapChecker(String conn, String wfId, int ms, Dataset ds) {
		this.ds = ds;
		wfid = wfId;
		this.conn = conn;
		this.ms = ms;
	}

	@Override
	public void run() {
		try {
			if (customChker != null)
				CheapEnginv1.checked += customChker.check(conn);
			else CheapEnginv1.checked += checkTimeout();
		}
		catch (Exception ex) { ex.printStackTrace(); }
	}
	
	/**Check is any node instances time outed?
	 * @return workflow type count, no matter is there any timeout instance.
	 * @throws SQLException
	 * @throws SemanticException
	 */
	int checkTimeout() throws SQLException, SemanticException {
		if (CheapEnginv1.debug)
			Utils.logi("CheapChecker - checking timeout ...");

		int checked = 0;;
		ArrayList<CheapEvent> evts = new ArrayList<CheapEvent>();
		
		// select TIMESTAMPDIFF(minute, opertime, now()) expMin, i.opertime, n.timeouts, n.timeoutRoute,
		// n.wfId, i.nodeId nodeId, i.taskId taskId 
		// from ir_prjnodes i join oz_wfnodes n on i.nodeId = n.nodeId and n.timeouts > 0
		// where TIMESTAMPDIFF(second, opertime, now()) > n.timeouts;
		String sql = ds.getSql(Connects.driverType(conn));
		if (LangExt.isblank(sql)) {
			if (CheapEnginv1.debug)
				Utils.warn("[CheapEngin.debug] Can't find timemout checking sql configuration. wfId: %s\nsql:\n%s",
						ds.sk(), sql);
			return checked;
		}

		SResultset rs = Connects.select(sql);
		rs.beforeFirst();
		while (rs.next()) {
			String nid = rs.getString("nodeId");
			CheapNode n = CheapEnginv1.getWf(wfid).getNode(nid);
			CheapNode go = CheapEnginv1.getWf(wfid).getNode(n.timeoutRoute().to);

			evts.add(new CheapEvent(n.wfId(), Evtype.timeout, n, go,
					rs.getString("taskId"), rs.getString("instId"), null,
					null, n.timeoutTxt()));
		}
		rs.close();

		checked++;

		for (CheapEvent evt : evts) {
			try {
				// timeout stepping 
				// timeout(evt);
				CheapApi.stepTimeout(evt.wfId(), evt.currentNodeId(), evt.taskId(), evt.instId());
	 
				// call user handler
				ICheapEventHandler handler = CheapEnginv1.getWf(wfid).getNode(evt.currentNodeId()).timeoutHandler();
				if (handler != null)
					new Thread(() -> {
						try {handler.onTimeout(evt);}
						catch (Throwable t) { 
							Utils.warn("Handler failed for event on-timeout. taskId: %s, instId: %s\ndetais:\n%s",
									evt.taskId(), evt.instId(), t.getMessage());
						}
					}).start();
			} catch (Exception ex) {
				Utils.warn("Timeout event ignored.\nEvent:\n%s\nException:\n%s",
						evt.toString(), ex.getMessage());
			}
		}
		return checked;
	}

	/**call {@link #checkTimeout()}
	 * @see io.odysz.sworkflow.ICheapChecker#check(java.lang.String)
	 */
	@Override
	public int check(String conn) throws SemanticException, SQLException {
		return checkTimeout();
	}

	@Override public long ms() { return ms; }
	
	@Override public String wfId() { return wfid; }
}
