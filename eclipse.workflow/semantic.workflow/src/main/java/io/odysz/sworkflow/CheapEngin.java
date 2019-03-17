package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DASemantext;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Act;
import io.odysz.sworkflow.EnginDesign.Event;
import io.odysz.sworkflow.EnginDesign.Instabl;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfProtocol;
import io.odysz.sworkflow.EnginDesign.Wftabl;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

/**A simple work flow engine
 * @author odys-z@github.com
 */
public class CheapEngin {
	public static final boolean debug = true;

	static IUser checkUser;
	static CheapTransBuild transBuilder;
	
	static HashMap<String, CheapWorkflow> wfs;
	public static HashMap<String, CheapWorkflow> wfs() { return wfs; }

	private static ScheduledFuture<?> schedualed;
	private static ScheduledExecutorService scheduler;

	static void init (String connId, String rootpath) throws SemanticException, SAXException, IOException{
		checkUser = new CheapRobot();
		ISemantext s = new DASemantext(connId, CheapTransBuild.initConfigs(connId, rootpath + "/semantics.xml"));
		transBuilder = new CheapTransBuild(s);
	}

	/**Init cheep engine configuration, schedual a timeout checker. 
	 * @param customChecker 
	 * @throws TransException */
	public static void initCheap(String configPath, ICustomChecker customChecker) throws TransException {
		reloadCheap(configPath);

		// worker thread 
		stopCheap();
		
		scheduler = Executors.newScheduledThreadPool(0);
		schedualed = scheduler.scheduleAtFixedRate(
				new CheapChecker(wfs, customChecker), 0, 1, TimeUnit.MINUTES);
	}

	private static void reloadCheap(String filepath) throws TransException {
		try {
			EnginDesign.reload(filepath);

			// String sql = String.format("select * from %s", EnginDesign.Wftabl.tabl);
			// SResultset rs = Connects.select(sql);
			SemanticObject o = (SemanticObject) transBuilder
					.select(EnginDesign.Wftabl.tabl)
					.rs(transBuilder.staticContext()); // static context is enough to load cheap configs
			SResultset rs = (SResultset) o.get("rs");

			rs.beforeFirst();

			wfs = new HashMap<String, CheapWorkflow>(rs.getRowCount());
			while (rs.next()) {
				// CheapWorkflow ( wfType,  bTabl,  bRecId,  bRefCol,  node1)
				CheapWorkflow wf = new CheapWorkflow(
						rs.getString(Wftabl.recId),
						rs.getString(Wftabl.wfName),
						rs.getString(Wftabl.bussTable),
						rs.getString(Wftabl.bRecId),
						rs.getString(Wftabl.bRefCol),
						rs.getString(Wftabl.bussCateCol),
						rs.getString(Wftabl.node1),
						rs.getString(Wftabl.backRefs));
				wfs.put(rs.getString(Wftabl.recId), wf);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static void stopCheap() {
		if (schedualed == null && scheduler == null) return;
		// stop worker
		System.out.println("cancling WF-Checker ... ");
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
	
	/**Test plausibility, find routes of current node, or handle commitment.<br>
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
	 */
	private SemanticObject handle(SemanticObject jobj, String connId, String t, IUser usr)
			throws SQLException, IOException, SemanticException, CheapException {
		String wftype = (String) jobj.get(WfProtocol.wfid);
		String req = (String) jobj.get(WfProtocol.cmd);
		String busiId = (String) jobj.get(WfProtocol.busid); // taskId
		String currentInstId = (String) jobj.get(WfProtocol.current);
		String nodeDesc = (String)jobj.get(WfProtocol.ndesc);
		String[] busiNvs = (String[])jobj.get(WfProtocol.nvs);
		SemanticObject multireq = (SemanticObject)jobj.get(WfProtocol.busiMulti);
		String[] postreq = (String[])jobj.get(WfProtocol.busiPostupdate);
			
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
					req, busiId, nodeDesc, busiNvs, multireq, parsePosts(postreq));
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

	/**step to next node according to current node and request.<br>
	 * Use case 1: To create a new e_insp_task(start):<br>
	 * 1. create task (just a virtual node stepping next and on arrive event = newBusi);<br>
	 * 2.1. generate nodeId for new instance node;<br>
	 * 2.2. create node - with node.taskId = AUTO;<br>
	 * 3. update task.taskStatus = new-nodeId got in step 2<br>
	 * 3.1. update task.startNode = new-nodeId when nodeId = 'f01' (ir_workflow.backRef = "f01:startNode")<br>
	 * 3.2. update currentNode.nodeState = req-name (starting wf ignored)<br>
	 * 3.3. handle multi-operation request (e.g. task-devices details)<br>
	 * 3.4. any post update not related to 3.3 multi-update (with AUTO taskId)<br>
	 * <p/>
	 * Use case 2: update task state (next):<br>
	 * 1. <br>
	 * 2.1. generate nodeId for new instance node;<br>
	 * 2.2. create node - with node.taskId = taskId, prevNode=current-nodeId;<br>
	 * 3. update task.taskStatus = new-nodeId got in step 2<br>
	 * 3.1. update task.startNode = new-nodeId when nodeId = 'f01' (ir_workflow.backRef = "f01:startNode")<br>
	 * 3.2. update currentNode.nodeState = req-name (if not start)<br>
	 * 3.3. handle multi-operation request (e.g. task-devices details)<br>
	 * 3.4. any post update not related to 3.3 multi-update (providing taskId)<br>
	 * <p/>
	 * Use case 3: timeout step:<br>
	 * 1. <br>
	 * 2.1. generate nodeId for new instance node;<br>
	 * 2.2. create node - with node.taskId = taskId, prevNode=current-nodeId;<br>
	 * 3. update task.taskStatus = new-nodeId got in step 2<br>
	 * 3.1. update task.startNode = new-nodeId when nodeId = 'f01' (ir_workflow.backRef = "f01:startNode")<br>
	 * 3.2. update currentNode.nodeState = req-name ("timeout")<br>
	 * 3.3. (null args) handle multi-operation request (e.g. task-devices details)<br>
	 * 3.4. (null args) any post update not related to 3.3 multi-update (providing taskId)<br>
	 * @param usr
	 * @param wftype
	 * @param currentInstId current workflow instance id, e.g. value of c_process_processing.recId
	 * @param req
	 * @param busiId business record ID if exists, or null to create (providing piggyback)
	 * @param nodeDesc workflow instance node description
	 * @param busiPack nvs for task records
	 * @param multireq  {tabl: tablename, del: dels, insert: eaches};
	 * @param postreq
	 * @return [0: {@link Insert} (for committing), <br>
	 * 1: start/step event (new task ID must resolved), <br>
	 * 2: {@link ICheapEventHandler} for req (step/deny/next) if there is one configured]<br>
	 * 3: {@link ICheapEventHandler} for arriving event if there is one configured]
	 * @throws SQLException
	 * @throws CheapException
	 */
	public static SemanticObject onReqCmd(IUser usr, String wftype, String currentInstId, Req req,
			String busiId, String nodeDesc, ArrayList<String[]> busiPack, SemanticObject multireq, Update postreq)
					throws SQLException, CheapException {

		CheapNode currentNode; 
		CheapEvent evt = null; 

		CheapWorkflow wf = wfs.get(wftype);
		if (req == Req.start) {
			currentNode = wf.start(); // a virtual node
		}
		else {
			currentNode = wf.getNodeByInst(currentInstId);
		}
		if (currentNode == null) throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));
		CheapNode nextNode = currentNode.getRoute(req);
		
		if (nextNode == null)
			// a configuration problem?
			throw new SQLException(
				String.format(Configs.getCfg("cheap-workflow", "t-no-node"),
				wftype, currentInstId, req));

		// FIX: should checking exception when target node already exists?
		// if (!Req.eq(Req.start, req))
		//		checkExistance(nextNode, busiId);

		wf.checkRights(usr, currentNode, nextNode);
			
		// 2.1 new node-id
		// using semantic support instead - semantics all ready added.
		// String newInstancId = transBuilder.genId(Instabl.tabl, Instabl.instId, null);

		// 3 update task.taskStatus
		// 3.4. postupdate requested by client
		Update postupClient = null;
		postupClient = postreq;

		// 3.3. handle multi-operation request 
		Update upd3 = CheapEngin.transBuilder.update(wf.bTabl)
				.where("=", wf.bRecId, busiId == null ? "AUTO" : busiId);
		if (multireq != null) {
			upd3.postChildren(multireq);
		}
		// IMPORTANT timeout checking depends on this (null is not handled for timeout checking)
		// 3.2 save command name as current node's state (c_process_processing.nodeState = cmdName)
		Update post32 = null;
		if (currentNode != null && Req.start != req && EnginDesign.Instabl.handleCmd != null) {
			post32 = CheapEngin.transBuilder.update(Instabl.tabl)
					.where("=", Instabl.instId, currentInstId)
					.nv(Instabl.handleCmd, currentNode.getReqName(req))
					.post(postupClient);
		}
		if (post32 == null)
			post32 = postupClient;

		// 3.1 update task.startNode = new-nodeId when nodeId = 'f01' (ir_workflow.backRef = "f01:startNode")
		if (wf.backRefs != null && wf.backRefs.containsKey(nextNode.nodeId())) {
			// if next node == "f01", update task.startNode = new-node-instance-id
			String colname = wf.backRefs.get(nextNode.nodeId());
			upd3.nv(wf.bRefCol, newInstancId)
				.nv(wf.bCateCol, wf.wfId)
				.nv(colname, newInstancId);
		}

		// 2. create node
		// starting a new wf at the beginning
		// nodeId = new-id
		Insert ins2 = CheapEngin.transBuilder.insert(Instabl.tabl);
		ins2.nv(Instabl.instId, newInstancId)
			.nv(Instabl.nodeFk, nextNode.nodeId())
			.nv(Instabl.descol, nodeDesc);
		// 2.2 prevNode=current-nodeId;
		if (currentInstId != null)
			ins2.nv(Instabl.prevInstNode, currentInstId);
		// [OPTIONAL] nodeinstance.wfId = wf.wfId
		if (Instabl.wfIdFk != null)
			ins2.nv(Instabl.wfIdFk, wf.wfId);
		// check: starting with null busiId
		// check: busiId not null for step, timeout, ...
		if (Req.start == req && busiId != null)
			Utils.warn("Wf: starting a new instance with existing business record '%s' ?", busiId);
		else if (Req.start != req && busiId == null)
			throw new CheapException(Configs.getCfg("cheap-workflow", "t-no-business"), wf.wfId);

		// c_process_processing.baseProcessDataId = e_inspect_tasks.taskId
		ins2.nv(Instabl.busiFK, Req.start == req ? "AUTO" : busiId);

		Insert ins1 = null; 
		if (Req.start == req) {
			// 1. create task
			ins1 = CheapEngin.transBuilder.insert(wf.bTabl);
			ins1.nv(wf.bCateCol, wf.wfId);
			if (busiPack != null) {
				for (String[] nv : busiPack) {
					ins1.nv(nv[0], nv[1]);
				}
			}
			ins1.post(ins2);
		}
		else {
			ins1 = ins2;
			Act act = (nextNode.getAct(EnginDesign.Event.arrive));
			if (act != null && act.eq(Act.close)) {
				// do nothing
			}
			else if (act != null)
				throw new SQLException("TODO..."); 
			// and act is not necessary?
		}

		// stepping event except a starting one
		evt = new CheapEvent(currentNode.wfId(), currentNode.nodeId(),
						nextNode.nodeId(), newInstancId,
						// busiId is null for new task, resolved later
						Req.start == req ? "AUTO" : busiId, req);
		
//		return new Object[] {ins1, evt, currentNode.stepEventHandler(req),
//				nextNode.onEventHandler(Event.arrive)};
		return new SemanticObject()
				.put("stmt", ins1)
				.put("startEvt", evt)
				.put("stepEvt", currentNode.stepEventHandler(req))
				.put("arriEvt", nextNode.onEventHandler(Event.arrive));
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
