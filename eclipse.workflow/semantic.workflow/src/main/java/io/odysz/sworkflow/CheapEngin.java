package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.module.xtable.IXMLStruct;
import io.odysz.module.xtable.Log4jWrapper;
import io.odysz.module.xtable.XMLDataFactoryEx;
import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.Dataset;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapEvent.Evtype;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.sworkflow.EnginDesign.WfMeta.nodeInst;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

/**A simple work flow engine
 * @author odys-z@github.com
 */
public class CheapEngin {
	public static final boolean debug = true;

	static CheapTransBuild trcs;
	static IUser checkUser;
	
	static HashMap<String, CheapWorkflow> wfs;
	public static HashMap<String, CheapWorkflow> wfs() { return wfs; }

	private static ScheduledFuture<?> schedualed;
	private static ScheduledExecutorService scheduler;

	static HashMap<String, Dataset> ritConfigs;

	/**<b>Important Note: This context can not handle semantics</b> */
	static ISemantext basictx;

	/**Init cheap engine configuration, schedule a timeout checker. 
	 * @param configPath 
	 * @param customChecker 
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SAXException */
	public static void initCheap(String configPath, ICustomChecker customChecker)
			throws TransException, IOException, SAXException {
		// worker thread 
		stopCheap();
		
		reloadCheap(configPath);

		scheduler = Executors.newScheduledThreadPool(0);
		schedualed = scheduler.scheduleAtFixedRate(
				new CheapChecker(wfs, customChecker), 0, 1, TimeUnit.MINUTES);
	}

	private static void reloadCheap(String filepath) throws TransException, IOException, SAXException {
		try {
			LinkedHashMap<String, XMLTable> xtabs = loadXmeta(filepath);
			// table = conn
			XMLTable tb = xtabs.get("conn");
			tb.beforeFirst().next();
			String conn = tb.getString("conn");
			
			// table = rigth-ds 
			ritConfigs = new HashMap<String, Dataset>();
			DatasetCfg.parseConfigs(ritConfigs, xtabs.get("right-ds"));

			// table = semantics
			trcs = new CheapTransBuild(conn, xtabs.get("semantics"));
			basictx = trcs.instancontxt(new CheapRobot());

			// select * from oz_wfworkflow;
			SResultset rs = (SResultset) trcs
					.select(WfMeta.wftabl)
					.rs(basictx); // static context is enough to load cheap configs

			rs.beforeFirst();

			wfs = new HashMap<String, CheapWorkflow>(rs.getRowCount());
			while (rs.next()) {
				// 1. Load work flow meta configuration from xml
				String busitabl = rs.getString(WfMeta.bussTable);
				String busiState = rs.getString(WfMeta.bTaskState);
				String bRecId = rs.getString(WfMeta.bRecId);
				String instabl = rs.getString(WfMeta.instabl);
				CheapWorkflow wf = new CheapWorkflow(
						rs.getString(WfMeta.recId),
						rs.getString(WfMeta.wfName),
						instabl,
						busitabl, // tasks
						bRecId, // tasks.taskId
						busiState, // tasks.wfState
						rs.getString(WfMeta.bussCateCol),
						rs.getString(WfMeta.node1),
						rs.getString(WfMeta.bNodeInstBackRefs));
				wfs.put(rs.getString(WfMeta.recId), wf);

				// 2. append semantics for handling routes, etc.

				// 2.1 node instance auto key, e.g. task_nodes.instId
				CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.autoInc, nodeInst.id);
				
				// 2.2 node instance fk-ins to tasks.taskId
				// in case of step, task-id is not created, the ref string is kept untouched
				// - this shall be improved, implicit semantics is not encouraged.
				CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.fkIns,
						new String[] { nodeInst.busiFk, wf.bTabl, wf.bRecId });


				// 2.3 node instance oper, opertime
				CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.opTime,
						new String[] { nodeInst.oper, nodeInst.opertime });

				// 2.4 business task's pk and current state ref, e.g. tasks.wfState -> task_nodes.instId
				CheapTransBuild.addSemantics(conn, busitabl, bRecId, smtype.autoInc, bRecId);
				CheapTransBuild.addSemantics(conn, busitabl, bRecId, smtype.fkIns,
						new String[] {busiState, instabl, nodeInst.id});
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static LinkedHashMap<String,XMLTable> loadXmeta(String filepath) throws SAXException, IOException {
		LinkedHashMap<String, XMLTable> xtabs = XMLDataFactoryEx.getXtables(
				new Log4jWrapper("").setDebugMode(false), filepath, new IXMLStruct() {
						@Override public String rootTag() { return "workflow"; }
						@Override public String tableTag() { return "t"; }
						@Override public String recordTag() { return "s"; }});

		return xtabs;
	}

	public static void stopCheap() {
		if (schedualed == null && scheduler == null) return;
		// stop worker
		Utils.logi("cancling WF-Checker ... ");
		schedualed.cancel(true);
		scheduler.shutdown();
		try {
		    if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
		        scheduler.shutdownNow();
		    } 
		} catch (InterruptedException e) {
		    scheduler.shutdownNow();
		}
	}

	public static CheapWorkflow getWf(String type) {
		return wfs == null ? null : wfs.get(type);
	}

	/**step to next node according to current node and request.<br>
	 * 1. create node instance;<br>
	 * nv: currentNode.nodeState = cmd-name except start<br>
	 * 2.1. create task, with busiPack as task nvs.<br>
	 * semantics: autopk(tasks.taskId), fk(tasks.wfState - task_nodes.instId);<br>
	 * add back-ref(nodeId:task.nodeBackRef);<br>
	 * 2.2. or update task,<br>
	 * semantics: fk(tasks.wfState - task_nodes.instId)<br>
	 * add back-ref(nodeId:task.nodeBackRef);<br>
	 * 3. handle multi-operation request, e.g. multireq &amp; postreq<br>
	 * 
	 * @param usr
	 * @param wftype
	 * @param req
	 * @param cmd commands in the same as that configured in oz_wfcmds.cmd
	 * @param busiId business record ID if exists, or null to create (providing piggyback)
	 * @param nodeDesc workflow instance node description
	 * @param busiPack nvs for task records
	 * @param multireq  {tabl: tablename, del: dels, insert: eaches};
	 * @param postreq
	 * @return {stmt: {@link Insert}/{@link Update} (for committing), <br>
	 * 	evt: start/step event (new task ID to be resolved), <br>
	 *	stepHandler: {@link ICheapEventHandler} for req (step/deny/next) if there is one configured]<br>
	 *	arriHandler: {@link ICheapEventHandler} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException 
	 */
	public static SemanticObject onReqCmd(IUser usr, String wftype, Req req, String cmd,
			String busiId, String nodeDesc, ArrayList<String[]> busiPack,
			SemanticObject multireq, Update postreq)
					throws SQLException, TransException {
		String currentInstId = null;
		CheapNode currentNode; 

		if (wfs == null)
			throw new SemanticException("Engine must be initialized");

		// prepare current node
		CheapWorkflow wf = wfs.get(wftype);
		if (req == Req.start) {
			currentNode = wf.start(); // a virtual node
			cmd = Req.start.name();
		}
		else {
			if (busiId == null)
				throw new CheapException("Command %s.%s needing find task/business record first. but busi-id is null",
						req.name(), cmd);
			String[] tskInf = wf.getInstByTask(trcs, busiId);
			currentInstId = tskInf[1];
			currentNode = wf.getNode(tskInf[2]); // FIXME can be simplified
		}

		// prepare next node
		if (currentNode == null) throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));
		CheapNode nextNode = currentNode.findRoute(cmd);
		
		if (nextNode == null)
			// a configuration problem?
			throw new CheapException(wf.txt("t-no-node"), 
				wftype, currentInstId, req);

		if (req == Req.start)
			nextNode.checkRights(trcs, usr, cmd, busiId);
		else
			// wf.checkRights(trcs, usr, currentNode, cmd, busiId);
			currentNode.checkRights(trcs, usr, cmd, busiId);

		// 1. create node instance;<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		// post nv: currentNode.nodeState = cmd-name except start<br>
		Insert ins1 = CheapEngin.trcs.insert(wf.instabl(), usr);
		ins1.nv(nodeInst.nodeFk, nextNode.nodeId())
			.nv(nodeInst.descol, nodeDesc);
		String newInstId = basictx.formatResulv(wf.instabl, nodeInst.id);
		if (nodeInst.wfIdFk != null)
			ins1.nv(nodeInst.wfIdFk, wf.wfId);

		// with nv: currentInst.nodeState = cmd-name except start<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		if (Req.start != req) {
			ins1.nv(nodeInst.prevInst, currentInstId)
				.nv(nodeInst.busiFk, busiId); // busiId shouldn't resulved with fk-ins

			ins1.post(trcs.update(wf.instabl)
						.nv(nodeInst.handleCmd, cmd)
						.where("=", nodeInst.id, "'" + currentInstId + "'"));
		}

		// 2.0. prepare back-ref(nodeId:task.nodeBackRef);
		// e.g. oz_workflow.bacRefs = 't01.03:requireAllStep', so set tasks.requireAll = new-inst-id if nodeId = 't01.03';<br>
		String colname = null; 
		if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(nextNode.nodeId())) {
			// requireAllStep = 't01.03'
			colname = wf.bNodeInstRefs.get(nextNode.nodeId());
		}

		//  2.1. create task, with busiPack as task nvs.<br>
		//  semantics: autopk(tasks.taskId), fk(tasks.wfState - task_nodes.instId);<br>
		//  add back-ref(nodeId:task.nodeBackRef),
		if (Req.start == req) {
			Insert ins2 = trcs
					.insert(wf.bTabl, usr)
					// TODO check semantics fkIns(tasks.wfState - task_nodes.instId) 
					.nv(wf.bCateCol, wf.wfId);
			if (busiPack != null)
				for (String[] nv : busiPack)
					ins2.nv(nv[0], nv[1]);
			
			if (colname != null)
				ins2.nv(colname, newInstId);
			
			ins1.post(ins2);
		}
		//  2.2. or update task,<br>
		//  semantics: fk(tasks.wfState - task_nodes.instId)<br>
		//  add back-ref(nodeId:task.nodeBackRef),
		else if (Req.cmd == req) {
			Update upd2 = trcs.update(wf.bTabl, usr)
					.nv(wf.bTaskStateRef,
							// trcs.basictx().formatResulv(wf.instabl, wf.bRecId));
							newInstId)
					.where("=", wf.bRecId, "'" + busiId + "'");

			if (colname != null)
				upd2.nv(colname, newInstId);

			ins1.post(upd2);
		}

		//3. handle multi-operation request, e.g. multireq &amp; postreq<br>
		ins1.postChildren(multireq, trcs);
		
		CheapEvent evt = null;
		if (Req.start == req)
			// start: create task
			evt = new CheapEvent(currentNode.wfId(), Evtype.start,
						currentNode, nextNode,
						// busiId is null for new task, resolved later
						basictx.formatResulv(wf.bTabl, wf.bRecId),
						newInstId,
						Req.start, Req.start.name());
		else
			// step: insert node instance, update task as post updating.
			evt = new CheapEvent(currentNode.wfId(), Evtype.step,
						currentNode, nextNode,
						busiId, newInstId, req, cmd);

		return new SemanticObject()
				.put("stmt", ins1)
				.put("evt", evt)
				.put("stepHandler", currentNode.onEventHandler())
				.put("arriHandler", nextNode.isArrived(currentNode) ? nextNode.onEventHandler() : null);
	}
}
