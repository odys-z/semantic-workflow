package io.odysz.sworkflow;

public class EnginDesign {
	/**Request types, heartbeat, session, test, find-route, ... */
	public enum Req { heartbeat("ping"),
		/** load workflow */
		load("load"),
		/** load node's commands */
		nodeCmds("nodeCmds"),
		TgetDef("get-def"), findRoute("findroute"),
		rights("rights"),
		/** client use this to ask plausible operation using 't' */
		Ttest("test"), start("start"),
		/**request stepping according to cmds configured in oz_wfcmds */
		cmd("cmd"), close("close"), timeout("timeout");
		@SuppressWarnings("unused")
		private String rq;
		Req(String req) { this.rq = req; }

		public boolean eq(String code) {
			if (code == null) return false;
			Req c = valueOf(Req.class, code);
			return this == c;
		}

		public static Req parse(String t) {
			if (t == null)
				return null;
			t = t.trim();
			if (heartbeat.name().equals(t))
				return heartbeat;
			if (load.name().equals(t))
				return load;
			if (nodeCmds.name().equals(t))
				return nodeCmds;
			if (TgetDef.name().equals(t))
				return TgetDef;
			if (findRoute.name().equals(t))
				return findRoute;
			if (rights.name().equals(t))
				return rights;
			if (Ttest.name().equals(t))
				return Ttest;
			if (start.name().equals(t))
				return start;
			if (cmd.name().equals(t))
				return cmd;
			if (close.name().equals(t))
				return close;
			if (timeout.name().equals(t))
				return timeout;
			return null;
		}
	};

	static class WfMeta {
		/** virtual node code hard coded */
		static final String wftabl = "oz_workflow";
		static final String wfName = "wfName";

		static final String virtualNCode = "virt";
		static final String recId = "wfId";

		/**Column of node-instance-table-name<br>
		 * This table name is configurable in oz_workflow.instabl,
		 * for separating node instance table to improve performance
		 * - this table can be large.*/
		static final String instabl = "instabl";
		static final String bussTable = "bussTable";
		static final String bRecId = "bRecId";
		static final String bTaskState = "bStateRef";
		/** bussiness wf type, like e_inspect_tasks.taskType */
		static final String bussCateCol = "bussCateCol";
		static final String node1 = "Node1";
		static final String bNodeInstBackRefs = "backRefs";

		static final String nodeTabl = "oz_wfnodes";
		static final String nodeWfId = "wfId";
		static final String nid = "nodeId";
		static final String ncode = "nodeCode";
		static final String nname = "nodeName";
		static final String narriveCondit = "arrivCondit";
		static final String ncmdRigths = "cmdRights";
		static final String nonEvents = "onEvents";
		/**time out in minute */
		static final String noutTime = "timeouts";
		/**timeout route (nodeId) */
		static final String ntimeoutRoute = "timeoutRoute";
		static final String nsort = "sort";
		static final String nisFinish = "isFinish";
		
		//////// oz_wfcmds
		static final String cmdTabl = "oz_wfcmds";
		static final String cmdCmd = "cmd";
		static final String cmdRoute = "route";
		static final String cmdTxt = "txt";
		static final String cmdSort = "sort";

		/**Node instance table, e.g task_nodes.
		 * Node instance table name is configured in WfMeta.instabl, e.g. oz_workflow.instabl.
		 * */
		static class nodeInst {
			/**e.g. task_nodes.instId */
			static final String id = "instId";

			static final String nodeFk = "nodeId";

			/**wf type id FK to Wftabl.recId (nodeWfId) */
			// static final String wfIdFk = "";

			/**TODO modify tables<br>
			 * task-id fk to tasks */
			static final String busiFk = "taskId";

			static final String oper ="oper";
			static final String opertime ="opertime";

			static final String handleCmd = "handlingCmd";

			static final String descol = "descpt";

			static final String prevInst = "prevRec";
		}

		
	}

}