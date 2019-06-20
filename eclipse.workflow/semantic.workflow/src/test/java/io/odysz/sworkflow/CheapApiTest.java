package io.odysz.sworkflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.junit.Test;
import org.xml.sax.SAXException;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.sql.Statement;
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
	css varchar(200),
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

insert into oz_wfcmds (nodeId, cmd, rightFilter, txt, route, css, sort)
values
	('t01.01',  'start',        'a', 'start check',   '', 		 'started', 0),
	('t01.01',  't01.01.stepA', 'a', 'Go A(t01.02A)', 't01.02A', 'pass', 1),
	('t01.01',  't01.01.stepB', 'b', 'Go B(t01.02B)', 't01.02B', 'pass', 2),
	('t01.02A', 't01.02.go03',  'a', 'A To 03',       't01.03', 'pass', 1),
	('t01.02B', 't01.02B.go03', 'a', 'B To 03',       't01.03', 'deny', 2),
	('t01.03',  't01.03.go-end','a', '03 Go End',     't01.04', null, 9);
	
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

	static String conn = "local-sqlite";
	static LoggingUser usr;

	// private static HashMap<String, TableMeta> metas;

	static {
		Utils.printCaller(false);
		
		SemanticObject jo = new SemanticObject();
		jo.put("userId", "CheapApiTest");
		SemanticObject usrAct = new SemanticObject();
		usrAct.put("funcId", "cheap engine testing");
		usrAct.put("funcName", "test cheap engine");
		jo.put("usrAct", usrAct);

		try {
			initSqlite(conn);
			CheapEngin.initCheap("src/test/res/workflow-meta.xml", null);
			usr = new LoggingUser(conn,
					"src/test/res/semantic-log.xml", "CheapApiTest", jo);
		} catch (SQLException | TransException | IOException | SAXException e) {
			e.printStackTrace();
		}
	}

	private String newInstId;
	private String newTaskId;

	public void test_1_Start() throws SQLException, TransException {
		// case 1 start new task, without task record exists
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
		
		ArrayList<Statement<?>> postups = new ArrayList<Statement<?>>();
		postups.add(CheapEngin.trcs
					.insert("task_details")		// new node instance can not auto created in test-start.
					.nv("remarks", newInstId));	// semantics will handle recId (auto-key)

		SemanticObject res1 = CheapApi.start(wftype)
				.nodeDesc("desc: starting " + DateFormat.formatime(new Date()))
				.taskNv("remarks", "testing")
				// .taskChildMulti("task_details", null, inserts)
				.postupdates(postups)
				.commitReq(usr);

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res1.get("stepHandler");
		if (eh != null) {
			CheapEvent evt = (CheapEvent) res1.get("evt");
			eh.onCmd(evt);
			newInstId = (String) evt.instId();
			newTaskId = (String) evt.taskId();
		}
		else Utils.logi("No stepping event");

		eh = (ICheapEventHandler) res1.get("arriHandler");
		if (eh != null)
			eh.onArrive(((CheapEvent) res1.get("evt")));
		else Utils.logi("No arriving event handler");
		
		// case 2 start new task, with task record exists
		ISemantext tr2 = CheapEngin.trcs.instancontxt(usr);
		CheapWorkflow wf = CheapEngin.getWf(wftype);
		SemanticObject res2 = (SemanticObject) CheapEngin.trcs
				.insert(wf.bTabl, usr)
				.nv("remarks", "testing case 2")
				.nv(wf.bTaskStateRef, "null stub")
				.nv("wfId", wftype)
				.ins(tr2);
		String task2 = (String) tr2.resulvedVal(wf.bTabl, wf.bRecId);

		res2 = CheapApi.start(wftype, task2)
				.nodeDesc("desc: starting " + task2)
				.taskNv("remarks", "testing case 2")
				.commitReq(usr);
		
		res2 = CheapEngin.trcs.select(wf.instabl)
				.col(WfMeta.nodeInst.nodeFk, "nid")
				.where_("=", WfMeta.nodeInst.busiFk, task2)
				.rs(CheapEngin.trcs.basictx());
		
		SResultset rs = (SResultset) res2.rs(0);
		rs.beforeFirst().next();
		assertTrue(!LangExt.isblank(rs.getString("nid")));
	
		// case 3 trying start task2 should failed because tasks already started
		try {
			res2 = CheapApi.start(wftype, task2)
				.nodeDesc("desc: starting " + task2)
				.taskNv("remarks", "testing case 2")
				.commitReq(usr);
			fail("task2 started again");
		} catch (CheapException x) {
			Utils.warn("Got expecting mesage: %s", x.getMessage());
			assertEquals(CheapException.ERR_WF_COMPETATION, x.code());
		}
	}

	@Test
	public void test_2_load() throws SQLException, TransException {
		if (newTaskId == null)
			test_1_Start();
		
		CheapWorkflow wf = CheapEngin.getWf(wftype);

		SemanticObject res1 = CheapApi.loadFlow(wftype, newTaskId, usr);
		SResultset nodes = (SResultset) res1.rs(0);
		int nodestotal = res1.total(0);
		SResultset insts = (SResultset) res1.rs(1);

		nodes.beforeFirst().next();
		assertEquals(nodestotal, nodes.getRowCount());
		assertEquals(newTaskId, nodes.getString(wf.bRecId));

		insts.beforeFirst().next();
		assertEquals(1, insts.getRowCount());
		// only true for new started instance
		assertEquals(nodes.getString(WfMeta.nodeInst.id), insts.getString(WfMeta.nodeInst.id));
		
		// test load commands and rights
		String nid = nodes.getString(WfMeta.nodeInst.nodeFk);
		res1 = CheapApi.loadCmds(wftype, nid, newTaskId, usr.uid());
		SResultset rs = (SResultset) res1.rs(0);
		rs.beforeFirst().next();
		assertEquals(nid, rs.getString("nodeId"));
		assertEquals(rs.getRowCount(), res1.total(0));
	}

	@Test
	public void test_3_Next() throws SQLException, TransException {
		if (newTaskId == null)
			test_1_Start();

		// A post updating mocking the case that only business handlings knows the semantics.
		ArrayList<Statement<?>> postups = new ArrayList<Statement<?>>();
		postups.add(CheapEngin.trcs
					.update("task_details")		 // new node instance can not auto created in test-start.
					.nv("remarks", newInstId)
					.where_("=", "taskId", newTaskId)
					);

		SemanticObject res = CheapApi.next(wftype, newTaskId, "t01.01.stepA")
				.nodeDesc("desc: next " + DateFormat.formatime(new Date()))
				.postupdates(postups)
				.commitReq(usr);

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res.get("stepHandler");
		if (eh != null)
			eh.onCmd(((CheapEvent) res.get("evt")));
		else Utils.logi("No stepping event");

		// verify results of post update
		res = CheapEngin.trcs.select("task_details")
			.col("remarks")
			.where_("=", "taskId", newTaskId)
			.rs(CheapEngin.trcs.instancontxt(usr));
		SResultset rs = (SResultset) res.rs(0);
		rs.beforeFirst().next();
		assertEquals(newInstId, rs.getString("remarks"));

		// now step branch
		res = CheapApi.next(wftype, newTaskId, "t01.01.stepB")
			.commitReq(usr);
		assertWf(wftype, newTaskId, "t01.02B");

		// back
		res = CheapApi.next(wftype, newTaskId, "t01.02B.go01")
			.commitReq(usr);
		assertWf(wftype, newTaskId, "t01.01");

		// and loop
		res = CheapApi.next(wftype, newTaskId, "t01.01.stepB")
			.commitReq(usr);
		assertWf(wftype, newTaskId, "t01.02B");

		// arriving 
		res = CheapApi.next(wftype, newTaskId, "t01.02A.go03")
			.commitReq(usr);
		assertWf(wftype, newTaskId, "t01.03");

		// arrived 
		res = CheapApi.next(wftype, newTaskId, "t01.02B.go03")
			.commitReq(usr);
		assertWf(wftype, newTaskId, "t01.03");

		// logic working?
		eh = (ICheapEventHandler) res.get("arriHandler");
		if (eh != null)
			eh.onArrive(((CheapEvent) res.get("evt")));
		else Utils.warn("No arriving event handler");
	}

	/**Verify task's current state is the currentNode.
	 * @param wftype
	 * @param taskId
	 * @param crntNid current node id
	 * @throws SQLException 
	 * @throws TransException 
	 */
	public static void assertWf(String wftype, String taskId, String crntNid) throws SQLException, TransException {
		assertFalse(LangExt.isblank(crntNid));

		SemanticObject res1 = CheapApi.loadFlow(wftype, taskId, usr);
		SResultset nodes = (SResultset) res1.rs(0);
		SResultset insts = (SResultset) res1.rs(1);
		
		nodes.beforeFirst();
		boolean ok = false;
		while (nodes.next()) {
			String nid = nodes.getString("nodeId");
			if (nid == null)
				break;
			else if (crntNid.equals(nid)) {
				ok = true;
				break;
			}
		}
		if (!ok)
			fail("Node Id is not the current state's node.");

		assertEquals(1, insts.getRowCount());
		insts.beforeFirst().next();
		assertEquals(taskId, insts.getString(WfMeta.nodeInst.busiFk));
		assertEquals(crntNid, insts.getString(WfMeta.nodeInst.nodeFk));
	}

//	@Test
//	public void testCheckTimeout() throws SQLException, SAXException, TransException {
//		if (newTaskId == null)
//			test_3_Next();
//		// initSqlite(conn);
//
//		Dataset ds;
//		{
//			String[] sqls = new String[4];
//			sqls[DatasetCfg.ixSqlit] = "select (CAST(strftime('%s', CURRENT_TIMESTAMP) as integer) - CAST(strftime('%s', i.opertime) as integer) )/60 expMin, \n" + 
//					"		i.opertime, n.timeouts, n.timeoutRoute, n.wfId, i.nodeId nodeId, i.taskId taskId, i.instId\n" + 
//					"		from task_nodes i join oz_wfnodes n on i.nodeId = n.nodeId and n.timeouts > 0 and i.handlingCmd is null\n" + 
//					"		where CAST(strftime('%s', CURRENT_TIMESTAMP) as integer) - CAST(strftime('%s', i.opertime) as integer) > n.timeouts";
//
//			ds = new Dataset("t01", null, sqls, null);
//		}
//
//		CheapChecker chkr = new CheapChecker(conn, "t01", 2000, ds);
//		int c = chkr.checkTimeout();
//		Utils.logi(String.valueOf(c));
//	}

	static void initSqlite(String conn) throws SQLException, SemanticException {
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
					"  cnt INTEGER,\n" +
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
					 "checker varchar(50), -- background checker's finger print, used for competition check \n" +
					 "sort int(11) DEFAULT NULL,\n" +
					 "PRIMARY KEY (wfId) )"
					);

				sqls.add("CREATE TABLE oz_wfnodes (\n" +
					 "wfId varchar(50) NOT NULL,\n" +
					 "nodeId varchar(50) NOT NULL,\n" +
					 "sort int default 1,\n" +
					 "nodeName varchar(20) DEFAULT NULL,\n" +
					 "nodeCode varchar(20) DEFAULT NULL,\n" +
					 "isFinish varchar(2),\n" +
					 "arrivCondit varchar(200) DEFAULT NULL, -- '[TODO] previous node list. If not null, all previous node handlered can reach here . EX: a01 AND (a02 OR a03)',\n" +
					 "cmdRights varchar(20), -- rights view sql key, see engine-meta.xml/table=rights-ds\n" +
					 "timeoutRoute varchar(500) NULL, -- 'timeout-node-id:handled-text:(optional)event-handler(implement ICheapEventHandler)',\n" +
					 "timeouts int(11) DEFAULT NULL, -- 'timeout minutes',\n" +
					 "onEvents varchar(200) DEFAULT NULL, -- the envent handler's class name\n" +
					 "PRIMARY KEY (nodeId) )"
					);

				sqls.add("CREATE TABLE oz_wfcmds (\n" +
					 "-- workflow commnads\n" +
					 "nodeId varchar(20) NOT NULL, -- fkIns: oz_wfnodes.nodeId\n" +
					 "cmd varchar(20) NOT NULL, -- command / req id\n" +
					 "rightFilter varchar(20), -- flag lick read, update that can be used as command type when filtering rights\n" +
					 "txt varchar(50), -- readable command text\n" +
					 "route varchar(20) NOT NULL, -- route: next nodeId for cmd\n" +
					 "css varchar(200),\n" +
					 "sort int default 0,\n" +
					 "PRIMARY KEY (cmd) )"
					);

				sqls.add("CREATE TABLE task_nodes (\n" +
					"-- work flow node instances, table name is configured in oz_workflow.instabl (separating table for performance)\n" +
					 "instId varchar(20) NOT NULL,\n" +
					 "nodeId varchar(20) NOT NULL, -- node FK\n" +
					 "taskId varchar(20) NOT NULL, -- fk to tasks.taskId\n" +
					 "oper varchar(20) NOT NULL,\n" +
					 "opertime DATETIME NOT NULL,\n" + 
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
						"	cmdFilter varchar(20), -- only used by client for UI\n" +
						"	roleId varchar(20))");

				sqls.add("CREATE TABLE tasks (\n" +
					"-- business task\n" +
					 "taskId varchar(20) NOT NULL,\n" +
					 "wfId varchar(20) NOT NULL,\n" +
					 "wfState varchar(20) NOT NULL,\n" +
					 "oper varchar(20) NOT NULL,\n" +
					 "opertime DATETIME,\n" +
					 "remarks varchar(2000),\n" +
					 "startNode varchar(10),\n" +
					 "requireAllStep varchar(20),\n" +
					 "PRIMARY KEY (taskId) )"
					);

				sqls.add("CREATE TABLE task_details (\n" +
					"-- business task details, update / insert details batch commit submitted by cheap engine.\n" +
					 "taskId varchar(20) NOT NULL,\n" +
					 "recId varchar(20) NOT NULL,\n" +
					 "remarks varchar(200),\n" +
					 "PRIMARY KEY (recId) )"
					);
				
				sqls.add("CREATE TABLE a_user (\n" + 
						"  userId varchar(20) not null,\n" + 
						"  orgId varchar(20),\n" + 
						"  roleId varchar(20),\n" + 
						"  pswd varchar(50) NOT NULL,\n" + 
						"  encAuxiliary varchar(50),\n" + 
						"  userName varchar(50),\n" + 
						"  PRIMARY KEY (userId) )"
						);
				Connects.commit(usr, sqls, Connects.flag_nothing);
				sqls.clear();

				sqls.add("insert into oz_workflow (wfId, wfName, instabl, bussTable, bRecId, bStateRef, bussCateCol, node1, backRefs, sort)\n" +
					"values ('t01', 'workflow 01', 'task_nodes', 'tasks', 'taskId', 'wfState', 'wfId', 't01.01', 't01.01:startNode,t01.03:requireAllStep', '0')");
				
				sqls.add("insert into oz_wfnodes( wfId, nodeId, sort, nodeName, nodeCode,  \n" +
					"	arrivCondit, cmdRights, timeoutRoute, timeouts, onEvents )\n" +
					"values\n" +
					"('t01', 't01.01', 10, 'starting', 't01.01',  \n" +
						"null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02A', 20, 'plan A', 't01.02A',\n" +
						"null, 'ds-allcmd', 't01.03:Time Out:', 15, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.02B', 30, 'plan B', 't01.02B',\n" +
						"null, 'ds-allcmd', 't01.03:Time Out:', 25, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.03', 90, 'abort', 't01.03',\n" +
						"'t01.02 AND t01.02B', 'ds-v1', null, null, 'io.odysz.sworkflow.CheapHandler'),\n" +
					"('t01', 't01.04', 99, 'finished', 't01.04',\n" +
						"null, 'ds-allcmd', null, null, 'io.odysz.sworkflow.CheapHandler')\n");

				sqls.add("insert into oz_wfcmds (nodeId, cmd, rightFilter, txt, route, css, sort)\n" +
					"values\n" +
					"	('t01.01',  'start',        'a', 'start check',   '', 		'start', 0),\n" +
					"	('t01.01',  't01.01.stepA', 'a', 'Go A(t01.02A)', 't01.02A','pass', 1),\n" +
					"	('t01.01',  't01.01.stepB', 'b', 'Go B(t01.02B)', 't01.02B','deny', 2),\n" +
					"	('t01.02A', 't01.02A.go03', 'a', 'A To 03',       't01.03', 'pass', 1),\n" +
					"	('t01.02B', 't01.02B.go03', 'a', 'B To 03',       't01.03', 'pass', 2),\n" +
					"	('t01.02B', 't01.02B.go01', 'a', 'B To 01',       't01.01', 'deny', 0),\n" +
					"	('t01.03',  't01.03.go-end','a', '03 Go End',     't01.04', null,   9)\n");

				sqls.add("insert into task_rights (wfId, nodeId, userId, cmdFilter, roleId)\n" +
					"	values\n" +
					"	('t01', 't01.01', 'CheapApiTest', 'a', 'r01'),\n" +
					"	('t01', 't01.02A', 'CheapApiTest', 'a', 'r01'),\n" +
					"	('t01', 't01.02B', 'CheapApiTest', 'a', 'r01'),\n" +
					"	('t01', 't01.03', 'CheapApiTest', 'a', 'r01')\n");

				sqls.add("insert into oz_autoseq (sid, seq, remarks) values\n" +
					"('a_logs.logId', 0, 'log'),\n" +
					"('task_nodes.instId', 64, 'node instances'),\n" +	// 64: for readable difference
					"('tasks.taskId', 0, 'tasks'),\n" +
					"('task_details.recId', 128, 'task details')\n");	// 128: for readable difference

				sqls.add("update oz_wfnodes set isFinish = '1' where timeoutRoute is null and nodeId not in "
						+ "(select distinct nodeId from oz_wfcmds)");
				
				sqls.add("insert into a_user(userId, userName, roleId, pswd) values "
						+ "('admin', 'Administrator', 'r01', '123456'),\n"
						+ "('CheapApiTest', 'Cheap Tester', 'r01', '123456')");
				Connects.commit(usr, sqls, Connects.flag_nothing);
			} catch (Exception e) {
				Utils.warn("Make sure table oz_autoseq already exists, and only for testing aginst a sqlite DB.");
			}
		}
	}
	
}
