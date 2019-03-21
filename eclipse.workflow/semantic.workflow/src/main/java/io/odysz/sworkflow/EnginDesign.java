package io.odysz.sworkflow;

import java.util.HashMap;

import io.odysz.common.Utils;
import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DASemantics;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantics.ISemantext;

public class EnginDesign {


	public enum Req { heartbeat("ping.serv"),
		session("login.serv"), TgetDef("get-def"), findRoute("findroute"),
		/** client use this to ask plausible operation using 't' */
		Ttest("test"),
		start("start"),
		cmd("cmd"), // request stepping according to cmds configured in oz_wfcmds
		close("close"), timeout("timeout");
		@SuppressWarnings("unused")
		private String rq;
		Req(String req) { this.rq = req; }

		public boolean eq(String code) {
			if (code == null) return false;
			Req c = valueOf(Req.class, code);
			return this == c;
		}
	};

	/**Keywords used to communicate with client
	 * @author odys-z@github.com
	 */
	static class WfProtocol {
		public static String reqBody = "wfreq";

		public static String ok = "ok";

		static final String wfid = "wfid";
		static final String cmd = "cmd";
		static final String busid = "busid";
		static final String current = "current";
		static final String ndesc = "nodeDesc";
		static final String nvs = "nvs";
		static final String busiMulti = "multi";
		static final String busiPostupdate = "post";
		
		static final String routes = "routes";

		static final String wfName = "wfname";
		static final String wfnode1 = "node1";
		static final String bTabl = "btabl";
	}

	/**Workflow instance(task) and node instance (task state) information
	 * a.k.a. columns and tables of business that must be handled by workflow engine.
	 */
	static class Instabl {
		static final String instId = "recId";
		/**Workflow instance table's pk column name */
		// static public String instId() { return instId; }
		
		static final String nodeFk = "processNodeId";
		/**Workflow instance table's node id fk to ir_wfdef.nodeId, e.g processNodeId of c_process_processing */
		// static public String nodeFk() { return nodeFk; }
		
		static final String wfIdFk = "processTypeId";
		/**Optional wf type id FK to Wftabl.recId (wfId) */
		// static public String wfIdFk() { return wfIdFk; }

		static final String busiFK = "baseProcessDataId";
		/**Workflow instance reference to business record, e.g. e_inspect_tasks.taskId */
		// static public String busiFK() { return busiFK; }

		static final String descol = "dealDescribe";
		/**Workflow instance table's node description column name */
		// static public String descol() { return descol; }
		
		static final String operTime = "disposalTime";
		/**update time in instance table*/
		// static public String operTime() { return operTime; }
		
		static final String handleCmd = "nodeStatus";
		/**Workflow instance table's column that recording user's command handled this node,
		 * e.g. c_process_processing.nodeState used to record req name. */
		// static public String handleCmd() { return handleCmd; }
		
		static final String prevInstNode = "prevRec";
		/** Previous instance node cole: c_process_processing.prevRec<br>
		 * Reason? The last handled description is important to show in main list. */
		// static public String prevInstNode() { return prevInstNode; }
	}

	static class WfMeta {
		/** virtual node code hard coded */
		static final String wftabl = "oz_workflow";

		static final String virtualNCode = "virt";
		static final String recId = "wfId";
		static final String wfName = "wfName";
		static final String bussTable = "bussTable";
		static final String bRecId = "bRecId";
		static final String bTaskState = "bStateRef";
		/** bussiness wf type, like e_inspect_tasks.taskType */
		static final String bussCateCol = "bussCateCol";
		static final String node1 = "Node1";
		static final String bNodeInstBackRefs = "backRefs";

		static final String nodeTabl = "oz_wfnodes";
		static final String wfId = "wfId";
		static final String nid = "nodeId";
		static final String ncode = "nodeCode";
		static final String nname = "nodeName";
		static final String arriveCondit = "arrivCondit";
		static final String onEvents = "onEvents";
		/**time out in minute */
		static final String outTime = "timeouts";
		/**timeout route (nodeId) */
		static final String timeoutRoute = "timeoutRoute";
		
		//////// oz_wfcmds
		static final String cmdTabl = "oz_wfcmds";
		static final String cmdCmd = "cmd";
		static final String cmdRoute = "route";
		static final String cmdTxt = "txt";
		static final String cmdSort = "sort";

		//////// Node instance table
		/** column of node-instance-table-name
		 * This table name is configurable in oz_workflow.instabl,
		 * for separating node instance table to improve performance
		 * - this table can be large.*/
		static final String nodeInstabl = "instabl";

		/**e.g. task_nodes.instId */
		static final String nodeInstId = "instId";

		/**e.g. task_nodes.nodeId */
		static final String nodeInstNode = "nodeId";

		static final String nodeRigths = "cmdRights";
	}


	/**Load meta from xml configure file (workflow-meta.xml).
	 * @param filepath
	 * @return 
	public static CheapTransBuild reloadMeta(String connId, XMLTable xcfgs) {
		// Utils.warn("loading meta from: %s", filepath);
		// String connId = "local-sqlite"; // TODO conn-id comes from configuration
		CheapTransBuild builder = new CheapTransBuild(connId, xcfgs);
		return builder;
	}
	 */
}