package io.odysz.sworkflow;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.junit.Test;
import org.xml.sax.SAXException;

import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

/**This class use sqlite for test.
 * To initialize mysql tables, use:<pre>
CREATE TABLE oz_workflow (
	wfId varchar(50) NOT NULL,
	wfName varchar(50) NOT NULL,
	instabl varchar(20) comment 'node instance''s table name' NOT NULL, 
	bussTable varchar(20) comment 'e.g. task' NOT NULL, 
	bRecId varchar(50) comment 'e.g. task.taskId' NOT NULL , 
	bStateRef varchar(20) comment ' task.state (node instance id ref in business table)' DEFAULT NULL , 
	bussCateCol varchar(20) comment 'cate id in business table, e.g. task.tasktype.  The value is one of ir_workflow.wfId.' DEFAULT NULL ,
	node1 varchar(50) comment 'start node id in ir_wfdef' NOT NULL ,
	backRefs varchar(200) comment 'node instance back reference to business task record pk, format [node-id]:[business-col]' DEFAULT NULL ,
	sort int(11) DEFAULT NULL,
	PRIMARY KEY (wfId) 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 collate utf8mb4_bin;

CREATE TABLE oz_wfnodes (
	wfId varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
	nodeId varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
	sort int default 1,
	nodeName varchar(20) DEFAULT NULL,
	nodeCode varchar(20) DEFAULT NULL,
	arrivCondit varchar(200) comment '[TODO] previous node list. If not null, all previous node handlered can reach here . EX: a01 AND (a02 OR a03)' DEFAULT NULL, 
	cmdRights varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin comment 'rights view sql key, see engine-meta.xml/table=rights-ds',
	timeoutRoute varchar(500) comment 'timeout-node-id:handled-text:(optional)event-handler(implement ICheapEventHandler)' NULL,
	timeouts int(11) comment 'timeout minutes' DEFAULT NULL,
	nonEvents varchar(200) comment 'the envent handler''s class name' DEFAULT NULL, 
	PRIMARY KEY (nodeId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oz_wfcmds (
	nodeId varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin comment 'fkIns: oz_wfnodes.nodeId'  NOT NULL ,
	cmd varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin comment 'command / req id' NOT NULL ,
	rightFilter varchar(20) comment 'flag lick read, update that can be used as command type when filtering rights',
	txt varchar(50) comment 'readable command text',
	route varchar(20) comment 'route: next nodeId for cmd' NOT NULL ,
	sort int default 0,
	PRIMARY KEY (cmd) 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
comment 'workflow commnads';

CREATE TABLE oz_wfrights (
	wfId varchar(20),
	nodeId varchar(20),
	roleId varchar(20),
	cmdFilter varchar(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_bin
comment 'user''s workflow rights configuration.
Engine use workflow-meta.xml/rights-ds/sql to find user''s rights.';

CREATE TABLE oz_autoseqs (
  sid varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  seq int(11) NOT NULL,
  remarks varchar(50) DEFAULT NULL,
  PRIMARY KEY (sid) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE r_expense (
  expenseId varchar(45) COLLATE utf8mb4_bin NOT NULL,
  expenseType varchar(1) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'daily expense',
  projectId varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  expenseDate date DEFAULT NULL,
  expenseUser varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  totalAccount float DEFAULT NULL,
  billscount int(11) DEFAULT NULL,
  orgId varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  currentNode varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  startNode varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  addUser varchar(50) COLLATE utf8mb4_bin NOT NULL,
  addDate date DEFAULT NULL,
  attachment varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (expenseId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

insert into oz_workflow (wfId, wfName, instabl, bussTable, bRecId, bStateRef,
	bussCateCol, node1, backRefs, sort)
values 
	('t01', 'expense x 1', 'oz_ninsts', 'r_expens', 'expenseId', 'currentNode',
	 'wfId', 't01.01', 't01.01:startNode,t01.03:requireAllStep', '0');

insert into oz_wfnodes( wfId, nodeId, sort, nodeName, nodeCode,  
	arrivCondit, cmdRights, timeoutRoute, timeouts, nonEvents )
values
	('t01', 't01.01', 10, 'starting', 't01.01',  
	null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler'),
	('t01', 't01.02A', 20, 'plan A', 't01.02A',
	null, 'ds-allcmd', 't03:Time Out:', 15, 'io.odysz.sworkflow.CheapHandler'),
	('t01', 't01.02B', 30, 'plan B', 't01.02B',
	null, 'ds-allcmd', 't03:Time Out:', 25, 'io.odysz.sworkflow.CheapHandler'),
	('t01', 't01.03', 90, 'abort', 't01.03',
	't01.02 AND t01.02B', 'ds-v1', null, null, 'io.odysz.sworkflow.CheapHandler'),
	('t01', 't01.04', 99, 'finished', 't01.04',
	null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler');

insert into oz_wfcmds (nodeId, cmd, rightFilter, txt, route, sort)
values
	('t01.01',  'start',        'a', 'start check',   '', 0),
	('t01.01',  't01.01.stepA', 'a', 'Go A(t01.02A)', 't01.02A', 1),
	('t01.01',  't01.01.stepB', 'b', 'Go B(t01.02B)', 't01.02B', 2),
	('t01.02A', 't01.02.go03',  'a', 'A To 03',       't01.03', 1),
	('t01.02B', 't01.02B.go03', 'a', 'B To 03',       't01.03', 2),
	('t01.03',  't01.03.go-end','a', '03 Go End',     't01.04', 9);
	
insert into oz_wfrights (wfId, nodeId, roleId, cmdFilter)
	values
	('t01', 't01.01', 'CheapApiTest', 'a'),
	('t01', 't01.02A', 'CheapApiTest', 'a'),
	('t01', 't01.02B', 'CheapApiTest', 'a'),
	('t01', 't01.03', 'CheapApiTest', 'a');
	
insert into oz_autoseqs (sid, seq, remarks) values
('a_logs.logId', 0, 'log'),
('oz_ninsts.instId', 64, 'node instances'),
('r_expense.expenseId', 0, 'tasks expense');</pre>
 * @author odys-z@github.com
 */
public class CheapApiTest {
	static final String wftype = "t01";

	static LoggingUser usr;

	private static HashMap<String, TableMeta> metas;

	static {
		Utils.printCaller(false);
		
		SemanticObject jo = new SemanticObject();
		jo.put("userId", "CheapApiTest");
		SemanticObject usrAct = new SemanticObject();
		usrAct.put("funcId", "cheap engine testing");
		usrAct.put("funcName", "test cheap engine");
		jo.put("usrAct", usrAct);

		try {
			String conn = "local-sqlite";
			initSqlite(conn);
			metas = Connects.loadMeta(conn);
			CheapEngin.initCheap("src/test/res/workflow-meta.xml", metas, null);
			usr = new LoggingUser(conn, "src/test/res/semantic-log.xml", "CheapApiTest", jo);
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
		ArrayList<ArrayList<?>> inserts = new ArrayList<ArrayList<?>>();
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
				.commit(usr);

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res.get("stepHandler");
		if (eh != null) {
			CheapEvent evt = (CheapEvent) res.get("evt");
			eh.onCmd(evt);
			newInstId = (String) evt.instId();
			newTaskId = (String) evt.taskId();
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
		Update postups = CheapEngin.trcs.update("task_details")
				.nv("remakrs", newInstId) // new node instance auto created in test-start.
				.where("=", "taskId", newTaskId);

		SemanticObject res = CheapApi.next(wftype, newTaskId, "t01.01.stepA")
				.nodeDesc("desc: next " + DateFormat.formatime(new Date()))
				.postupdates(postups)
				.commit(usr);

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

	private static void initSqlite(String conn) throws SQLException, SemanticException {
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
					"  logId text(20) NOT NULL,\n" + 
					"  funcId text(20),\n" + 
					"  funcName text(50),\n" + 
					"  oper text(20),\n" + 
					"  logTime text(20),\n" + 
					"  sqlCount INTEGER,\n" +
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
