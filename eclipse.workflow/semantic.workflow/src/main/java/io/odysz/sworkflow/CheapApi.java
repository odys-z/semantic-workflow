package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.odysz.common.LangExt;
import io.odysz.module.rs.SResultset;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapNode.VirtualNode;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**CheapEngine API for server side, equivalent to js/cheapwf.<br>
 * Check Schedual.startInspectask() for sample code.
 * @author ody
 */
public class CheapApi {
	static ReentrantLock lock = new ReentrantLock();
	/**Get an API instance to start a new workflow of type wftype.
	 * @param wftype
	 * @param existask optional, if not exists, cheap engine will create a task record. 
	 * @return new CheapApi instance
	 */
	public static CheapApi start(String wftype, String... existask) {
		CheapApi api = new CheapApi(wftype, Req.start, null);

		api.taskId = existask == null || existask.length == 0 ? null : existask[0];
		return api;
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

	public static SemanticObject loadFlow(String wftype, String taskid, IUser usr)
			throws TransException, SQLException {
		SemanticObject sobj = new SemanticObject();
		
		CheapWorkflow wf = CheapEngin.wfs.get(wftype);
		if (wf == null) 
			throw new SemanticException("Can not find workflow with id: %s", wftype);

		// select n.sort, n.nodeName, i.* from oz_wfnodes n 
		// left outer join task_nodes i on i.nodeId = n.nodeId and i.taskId = '000001'
		// where n.wfId = 't01'
		// order by n.sort;
		SemanticObject list = CheapEngin.trcs
				.select(WfMeta.nodeTabl, "n")
				.col("n." + WfMeta.nsort)
				.col("n." + WfMeta.nname)
				.col("c." + WfMeta.cmdTxt, "handleTxt")
				// sqlite: case when b.wfState = i.instId then 9 else case  when c.txt is null then 0 else 1 end end isCurrent
				// mysql:  if(b.currentNode = i.instId, 9, if(c.txt is null, 0, 1)) isCurrent
				.col(Funcall.ifElse(String.format("b.%s = i.%s", wf.bTaskStateRef, WfMeta.nodeInst.id), 9, 
						Funcall.ifNullElse("c." + WfMeta.cmdTxt, 0, 1)), "isCurrent")
				.col("i.*")
				.l(wf.instabl, "i", "i." + WfMeta.nodeInst.nodeFk + " = n." + WfMeta.nid + " and i." + WfMeta.nodeInst.busiFk + " = '" + taskid + "'")
				// left outer join p_change_application b on i.nodeId = n.nodeId and b.currentNode = i.instId AND i.taskId = '00000I' 
				.l(wf.bTabl, "b", String.format("i.%s = n.%s and b.%s = i.%s AND i.%s = '%s'",
							WfMeta.nodeInst.nodeFk, WfMeta.nid, wf.bTaskStateRef, WfMeta.nodeInst.id, WfMeta.nodeInst.busiFk, taskid))
				.l(WfMeta.cmdTabl, "c", String.format("i.%s = c.%s", WfMeta.nodeInst.handleCmd, WfMeta.cmdCmd))
				.where("=", "n." + WfMeta.nodeWfId, "'" + wftype + "'")
				.orderby("n." + WfMeta.nsort)
				.orderby("i." + WfMeta.nodeInst.prevInst)
				.rs(CheapEngin.trcs.instancontxt(usr));
		SResultset lst = (SResultset) list.rs(0);

		// load current instance
		// select i.* from task_nodes i
		// join tasks b on b.wfState = i.instId and b.taskId = '000010' where b.wfId = 't01'
		list = CheapEngin.trcs
				.select(wf.instabl, "i")
				.col("i.*")
				.j(wf.bTabl, "b", String.format("b.%s = i.%s and b.%s = '%s'",
						wf.bTaskStateRef, WfMeta.nodeInst.id, wf.bRecId, taskid))
				.where("=", "b." + wf.bCateCol, "'" + wftype + "'")
				.rs(CheapEngin.trcs.instancontxt(usr));
		SResultset ist = (SResultset) list.rs(0);

		sobj.rs(lst, lst.getRowCount());
		sobj.rs(ist, ist.getRowCount());
		sobj.put("rs structure", String.format("0: nodes joining instances of %s, 1: current instance",
					taskid));
		return sobj;
	}

	/**
	 * @param wftype
	 * @param nId
	 * @param taskid
	 * @param uid
	 * @return <pre>
nodeId   |cmd             |txt         |rightFilter |rt |
---------|----------------|------------|------------|---|
chg01.01 |chg01.01.submit |zh: submit  |a           |1  |
chg01.01 |chg01.start     |start check |a           |0  |</pre>
	 * @throws TransException
	 * @throws SQLException
	 */
	public static SemanticObject loadCmds(String wftype, String nId, String taskid, String uid)
			throws TransException, SQLException {
		if (LangExt.isblank(wftype) || LangExt.isblank(nId))
			throw new CheapException(CheapException.ERR_WF,
					"Target wftype or node is null. wfid: %s, nodeId: %s",
					wftype, nId);
		
		CheapTransBuild t = CheapEngin.trcs;
		CheapNode n = CheapEngin.getWf(wftype).getNode(nId);
		if (n == null)
			throw new CheapException(CheapException.ERR_WF,
					"Can't find node: wfId: %s, node: %s",
					wftype, nId);
		// select c.nodeId, c.cmd, _v.cmd from oz_wfnodes n 
		// join oz_wfcmds c  on n.wfId = 'chg01' and n.nodeId = c.nodeId and n.nodeId = 'chg01.10'
		// 		left outer join (	
		// 			select c.cmd, c.txt from oz_wfrights r
		// 			join a_user u on u.userId = 'admin'
		// 			join oz_wfcmds c on r.cmd = c.cmd and r.roleId = u.roleId and r.wfId = 'chg01' and r.nodeId = 'chg01.10'
		// 		) _v on c.cmd = _v.cmd
		
		String rightSelct = String.format(CheapNode.rightDs(n.rightSk(), t), wftype, n.nodeId(), uid, taskid);
		SemanticObject list = t.select(WfMeta.nodeTabl, "n")
				.col("c.nodeId").col("c.cmd").col("c.txt").col("c.css")
				// .col("_v.cmd")
				.col(Funcall.ifNullElse("_v.cmd", false, true), "rt")
				.j(WfMeta.cmdTabl, "c", Sql.condt(String.format(
						"n.wfId = '%s' and n.nodeId = c.nodeId and n.nodeId = '%s'",
						wftype, nId)))
				// .l(t.select("(" + CheapNode.rightDs(n.rightSk(), t) + ")", "_v"), "", "c.cmd = _v.cmd")
				.l(t.select("(" + rightSelct + ")", "_v"), "", "c.cmd = _v.cmd")
				.rs(CheapEngin.trcs.basictx());
		SResultset lst = (SResultset) list.rs(0);
		
		return new SemanticObject().rs(lst, lst.getRowCount());
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
	private ArrayList<Object[]> nvs;

	// private String multiChildTabl;
	// private ArrayList<String[]> multiDels;
	/** 3d array ArrayList<ArrayList<String[]>>*/
	// private ArrayList<ArrayList<?>> multiInserts;
	private ArrayList<Statement<?>> postups;
	private String cmd;

	protected CheapApi(String wftype, Req req, String cmd) {
		this.wftype = wftype;
		this.req = req;
		this.cmd = cmd;
	}
	
	public CheapApi taskNv(String n, Object v) {
		if (nvs == null)
			nvs = new ArrayList<Object[]>();
		nvs.add(new Object[] {n, v});
		return this;
	}

	public CheapApi taskNv(List<Object[]>nvs) {
		if (nvs != null)
			for (Object[] nv : nvs)
				taskNv((String)nv[0], nv[1]);
		return this;
	}

	public CheapApi nodeDesc(String nodeDesc) {
		this.nodeDesc = nodeDesc;
		return this;
	}
	
//	public CheapApi taskChildMulti(String tabl,
//			ArrayList<String[]> delConds, ArrayList<ArrayList<?>> inserts) {
//		multiChildTabl = tabl;
//		multiDels = delConds;
//		multiInserts = inserts;
//		return this;
//	}
	
	public CheapApi postupdates(ArrayList<Statement<?>> postups) {
		this.postups = postups;
		return this;
	}

	/**Commit current request set in {@link #req}. TODO how about rename this as doCmd()?
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
//		SemanticObject multireq = formatMulti(st, usr, multiChildTabl, multiDels, multiInserts);
//		SemanticObject jreq = CheapEngin.onReqCmd(usr, wftype, req, cmd,
//					taskId, nodeDesc, nvs, multireq, postups);

		SemanticObject jreq = CheapEngin.onReqCmd(usr, wftype, req, cmd,
					taskId, nodeDesc, nvs, postups);

		Insert ins = (Insert) jreq.get("stmt");
		ISemantext smtxt = st.instancontxt(usr);

		// prepare competition checking
		CheapEvent evt = (CheapEvent) jreq.get("evt");
		CheapWorkflow wf = CheapEngin.getWf(evt.wfId());

		Query q;
		if (req == Req.start && !LangExt.isblank(taskId)) {
			// CheapEngin only support 1 starting node
			
			// select count(*) cnt from tasks b 
			// join task_nodes i on b.taskId = '000002' and b.startNode is not null and b.startNode = i.instId
			// 		where wfState is not null AND wfState not in 
			// 		( select nodeId from oz_wfnodes  where isFinish = '1' and wfId = 't01' );
			q = st.select(wf.bTabl, "b")
					.col("count(*)", "cnt")
					.j(wf.instabl, "i", String.format("b.%s = '%s' and b.startNode is not null and b.startNode = i.instId",
													wf.bRecId, taskId))
					// .where("=", wf.bTaskStateRef, startCol)
					// .where(new Condit(Logic.op.isNotnull, wf.bTaskStateRef, wf.bTaskStateRef)) 	// alread started
					.where(new Condit(Logic.op.notin, "i." + WfMeta.nid, st.select(WfMeta.nodeTabl)		// not finish yet
													.col(WfMeta.nid)
													.where_("=", WfMeta.nodeWfId, wftype)
													.where_("=", WfMeta.nisFinish, "1")))
					;
		}
		// select count(n.nodeId) from oz_wfnodes n 
		// join task_nodes prv on n.nodeId = prv.nodeId
		// join task_nodes nxt on n.nodeId = nxt.nodeId and nxt.prevRec = prv.instId
		// where n.arrivCondit is null
		else if (req != Req.start) {
			q = st.select(WfMeta.nodeTabl, "n")
				.col("count(n.nodeId)", "cnt")
				.j(wf.instabl, "prv", String.format("n.nodeId = prv.nodeId and prv.taskId = '%s'", taskId))
				.j(wf.instabl, "nxt", "n.nodeId = nxt.nodeId and nxt.prevRec = prv.instId")
				.where(new Condit(Logic.op.isnull, WfMeta.narriveCondit, ""));
		}
		else {
			q = null;
		}

		// check competition, commit.
		// FIXME Is this a performance problem? But only supported with RDBMS that have stored processes?
		lock.lock();
		try {
			if (q != null) {
				SemanticObject s = q.rs(smtxt);
				SResultset rs = (SResultset) s.rs(0);
				if (rs.beforeFirst().next()) {
					if (rs.getInt("cnt") > 0)
						throw new CheapException(CheapException.ERR_WF_COMPETATION,
							"Target instance already exists. wfid = %s, current state = %s, cmd = %s, business Id = %s",
							wf.wfId, evt.currentNodeId(), evt.cmd(), taskId);
				}
			}
			ins.ins(smtxt);
		} finally { lock.unlock(); }

		((CheapEvent) jreq.get("evt")).resulve(smtxt);

		jreq.remove("stmt");
		
		// FIXME Why events handler not called here?
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
	 */
}
