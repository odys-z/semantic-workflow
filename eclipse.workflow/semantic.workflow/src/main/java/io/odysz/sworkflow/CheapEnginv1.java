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
import java.util.concurrent.locks.ReentrantLock;

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
import io.odysz.sworkflow.CheapNode.CheapRoute;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.sworkflow.EnginDesign.WfMeta.nodeInst;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**A simple work flow engine
 * @author odys-z@github.com
 */
public class CheapEnginv1 {
	public static final boolean debug = true;
	static ReentrantLock lock = new ReentrantLock();

	static CheapTransBuild trcs;
	static IUser checkUser;
	
	/** Finger print used for checking timeout checker's competition.
	 * When initializing, will update a random value to db, when checking, query it and compare with this version.
	 */
	static String cheaprint;

	static HashMap<String, CheapWorkflow> wfs;
	public static HashMap<String, CheapWorkflow> wfs() { return wfs; }

	private static ArrayList<ScheduledFuture<?>> schedualeds;
	private static ScheduledExecutorService scheduler;

	static HashMap<String, Dataset> ritConfigs;

	/**<b>Important Note: This context can not handle semantics</b> */
	static ISemantext basictx;

	public static String confpath;

	/**Checked times - not very accurate as competition exists */
	static int checked = 0;

	/**Init cheap engine configuration, schedule a timeout checker.<br>
	 * @param string
	 * @param object
	 * @throws SAXException 
	 * @throws IOException 
	 * @throws TransException 
	 */
	public static void initCheap(String configPath,
			HashMap<String, ICheapChecker> customCheckers) throws TransException, IOException, SAXException {
		
		checkUser = new CheapRobot(); 

		// worker thread 
		stopCheap();
		
		reloadCheap(configPath, customCheckers);
		confpath = configPath;
	}

	private static void reloadCheap(String filepath, HashMap<String, ICheapChecker> customCheckers)
			throws TransException, IOException, SAXException {
		try {
			LinkedHashMap<String, XMLTable> xtabs = loadXmeta(filepath);
			// table = conn
			XMLTable cfg = xtabs.get("cfg");
			String conn = null;
			boolean enableChkr = false;
			cfg.beforeFirst();
			while (cfg.next()) {
				String k = cfg.getString("k");
				if ("conn".equals(k))
					conn = cfg.getString("v");
				else if ("enable-checker".equals(k))
					enableChkr = cfg.getBool("v", false);
				else if ("user-meta".equals(k)) {
					String[] vss = LangExt.split(cfg.getString("v"), ",");
					WfMeta.user.tbl = vss[0];
					WfMeta.user.id = vss[1];
					WfMeta.user.name = vss[2];
					if (vss.length > 3)
					WfMeta.user.roleFk = vss[3];
				}
				else if ("wfrights-meta".equals(k)) {
					String[] vss = LangExt.split(cfg.getString("v"), ",");
					WfMeta.rights.tbl = vss[0];
					WfMeta.rights.nodeFk = vss[1];
					WfMeta.rights.roleFk = vss[2];
				}
			}
			
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
						rs.getString(WfMeta.wfWfid),
						rs.getString(WfMeta.wfName),
						instabl,
						busitabl, // tasks
						bRecId, // tasks.taskId
						busiState, // tasks.wfState
						rs.getString(WfMeta.bussCateCol),
						rs.getString(WfMeta.node1),
						rs.getString(WfMeta.bNodeInstBackRefs));
				wfs.put(rs.getString(WfMeta.wfWfid), wf);

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

			if (enableChkr) {
				schedualeds = loadCheckers(conn, wfs, xtabs.get("cheap-checker"), customCheckers);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static ArrayList<ScheduledFuture<?>> loadCheckers(String conn, HashMap<String, CheapWorkflow> wfs,
			XMLTable xconfgs, HashMap<String, ICheapChecker> customChks) throws SAXException, TransException, SQLException {
		ArrayList<ScheduledFuture<?>> scheduals =
				new ArrayList<ScheduledFuture<?>>(xconfgs == null ? 0 : xconfgs.getRowCount());
		
		// added customer checker's name, later will add all user provided checkers if not overriding xconfig.
		HashSet<String> addedCostums = new HashSet<String>(customChks == null ? 0 : customChks.size());
		if (xconfgs != null) {
			if (scheduler == null)
				scheduler = Executors.newScheduledThreadPool(1);

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
					addedCostums.add(wfid);
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
					
					addedCostums.add(wfid);
				}
				scheduals.add(schedualed);
			}
		}
		
		if (customChks != null)
			for (ICheapChecker ckr : customChks.values()) {
				if (addedCostums.contains(ckr.wfId()))
					continue;
				else {
					ICheapChecker chk = customChks.get(ckr.wfId());
					scheduler.scheduleAtFixedRate
						(new CheapChecker(conn, chk), 0, chk.ms(), TimeUnit.MICROSECONDS);
					addedCostums.add(ckr.wfId());
				}
			}

		CheapApi.initFingerPrint(checkUser, addedCostums);

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
	
	public static int checked() { return checked ; }

	public static CheapWorkflow getWf(String type) {
		return wfs == null ? null : wfs.get(type);
	}

	/**Create statements that can be committed to step the timeout routes,
	 * return an event object with target instance can be resulved if committing the statements.
	 * @param usr
	 * @param wftype
	 * @param nodeId
	 * @param busiId
	 * @param instId
	 * @return {stmt: {@link Insert}/{@link Update} (for committing), <br>
	 * 	evt: start/step event (new task ID ({@link Resulving} to be resolved), <br>
	 *	stepHandler: {@link ICheapEventHandler} for req (step/deny/next) if there is one configured]<br>
	 *	arriHandler: {@link ICheapEventHandler} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException
	 */
	public static SemanticObject onTimeout(IUser usr, String wftype, String nodeId,
			String busiId, String instId) throws SQLException, TransException {
		if (wfs == null)
			throw new SemanticException("Before step timeout, cheap engine must been initialized.");
		if (LangExt.isblank(busiId) ||
				LangExt.isblank(nodeId) ||
				LangExt.isblank(instId)) {
			Utils.warn("Found inconsistant timeout satuation (blank taskId). nodeId %s, busiId %s, instId %s",
					nodeId, busiId, instId);
			return null;
		}
		CheapWorkflow wf = wfs.get(wftype);
		CheapNode timeoutNode = wf.getNode(nodeId);
		CheapRoute toRoute = timeoutNode.timeoutRoute();
		CheapNode nextNode = wf.getNode(toRoute.to);

		// 1. insert a target instance stepped to it via timeout
		Insert ins1 = CheapEnginv1.trcs.insert(wf.instabl(), usr)
				.nv(nodeInst.nodeFk, nodeId)
				.nv(nodeInst.busiFk, busiId)
				.nv(nodeInst.descol, "from " + instId + " timeout")
				// update the current (timeout) node as the prvNode
				.nv(nodeInst.prevInst, instId) // can't be null - no null node can timeout
				// op-time semantics is removed to avoid updating when stepping next
				.nv(nodeInst.opertime, Funcall.now(CheapEnginv1.trcs.basictx().dbtype()))
				.nv(nodeInst.oper, usr.uid());

		Resulving newInstId = new Resulving(wf.instabl, nodeInst.id);

		// 2. update the task's current state
		Update upd2 = trcs.update(wf.bTabl, usr)
				.nv(wf.bTaskStateRef, newInstId)
				.whereEq(wf.bRecId, busiId);

		// 2.0. prepare back-ref(nodeId:task.nodeBackRef);
		// e.g. oz_workflow.bacRefs = 't01.03:requireAllStep',
		// so set tasks.requireAll = new-inst-id if nodeId = 't01.03';
		if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(nextNode.nodeId())) {
			// requireAllStep = 't01.03'
			String colname = wf.bNodeInstRefs.get(timeoutNode.nodeId());
			if (!LangExt.isblank(colname))
				upd2.nv(colname, newInstId);
		}

		ins1.post(upd2)

		// 3. update the current (timeout) node handling-cmd with new instance Id.
			.post(trcs.update(wf.instabl, usr)
				.nv(nodeInst.handleCmd, nextNode.nodeId())
				.nv(nodeInst.descol, newInstId)
				.whereEq(nodeInst.id, instId))
			;

		// timeout event
		CheapEvent evt = new CheapEvent(timeoutNode.wfId(), Evtype.timeout,
					timeoutNode, nextNode, busiId,
					instId, newInstId, Req.timeout, Req.timeout.name());

		return new SemanticObject()
			.put("stmt", ins1)
			.put("evt", evt)
			.put("stepHandler", timeoutNode.onEventHandler())
			.put("arriHandler", nextNode.isArrived(timeoutNode) ? nextNode.onEventHandler() : null);
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
	 * @param prevInstDsc current node instance description - the handling description
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
			String busiId, String prevInstDsc, ArrayList<Object[]> busiPack,
			ArrayList<Statement<?>> postups) throws SQLException, TransException {
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
		}
		else {
			// 0.2 step, find the task and the current state node
			if (busiId == null)
				throw new CheapException(CheapException.ERR_WF,
						"Command %s.%s need to find task/business record first. but busi-id is null",
						req.name(), cmd);

			String[] prevInf = findFrom(busiId, cmd, wf);
			fromNode = wf.getNode(prevInf[0]);

			if (prevInf == null || fromNode == null) {
				// may be a server error
				Utils.warn("Can't find task's current node. taskId = %s, wfId = %s", busiId, wf.wfId);
				// may be a client error
				throw new CheapException(CheapException.ERR_WF,
						"Can't find task's current node. taskId = %s, wfId = %s",
						busiId, wf.wfId);
			}
			fromInstId = LangExt.isblank(prevInf[1]) ? null : prevInf[1];
			
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
		// FIXME - not always an inserting, e.g. the arriving at a node via merging routes.
		// FIXME - not always an inserting, e.g. the arriving at a node via merging routes.
		// FIXME - not always an inserting, e.g. the arriving at a node via merging routes.
		// FIXME - not always an inserting, e.g. the arriving at a node via merging routes.

		// post nv: nextInst.prevNode = current.id except start<br>
		// post nv: currentNode.nodeState = cmd-name except start<br>
		Insert ins1 = CheapEnginv1.trcs.insert(wf.instabl(), usr)
			// .nv(nodeInst.busiFk, "Resulving...")
			.nv(nodeInst.nodeFk, nextNode.nodeId()) // .nv(nodeInst.descol, nodeDesc)
			// op-time semantics is removed to avoid updating when stepping next and updating 'preNode'
			.nv(nodeInst.opertime, Funcall.now(CheapEnginv1.trcs.basictx().dbtype()))
			.nv(nodeInst.oper, usr.uid());

		Resulving newInstId = new Resulving(wf.instabl, nodeInst.id);

		// with nv: currentInst.nodeState = cmd-name except start<br>
		// post nv: nextInst.prevNode = current.id except start<br>
		if (Req.start != req) {
			ins1.nv(nodeInst.prevInst, fromInstId)
				// busiId shouldn't resulved with fk-ins, must alread exists
				.nv(nodeInst.busiFk, busiId);

			ins1.post(trcs.update(wf.instabl)
						.nv(nodeInst.handleCmd, cmd)
						.nv(nodeInst.descol, prevInstDsc)
						.whereEq(nodeInst.id, fromInstId));
		}
		else {
			// 2019.5.26 can not resulved by post-fk here, using Resulving instead. 
			// see https://odys-z.github.io/notes/semantics/best-practices.html#post-fk
			// resulved by postFk

			// if (!LangExt.isblank(busiId))
				ins1.nv(nodeInst.busiFk, LangExt.isblank(busiId, "null") ? "Resulving..." : busiId);
			
			// starting node instance's handling command = start 
//			ins1.nv(nodeInst.handleCmd, Req.start.name());
		}

		// 2.0. prepare back-ref(nodeId:task.nodeBackRef);
		// e.g. oz_workflow.bacRefs = 't01.03:requireAllStep', so set tasks.requireAll = new-inst-id if nodeId = 't01.03';<br>
		String colnameBackRef = null; 
		if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(nextNode.nodeId())) {
			// requireAllStep = 't01.03'
			colnameBackRef = wf.bNodeInstRefs.get(nextNode.nodeId());
		}

		//  2.1. create task, with busiPack as task nvs.<br>
		//  semantics: autopk(tasks.taskId), fk(tasks.wfState - task_nodes.instId);<br>
		//  add back-ref(nodeId:task.nodeBackRef),
		if (Req.start == req && LangExt.isblank(busiId)) {
			Insert ins2 = trcs
					.insert(wf.bTabl, usr)
					.nv(wf.bCateCol, wf.wfId)
					// .nv(wf.bTaskStateRef, "Resulving...")
					;
			if (busiPack != null)
				for (Object[] nv : busiPack)
					if (nv[1] instanceof AbsPart)
						ins2.nv((String)nv[0], (AbsPart)nv[1]);
					else 
						ins2.nv((String)nv[0], (String)nv[1]);
			
			if (colnameBackRef != null)
				ins2.nv(colnameBackRef, newInstId);
			
			ins1.post(ins2);
		}
		//  2.2. or if the task exists, update task,<br>
		//  semantics: fk(tasks.wfState - task_nodes.instId)<br>
		//  add back-ref(nodeId:task.nodeBackRef),
		else if (Req.cmd == req || Req.start == req) {
			Update upd2 = trcs.update(wf.bTabl, usr)
					.nv(wf.bTaskStateRef, newInstId)
					.where("=", wf.bRecId, "'" + busiId + "'");
			if (busiPack != null)
				for (Object[] nv : busiPack) {
					if (nv[1] instanceof AbsPart)
						upd2.nv((String)nv[0], (AbsPart)nv[1]);
					else
						upd2.nv((String)nv[0], (String)nv[1]);
				}

			if (colnameBackRef != null)
				upd2.nv(colnameBackRef, newInstId);

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
						fromInstId, newInstId,
						Req.start, Req.start.name());
		else
			// step: insert node instance, update task as post updating.
			evt = new CheapEvent(fromNode.wfId(), Evtype.step,
						fromNode, nextNode, busiId,
						fromInstId, newInstId, req, cmd);

		evt.qryCompetition(qryCompetition(req, wf, busiId, nextNode.nodeId(), fromInstId));

		return new SemanticObject()
				.put("stmt", ins1)
				.put("evt", evt)
				.put("stepHandler", fromNode.onEventHandler())
				.put("arriHandler", nextNode.isArrived(fromNode) ? nextNode.onEventHandler() : null);
	}

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
		// select c.nodeId, instId 
		// from oz_wfcmds c 
		// left outer join task_nodes i on c.nodeId = i.nodeId AND i.taskId = '00000L' And i.handlingCmd is null 
		// where cmd = 't01.01.stepB'
		Query q = trcs.select(WfMeta.cmdTabl, "c")
				.col("c." + WfMeta.cmdNodeFk).col(WfMeta.nodeInst.id)
				// FIXME assuming last node is loop and waiting nodes is a logical defect.
				// .l(wf.instabl, "i", String.format("c.%s = i.%s and i.%s = '%s' and i.%s is null",
				.l(wf.instabl, "i", String.format("c.%s = i.%s and i.%s = '%s'",
									WfMeta.cmdNodeFk, WfMeta.nodeInst.nodeFk,
									WfMeta.nodeInst.busiFk, busiId,
									WfMeta.nodeInst.handleCmd))
				.whereEq(WfMeta.cmdCmd, cmd)
				// FIXME assuming last node is loop and waiting nodes is a logical defect.
				.orderby("i." + nodeInst.opertime, "desc");

		SemanticObject res = q.rs(trcs.basictx()); // shouldn't using context?
		SResultset rs = (SResultset) res.rs(0);
		
		if (rs == null || rs.getRowCount() < 1) {
			Utils.warn("CheapEngin#findFrom(): Found %s starting instance for the cmd %s, task: %s",
					rs.getRowCount(), cmd, busiId);
			if (debug)
				throw new CheapException(CheapException.ERR_WF_INTERNAL,
						"CheapEngin#findFrom(): Found %s starting instance for the cmd %s, task: %s",
						rs.getRowCount(), cmd, busiId);
		}

		return rs.beforeFirst().next() ?
			new String[] {rs.getString(WfMeta.cmdNodeFk), rs.getString(WfMeta.nodeInst.id)}
			: null;
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
	 * @param wftype 
	 * @param req 
	 * @param cmd 
	 * @param taskId 
	 * @param nodeDesc 
	 * @param nvs 
	 * @param postups 
	 * @return { evt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepHandler: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriHandler: {@link CheapEvent} for arriving event if there is one configured<br>
	 * }
	 * @throws SQLException
	 * @throws TransException 
	 */
	public static SemanticObject commitReq(IUser usr, String wftype, Req req, String cmd,
			String taskId, String nodeDesc, ArrayList<Object[]> nvs, ArrayList<Statement<?>> postups)
					throws SQLException, TransException {
		CheapTransBuild st = CheapEnginv1.trcs;

		SemanticObject logic = CheapEnginv1.onReqCmd(usr, wftype, req, cmd,
					taskId, nodeDesc, nvs, postups);

		Insert ins = (Insert) logic.get("stmt");
		ISemantext smtxt = st.instancontxt(usr);

		// prepare competition checking
		CheapEvent evt = (CheapEvent) logic.get("evt");
		CheapWorkflow wf = CheapEnginv1.getWf(evt.wfId());

		// Query q = qCompet(req, wf, taskId, evt.nextNodeId(), evt.prevInstId());
		Query q = evt.qryCompetition();

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

	/**
	 *<pre>debug: competition instance for checking:
Steping from 00005L to 00005N and 00005M:
insert into ir_prjnodes  (nodeId, opertime, oper, prevRec, taskId, instId) 
values ('pcac-manager', now(), 'admin', null, '00000l', '00005N')

insert into ir_prjnodes  (nodeId, opertime, oper, prevRec, taskId, instId) 
values ('pcac-manager', now(), 'admin', '00005L', '00000l', '00005M')

The failed checking (deprecated):
SELECT count( n.nodeId ) cnt FROM oz_wfnodes n
JOIN ir_prjnodes prv ON n.nodeId = prv.nodeId AND prv.taskId = '00000l'
JOIN ir_prjnodes nxt ON n.nodeId = nxt.nodeId AND nxt.prevRec = prv.instId 
WHERE arrivCondit IS null

Real Data:
select * from ir_prjnodes where taskId = '00000l';

select * from p_change_application where changeId = '00000l';

instId |nodeId       |taskId |oper  |opertime            |handlingCmd       |prevRec |
-------|-------------|-------|------|--------------------|------------------|--------|
00003B |mpac-start   |00000l |admin |2019-06-18 13:48:15 |start             |        |
00003C |mpac-ac      |00000l |admin |2019-06-18 13:48:21 |mpac-ac.next      |[null : competition]
00003D |mpac-gm      |00000l |admin |2019-06-18 14:06:23 |mpac-gm.deny      |00003C  |
00003E |mpac-start   |00000l |admin |2019-06-18 14:07:47 |mpac-start.submit |00003D  |
00003F |mpac-ac      |00000l |admin |2019-06-18 14:08:03 |mpac-ac.next      |00003E  |
00003G |mpac-gm      |00000l |admin |2019-06-18 14:31:08 |mpac-gm.pass      |00003F  |
00004J |mpac-finish  |00000l |admin |2019-06-20 12:55:25 |                  |00003G  |
00005J |pcac-start   |00000l |admin |2019-06-25 16:04:55 |pcac-start.submit |        |
00005K |pcac-pm      |00000l |admin |2019-06-25 16:05:05 |pcac-pm.upload    |00005J  |
00005L |pcac-ac      |00000l |admin |2019-06-25 16:05:13 |pcac-ac.next      |00005K  |
00005M |pcac-manager |00000l |admin |2019-06-25 16:05:26 |                  |00005L  |
00005N |pcac-manager |00000l |admin |2019-06-25 16:05:29 |                  |[null : competition]
</pre>
	 * @param req
	 * @param wf
	 * @param taskId
	 * @param next
	 * @param prevInstId
	 * @return
	 * @throws TransException
	 */
	static Query qryCompetition(Req req, CheapWorkflow wf, String taskId, String nextNodeId, String prevInstId)
			throws TransException {
		Query q;
		// 0 shouldn't happen - request step with empty task
		if (req != Req.start && LangExt.isblank(taskId)) {
			throw new TransException("CheapApi#qCompet(): req != start, taskId is blank, this is shouldn't happening.");
		}
		// 1 All instance must have a prevRec, except the only starting node instance.
		// 2 Check: when starting, if there is already an instance with null handleCmd for the task, fail;
		else if (req == Req.start) {
			if (LangExt.isblank(taskId)) {
				// FIXME can't check it currently
				// Creating a task together with starting new flow can be dangerous
				// - user can create two more tasks of cause, not workflow's responsibility to check.
				// TODO docs here
				q = null;
			}
			else {
				// select count(*) from p_change_application
				// where changeId = '00000l' and currentNode is null (and startNode is null);
				q = CheapEnginv1.trcs
					.select(wf.bTabl, "b")
					.col("count(*)", "cnt")
					.whereEq(wf.bRecId, taskId)
					.where(new Condit(Logic.op.isNotnull, wf.bTaskStateRef, ""));
					;
				if (wf.bNodeInstRefs != null && wf.bNodeInstRefs.containsKey(wf.node1))
					q.where(new Condit(Logic.op.isNotnull, wf.bNodeInstRefs.get(wf.node1), ""));
			}
		}
		// 3 Check: If not starting and prevCmd is null,
		//   fail (shouldn't happen when engine upgraded to v7.4);
		// 4 Check: If not starting and the out going instance's routes include the requesting one, fail
		//   - old looping instance is not the same (logic by #findFrom(): handleCmd is null);
		//   - branching from the out going instance doesn't have a same route;
		else if (req != Req.start) {
			// select count(i.nodeId) cnt from task_nodes i 
			// join oz_wfnodes nxNod on nxNod.nodeId = 't01.02B' AND i.nodeId = nxNod.nodeId AND taskId = '00001D' AND prevRec = '00003d';
			q = CheapEnginv1.trcs
				.select(wf.instabl, "i")
				.col("count(i.nodeId)", "cnt")
				.j(WfMeta.nodeTabl, "nxNod",
						"nxNod.%1$s = '%2$s' and i.%3$s = nxNod.%1$s and taskId = '%4$S' and prevRec = '%5$s'",
						WfMeta.nid, nextNodeId, nodeInst.nodeFk, taskId, prevInstId);
		}
		else q = null;

		return q;
	}
	
	public static SemanticObject commitimeout(IUser usr, String wfid, String nodeId, String busiId, String instId)
			throws SQLException, TransException {
		SemanticObject logic = CheapEnginv1.onTimeout(usr, wfid, nodeId, busiId, instId);

		// check competition - prevent multiple server running as checker
		checkerCompetition(wfid);
		
		Insert ins = (Insert) logic.get("stmt");
		ISemantext smtx = CheapEnginv1.trcs.instancontxt(usr);
		ins.ins(smtx);
		((CheapEvent) logic.get("evt")).resulve(smtx);
		logic.remove("stmt");
		return logic;
	}

	/**Check Competition - prevent multiple server running as checker
	 * @param wfid
	 * @throws TransException
	 * @throws SQLException
	 */
	private static void checkerCompetition(String wfid) throws TransException, SQLException {
		SemanticObject res = CheapEnginv1.trcs.select(WfMeta.wftabl, "wf")
			.col(WfMeta.wfChecker, "chkr")
			.where_("=", WfMeta.wfWfid, wfid)
			.rs(CheapEnginv1.trcs.basictx());
		SResultset rs = (SResultset) res.rs(0);
		if (rs.beforeFirst().next()) {
			if (CheapEnginv1.cheaprint == null)
				Utils.warn("CheapAip is checking competations but it's finger print is null, this should only hanppened when testing");
			else if (CheapEnginv1.cheaprint.equals(rs.getString("chkr")))
				throw new CheapException(CheapException.ERR_WF_COMPETATION,
						"Another timeout checker is running - the value of %s.%s is different than my finger print: %s.\n" +
						"To disable background checker, set workflow-meta.xml/t=cfg/k=enable-checker/v: false.",
						WfMeta.wftabl, WfMeta.wfChecker, CheapEnginv1.cheaprint);
		}
		else Utils.warn("CheapApi#checkerCompetition(): Checking timeout commitment competition fialed. wfid: %s", wfid);
	}

}
