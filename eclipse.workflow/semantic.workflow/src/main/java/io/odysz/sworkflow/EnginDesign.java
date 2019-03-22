package io.odysz.sworkflow;

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
//	static class Instabl {
//		/**Workflow instance table's pk column name */
//		static final String instId = "recId";
//		
//		/**Workflow instance table's node id fk to ir_wfdef.nodeId, e.g tasks.nodeId referering oz_wfnodes.nodeId */
//		static final String nodeFk = "nodeId";
//		
//		/**<b>Optional</b> wf type id FK to Wftabl.recId (nodeWfId) */
//		static final String wfIdFk = null;
//
//		/**Workflow instance reference to business record, e.g. e_inspect_tasks.taskId */
//		// static final String busiFK = "baseProcessDataId";
//
//		/**Workflow instance table's node description column name */
//		static final String descol = "descpt";
//		
//		/**update time in instance table*/
//		static final String operTime = "oper";
//		
//		/**Workflow instance table's column that recording user's command handled this node,
//		 * e.g. c_process_processing.nodeState used to record req name. */
//		static final String handleCmd = "handlingCmd";
//		
//		/** Previous instance node cole: c_process_processing.prevRec<br>
//		 * Reason? The last handled description is important to show in main list. */
//		static final String prevInstNode = "prevRec";
//	}

	static class WfMeta {
		/** virtual node code hard coded */
		static final String wftabl = "oz_workflow";
		static final String wfName = "wfName";

		static final String virtualNCode = "virt";
		static final String recId = "wfId";
		static final String instabl = "instabl";
		static final String bussTable = "bussTable";
		static final String bRecId = "bRecId";
		static final String bTaskState = "bStateRef";
		/** bussiness wf type, like e_inspect_tasks.taskType */
		static final String bussCateCol = "bussCateCol";
		static final String node1 = "Node1";
		static final String bNodeInstBackRefs = "backRefs";

		static final String nodeTabl = "oz_wfnodes";
		static final String nodeWfId = "nodeWfId";
		static final String nid = "nodeId";
		static final String ncode = "nodeCode";
		static final String nname = "nodeName";
		static final String narriveCondit = "arrivCondit";
		static final String nonEvents = "onEvents";
		/**time out in minute */
		static final String noutTime = "timeouts";
		/**timeout route (nodeId) */
		static final String ntimeoutRoute = "timeoutRoute";
		
		//////// oz_wfcmds
		static final String cmdTabl = "oz_wfcmds";
		static final String cmdCmd = "cmd";
		static final String cmdRoute = "route";
		static final String cmdTxt = "txt";
		static final String cmdSort = "sort";

		/** Node instance table, e.g task_nodes*/
		static class nodeInst {
			/** column of node-instance-table-name
			 * This table name is configurable in oz_workflow.instabl,
			 * for separating node instance table to improve performance
			 * - this table can be large.*/
			static final String tabl = "task_nodes";

			/**e.g. task_nodes.instId */
			static final String id = "instId";

			static final String nodeFk = "nodeId";

			/**<b>Optional</b> wf type id FK to Wftabl.recId (nodeWfId) */
			static final String wfIdFk = null;

			static final String oper ="oper";
			static final String opertime ="opertime";

			static final String handleCmd = "handlingCmd";

			static final String cmdRigths = "cmdRights";

			static final String descol = "descpt";

			static final String prevInst = "prevRec";
		}
		
	}

}