package io.odysz.sworkflow;

import io.odysz.common.Utils;

public class EnginDesign {


	public enum Req { heartbeat("ping.serv"),
		session("login.serv"), TgetDef("get-def"), findRoute("findroute"),
		/** client use this to ask plausible operation using 't' */
		Ttest("test"),
		start("start"), next("next"), back("back"),
		deny("deny"), close("close"), timeout("timeout");
		@SuppressWarnings("unused")
		private String cmd;
		Req(String cmd) { this.cmd = cmd; }

		public boolean eq(String code) {
			if (code == null) return false;
			Req c = valueOf(Req.class, code);
			return this == c;
		}
	};

//	static class Event {
//		static int arrive = 11;
//
//		public static Integer parse(String trim) {
//			throw new NullPointerException(); // TODO
//		}
//	}
//
//	/** ? */
//	static class Act {
//		static int newbusi = 21;
//		static int close = 22;
//
//		private int acode = newbusi;
//		public boolean eq(int actCode) {
//			return acode == actCode;
//		}
//	}
	
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
		static String tabl = "wf_nodeInsts";
		/**Workflow instance table name: wf_nodeInsts */
		static public String tabl() { return tabl; }
		
		static String instId = "recId";
		/**Workflow instance table's pk column name */
		static public String instId() { return instId; }
		
		static String nodeFk = "processNodeId";
		/**Workflow instance table's node id fk to ir_wfdef.nodeId, e.g processNodeId of c_process_processing */
		static public String nodeFk() { return nodeFk; }
		
		static String wfIdFk = "processTypeId";
		/**Optional wf type id FK to Wftabl.recId (wfId) */
		static public String wfIdFk() { return wfIdFk; }

		static String busiFK = "baseProcessDataId";
		/**Workflow instance reference to business record, e.g. e_inspect_tasks.taskId */
		static public String busiFK() { return busiFK; }

		static String descol = "dealDescribe";
		/**Workflow instance table's node description column name */
		static public String descol() { return descol; }
		
		static private String operTime = "disposalTime";
		/**update time in instance table*/
		static public String operTime() { return operTime; }
		
		static String handleCmd = "nodeStatus";
		/**Workflow instance table's column that recording user's command handled this node,
		 * e.g. c_process_processing.nodeState used to record req name. */
		static public String handleCmd() { return handleCmd; }
		
		static String prevInstNode = "prevRec";
		/** Previous instance node cole: c_process_processing.prevRec<br>
		 * Reason? The last handled description is important to show in main list. */
		static public String prevInstNode() { return prevInstNode; }
	}

	static class Wftabl {
		/** virtual node code hard coded */
		static String tabl = "ir_workflow";

		static private String virtualNCode = "virt";
		static public String virtualNCode() { return virtualNCode; }
		static String recId = "wfId";
		static String wfName = "wfName";
		static String bussTable = "bussTable";
		static String bRecId = "bRecId";
		static String bTaskState = "bTaskState";
		/** bussiness wf type, like e_inspect_tasks.taskType */
		static String bussCateCol = "bussCateCol";
		static String node1 = "Node1";
		static String bNodeInstRefs = "bNodeInstRefs";
	}

	static class WfDeftabl {
		static private String tabl = "ir_wfdef";
		static public String tabl() { return tabl; }
		static private String wfId = "wfId";
		static public String wfId() { return wfId; }
		static private String nid = "nodeId";
		static public String nid() { return nid; }
		static private String ncode = "nodeCode";
		static public String ncode() { return ncode; }
		static private String nname = "nodeName";
		static public String nname() { return nname; }
		static private String cmdRoute = "route";
		static public String cmdRoute() { return cmdRoute; }
		static private String onEvents = "onEvents";
		static public String onEvents() { return onEvents; }
		/**time out in minute */
		static private String outTime = "timeoutmm";
		static public String outTime() { return outTime; }
		/**timeout route (nodeId) */
		static private String timeoutRoute = "timeoutRoute";
		static public String timeoutRoute() { return timeoutRoute; }
	}

	static class Wfrole {
		static private String tabl = "ir_wfrole";
		static public String tabl() { return tabl; }
		static private String wfId = "wfId";
		static public String wfId() { return wfId; }
		static private String nid = "nodeId";
		static public String nid() { return nid; }
		static private String roleId = "roleId";
		static public String roleId() { return roleId; }
	}

	static String connId;

	/**Load meta from xml configure file (workflow-meta.xml).
	 * @param filepath
	 */
	public static void reloadMeta(String filepath) {
		Utils.warn("loading meta to be done: %s", filepath);
	}
}