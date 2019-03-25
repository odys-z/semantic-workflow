package io.odysz.sworkflow;

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

	private String newInstId;
	private String newTaskId;

	@Test
	public void test_1_Start() throws SQLException, TransException {
		// add some business details (not logic of workflow, but needing committed in same transaction)
		// also check fkIns, task_details, , taskId, tasks, taskId configurations
		ArrayList<ArrayList<String[]>> inserts = new ArrayList<ArrayList<String[]>>();
		ArrayList<String[]> row = new ArrayList<String[]>();
		row.add(new String[] {"remarks", "detail-1"});
		inserts.add(row);

		row = new ArrayList<String[]>();
		row.add(new String[] {"remarks", "detail-2"});
		inserts.add(row);

		row = new ArrayList<String[]>();
		row.add(new String[] {"remarks", "detail-3"});
		inserts.add(row);
		
		Update postups = null;
		SemanticObject res = CheapApi.start(wftype)
				.nodeDesc("desc: starting " + DateFormat.formatime(new Date()))
				.taskNv("remarks", "testing")
				.taskChildMulti("task_details", null, inserts)
				.postupdates(postups)
				.commit(usr, testTrans);

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res.get("stepHandler");
		if (eh != null) {
			CheapEvent evt = (CheapEvent) res.get("evt");
			eh.onCmd(evt);
			newInstId = evt.instId();
			newTaskId = evt.taskId();
		}
		else Utils.logi("No stepping event");

		eh = (ICheapEventHandler) res.get("arriHandler");
		if (eh != null)
			eh.onArrive(((CheapEvent) res.get("evt")));
		else Utils.logi("No arriving event handler");
	}

	@Test
	public void test_2_Next() throws SQLException, TransException {
		test_1_Start();

		// A post updating mocking the case that only business handlings knows the semantics.
		Update postups = testTrans.update("task_details")
				.nv("remakrs", newInstId) // new node instance auto created in test-start.
				.where("=", "taskId", newTaskId);

		SemanticObject res = CheapApi.next(wftype, newTaskId, "t01.01.stepA")
				.nodeDesc("desc: next " + DateFormat.formatime(new Date()))
				.postupdates(postups)
				.commit(usr, testTrans);

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res.get("stepHandler");
		if (eh != null)
			eh.onCmd(((CheapEvent) res.get("evt")));
		else Utils.logi("No stepping event");

		eh = (ICheapEventHandler) res.get("arriHandler");
		if (eh != null)
			eh.onArrive(((CheapEvent) res.get("evt")));
		else Utils.logi("No arriving event handler");
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
					 "wfId varchar(50) NOT NULL,\n" +
					 "wfName varchar(50) NOT NULL,\n" +
					 "instabl varchar(20) NOT NULL, -- node instance's table name\n" +
					 "bussTable varchar(20) NOT NULL, -- e.g. task\n" +
					 "bRecId varchar(50) NOT NULL , -- e.g. task.taskId,\n" +
					 "bStateRef varchar(20) DEFAULT NULL , -- task.state (node instance id ref in business table),\n" +
					 "bussCateCol varchar(20) DEFAULT NULL , -- cate id in business table, e.g. task.tasktype.  The value is one of ir_workflow.wfId.,\n" +
					 "node1 varchar(50) NOT NULL , -- start node id in ir_wfdef,\n" +
					 "backRefs varchar(200) DEFAULT NULL , -- node instance back reference to business task record pk, format [node-id]:[business-col],\n" +
					 "sort int(11) DEFAULT NULL,\n" +
					 "PRIMARY KEY (wfId) )"
					);

				sqls.add("CREATE TABLE oz_wfnodes (\n" +
					 "wfId varchar(50) NOT NULL,\n" +
					 "nodeId varchar(50) NOT NULL,\n" +
					 "sort int default 1,\n" +
					 "nodeName varchar(20) DEFAULT NULL,\n" +
					 "nodeCode varchar(20) DEFAULT NULL,\n" +
					 "arrivCondit varchar(200) DEFAULT NULL, -- '[TODO] previous node list. If not null, all previous node handlered can reach here . EX: a01 AND (a02 OR a03)',\n" +
					 "cmdRights varchar(20), -- rights view sql key, see engine-meta.xml/table=rights-ds\n" +
					 "timeoutRoute varchar(500) NULL, -- 'timeout-node-id:handled-text:(optional)event-handler(implement ICheapEventHandler)',\n" +
					 "timeouts int(11) DEFAULT NULL, -- 'timeout minutes',\n" +
					 "nonEvents varchar(200) DEFAULT NULL, -- the envent handler's class name\n" +
					 "PRIMARY KEY (nodeId) )"
					);

				sqls.add("CREATE TABLE oz_wfcmds (\n" +
					 "-- workflow commnads\n" +
					 "nodeId varchar(20) NOT NULL, -- fkIns: oz_wfnodes.nodeId\n" +
					 "cmd varchar(20) NOT NULL, -- command / req id\n" +
					 "rightFilter varchar(20), -- flag lick read, update that can be used as command type when filtering rights\n" +
					 "txt varchar(50), -- readable command text\n" +
					 "route varchar(20) NOT NULL, -- route: next nodeId for cmd\n" +
					 "sort int default 0,\n" +
					 "PRIMARY KEY (cmd) )"
					);

				sqls.add("CREATE TABLE task_nodes (\n" +
					"-- work flow node instances, table name is configured in oz_workflow.instabl (separating table for performance)\n" +
					 "instId varchar(20) NOT NULL,\n" +
					 "nodeId varchar(20) NOT NULL, -- node FK\n" +
					 "taskId varchar(20) NOT NULL, -- fk to tasks.taskId\n" +
					 "oper varchar(20) NOT NULL,\n" +
					 "opertime DATETIME,\n" +
					 "descpt varchar(200),\n" + 
					 "remarks varchar(2000),\n" +
					 "handlingCmd varchar(10),\n" +
					 "prevRec varchar(20),\n" +
					 "PRIMARY KEY (instId) )"
					);

				sqls.add("CREATE TABLE task_rights (\n" +
						"-- user's workflow rights configuration.\n" + 
						"-- Engine use workflow-meta.xml/rights-ds/sql to find user's rights.\n" + 
						"	wfId varchar(20),\n" + 
						"	nodeId varchar(20) NOT NULL,\n" + 
						"	userId varchar(20) NOT NULL, -- Its more commonly using role id here. Using user id here for simplifying testing.\n" + 
						"	cmdFilter varchar(20))\n");

				sqls.add("CREATE TABLE tasks (\n" +
					"-- business task\n" +
					 "taskId varchar(20) NOT NULL,\n" +
					 "wfId varchar(20) NOT NULL,\n" +
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

				sqls.add("insert into oz_workflow (wfId, wfName, instabl, bussTable, bRecId, bStateRef, bussCateCol, node1, backRefs, sort)\n" +
					"values ('t01', 'workflow 01', 'task_nodes', 'tasks', 'taskId', 'wfState', 'wfId', 't01.01', 't01.01:startNode,t01.03:requireAllStep', '0')");
				
				sqls.add("insert into oz_wfnodes( wfId, nodeId, sort, nodeName, nodeCode,  \n" +
					"	arrivCondit, cmdRights, timeoutRoute, timeouts, nonEvents )\n" +
					"values\n" +
					"('t01', 't01.01', 10, 'starting', 't01.01',  \n" +
						"null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02A', 20, 'plan A', 't01.02A',\n" +
						"null, 'ds-allcmd', 't03:Time Out:', 15, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02B', 30, 'plan B', 't01.02B',\n" +
						"null, 'ds-allcmd', 't03:Time Out:', 25, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.03', 90, 'abort', 't01.03',\n" +
						"'t01.02 AND t01.02B', 'ds-v1', null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.04', 99, 'finished', 't01.04',\n" +
						"null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler')\n");

				sqls.add("insert into oz_wfcmds (nodeId, cmd, rightFilter, txt, route, sort)\n" +
					"values\n" +
					"	('t01.01',  'start',        'a', 'start check',   '', 0),\n" +
					"	('t01.01',  't01.01.stepA', 'a', 'Go A(t01.02A)', 't01.02A', 1),\n" +
					"	('t01.01',  't01.01.stepB', 'b', 'Go B(t01.02B)', 't01.02B', 2),\n" +
					"	('t01.02A', 't01.02.go03',  'a', 'A To 03',       't01.03', 1),\n" +
					"	('t01.02B', 't01.02B.go03', 'a', 'B To 03',       't01.03', 2),\n" +
					"	('t01.03',  't01.03.go-end','a', '03 Go End',     't01.04', 9)\n");

				sqls.add("insert into task_rights (wfId, nodeId, userId, cmdFilter)\n" +
					"	values\n" +
					"	('t01', 't01.01', 'CheapApiTest', 'a'),\n" +
					"	('t01', 't01.02A', 'CheapApiTest', 'a'),\n" +
					"	('t01', 't01.02B', 'CheapApiTest', 'a'),\n" +
					"	('t01', 't01.03', 'CheapApiTest', 'a')\n");

				sqls.add("insert into oz_autoseq (sid, seq, remarks) values\n" +
					"('a_logs.logId', 0, 'log'),\n" +
					"('task_nodes.instId', 64, 'node instances'),\n" +	// 64: for readable difference
					"('tasks.taskId', 0, 'tasks'),\n" +
					"('task_details.recId', 128, 'task details')\n");	// 128: for readable difference

				Connects.commit(usr, sqls, Connects.flag_nothing);
			} catch (Exception e) {
				Utils.warn("Make sure table oz_autoseq already exists, and only for testing aginst a sqlite DB.");
			}
		}

	}
}
