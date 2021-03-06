package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.odysz.common.AESHelper;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapNode.VirtualNode;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.sworkflow.EnginDesign.WfMeta.nodeInst;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**CheapEngine API for server side, equivalent to js/cheapwf.<br>
 * Check Schedual.startInspectask() for sample code.
 * @author ody
 */
public class CheapApi {

	/** Finger print used for checking timeout checker's competition.
	 * When initializing, will update a random value to db, when checking, query it and compare with this version.
	 */
	static String cheaprint;

	public static void initFingerPrint(IUser checkUsr, HashSet<String> wfids)
			throws SQLException, TransException {
		cheaprint = AESHelper.encode64(AESHelper.getRandom());
		ArrayList<String> sqls = new ArrayList<String>(wfids.size());
		for (String wfid : wfids) {
				CheapEnginv1.trcs.update(WfMeta.wftabl)
					.nv(WfMeta.wfChecker, cheaprint)
					.whereEq(WfMeta.wfWfid, wfid)
					.commit(sqls);	
		}
		Connects.commit(checkUsr, sqls);
	}

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
	
	public static CheapResp right(String wftype, String usrId, String nodeId, String taskId)
			throws SemanticException, SQLException {
		if (nodeId == null)
			throw new SemanticException("Node Id is null");

		CheapResp sobj = new CheapResp();
		
		CheapWorkflow wf = CheapEnginv1.wfs.get(wftype);

		// take virtual node as starting node.
		// this logic changed when virtual id composing is changed.
		nodeId = nodeId.replace(VirtualNode.prefix, "");

		CheapNode n = wf.getNode(nodeId);
		if (n == null)
			throw new SemanticException("Node not found: ", nodeId);

		sobj.rights(n.rights(CheapEnginv1.trcs, usrId, taskId));
		return sobj;
	}
	
	public static SemanticObject myTasks(IUser usr) throws TransException, SQLException {
		if (CheapEnginv1.wfs != null) {
			SemanticObject tasks = new SemanticObject();
			for (CheapWorkflow wf : CheapEnginv1.wfs.values()) {
				// select b.changeContent, i.* from ir_prjnodes i
				// join oz_wfnodes n on i.nodeId = n.nodeId and i.handlingCmd is null and n.isFinish <> '1'
				// join a_user u on u.userId = 'admin'
				// join oz_wfrights r on r.nodeId = n.nodeId and r.roleId = u.roleId
				// join p_change_application b on b.currentNode = i.instId;
				AnResultset task = (AnResultset) CheapEnginv1.trcs
						.select(wf.instabl, "i")
						.col("b.*").col("n." + WfMeta.nname)
						.j(WfMeta.nodeTabl, "n", 
								"i.%s = n.%s and i.%s IS null and n.%s = false",
								nodeInst.nodeFk, WfMeta.nid, nodeInst.handleCmd, WfMeta.nisFinish)
						.j(WfMeta.user.tbl, "u", "u.%s = '%s'",
								WfMeta.user.id, usr.uid())
						.j(WfMeta.rights.tbl, "r", "r.%s = n.%s and r.%s = u.%s",
								WfMeta.rights.nodeFk, WfMeta.nid, WfMeta.rights.roleFk, WfMeta.user.roleFk)
						.j(wf.bTabl, "b", "b.%s = i.%s",
								wf.bTaskStateRef, nodeInst.id)
						.rs(CheapEnginv1.trcs.instancontxt(usr)).rs(0);
				tasks.put(wf.wfId, task);
			}
			return tasks;
		}
		return null;
	}

	/**Load a task's workflow instances.<br>
	 * rs[0] - nodes outer join instances:
	 * <pre>select n.sort, n.nodeName, c.txt handleTxt,
case when b.wfState = i.instId then 9 else case when c.txt is null then 0 else 1 end end isCurrent,
i.*, n.isFinish, u.userName
from oz_wfnodes n left outer join task_nodes i on i.nodeId = n.nodeId AND i.taskId = '00000Z'
left outer join tasks b on i.nodeId = n.nodeId AND b.wfState = i.instId AND i.taskId = '00000Z'
left outer join oz_wfcmds c on i.handlingCmd = c.cmd left outer join a_user u on u.userId = i.oper
where n.wfId = 't01' order by n.sort asc, i.prevRec asc

sort |nodeName |handleTxt |isCurrent |instId |nodeId |taskId |oper         |opertime            |descpt |remarks |handlingCmd |prevRec |isFinish |userName     |
-----|---------|----------|----------|-------|-------|-------|-------------|--------------------|-------|--------|------------|--------|---------|-------------|
10   |starting |          |9         |00002L |t01.01 |00000X |CheapApiTest |2019-06-22 17:29:27 |       |        |            |        |         |Cheap Tester |
20   |plan A   |          |0         |       |       |       |             |                    |       |        |            |        |         |             |
30   |plan B   |          |0         |       |       |       |             |                    |       |        |            |        |         |             |
90   |abort    |          |0         |       |       |       |             |                    |       |        |            |        |1        |             |
99   |finished |          |0         |       |       |       |             |                    |       |        |            |        |1        |             |</pre>

	 *
	 * rs[1] - the current instance:<br>
<pre>select i.*, n.isFinish
from task_nodes i
join tasks b on b.wfState = i.instId AND b.taskId = '00000X' AND b.wfId = 't01'
join oz_wfnodes n on n.nodeId = i.nodeId;

instId |nodeId |taskId |oper         |opertime            |descpt |remarks |handlingCmd |prevRec |isFinish |
-------|-------|-------|-------------|--------------------|-------|--------|------------|--------|---------|
00001K |t01.03 |000005 |CheapApiTest |2019-06-22 16:38:00 |       |        |            |00001I  |1        |</pre>
	 * @param wftype
	 * @param taskid
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	public static CheapResp loadFlow(String wftype, String taskid, IUser usr)
			throws TransException, SQLException {
		CheapResp sobj = new CheapResp();
		
		CheapWorkflow wf = CheapEnginv1.wfs.get(wftype);
		if (wf == null) 
			throw new SemanticException("Can not find workflow with id: %s", wftype);

		// select n.sort, n.nodeName, i.* from oz_wfnodes n 
		// left outer join task_nodes i on i.nodeId = n.nodeId and i.taskId = '000001'
		// where n.wfId = 't01'
		// order by n.sort;
		Query q = CheapEnginv1.trcs
				.select(WfMeta.nodeTabl, "n")
				.col("n." + WfMeta.nsort)
				.col("n." + WfMeta.nname)
				.col("c." + WfMeta.cmdTxt, "handleTxt")
				// sqlite: case when b.wfState = i.instId then 9 else case  when c.txt is null then 0 else 1 end end isCurrent
				// mysql:  if(b.currentNode = i.instId, 9, if(c.txt is null, 0, 1)) isCurrent
				.col(Funcall.ifElse(String.format("b.%s = i.%s", wf.bTaskStateRef, WfMeta.nodeInst.id), 9, 
						Funcall.ifNullElse("c." + WfMeta.cmdTxt, 0, 1)), "isCurrent")
				.col("i.*")
				.col("n." + WfMeta.nisFinish)
				.l(wf.instabl, "i", "i." + WfMeta.nodeInst.nodeFk + " = n." + WfMeta.nid + " and i." + WfMeta.nodeInst.busiFk + " = '" + taskid + "'")

				// left outer join p_change_application b on i.nodeId = n.nodeId and b.currentNode = i.instId AND i.taskId = '00000I' 
				// bug 2019.7.30, i.taskId = '00003U' -> b.expenseId = '00003U'
//				.l(wf.bTabl, "b", String.format("i.%s = n.%s and b.%s = i.%s AND i.%s = '%s'",
//							WfMeta.nodeInst.nodeFk, WfMeta.nid, wf.bTaskStateRef, WfMeta.nodeInst.id, WfMeta.nodeInst.busiFk, taskid))
				.l(wf.bTabl, "b", String.format("i.%s = n.%s and b.%s = i.%s AND b.%s = '%s'",
							WfMeta.nodeInst.nodeFk, WfMeta.nid, wf.bTaskStateRef, WfMeta.nodeInst.id, wf.bRecId, taskid))

				.l(WfMeta.cmdTabl, "c", String.format("i.%s = c.%s", WfMeta.nodeInst.handleCmd, WfMeta.cmdCmd))
				.where("=", "n." + WfMeta.nodeWfId, "'" + wftype + "'")
				.orderby("n." + WfMeta.nsort)
				.orderby("i." + WfMeta.nodeInst.prevInst);
		
		if (WfMeta.user.tbl != null) {
			q.l(WfMeta.user.tbl, "u", String.format("u.%s = i.%s", WfMeta.user.id, WfMeta.nodeInst.oper))
				.col("u." + WfMeta.user.name);
		}
		
		SemanticObject list = q.rs(CheapEnginv1.trcs.instancontxt(usr));
		AnResultset lst = (AnResultset) list.rs(0);

		// load current instance
		// select i.* from task_nodes i
		// join tasks b on b.wfState = i.instId and b.taskId = '000010' where b.wfId = 't01'
		list = CheapEnginv1.trcs
				.select(wf.instabl, "i")
				.col("i.*")
				.col("n." + WfMeta.nisFinish)
				.j(wf.bTabl, "b", String.format("b.%s = i.%s and b.%s = '%s' and b.%s = '%s'",
						wf.bTaskStateRef, WfMeta.nodeInst.id, wf.bRecId, taskid, wf.bCateCol, wftype))
				// .where("=", "b." + wf.bCateCol, "'" + wftype + "'")
				.j(WfMeta.nodeTabl, "n", String.format("n.%s = i.%s", WfMeta.nid, nodeInst.nodeFk))
				.rs(CheapEnginv1.trcs.instancontxt(usr));
		AnResultset ist = (AnResultset) list.rs(0);

		sobj.rs(lst, lst.getRowCount());
		sobj.rs(ist, ist.getRowCount());
		sobj.rsInfo(String.format("0: nodes joining instances of %s, 1: current instance",
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
	public static CheapResp loadCmds(String wftype, String nId, String taskid, String uid)
			throws TransException, SQLException {
		if (LangExt.isblank(wftype) || LangExt.isblank(nId))
			throw new CheapException(CheapException.ERR_WF,
					"Target wftype or node is null. wfid: %s, nodeId: %s",
					wftype, nId);
		
		CheapTransBuild t = CheapEnginv1.trcs;
		CheapNode n = CheapEnginv1.getWf(wftype).getNode(nId);
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
				.rs(CheapEnginv1.trcs.basictx());
		AnResultset lst = (AnResultset) list.rs(0);
		
		return (CheapResp) new CheapResp().rs(lst, lst.getRowCount());
	}

	/**Get next route node according to ntimeoutRoute (no time checking).<br>
	 * Only called by CheapChecker?
	 * @param wftype
	 * @param currentNode
	 * @param taskId
	 * @param instId 
	 * @return the update statement for committing
	 * @throws TransException 
	 * @throws SQLException 
	 */
	static SemanticObject stepTimeout(String wftype, String nodeId, String taskId, String instId) throws SQLException, TransException {
		CheapApi api = new CheapApi(wftype, Req.timeout, Req.timeout.name());
		api.taskId = taskId;
		SemanticObject jreq = (SemanticObject) CheapEnginv1
					.commitimeout(CheapEnginv1.checkUser, wftype, nodeId, taskId, instId)
					.get("res");
		return jreq;
	}

	private String wftype;
	private Req req;
	private String taskId;
	private String nodeDesc;
	/** task table n-vs */
	private ArrayList<Object[]> nvs;

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
	
	public CheapApi postupdates(ArrayList<Statement<?>> postups) {
		this.postups = postups;
		if (CheapEnginv1.debug && postups != null && req != Req.start)
			for (Statement<?> post : postups)
				if (post instanceof Insert)
					Utils.warn("[CheapEngin.debug] CheapApi#postupdates(): Inserting new records to %s in a %s request?\n" +
							"You can add this post insertion but the task's auto pk won't resulved.",
							post.mainTabl(), req.name());;
		return this;
	}

	/**Commit current request set in {@link #req}.
	 * <h6>About Competition Check</h6>
	 * <p>One condition and three competition rules in Cheap-workflow v0.8:
	 * <ol> <li>Condition: All instance must have a prevRec, except the only starting node instance.<br>
	 * 			If can't find an out going node with condition 'handleCmd is null',
	 * 			the checking will fail in early stage;</li>
	 * 		<li>Check: When starting, if there is already an instance with null handleCmd for the task, fail;</li>
	 * 		<li>Check: If not starting and prevCmd is null,<br>
	 * 			fail (shouldn't happen when engine upgraded to v7.4);</li>
	 * 		<li>Check: If not starting and the out going instance's routes include the requesting one,
	 * 			fail<br>
	 * 			- old looping instance is not the same (logic by {@link CheapEngin#findFrom()}: handleCmd is null);<br>
	 * 			- branching from the out going instance doesn't have a same route;</li>
	 * </ol>
	 * </p>
	 * 
	 * @param usr
	 * @return { evt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepHandler: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriHandler: {@link CheapEvent} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException 
	 * /
	public SemanticObject commitReq(IUser usr) throws SQLException, TransException {
		CheapTransBuild st = CheapEngin.trcs;

		SemanticObject logic = CheapEngin.onReqCmd(usr, wftype, req, cmd,
					taskId, nodeDesc, nvs, postups);

		Insert ins = (Insert) logic.get("stmt");
		ISemantext smtxt = st.instancontxt(usr);

		// prepare competition checking
		CheapEvent evt = (CheapEvent) logic.get("evt");
		CheapWorkflow wf = CheapEngin.getWf(evt.wfId());

		/*
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
		* /
		Query q = qCompet(req, wf);
		// bug: competition not checked:
		/*insert into ir_prjnodes  (nodeId, opertime, oper, prevRec, taskId, instId) 
values ('pcac-manager', now(), 'admin', null, '00000l', '00005N')

insert into ir_prjnodes  (nodeId, opertime, oper, prevRec, taskId, instId) 
values ('pcac-manager', now(), 'admin', '00005L', '00000l', '00005M')

SELECT count( n.nodeId ) cnt FROM oz_wfnodes n
JOIN ir_prjnodes prv ON n.nodeId = prv.nodeId AND prv.taskId = '00000l'
JOIN ir_prjnodes nxt ON n.nodeId = nxt.nodeId AND nxt.prevRec = prv.instId 
WHERE arrivCondit IS null

select * from ir_prjnodes where taskId = '00000l';

select * from p_change_application where changeId = '00000l';

instId |nodeId       |taskId |oper  |opertime            |descpt         |remarks |handlingCmd       |prevRec |
-------|-------------|-------|------|--------------------|---------------|--------|------------------|--------|
00003B |mpac-start   |00000l |admin |2019-06-18 13:48:15 |               |        |start             |        |
00003C |mpac-ac      |00000l |admin |2019-06-18 13:48:21 |               |        |mpac-ac.next      |<null : competition>
00003D |mpac-gm      |00000l |admin |2019-06-18 14:06:23 |               |        |mpac-gm.deny      |00003C  |
00003E |mpac-start   |00000l |admin |2019-06-18 14:07:47 |               |        |mpac-start.submit |00003D  |
00003F |mpac-ac      |00000l |admin |2019-06-18 14:08:03 |               |        |mpac-ac.next      |00003E  |
00003G |mpac-gm      |00000l |admin |2019-06-18 14:31:08 |11111111111111 |        |mpac-gm.pass      |00003F  |
00004J |mpac-finish  |00000l |admin |2019-06-20 12:55:25 |               |        |                  |00003G  |
00005J |pcac-start   |00000l |admin |2019-06-25 16:04:55 |rrrrr          |        |pcac-start.submit |        |
00005K |pcac-pm      |00000l |admin |2019-06-25 16:05:05 |               |        |pcac-pm.upload    |00005J  |
00005L |pcac-ac      |00000l |admin |2019-06-25 16:05:13 |qqq            |        |pcac-ac.next      |00005K  |
00005M |pcac-manager |00000l |admin |2019-06-25 16:05:26 |               |        |                  |00005L  |
00005N |pcac-manager |00000l |admin |2019-06-25 16:05:29 |               |        |                  |<null : competition>
		 * /

		// check competition, commit.
		// FIXME Is this a performance problem? But only supported with RDBMS that have stored processes?
		lock.lock();
		try {
			if (q != null) {
				// check competation
				SemanticObject s = q.rs(smtxt);
				SResultset rs = (SResultset) s.rs(0);
				if (rs.beforeFirst().next()) {
					if (rs.getInt("cnt") > 0)
						throw new CheapException(CheapException.ERR_WF_COMPETATION,
							"Target instance already exists. wfid = %s, current state = %s, cmd = %s, business Id = %s",
							wf.wfId, evt.currentNodeId(), evt.cmd(), taskId);
				}
			}
			// step
			ins.ins(smtxt);
		} finally { lock.unlock(); }

		((CheapEvent) logic.get("evt")).resulve(smtxt);

		logic.remove("stmt");
		
		// FIXME Why events handler not called here?
		return logic;
	}
	*/

	/**Commit current request set in {@link #req}.
	 * @param usr
	 * @return { evt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepHandler: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriHandler: {@link CheapEvent} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException 
	 */
	public CheapResp commitReq(IUser usr) throws SQLException, TransException {
		return CheapEnginv1.commitReq(usr, wftype, req, cmd,
					taskId, nodeDesc, nvs, postups);
	}

}
