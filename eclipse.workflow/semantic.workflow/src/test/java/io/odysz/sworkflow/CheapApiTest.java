package io.odysz.sworkflow;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.junit.Test;
import org.xml.sax.SAXException;

import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

public class CheapApiTest {
	static final String wftype = "t01";

	static CheapTransBuild testTrans;

	static TestUser usr;
	static {
		Utils.printCaller(false);
		
		SemanticObject jo = new SemanticObject();
		jo.put("userId", "CheapApiTest");
		SemanticObject usrAct = new SemanticObject();
		usrAct.put("funcId", "cheap engine testing");
		usrAct.put("funcName", "test cheap engine");
		jo.put("usrAct", usrAct);

		try {
			initSqlite();
			CheapEngin.initCheap("src/test/res/workflow-meta.xml", null);
			testTrans = CheapEngin.trcs;
			usr = new TestUser("CheapApiTest", jo);
		} catch (SQLException | TransException | IOException | SAXException e) {
			e.printStackTrace();
		}
	}


	@Test
	public void testStart() throws SQLException, TransException {
		// TODO test support dels with semantics.xml
		// ArrayList<String[]> delConds = new ArrayList<String[]>();
		// delConds.add(new String[] {});

		// add some business details (not logic of workflow, but needing committed in same transaction)
		// also check fkIns, task_details, , taskId, tasks, taskId configurations
		ArrayList<String[]> inserts = new ArrayList<String[]>();
		inserts.add(new String[] {"remarks", "detail-1"});
		inserts.add(new String[] {"remarks", "detail-2"});
		inserts.add(new String[] {"remarks", "detail-3"});
		
		Update postups = null;
		SemanticObject res = CheapApi.start(wftype)
				.nodeDesc("node desc " + DateFormat.formatime(new Date()))
				.taskNv("remarks", "testing")
				.taskChildMulti("task_details", null, inserts)
				.postupdates(postups)
				.commit(usr, testTrans);
		// FIXME move commitment to engine
		// FIXME move commitment to engine
		Insert ins = (Insert) res.get("stmt");
		ArrayList<String> sqls = new ArrayList<String>();
		ins.ins(testTrans.instancontxt(usr));
		
		Utils.logi(sqls);

		// Utils.logi(res.get("stepEvt").toString());
		ICheapEventHandler eh = (ICheapEventHandler) res.get("stepHandler");
		if (eh != null)
			eh.onCmd((CheapEvent) res.get("evt"));
		else Utils.logi("No stepping event");

		eh = (ICheapEventHandler) res.get("arriHandler");
		if (eh != null)
			eh.onCmd((CheapEvent) res.get("evt"));
		else Utils.logi("No arriving event");
	}

	void testNext() {
		fail("Not yet implemented");
	}

	private static void initSqlite() throws SQLException {
		File file = new File("src/test/res");
		String path = file.getAbsolutePath();
		Connects.init(path);

		// testxt = new Transcxt(null);
		
		// initialize oz_autoseq - only for sqlite
		SResultset rs = Connects.select("SELECT type, name, tbl_name FROM sqlite_master where type = 'table' and tbl_name = 'oz_autoseq'",
				Connects.flag_nothing);
		if (rs.getRowCount() == 0) {
			try { 
				// create oz_autoseq
				ArrayList<String> sqls = new ArrayList<String>();
				sqls.add("CREATE TABLE oz_autoseq (\n" + 
					"  sid text(50),\n" + 
					"  seq INTEGER,\n" + 
					"  remarks text(200),\n" + 
					"  CONSTRAINT oz_autoseq_pk PRIMARY KEY (sid) )");

				sqls.add("CREATE TABLE a_logs (\n" +
					"  logId text(20),\n" + 
					"  funcId text(20),\n" + 
					"  funcName text(50),\n" + 
					"  oper text(20),\n" + 
					"  logTime text(20),\n" + 
					"  txt text(4000),\n" + 
					"  CONSTRAINT oz_logs_pk PRIMARY KEY (logId))");

				sqls.add("CREATE TABLE oz_workflow (\n" +
					 "nodeWfId varchar(50) NOT NULL,\n" +
					 "wfName varchar(50) NOT NULL,\n" +
					 "instabl varchar(20) NOT NULL, -- node instance's table name\n" +
					 "bussTable varchar(20) NOT NULL, -- e.g. task\n" +
					 "bRecId varchar(50) NOT NULL , -- e.g. task.taskId,\n" +
					 "bStateRef varchar(20) DEFAULT NULL , -- task.state (node instance id ref in business table),\n" +
					 "bussCateCol varchar(20) DEFAULT NULL , -- cate id in business table, e.g. task.tasktype.  The value is one of ir_workflow.wfId.,\n" +
					 "node1 varchar(50) NOT NULL , -- start node id in ir_wfdef,\n" +
					 "backRefs varchar(200) DEFAULT NULL , -- node instance back reference to business task record pk, format [node-id]:[business-col],\n" +
					 "sort int(11) DEFAULT NULL,\n" +
					 "PRIMARY KEY (nodeWfId) )"
					);

				sqls.add("CREATE TABLE oz_wfnodes (\n" +
					 "nodeWfId varchar(50) NOT NULL,\n" +
					 "nodeId varchar(50) NOT NULL,\n" +
					 "sort int default 1,\n" +
					 "nodeName varchar(20) DEFAULT NULL,\n" +
					 "nodeCode varchar(20) DEFAULT NULL,\n" +
					 "arrivCondit varchar(200) DEFAULT NULL, -- '[TODO] previous node list. If not null, all previous node handlered can reach here . EX: a01 AND (a02 OR a03)',\n" +
					 "cmdRights varchar(2000), -- rights view sql, args: $1s current id, $2s next id, $3s uid, $4s cmd (oz_wfnodes.cmd)\n" +
					 "ntimeoutRoute varchar(500) NULL, -- 'timeout-node-id:handled-text:(optional)event-handler(implement ICheapEventHandler)',\n" +
					 "timeouts int(11) DEFAULT NULL, -- 'timeout minutes',\n" +
					 "nonEvents varchar(200) DEFAULT NULL, -- the envent handler's class name\n" +
					 "PRIMARY KEY (nodeId) )"
					);

				sqls.add("CREATE TABLE oz_wfcmds (\n" +
					 "-- workflow commnads\n" +
					 "nodeId varchar(20) NOT NULL, -- fkIns: oz_wfnodes.nodeId\n" +
					 "cmd varchar(20) NOT NULL, -- command / req id\n" +
					 "txt varchar(50), -- readable command text\n" +
					 "route varchar(20) NOT NULL, -- route: next nodeId for cmd\n" +
					 "sort int default 0,\n" +
					 "PRIMARY KEY (cmd) )"
					);

				sqls.add("CREATE TABLE task_nodes (\n" +
					"-- work flow node instances, table name is configured in oz_workflow.instabl (separating table for performance)\n" +
					 "instId varchar(20) NOT NULL,\n" +
					 "nodeId varchar(20) NOT NULL, -- node FK\n" +
					 "oper varchar(20) NOT NULL,\n" +
					 "opertime DATETIME,\n" +
					 "descpt varchar(200),\n" + 
					 "remarks varchar(2000),\n" +
					 "handlingCmd varchar(10),\n" +
					 "prevRec varchar(20),\n" +
					 "PRIMARY KEY (instId) )"
					);

				sqls.add("CREATE TABLE tasks (\n" +
					"-- business task\n" +
					 "taskId varchar(20) NOT NULL,\n" +
					 "nodeWfId varchar(20) NOT NULL,\n" +
					 "wfState varchar(20) NOT NULL,\n" +
					 "oper varchar(20) NOT NULL,\n" +
					 "opertime DATETIME,\n" +
					 "remarks varchar(2000),\n" +
					 "startNode varchar(10),\n" +
					 "rquireAllStep varchar(20),\n" +
					 "PRIMARY KEY (taskId) )"
					);

				sqls.add("CREATE TABLE task_details (\n" +
					"-- business task details, update / insert details batch commit submitted by cheap engine.\n" +
					 "taskId varchar(20) NOT NULL,\n" +
					 "recId varchar(20) NOT NULL,\n" +
					 "remarks varchar(200),\n" +
					 "PRIMARY KEY (recId) )"
					);
				Connects.commit(usr, sqls, Connects.flag_nothing);
				sqls.clear();

				sqls.add("insert into oz_workflow (nodeWfId, wfName, instabl, bussTable, bRecId, bStateRef, bussCateCol, node1, backRefs, sort)\n" +
					"values ('t01', 'workflow 01', 'task_nodes', 'tasks', 'taskId', 'wfState', 'nodeWfId', 't01.01', 't01.03:requireAllStep', '0')");
				
				sqls.add("insert into oz_wfnodes( nodeWfId, nodeId, sort, nodeName, nodeCode,  \n" +
					"	arrivCondit, cmdRights, ntimeoutRoute, timeouts, nonEvents )\n" +
					"values\n" +
					"('t01', 't01.01', 10, 'starting', 't01.01',  \n" +
						"null, null, null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02A', 20, 'plan A', 't01.02A',\n" +
						"null, null, 't03:Time Out:', 15, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02B', 30, 'plan B', 't01.02B',\n" +
						"null, null, 't03:Time Out:', 25, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.03', 90, 'abort', 't01.03',\n" +
						"'t01.02 AND t01.02B', null, null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.04', 99, 'finished', 't01.04',\n" +
						"null, null, null, null, 'io.odysz.sworkflow.CheapHandler')\n");

				sqls.add("insert into oz_wfcmds (nodeId, cmd, txt, route, sort)\n" +
					"values\n" +
					"('t01.01',  't01.01.stepA', 'Go A(t01.02A)', 't01.02A', 0),\n" +
					"('t01.01',  't01.01.stepB', 'Go B(t01.02B)', 't01.02B', 1),\n" +
					"('t01.02A', 't01.02.go03',  'A To 03',       't01.03', 2),\n" +
					"('t01.02B', 't01.02B.go03', 'B To 03',       't01.03', 3),\n" +
					"('t01.03',  't01.03.go-end','03 Go End',     't01.04', 9)\n");

				sqls.add("insert into oz_autoseq (sid, seq, remarks) values\n" +
					"('a_logs.logId', 0, 'log'),\n" +
					"('task_nodes.instId', 0, 'node instances'),\n" +
					"('tasks.taskId', 0, 'tasks'),\n" +
					"('task_details.detailId', 0, 'task details')");

				Connects.commit(usr, sqls, Connects.flag_nothing);
			} catch (Exception e) {
				Utils.warn("Make sure table oz_autoseq already exists, and only for testing aginst a sqlite DB.");
			}
		}

	}
}
