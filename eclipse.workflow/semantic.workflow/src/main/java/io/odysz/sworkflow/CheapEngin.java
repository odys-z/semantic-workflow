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
import io.odysz.semantic.DA.DATranscxt;
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

	/**Init cheep engine configuration, schedual a timeout checker. 
	 * @param customChecker 
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SAXException */
	public static void initCheap(String configPath, ICustomChecker customChecker)
			throws TransException, IOException, SAXException {
		reloadCheap(configPath);

		// worker thread 
		stopCheap();
		
		scheduler = Executors.newScheduledThreadPool(0);
		schedualed = scheduler.scheduleAtFixedRate(
				new CheapChecker(wfs, customChecker), 0, 1, TimeUnit.MINUTES);
	}

	private static void reloadCheap(String filepath) throws TransException, IOException, SAXException {
		try {
			LinkedHashMap<String, XMLTable> xtabs = loadXmeta(filepath);
			XMLTable tb = xtabs.get("conn");
			tb.beforeFirst().next();
			String conn = tb.getString("conn");

			trcs = new CheapTransBuild(conn, xtabs.get("semantics"));

			// String conn = CheapEngin.trcs.basiconnId();

			// select * from oz_wfworkflow;
			SResultset rs = (SResultset) trcs
					.select(WfMeta.wftabl)
					.rs(trcs.basictx()); // static context is enough to load cheap configs

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

				// 2.2 node instance oper, opertime
				CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.opTime,
						new String[] { nodeInst.oper, nodeInst.opertime });

				// 2.3 node instance Fk to nodes.nodeId, e.g. task_nodes.nodeId -> oz_wfnodes.nodeId
//				DATranscxt.addSemantics(conn, nodeInstabl, WfMeta.nodeInstId, smtype.fkIns,
//						String.format("%s,%s,%s", WfMeta.nodeInstNode, WfMeta.nodeTabl, WfMeta.nid));

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
	 * @param currentInstId current workflow instance id, e.g. value of c_process_processing.recId
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
	public static SemanticObject onReqCmd_bak(IUser usr, String wftype, String currentInstId, Req req, String cmd,
			String busiId, String nodeDesc, ArrayList<String[]> busiPack,
			SemanticObject multireq, Update postreq)
					throws SQLException, TransException {

		CheapNode currentNode; 
		CheapEvent evt = null; 

		if (wfs == null)
			throw new SemanticException("Engine must be initialized");

		CheapWorkflow wf = wfs.get(wftype);
		if (req == Req.start) {
			currentNode = wf.start(); // a virtual node
			cmd = Req.start.name();
		}
		else {
			currentNode = wf.getNodeByInst(currentInstId);
		}
		if (currentNode == null) throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));
		CheapNode nextNode = currentNode.findRoute(cmd);
		
		if (nextNode == null)
			// a configuration problem?
			throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));

		// Check whether a target node already exists.
		// In competition saturation, client error, timeout while user considering, target can be already exists.
		// if (!Req.eq(Req.start, req))
		//		checkExistance(nextNode, busiId);

		wf.checkRights(usr, currentNode, nextNode, cmd);
			
		// 3 update task.taskStatus
		// 3.4. postupdate requested by client
		Update postupClient = null;
		postupClient = postreq;

		// 3.3. handle multi-operation request 
		Update upd3 = CheapEngin.trcs.update(wf.bTabl, usr)
				.where("=", wf.bRecId, busiId == null ?
						// FIXME trcs.basiContext().formatResulv() - this API should working but not a good design.
						// It's violating principal of context as a processing instance.
						trcs.basictx().formatResulv(wf.instabl(), wf.bRecId) : busiId);

		if (multireq != null) {
			// upd3.postChildren(multireq, trcs);
		}

		// IMPORTANT timeout checking depends on this (null is not handled for timeout checking)
		// 3.2 save command name as current node's state (task_nodes.nodeState = cmdName)
		Update post32 = null;
		if (currentNode != null && Req.start != req && WfMeta.nodeInst.handleCmd != null) {
			post32 = CheapEngin.trcs.update(wf.instabl())
					.where("=", WfMeta.nodeInst.id, currentInstId)
					.nv(WfMeta.nodeInst.handleCmd, currentNode.getReqText(req))
					.post(postupClient);
		}
		if (post32 == null)
			post32 = postupClient;

		// 3.1 update task.startNode = new-nodeId when nodeId = 'f01' (ir_workflow.backRef = "f01:startNode")
		if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(nextNode.nodeId())) {
			// if next node == "f01", update task.startNode = new-node-instance-id
			String colname = wf.bNodeInstRefs.get(nextNode.nodeId());

			upd3
			//	.nv(wf.bTaskStateRef, newInstancId) - fkIns already solved this semantics
				.nv(wf.bCateCol, wf.wfId);

			// - fkIns can't solve this semantics because it's not smart enough to find out that whether the data is applicable
			if (colname != null)
				upd3.nv(colname, "FIXME: fk-if-ask");
		}

		// 1. create node
		// starting a new wf at the beginning
		// nodeId = new-id
		Insert ins1 = CheapEngin.trcs.insert(wf.instabl(), usr);
		ins1
			// .nv(Instabl.instId, newInstancId)
			.nv(nodeInst.nodeFk, nextNode.nodeId())
			.nv(nodeInst.descol, nodeDesc);

		// 2.2 prevNode=current-nodeId;
		if (currentInstId != null)
			ins1.nv(nodeInst.prevInst, currentInstId);
		// [OPTIONAL] nodeinstance.wfId = wf.wfId
		if (nodeInst.wfIdFk != null)
			ins1.nv(nodeInst.wfIdFk, wf.wfId);
		// check: starting with null busiId
		// check: busiId not null for step, timeout, ...
		if (Req.start == req && busiId != null)
			Utils.warn("Wf: starting a new instance with existing business record '%s' ?", busiId);
		else if (Req.start != req && busiId == null)
			throw new CheapException(wf.txt("no-btask"), wf.wfId);

		// c_process_processing.baseProcessDataId = e_inspect_tasks.taskId
		// ins2.nv(Instabl.busiFK, Req.start == req ? "AUTO" : busiId);

		Insert ins2 = null; 
		if (Req.start == req) {
			// start: create task
			ins2 = CheapEngin.trcs.insert(wf.bTabl, usr);
			ins2.nv(wf.bCateCol, wf.wfId);
			if (busiPack != null) {
				for (String[] nv : busiPack) {
					ins2.nv(nv[0], nv[1]);
				}
			}
			ins2.post(ins1);

			evt = new CheapEvent(currentNode.wfId(), Evtype.start,
						currentNode, nextNode,
						// busiId is null for new task, resolved later
						String.format("RESULVE %s.%s", WfMeta.bussTable, WfMeta.bRecId),
						Req.start, Req.start.name());
		}
		else {
			// step: insert node instance, update task as post updating.
			ins2 = ins1;
			evt = new CheapEvent(currentNode.wfId(), Evtype.step,
						currentNode, nextNode,
						busiId, req, cmd);
		}

		
		return new SemanticObject()
				.put("stmt", ins2)
				.put("evt", evt)
				.put("stepHandler", currentNode.onEventHandler())
				.put("arriHandler", nextNode.isArrived(currentNode) ? nextNode.onEventHandler() : null);
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
	 * @param currentInstId current workflow instance id, e.g. value of c_process_processing.recId
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
	public static SemanticObject onReqCmd(IUser usr, String wftype, String currentInstId, Req req, String cmd,
			String busiId, String nodeDesc, ArrayList<String[]> busiPack,
			SemanticObject multireq, Update postreq)
					throws SQLException, TransException {

		CheapNode currentNode; 
//		CheapEvent evt = null; 

		if (wfs == null)
			throw new SemanticException("Engine must be initialized");

		CheapWorkflow wf = wfs.get(wftype);
		if (req == Req.start) {
			currentNode = wf.start(); // a virtual node
			cmd = Req.start.name();
		}
		else {
			currentNode = wf.getNodeByInst(currentInstId);
		}
		if (currentNode == null) throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));
		CheapNode nextNode = currentNode.findRoute(cmd);
		
		if (nextNode == null)
			// a configuration problem?
			throw new CheapException(wf.txt("t-no-node"), 
				wftype, currentInstId, req);

		// Check whether a target node already exists.
		// In competition saturation, client error, timeout while user considering, target can be already exists.
		// if (!Req.eq(Req.start, req))
		//		checkExistance(nextNode, busiId);

		wf.checkRights(usr, currentNode, nextNode, cmd);

		// 1. create node instance;<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		// post nv: currentNode.nodeState = cmd-name except start<br>
		Insert ins1 = CheapEngin.trcs.insert(wf.instabl(), usr);
		ins1.nv(nodeInst.nodeFk, nextNode.nodeId())
			.nv(nodeInst.descol, nodeDesc);
		String newInstId = trcs.basictx().formatResulv(wf.instabl, wf.bRecId);
		if (nodeInst.wfIdFk != null)
			ins1.nv(nodeInst.wfIdFk, wf.wfId);

		// with nv: currentInst.nodeState = cmd-name except start<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		if (Req.start != req) {
			ins1.nv(nodeInst.prevInst, currentInstId);

			ins1.post(trcs.update(wf.instabl)
						.nv(nodeInst.handleCmd, cmd)
						.where("=", nodeInst.id, currentInstId));
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
					.nv(wf.bTaskStateRef, trcs.basictx().formatResulv(wf.instabl, wf.bRecId) );

			if (colname != null)
				upd2.nv(colname, newInstId);

			ins1.post(upd2);
		}

		//3. handle multi-operation request, e.g. multireq &amp; postreq<br>
//		Update upd3 = CheapEngin.trcs.update(wf.bTabl, usr)
//				.where("=", wf.bRecId, req == Req.start ?
//						// FIXME trcs.basiContext().formatResulv() - this API should working but not a good design.
//						// It's violating principal of context as a processing instance.
//						"'" + newInstId + "'" : "'" + busiId + "'");
//
//		if (multireq != null) {
//			upd3.postChildren(multireq, trcs);
//		}
//		ins1.post(upd3);
		ins1.postChildren(multireq, trcs);
		
		CheapEvent evt = null;
		if (Req.start == req)
			// start: create task
			evt = new CheapEvent(currentNode.wfId(), Evtype.start,
						currentNode, nextNode,
						// busiId is null for new task, resolved later
						String.format("RESULVE %s.%s", WfMeta.bussTable, WfMeta.bRecId),
						Req.start, Req.start.name());
		else
			// step: insert node instance, update task as post updating.
			evt = new CheapEvent(currentNode.wfId(), Evtype.step,
						currentNode, nextNode,
						busiId, req, cmd);

		return new SemanticObject()
				.put("stmt", ins1)
				.put("evt", evt)
				.put("stepHandler", currentNode.onEventHandler())
				.put("arriHandler", nextNode.isArrived(currentNode) ? nextNode.onEventHandler() : null);
	}
}
