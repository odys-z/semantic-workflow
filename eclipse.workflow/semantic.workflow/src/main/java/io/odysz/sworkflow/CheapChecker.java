package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

public class CheapChecker implements Runnable {
	private final HashMap<String, CheapWorkflow> wfs;

	private ICustomChecker customChker;


//	private static final DbLog dbLog;
//	public static DbLog dbLog() { return dbLog; }
	
//	static {
//		checkUser = new CheapRobot();
////		dbLog = new DbLog(checkUser, "WF Checking", "WF Checking", "timeout");
//	}

	public CheapChecker(HashMap<String,CheapWorkflow> wfs, ICustomChecker usrChker) {
		this.wfs = wfs;
		
		this.customChker = usrChker;
	}

	@Override
	public void run() {
		try {
			if (customChker != null)
				customChker.check();
		}
		catch (Exception ex) { ex.printStackTrace(); }

		try { checkTimeout(); }
		catch (Exception ex) { ex.printStackTrace(); }
	}
	
	private void checkTimeout() throws SQLException {
		ArrayList<CheapEvent> evts = new ArrayList<CheapEvent>();
		/* let's rock
			// select TIMESTAMPDIFF(minute, disposalTime, now()) idled, n.timeoutmm, n.timeoutRoute,
			// i.processTypeId wfId, i.processNodeId nodeId, i.baseProcessDataId taskId 
			// from c_process_processing i join ir_wfdef n on i.processNodeId = n.nodeId and n.timeoutmm > 0
			// where TIMESTAMPDIFF(minute, disposalTime, now()) > n.timeoutmm;
			String sql = String.format(
					"select TIMESTAMPDIFF(minute, %s, now()) idled, i.%s wfid, i.%s nodeid, i.%s taskId, i.%s instId " + 
					"from %s i join %s n on i.%s = n.%s and n.%s > 0 " + 
					// IMPORTANT "i.nodeStatus is null" means not handled. Also check where it updated
					"where i.nodeStatus is null and TIMESTAMPDIFF(SECOND, %s, now()) > n.%s;",
					EnginDesign.Instabl.operTime(), EnginDesign.Instabl.wfIdFk(), EnginDesign.Instabl.nodeFk(), EnginDesign.Instabl.busiFK(), EnginDesign.Instabl.instId(),
					EnginDesign.Instabl.tabl(), EnginDesign.WfDeftabl.tabl(), EnginDesign.Instabl.nodeFk(), EnginDesign.WfDeftabl.nid(), EnginDesign.WfDeftabl.outTime(),
					EnginDesign.Instabl.operTime(), EnginDesign.WfDeftabl.outTime());
			ICResultset rs = DA.select(sql);
			rs.beforeFirst();
			while (rs.next()) {
				CheapNode n = wfs.get(rs.getString("wfid")).nodes.get(rs.getString("nodeid"));
				// CheapEvent(wfId,  currentNode,  nextNode,  instid,  taskId,  cmd)
				evts.add(new CheapEvent(n.wfId(), n.nodeId(),
						n.timeoutRoute(), rs.getString("instId"),
						rs.getString("taskId"), n.timeoutTxt()));
			}
			rs.close();
			 
			for (CheapEvent evt : evts) {
				try {
					// timeout stepping 
					timeout(evt);
	 
					// call user handler
					// wfs.get(evt.wfId()).getNode(evt.currentNodeId()).timeoutHandler().onTimeout(evt);
					ICheapEventHandler handler = wfs.get(evt.wfId()).getNode(evt.currentNodeId()).timeoutHandler();
					if (handler != null)
						handler.onTimeout(evt);
				} catch (Exception ex) {
					System.err.println("Timeout event ignored: ");
					System.err.println(evt.toString());
					System.err.println(ex.getMessage());
				}
			}
		*/
	}

	/**Handle timeout event: step event's current node to timeout node - timeout event not fired here.
	 * @param evt
	 * @throws SQLException
	 * @throws IOException
	 * @throws TransException 
	 */
	private static void timeout(CheapEvent evt)
			throws SQLException, IOException, TransException {
		// current node Id means current instance
		CheapApi wfapi = CheapApi.stepTimeout(evt.wfId(), evt.instId(), evt.taskId());
		Update jreq = (Update) wfapi.commit(CheapEngin.checkUser).get("res");
		if (jreq != null) {
			ArrayList<String> sqls = new ArrayList<String>();
			jreq.commit(sqls, CheapEngin.checkUser);
//			Connects.commit(CheapEngin.checkUser, sqls);
		}
		
	}
}
