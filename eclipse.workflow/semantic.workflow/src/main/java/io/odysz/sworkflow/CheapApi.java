package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.sql.Update;

/**CheapEngine API for server side, equivalent to js/cheapwf.<br>
 * Check Schedual.startInspectask() for sample code.
 * @author ody
 */
public class CheapApi {
	/**Get an API instance to start a new workflow of type wftype.
	 * @param wftype
	 * @return
	 */
	public static CheapApi start(String wftype) {
		return new CheapApi(wftype, Req.start);
	}

	public static CheapApi next(String wftype, String currentNode, String taskId) {
		CheapApi api = new CheapApi(wftype, Req.next);
		api.currentNode = currentNode;
		api.taskId = taskId;
		return api;
	}

	/**Get next route node according to timeoutRoute (no time checking).<br>
	 * Only called by CheapChecker?
	 * @param wftype
	 * @param currentNode
	 * @param taskId
	 * @return
	 */
	static CheapApi stepTimeout(String wftype, String currentNode, String taskId) {
		CheapApi api = new CheapApi(wftype, Req.timeout);
		api.currentNode = currentNode;
		api.taskId = taskId;
		return api;
	}

	private String wftype;
	private Req req;
	private String taskId;
	private String nodeDesc;
	private String currentNode;
	/** task table n-vs */
	private ArrayList<String[]> nvs;

	private String multiChildTabl;
	private ArrayList<String[][]> multiDels;
	private ArrayList<ArrayList<String[]>> multiInserts;
	private Update postups;
	

	protected CheapApi(String wftype, Req req) {
		this.wftype = wftype;
		this.req = req;
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
			ArrayList<String[][]> delConds, ArrayList<ArrayList<String[]>> inserts) {
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
	 * @return {stmt: {@link Update} (for committing), <br>
	 * 		stepEvt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepEvt: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriEvt: {@link CheapEvent} for arriving event if there is one configured}
	 * @throws SQLException
	 * @throws CheapException
	 */
	public SemanticObject commit(IUser usr) throws SQLException, CheapException {
		SemanticObject multireq = formatMulti(multiChildTabl, multiDels, multiInserts);
		return CheapEngin.onReqCmd(usr, wftype, currentNode, req, taskId,
									nodeDesc, nvs, multireq, postups);
	}

	// SHOULDN'T BE HERE
	@SuppressWarnings("unchecked")
	private SemanticObject formatMulti(String multiTabl, ArrayList<String[][]> delConds,
			ArrayList<ArrayList<String[]>> inserts) {
		JSONObject jmultis = new JSONObject();
		jmultis.put("method", "multi");

		// tabl
//		JSONObject jmultiObj = new JSONObject();
//		jmultiObj.put("tabl", multiTabl);
		jmultis.put("tabl", multiTabl);

		// del
//		JSONArray jdels = new JSONArray();
//		for (String[][] delCond : delConds) {
//			JsonObject d = JsonHelper.convert2FvLists(delCond);
//			jdels.add(d);
//		}
		JSONArray jdel = JsonHelper.convertNvs2EqConds(delConds);
		JSONArray jdels = new JSONArray();
		jdels.add(jdel);
		jmultis.put("del", jdels);
		
		// insert
//		JSONArray jinss = new JSONArray();
//		for (ArrayList<String[]> inse : inserts) {
//			JsonArray i = JsonHelper.convert2FvLists(inse).build();
//			// see UpdateBatch.updateMulti(): {field: "roleId", v: "role.01"}
//			jinss.add(i);
//		}

		JSONArray jinss = JsonHelper.convert2FvJSONArray(inserts);
		jmultis.put("insert", jinss);
		
//		jmultis.put("vals", jmultiObj);

		return jmultis;
	}

}
