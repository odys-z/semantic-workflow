package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

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
		return new CheapApi(wftype, Req.start, null);
	}

	public static CheapApi next(String wftype, String currentNode, String taskId) {
		CheapApi api = new CheapApi(wftype, Req.cmd, null);
		api.currentNode = currentNode;
		api.taskId = taskId;
		return api;
	}

	/**Get next route node according to ntimeoutRoute (no time checking).<br>
	 * Only called by CheapChecker?
	 * @param wftype
	 * @param currentNode
	 * @param taskId
	 * @return
	 */
	static CheapApi stepTimeout(String wftype, String currentNode, String taskId) {
		CheapApi api = new CheapApi(wftype, Req.timeout, null);
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
	private ArrayList<String[]> multiDels;
	private ArrayList<String[]> multiInserts;
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
			ArrayList<String[]> delConds, ArrayList<String[]> inserts) {
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
	 * @param st 
	 * @return {stmt: {@link Update} (for committing), <br>
	 * 		evt: {@link CheapEvent} for start event(new task ID must resolved), <br>
	 * 		stepHandler: {@link CheapEvent} for req (step/deny/next) if there is one configured, <br>
	 * 		arriHandler: {@link CheapEvent} for arriving event if there is one configured}
	 * @throws SQLException
	 * @throws TransException 
	 */
	public SemanticObject commit(IUser usr, CheapTransBuild st) throws SQLException, TransException {
		SemanticObject multireq = CheapJReq.formatMulti(st, multiChildTabl, multiDels, multiInserts);
		return CheapEngin.onReqCmd(usr, wftype, currentNode, req, cmd,
					taskId, nodeDesc, nvs, multireq, postups);
	}

}
