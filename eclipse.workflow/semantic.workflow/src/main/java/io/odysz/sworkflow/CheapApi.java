package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import io.odysz.semantics.IResults;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapNode.VirtualNode;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.x.TransException;

/**CheapEngine API for server side, equivalent to js/cheapwf.<br>
 * Check Schedual.startInspectask() for sample code.
 * @author ody
 */
public class CheapApi {
	static ReentrantLock lock = new ReentrantLock();
	/**Get an API instance to start a new workflow of type wftype.
	 * @param wftype
	 * @return new CheapApi instance
	 */
	public static CheapApi start(String wftype) {
		return new CheapApi(wftype, Req.start, null);
	}

	public static CheapApi next(String wftype, String taskId, String cmd) {
		CheapApi api = new CheapApi(wftype, Req.cmd, cmd);
		api.taskId = taskId;
		return api;
	}
	
	public static SemanticObject right(String wftype, String usrId, String nodeId, String taskId)
			throws SemanticException, SQLException {
		if (nodeId == null)
			throw new SemanticException("Node Id is null");

		SemanticObject sobj = new SemanticObject();
		
		CheapWorkflow wf = CheapEngin.wfs.get(wftype);

		// take virtual node as starting node.
		// this logic changed when virtual id composing is changed.
		nodeId = nodeId.replace(VirtualNode.prefix, "");

		CheapNode n = wf.getNode(nodeId);
		if (n == null)
			throw new SemanticException("Node not found: ", nodeId);

		sobj.put("rights", n.rights(CheapEngin.trcs, usrId, taskId));
		return sobj;
	}

	public static SemanticObject loadFlow(String wftype, String taskid, String usrid)
			throws TransException, SQLException {
		SemanticObject sobj = new SemanticObject();
		
		CheapWorkflow wf = CheapEngin.wfs.get(wftype);

		// select sort, n.nodeName, i.* from oz_wfnodes n 
		// left outer join task_nodes i on i.nodeId = n.nodeId and i.taskId = '000001'
		// where n.wfId = 't01'
		// order by n.sort;
		IResults lst = CheapEngin.trcs
				.select(WfMeta.nodeTabl, "n")
				.col("n.sort").col("n.nodeName").col("i.*").col("c.txt", "handleTxt")
				.l(wf.instabl, "i", "i.nodeId = n.nodeId and i.taskId = '" + taskid + "'")
				.l(WfMeta.cmdTabl, "c", "i.handlingCmd = c.cmd")
				.where("=", "n.wfId", "'" + wftype + "'")
				.orderby("n.sort")
				.rs(CheapEngin.trcs.basictx());

		sobj.rs(lst, lst.getRowCount());
		return sobj;
	}

	/**Get next route node according to ntimeoutRoute (no time checking).<br>
	 * Only called by CheapChecker?
	 * @param wftype
	 * @param currentNode
	 * @param taskId
	 * @return
	 */
	static CheapApi stepTimeout(String wftype, String taskId) {
		CheapApi api = new CheapApi(wftype, Req.timeout, null);
		api.taskId = taskId;
		return api;
	}

	private String wftype;
	private Req req;
	private String taskId;
	private String nodeDesc;
	/** task table n-vs */
	private ArrayList<String[]> nvs;

	private String multiChildTabl;
	private ArrayList<String[]> multiDels;
	/** 3d array ArrayList<ArrayList<String[]>>*/
	private ArrayList<ArrayList<?>> multiInserts;
	private Update postups;
	private String cmd;

	protected CheapApi(String wftype, Req req, String cmd) {
		this.wftype = wftype;
		this.req = req;
		this.cmd = cmd;
	}
	
	public CheapApi taskNv(String n, String v) {
		if (nvs == null)
			nvs = new ArrayList<String[]>();
		nvs.add(new String[] {n, v});
		return this;
	}

	public CheapApi nodeDesc(String nodeDesc) {
		this.nodeDesc = nodeDesc;
		return this;
	}
	
	public CheapApi taskChildMulti(String tabl,
			ArrayList<String[]> delConds, ArrayList<ArrayList<?>> inserts) {
		multiChildTabl = tabl;
		multiDels = delConds;
		multiInserts = inserts;
		return this;
	}
	
	public CheapApi postupdates(Update postups) {
		this.postups = postups;
		return this;
	}

	/**Commit current request set in {@link #req}.
	 * @param usr
	 * @return { evt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepHandler: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriHandler: {@link CheapEvent} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException 
	 */
	public SemanticObject commit(IUser usr) throws SQLException, TransException {
		CheapTransBuild st = CheapEngin.trcs;
		SemanticObject multireq = formatMulti(st, usr, multiChildTabl, multiDels, multiInserts);
		SemanticObject jreq = CheapEngin.onReqCmd(usr, wftype, req, cmd,
					taskId, nodeDesc, nvs, multireq, postups);

		Insert ins = (Insert) jreq.get("stmt");
		ISemantext smtxt = st.instancontxt(usr);

		// prepare competition checking
		CheapEvent evt = (CheapEvent) jreq.get("evt");
		CheapWorkflow wf = CheapEngin.getWf(evt.wfId());

		// select count(n.nodeId) from oz_wfnodes n 
		// join task_nodes prv on n.nodeId = prv.nodeId
		// join task_nodes nxt on n.nodeId = nxt.nodeId and nxt.prevRec = prv.instId
		// where n.arrivCondit is null
		Query q = st.select(WfMeta.nodeTabl, "n")
				.col("count(n.nodeId)", "cnt")
				.j(wf.instabl, "prv", "n.nodeId = prv.nodeId")
				.j(wf.instabl, "nxt", "n.nodeId = nxt.nodeId and nxt.prevRec = prv.instId")
				.where(new Condit(Logic.op.isnull, WfMeta.narriveCondit, null));

		lock.lock();
		try {
			// check competition, commit. FIXME performance problem? But only supported with RDBMS?
			IResults rs = (IResults) q.rs(smtxt);
			if (rs.beforeFirst().next()) {
				if (rs.getInt("cnt") > 0)
					throw new CheapException(
						"Target instance already exists. wfid = %s, current state = %s, cmd = %s, business Id = %s",
						wf.wfId, evt.currentNodeId(), evt.cmd(), evt.taskId());
			}
			ins.ins(smtxt);
		} finally { lock.unlock(); }

		((CheapEvent) jreq.get("evt")).resulve(smtxt);

		jreq.remove("stmt");
		return jreq;
	}
	
	/**Format multi-details request into SemanticObject.
	 * @param st
	 * @param usr 
	 * @param tabl
	 * @param delConds
	 * @param multiInserts
	 * @return formated SemanticObject
	 * @throws TransException 
	 */
	@SuppressWarnings("unchecked")
	private static SemanticObject formatMulti(CheapTransBuild st, IUser usr, String tabl,
			ArrayList<String[]> delConds, ArrayList<ArrayList<?>> multiInserts) throws TransException {
		SemanticObject jmultis = new SemanticObject();
		// del
		if (delConds != null) {
			Delete jdel = st.delete(tabl, usr);
			for (String[] cond : delConds) {
				jdel.where(cond[0], cond[1], cond[2]);
			}
			jmultis.put("del", jdel);
		}
		
		// insert
		if (multiInserts != null) {
			for (ArrayList<?> nvs : multiInserts) {
				Insert jinss = st.insert(tabl, usr);
				for (String[] nv : (ArrayList<String[]>)nvs) 
					jinss.nv(nv[0], nv[1]);
				jmultis.add("insert", jinss);
			}
		}
		return jmultis;
	}
}
