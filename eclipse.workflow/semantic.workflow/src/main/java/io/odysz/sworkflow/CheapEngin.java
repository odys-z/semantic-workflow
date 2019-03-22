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
			String connId = tb.getString("conn");

			trcs = new CheapTransBuild(connId, xtabs.get("semantics"));

			// select * from oz_wfworkflow;
			SResultset rs = (SResultset) trcs
					.select(WfMeta.wftabl)
					.rs(trcs.basiContext()); // static context is enough to load cheap configs

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
				String conn = CheapEngin.trcs.basiconnId();

				// 2.1 node instance auto key, e.g. task_nodes.instId
				DATranscxt.addSemantics(conn, instabl, nodeInst.id, smtype.autoInc, nodeInst.id);

				// 2.2 task_nodes.oper, opertime
				DATranscxt.addSemantics(conn, instabl, nodeInst.id, smtype.opTime,
						new String[] { nodeInst.oper, nodeInst.id });

				// 2.3 node instance Fk to nodes.nodeId, e.g. task_nodes.nodeId -> oz_wfnodes.nodeId
//				DATranscxt.addSemantics(conn, nodeInstabl, WfMeta.nodeInstId, smtype.fkIns,
//						String.format("%s,%s,%s", WfMeta.nodeInstNode, WfMeta.nodeTabl, WfMeta.nid));

				// 2.4 business task's pk and current state ref, e.g. tasks.wfState -> task_nodes.instId
				DATranscxt.addSemantics(conn, busitabl, bRecId, smtype.autoInc, bRecId);
				DATranscxt.addSemantics(conn, busitabl, bRecId, smtype.fkIns,
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

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (IrSingleton.debug) System.out.println("cheapwf.serv get ------");
		jsonResp(request, response);
	}
	 */

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (IrSingleton.debug) System.out.println("cheapwf.serv post ======");
		jsonResp(request, response);
	}

	private void jsonResp(HttpServletRequest request, HttpServletResponse response) {
		response.setContentType("text/html;charset=UTF-8");
		JsonWriter writer = null;
		try {
			writer = Json.createWriter(response.getOutputStream());
			JsonStructure resp;

			String t = request.getParameter("t");
			if ("reload".equals(t)) {
				// reload workflow configuration (after data changed)
				reloadCheap();
				writer.write(JsonHelper.OK(String.format("There are %s workflow templates re-initialized.", wfs.size())));
			}
			else {
				JSONObject[] parsed = ServManager.parseReq(request); // [0] jheader, [1] jreq-obj
				DbLog dblog = IrSession.check(parsed[0]);

				JSONObject jarr = parsed[1];

				String connId = request.getParameter("conn");
				if (connId == null || connId.trim().length() == 0)
					connId = DA.getDefltConnId();

				IrUser usr = null;
				// check header
				JSONObject jheader = (JSONObject) jarr.get("header");
				if (jheader == null) throw new IrSessionException("Empty header for workflow request.");
				IrSession.check(jheader);
				usr = IrSession.getUser(jheader);
				if (usr == null)
					throw new IrSessionException("No such user logged in.");
			
				// handle request (next, back, deny, ...)
				JSONObject jreq = (JSONObject)jarr.get(EnginDesign.WfProtocol.reqBody);
				resp = handle(jreq, connId, t, usr, dblog);
				writer.write(resp);
			}

			writer.close();
			response.flushBuffer();
		} catch (CheapException chex) {
			if (writer != null)
				writer.write(JsonHelper.err(CheapException.ERR_WF, chex.getMessage()));
		} catch (IrSessionException ssex) {
			if (writer != null)
				writer.write(JsonHelper.err(IrSession.ERR_CHK, ssex.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} finally {
			if (writer != null)
				writer.close();
		}
	}
	 */
	
	/**TODO move to CheapServ
	 * Test plausibility, find routes of current node, or handle commitment.<br>
	 * To commit:<br>
	 * 1. t='test', test the accessibility<br>
	 * 1.1 req = 'start', can user start a workflow? Where right considered the same as the node1.<br>
	 * 2.2 req = other, can user commit with the request?<br>
	 * 2. t='route', (req ignored)<br>
	 *    find all possible routes<br>
	 * 3. start or commit a workflow<br>
	 * @param jobj
	 * @param connId
	 * @param t
	 * @param dblog 
	 * @param usr
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws IrSemanticsException
	 * @throws CheapException
	private SemanticObject handle(SemanticObject jobj, String connId, String t, IUser usr)
			throws SQLException, IOException, SemanticException, CheapException {
		String wftype = (String) jobj.get(WfProtocol.wfid);
		String req = (String) jobj.get(WfProtocol.cmd);
		String busiId = (String) jobj.get(WfProtocol.busid); // taskId
		String currentInstId = (String) jobj.get(WfProtocol.current);
		String nodeDesc = (String)jobj.get(WfProtocol.ndesc);
		String[] busiNvs = (String[])jobj.get(WfProtocol.nvs);
		SemanticObject multireq = (SemanticObject)jobj.get(WfProtocol.busiMulti);

		// String[] postreq = (String[])jobj.get(WfProtocol.busiPostupdate);
		SemanticObject postreq = (SemanticObject)jobj.get(WfProtocol.busiPostupdate);
	
		// 1. t= test
		if (Req.Ttest.eq(t) && req != null && req.trim().length() > 0) {
			// can next? can start?
			CheapNode currentNode; 
			CheapWorkflow wf = wfs.get(wftype);
			if (Req.start.eq(req)) {
				currentNode = wf.start(); // a virtual node
				// can start?
				if (currentNode == null)
					// return JsonHelper.err(CheapException.ERR_WF, Configs.getCfg("cheap-workflow", "err-no-start-node"));
					return new SemanticObject()
							.code(CheapException.ERR_WF)
							.msg(Configs.getCfg("cheap-workflow", "err-no-start-node"));
			}
			else {
				currentNode = wf.getNodeByInst(currentInstId);
				// can next?
				if (currentNode == null)
					return new SemanticObject().code(CheapException.ERR_WF)
							.msg(Configs.getCfg("cheap-workflow", "err-no-current"));
			}

			// has route?
			CheapNode nextNode = currentNode.getRoute(req);
			if (nextNode == null)
				return new SemanticObject().code(CheapException.ERR_WF)
						.error(Configs.getCfg("cheap-workflow", "t-no-route"), req);

			// check rights
			wf.checkRights(usr, currentNode, nextNode);
		
			return new SemanticObject().code(WfProtocol.ok)
					.msg("%s:%s", req, nextNode.nodeId());
		}
		// 2. t= findroute
		if (Req.findRoute.eq(t)) {
			CheapNode currentNode; 
			CheapWorkflow wf = wfs.get(wftype);
			currentNode = wf.getNodeByInst(currentInstId);
			if (currentNode != null)
				return currentNode.formatAllRoutes(usr);
			else
				// wrong data
				return new SemanticObject().code(CheapException.ERR_WF).error("[]");
		}
		// 3. t = getDef
		else if (Req.TgetDef.eq(t)) {
			CheapWorkflow wf = wfs.get(wftype);
			if (wf != null) return wf.getDef(); else return null;
		}
		// t is anything else, handle request commands
		else {
			Object[] rets = onReqCmd(usr, wftype, currentInstId,
					req, busiId, nodeDesc, busiNvs, multireq,
					// parsePosts(postreq)
					postreq);
			Insert jupdate = (Insert) rets[0];
			ArrayList<String> sqls = new ArrayList<String>(1);
			SemanticObject newIds = jupdate.commit(sqls, usr);
			
			// fire event
			CheapEvent evt = (CheapEvent) rets[1];
			evt.resolveTaskId(newIds);
			// FIXME: Event handling can throw an exception.
			// FIXME: Event handling may needing a returning message to update nodes.
			ICheapEventHandler stepHandler = (ICheapEventHandler) rets[2];
			if (stepHandler != null)
				stepHandler.onNext(evt);
			ICheapEventHandler arriveHandler = (ICheapEventHandler) rets[3];
			if (arriveHandler  != null)
				arriveHandler.onArrive(evt);

			return new SemanticObject().code(WfProtocol.ok).data(newIds);
		}
	}
	 */

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
						trcs.basiContext().formatResulv(wf.instabl(), wf.bRecId) : busiId);

		if (multireq != null) {
			upd3.postChildren(multireq, trcs);
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

	/**SHOUDN'T BE HERE
	 * Parse post updates for business requirements.<pre>
[
  -- post update request 1
  [
	{	"method":"record",
		"vals": {
				"tabl":"c_fault_rec",
				"act":{"a":"insert",
					"vals":[{"name":"inspTaskId","value":"AUTO"}],
					"conds":[{"field":"recId","v":"000001","logic":"="}]}
				}
	},
	{	"method":"multi","vals":[]	} -- any method other than "record" are ignored.
  ]
  -- more post update following here can be handled
]
</pre>
	 * Complicate request structure (self recursive structured) can not handled here. 
	 * @param postups
	 * @return
	 * @throws SQLException
	@SuppressWarnings("rawtypes")
	private static Update parsePosts(JSONArray postups) throws SQLException {
		if (postups != null) {
			// case 1
			// [
			//	-- update 1 (post_u)
			//	[ {	"method":"record",				-- record update (up_v)
			//		"vals":{"tabl":"c_fault_rec",
			//				"act":{	"a":"insert",
			//						"vals":[{"name":"inspTaskId","value":"AUTO"}],
			//						"conds":[{"field":"recId","v":"000001","logic":"="}]
			//					  }
			//				}
			//	  },
			//	  {	"method":"multi","vals":[]}		-- multi update
			//	]
			//	-- update 2
			// ]
			
			// case 2
			// [{"method":"record","vals":{"tabl":"e_areas","act":{"a":"update","vals":[{"name":"maintenceStatus"}],"conds":[{"field":"areaId","v":"000006","logic":"="}]},"postupdates":null}}]

			JRequestUpdate updates = null;
			Iterator u = postups.iterator();
			while (u.hasNext()) {
				// post update-i
				JSONArray post_u = (JSONArray) u.next();
				
				Iterator v = post_u.iterator();
				while (v.hasNext()) {
					JSONObject up_v = (JSONObject) v.next();
					String method = (String) up_v.get("method");
					// FIXME Our protocol not designed with recursive structure in mind.
					// It's not protocol needing upgraded, it's the server side structure needing to be re-designed.
					// Of course the protocol should be designed in a more elegant style.
					if (!"record".equals(method)) {
						if (debug) {
							System.err.println("CheapEngine can not handle the request (only 'record' method is supported). This is due to the protocol and architect design limitation.");
							System.err.println("Request ignored: " + up_v.toString());
						}
						continue;
					}

					JSONObject vals_v = (JSONObject) up_v.get("vals");
					String tabl = (String) vals_v.get("tabl");
					JSONObject act = (JSONObject) vals_v.get("act");
					if (act == null) continue;
				
					String a = (String) act.get("a");
					JSONArray conds = (JSONArray) act.get("conds");
					JSONArray vals = (JSONArray) act.get("vals");

					String pk = null;
					if ( conds != null && conds.size() == 1) {
						Iterator c = conds.iterator();
						JSONObject cond = (JSONObject) c.next();
						pk = (String) cond.get("field");
					}

					String[][] eqConds = JsonHelper.convertFvList(conds);
					ArrayList<String[]> inserVals = JsonHelper.convertNvList(vals);

					if ("update".equals(a))
						// updateRecord(usr, sqls, post, connId, tabl, lobs, autoK, logBuffer, flag);
						updates = new JRequestUpdate(Cmd.update, tabl, pk, eqConds, inserVals, updates);
					else if ("insert".equals(a))
						updates = new JRequestUpdate(Cmd.insert, tabl, pk, eqConds, inserVals, updates);
					else if ("delete".equals(a))
						updates = new JRequestUpdate(Cmd.delete, tabl, pk, eqConds, inserVals, updates);
					else {
						System.err.println(String.format("WARN - DeleteBatch: postupdate command is not 'update/delete/insert'. New function requirements must implemented. \ntabl=%s,\nact=%s",
								tabl, act.toJSONString()));
					}
				}
			}
			return updates;
		}
		return null;
	}
	 */

}
