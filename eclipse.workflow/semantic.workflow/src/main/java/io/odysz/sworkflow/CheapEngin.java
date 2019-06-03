package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.LangExt;
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
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.Funcall;
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

	private static ArrayList<ScheduledFuture<?>> schedualeds;
	private static ScheduledExecutorService scheduler;

	static HashMap<String, Dataset> ritConfigs;

	/**<b>Important Note: This context can not handle semantics</b> */
	static ISemantext basictx;

	public static String confpath;

	/**Init cheap engine configuration, schedule a timeout checker.<br>
	 * @param string
	 * @param object
	 * @throws SAXException 
	 * @throws IOException 
	 * @throws TransException 
	 */
	public static void initCheap(String configPath,
			HashMap<String, ICheapChecker> customCheckers) throws TransException, IOException, SAXException {
		// worker thread 
		stopCheap();
		
		reloadCheap(configPath, customCheckers);
		confpath = configPath;

		// scheduler = Executors.newScheduledThreadPool(1);

		// schedualed = scheduler.scheduleAtFixedRate(
		// 		new CheapChecker(wfs, customChecker), 0, 1, TimeUnit.MINUTES);
		
	}

	/**Init cheap engine configuration, schedule a timeout checker.<br>
	 * <b>Note:</b> Calling this only after DAStranscxt initialized with metas.
	 * @param customCheckers 
	 * @param configPath 
	 * @param customCheckers 
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SAXException
	public static void initCheap(String configPath, ICheapChecker customChecker)
			throws TransException, IOException, SAXException {
		initCheap(configPath, null, customChecker);
	}
	 * */

	private static void reloadCheap(String filepath, HashMap<String, ICheapChecker> customCheckers)
			throws TransException, IOException, SAXException {
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
			SemanticObject s = trcs
					.select(WfMeta.wftabl)
					.rs(basictx); // static context is enough to load cheap configs
			SResultset rs = (SResultset) s.rs(0); 
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
				if (!CheapTransBuild.hasSemantics(conn, instabl, smtype.postFk))
					CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.postFk,
						new String[] { nodeInst.busiFk, wf.bTabl, wf.bRecId });


				// 2.3 node instance oper, opertime
				if (!CheapTransBuild.hasSemantics(conn, instabl, smtype.opTime)) {
					// e.g. update task_nodes  set handlingCmd='t01.01.stepA', opertime=datetime('now'), oper='CheapApiTest' where instId = '00001Z'
					// This makes stepping commands will update opertime.
					// CheapTransBuild.addSemantics(conn, instabl, nodeInst.id, smtype.opTime,
					//	new String[] { nodeInst.oper, nodeInst.opertime });
				}
				else {
					Utils.logi("Found op-time semantics of node instance table %s.\n"
							+ "The handler and handling time may be updated when the flow stepping to next nodes.", instabl);
				}

				// 2.4 business task's pk and current state ref, e.g. tasks.wfState -> task_nodes.instId
				if (!CheapTransBuild.hasSemantics(conn, busitabl, smtype.autoInc))
					CheapTransBuild.addSemantics(conn, busitabl, bRecId, smtype.autoInc, bRecId);
				if (!CheapTransBuild.hasSemantics(conn, busitabl, smtype.fkIns))
					CheapTransBuild.addSemantics(conn, busitabl, bRecId, smtype.fkIns,
						new String[] {busiState, instabl, nodeInst.id});
				if (!CheapTransBuild.hasSemantics(conn, busitabl, smtype.opTime))
					Utils.warn("WARN -- CheapEngin --\nCheapEngin didn't find oper-time semanticcs for business table %s.\n" +
								"CheapEngin doesn't require this semantics, and it can be configured in it's own semantic.xml.",
								busitabl);
			}

			schedualeds = loadCheckers(conn, wfs, xtabs.get("cheap-checker"), customCheckers);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static ArrayList<ScheduledFuture<?>> loadCheckers(String conn, HashMap<String, CheapWorkflow> wfs,
			XMLTable xconfgs, HashMap<String, ICheapChecker> customChks) throws SAXException {
		ArrayList<ScheduledFuture<?>> scheduals =
				new ArrayList<ScheduledFuture<?>>(xconfgs == null ? 0 : xconfgs.getRowCount());
		
		if (xconfgs != null) {
			if (scheduler == null)
				scheduler = Executors.newScheduledThreadPool(1);

			// added customer checker's name, later will add all user provided checkers if not overriding xconfig.
			HashSet<String> addedCustoms = new HashSet<String>(customChks == null ? 0 : customChks.size());

			xconfgs.beforeFirst();
			while (xconfgs.next()) {
				// <t id="cheap-checker" pk="wfid" columns="wfid,ms,mysql,orcl,ms2k,sqlit">
				int ms = xconfgs.getInt("ms");
				String wfid = xconfgs.getString("wfid").trim();
				ScheduledFuture<?> schedualed;
				
				// if user provided checker for the wfid, override it
				if (customChks != null && customChks.containsKey(wfid)) {
					ICheapChecker chk = customChks.get(wfid);
					schedualed = scheduler.scheduleAtFixedRate
						(new CheapChecker(conn, chk), 0, chk.ms(), TimeUnit.MICROSECONDS);
					addedCustoms.add(wfid);
				}
				// otherwise create a default checker for the wfid,
				else {
					String[] sqls = new String[4];
					sqls[DatasetCfg.ixMysql] = xconfgs.getString("mysql");
					sqls[DatasetCfg.ixOrcl] = xconfgs.getString("orcl");
					sqls[DatasetCfg.ixSqlit] = xconfgs.getString("sqlit");
					sqls[DatasetCfg.ixMs2k] = xconfgs.getString("ms2k");
	
					Dataset ds = new Dataset(wfid, null, sqls, null);
					schedualed = scheduler.scheduleAtFixedRate
							(new CheapChecker(conn, wfid, ms, ds), 0, ms, TimeUnit.MICROSECONDS);
				}
				scheduals.add(schedualed);
			}
		}

		return scheduals;
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
		if (schedualeds != null)
			for (ScheduledFuture<?> schedualed : schedualeds) {
				if (schedualed == null && scheduler == null) continue;
				// stop worker
				Utils.logi("cancling WF-Checker ... ");
				schedualed.cancel(true);
				try {
		    		if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
		        		scheduler.shutdownNow();
		    		} 
				} catch (InterruptedException e) {
		    		scheduler.shutdownNow();
				}
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
	 * Modified 2019.6.2, supporting multiple outgoing branches.
	 */
	public static SemanticObject onReqCmd(IUser usr, String wftype, Req req, String cmd,
			String busiId, String nodeDesc, ArrayList<Object[]> busiPack,
			ArrayList<Statement<?>> postups)
					throws SQLException, TransException {
		// Design Notes:
		// We are not only step from current state, we also step from the out-going node asked by cmd.
		// The client needing change some how to adapt to the new style - multip node's can submit simultaneously.
		CheapNode fromNode; 
		// from-node's instance id
		String fromInstId;

		if (wfs == null)
			throw new SemanticException("Cheap engine must been initialized.");

		// 0 prepare current node
		CheapWorkflow wf = wfs.get(wftype);
		if (req == Req.start) {
			// 0.1 start
			fromNode = wf.start(); // a virtual node
			cmd = Req.start.name();
			// sometimes a task alread exists
			// but the instance shouldn't exist. Yet competation is not checked here
			fromInstId = null;

//			if (!LangExt.isblank(busiId, "\\s*null\\s*")) {
//				String[] tskInf = wf.getInstByTask(trcs, busiId);
//				if (tskInf != null && tskInf.length > 0)
//					currentInstId = tskInf[1];
//			}
		}
		else {
			// 0.2 step, find the task and the current state node
			if (busiId == null)
				throw new CheapException(CheapException.ERR_WF,
						"Command %s.%s need to find task/business record first. but busi-id is null",
						req.name(), cmd);
			// String[] tskInf = wf.getInstByTask(trcs, busiId);

			String[] tskInf = findFrom(busiId, cmd, wf);
			fromNode = wf.getNode(tskInf[0]);

			if (tskInf == null || fromNode == null) {
				// may be a server error
				Utils.warn("Can't find task's current node. taskId = %s, wfId = %s", busiId, wf.wfId);
				// may be a client error
				throw new CheapException(CheapException.ERR_WF,
						"Can't find task's current node. taskId = %s, wfId = %s",
						busiId, wf.wfId);
			}
			fromInstId = LangExt.isblank(tskInf[1]) ? null : tskInf[1];
			
			// 2019.5.31 current node should be exactly the FROM node
			// currentNode = wf.getNode(tskInf[2]);
		}

		// 0.3 prepare next node
		if (fromNode == null) throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
							wftype, fromInstId, req));
		CheapNode nextNode = fromNode.findRoute(cmd);
		
		if (nextNode == null)
			// a configuration problem?
			throw new CheapException(CheapException.ERR_WF,
					"Route resolving failed, next node not found: wfId %s, taskId %s, req %s, cmd %s", 
				wftype, busiId, req, cmd);

		if (req == Req.start)
			nextNode.checkRights(trcs, usr, req, cmd, busiId);
		else
			fromNode.checkRights(trcs, usr, req, cmd, busiId);

		// 1. create node instance;<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		// post nv: currentNode.nodeState = cmd-name except start<br>
		Insert ins1 = CheapEngin.trcs.insert(wf.instabl(), usr)
			.nv(nodeInst.nodeFk, nextNode.nodeId())
			.nv(nodeInst.descol, nodeDesc)
			// op-time semantics is removed to avoid updating when stepping next
			.nv(nodeInst.opertime, Funcall.now(CheapEngin.trcs.basictx().dbtype()))
			.nv(nodeInst.oper, usr.uid());

		Resulving newInstId = new Resulving(wf.instabl, nodeInst.id);

		// with nv: currentInst.nodeState = cmd-name except start<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		if (Req.start != req) {
			ins1.nv(nodeInst.prevInst, fromInstId)
				.nv(nodeInst.busiFk, busiId); // busiId shouldn't resulved with fk-ins

			ins1.post(trcs.update(wf.instabl)
						.nv(nodeInst.handleCmd, cmd)
						.where_("=", nodeInst.id, fromInstId));
		}
		else {
			// 2019.5.26 can not resulved by post-fk here, using Resulving instead. 
			// see https://odys-z.github.io/notes/semantics/best-practices.html#post-fk
			// resulved by postFk
			// ins1.nv(nodeInst.busiFk , "?");
			// ins1.nv(nodeInst.busiFk , ShPostFk.Resulving(wf.bTabl, wf.bRecId));
			if (!LangExt.isblank(busiId))
				ins1.nv(nodeInst.busiFk, busiId);
			
			// starting node instance's handling command = start 
			ins1.nv(nodeInst.handleCmd, Req.start.name());
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
		if (Req.start == req && LangExt.isblank(busiId)) {
			Insert ins2 = trcs
					.insert(wf.bTabl, usr)
					.nv(wf.bCateCol, wf.wfId);
			if (busiPack != null)
				for (Object[] nv : busiPack)
					ins2.nv((String)nv[0], nv[1]);
			
			if (colname != null)
				ins2.nv(colname, newInstId);
			
			ins1.post(ins2);
		}
		//  2.2. or task exists, update task,<br>
		//  semantics: fk(tasks.wfState - task_nodes.instId)<br>
		//  add back-ref(nodeId:task.nodeBackRef),
		else if (Req.cmd == req || Req.start == req) {
			Update upd2 = trcs.update(wf.bTabl, usr)
					.nv(wf.bTaskStateRef,
							// trcs.basictx().formatResulv(wf.instabl, wf.bRecId));
							newInstId)
					.where("=", wf.bRecId, "'" + busiId + "'");
			if (busiPack != null)
				for (Object[] nv : busiPack)
					upd2.nv((String)nv[0], nv[1]);

			if (colname != null)
				upd2.nv(colname, newInstId);

			ins1.post(upd2);
		}

		//3. handle multi-operation request, e.g. multireq &amp; postreq<br>
		// ins1.postChildren(multireq, trcs);
		ins1.post(postups);
		
		CheapEvent evt = null;
		if (Req.start == req)
			// start: create task
			evt = new CheapEvent(fromNode.wfId(), Evtype.start,
						fromNode, nextNode,
						// busiId is null for new task, resolved later
						// basictx.formatResulv(wf.bTabl, wf.bRecId),
						new Resulving(wf.bTabl, wf.bRecId),
						newInstId,
						Req.start, Req.start.name());
		else
			// step: insert node instance, update task as post updating.
			evt = new CheapEvent(fromNode.wfId(), Evtype.step,
						fromNode, nextNode,
						busiId, newInstId, req, cmd);

		return new SemanticObject()
				.put("stmt", ins1)
				.put("evt", evt)
				.put("stepHandler", fromNode.onEventHandler())
				.put("arriHandler", nextNode.isArrived(fromNode) ? nextNode.onEventHandler() : null);
	}
//	public static SemanticObject onReqCmd(IUser usr, String wftype, Req req, String cmd,
//			String busiId, String nodeDesc, ArrayList<Object[]> busiPack,
//			SemanticObject multireq2, ArrayList<Statement<?>> postups)
//					throws SQLException, TransException {
//		// from-node's instance id
//		String currentInstId = null;
//		
//		// Design Notes:
//		// We are not only step from current state, we also step from the out-going node asked by cmd.
//		// The client needing change some how to adapt to the new style - multip node's can submit simultaneously.
//		CheapNode fromNode; 
//
//		if (wfs == null)
//			throw new SemanticException("Cheap engine must been initialized.");
//
//		// 0 prepare current node
//		CheapWorkflow wf = wfs.get(wftype);
//		if (req == Req.start) {
//			// 0.1 start
//			fromNode = wf.start(); // a virtual node
//			cmd = Req.start.name();
//			// sometimes a task alread exists
//			if (!LangExt.isblank(busiId, "\\s*null\\s*")) {
//				String[] tskInf = wf.getInstByTask(trcs, busiId);
//				if (tskInf != null && tskInf.length > 0)
//					currentInstId = tskInf[1];
//			}
//		}
//		else {
//			// 0.2 step, find the task and the current state node
//			if (busiId == null)
//				throw new CheapException(CheapException.ERR_WF,
//						"Command %s.%s need to find task/business record first. but busi-id is null",
//						req.name(), cmd);
//			String[] tskInf = wf.getInstByTask(trcs, busiId);
//			if (tskInf == null || tskInf.length == 0) {
//				// may be a server error
//				Utils.warn("Can't find task's information. taskId = %s, wfId = %s", busiId, wf.wfId);
//				// may be a client error
//				throw new CheapException(CheapException.ERR_WF,
//						"Can't find task's information. taskId = %s, wfId = %s",
//						busiId, wf.wfId);
//			}
//			currentInstId = tskInf[1];
//			
//			// 2019.5.31 current node should be exactly the FROM node
//			// currentNode = wf.getNode(tskInf[2]);
//			fromNode = wf.getNode(findFrom(cmd));
//		}
//
//		// 0.3 prepare next node
//		if (fromNode == null) throw new SQLException(
//				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
//				wftype, currentInstId, req));
//		CheapNode nextNode = fromNode.findRoute(cmd);
//		
//		if (nextNode == null)
//			// a configuration problem?
//			throw new CheapException(CheapException.ERR_WF,
//					"Route resolving failed, next node not found: wfId %s, taskId %s, req %s, cmd %s", 
//				wftype, busiId, req, cmd);
//
//		if (req == Req.start)
//			nextNode.checkRights(trcs, usr, req, cmd, busiId);
//		else
//			fromNode.checkRights(trcs, usr, req, cmd, busiId);
//
//		// 1. create node instance;<br>
//		// post nv: nextInst.prevNode = current.id except start<br>
//		// post nv: currentNode.nodeState = cmd-name except start<br>
//		Insert ins1 = CheapEngin.trcs.insert(wf.instabl(), usr)
//			.nv(nodeInst.nodeFk, nextNode.nodeId())
//			.nv(nodeInst.descol, nodeDesc)
//			// op-time semantics is removed to avoid updating when stepping next
//			.nv(nodeInst.opertime, Funcall.now(CheapEngin.trcs.basictx().dbtype()))
//			.nv(nodeInst.oper, usr.uid());
//
//		Resulving newInstId = new Resulving(wf.instabl, nodeInst.id);
//
//		// with nv: currentInst.nodeState = cmd-name except start<br>
//		// post nv: nextInst.prevNode = current.id except start<br>
//		if (Req.start != req) {
//			ins1.nv(nodeInst.prevInst, currentInstId)
//				.nv(nodeInst.busiFk, busiId); // busiId shouldn't resulved with fk-ins
//
//			ins1.post(trcs.update(wf.instabl)
//						.nv(nodeInst.handleCmd, cmd)
//						.where("=", nodeInst.id, "'" + currentInstId + "'"));
//		}
//		else {
//			// 2019.5.26 can not resulved by post-fk here, using Resulving instead. 
//			// see https://odys-z.github.io/notes/semantics/best-practices.html#post-fk
//			// resulved by postFk
//			// ins1.nv(nodeInst.busiFk , "?");
//			// ins1.nv(nodeInst.busiFk , ShPostFk.Resulving(wf.bTabl, wf.bRecId));
//			if (!LangExt.isblank(busiId))
//				ins1.nv(nodeInst.busiFk, busiId);
//			
//			// starting node instance's handling command = start 
//			ins1.nv(nodeInst.handleCmd, Req.start.name());
//		}
//
//		// 2.0. prepare back-ref(nodeId:task.nodeBackRef);
//		// e.g. oz_workflow.bacRefs = 't01.03:requireAllStep', so set tasks.requireAll = new-inst-id if nodeId = 't01.03';<br>
//		String colname = null; 
//		if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(nextNode.nodeId())) {
//			// requireAllStep = 't01.03'
//			colname = wf.bNodeInstRefs.get(nextNode.nodeId());
//		}
//
//		//  2.1. create task, with busiPack as task nvs.<br>
//		//  semantics: autopk(tasks.taskId), fk(tasks.wfState - task_nodes.instId);<br>
//		//  add back-ref(nodeId:task.nodeBackRef),
//		if (Req.start == req && LangExt.isblank(busiId)) {
//			Insert ins2 = trcs
//					.insert(wf.bTabl, usr)
//					.nv(wf.bCateCol, wf.wfId);
//			if (busiPack != null)
//				for (Object[] nv : busiPack)
//					ins2.nv((String)nv[0], nv[1]);
//			
//			if (colname != null)
//				ins2.nv(colname, newInstId);
//			
//			ins1.post(ins2);
//		}
//		//  2.2. or task exists, update task,<br>
//		//  semantics: fk(tasks.wfState - task_nodes.instId)<br>
//		//  add back-ref(nodeId:task.nodeBackRef),
//		else if (Req.cmd == req || Req.start == req) {
//			Update upd2 = trcs.update(wf.bTabl, usr)
//					.nv(wf.bTaskStateRef,
//							// trcs.basictx().formatResulv(wf.instabl, wf.bRecId));
//							newInstId)
//					.where("=", wf.bRecId, "'" + busiId + "'");
//			if (busiPack != null)
//				for (Object[] nv : busiPack)
//					upd2.nv((String)nv[0], nv[1]);
//
//			if (colname != null)
//				upd2.nv(colname, newInstId);
//
//			ins1.post(upd2);
//		}
//
//		//3. handle multi-operation request, e.g. multireq &amp; postreq<br>
//		// ins1.postChildren(multireq, trcs);
//		ins1.post(postups);
//		
//		CheapEvent evt = null;
//		if (Req.start == req)
//			// start: create task
//			evt = new CheapEvent(fromNode.wfId(), Evtype.start,
//						fromNode, nextNode,
//						// busiId is null for new task, resolved later
//						// basictx.formatResulv(wf.bTabl, wf.bRecId),
//						new Resulving(wf.bTabl, wf.bRecId),
//						newInstId,
//						Req.start, Req.start.name());
//		else
//			// step: insert node instance, update task as post updating.
//			evt = new CheapEvent(fromNode.wfId(), Evtype.step,
//						fromNode, nextNode,
//						busiId, newInstId, req, cmd);
//
//		return new SemanticObject()
//				.put("stmt", ins1)
//				.put("evt", evt)
//				.put("stepHandler", fromNode.onEventHandler())
//				.put("arriHandler", nextNode.isArrived(fromNode) ? nextNode.onEventHandler() : null);
//	}

	/**<p>Find <i>from</i> node, the out going node, according to cmd request.</p> 
	 * <p>This method doesn't require a instId, instance id.
	 * If the outgoing node specified by cmd has multiple instances, it's a logic error,
	 * - each node can have only one instance for each task.</p>
	 * @param busiId task id
	 * @param cmd
	 * @param wf 
	 * @return [0] the out going node (null if failed); [1] the instance id of the outgoing node
	 * @throws SQLException 
	 * @throws TransException 
	 */
	private static String[] findFrom(String busiId, String cmd, CheapWorkflow wf) throws TransException, SQLException {
		Query q = trcs.select(WfMeta.cmdTabl, "c")
				.col("c." + WfMeta.cmdNodeFk).col(WfMeta.nodeInst.id)
				.l(wf.instabl, "i", String.format("c.%s = i.%s and i.%s = '%s'",
									WfMeta.cmdNodeFk, WfMeta.nodeInst.nodeFk, WfMeta.nodeInst.busiFk, busiId))
				.where_("=", WfMeta.cmdCmd, cmd);

		SemanticObject res = q.rs(trcs.basictx()); // shouldn't using context?
		SResultset rs = (SResultset) res.rs(0);
		
		if (rs == null || rs.getRowCount() != 1) {
			Utils.warn("found multiple instance for the cmd %s, task: %s",
					cmd, busiId);
			if (debug)
				throw new CheapException(CheapException.ERR_WF_INTERNAL,
						"found multiple instance for the cmd %s, task: %s",
						cmd, busiId);
		}

		return rs.beforeFirst().next() ?
			new String[] {rs.getString(WfMeta.cmdNodeFk), rs.getString(WfMeta.nodeInst.id)}
			: null;
	}
}
