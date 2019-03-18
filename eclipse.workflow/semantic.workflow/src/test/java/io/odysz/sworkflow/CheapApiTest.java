package io.odysz.sworkflow;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DASemantics;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

class CheapApiTest {
	static final String wftype = "t01";
	static IUser testUser = new IUser() {
		@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return sqls; }
		@Override public boolean login(Object req) throws TransException { return false; }
		@Override public String sessionId() { return null; }
		@Override public void touch() { }
		@Override public String uid() { return "CheapApiTest"; }
		@Override public SemanticObject logout() { return null; }
		@Override public void writeJsonRespValue(Object writer) throws IOException { }
	};

	static Transcxt stmtBuilder;
	static {
		try {
			HashMap<String, DASemantics> cfgs = DATranscxt.initConfigs("loca-sqlite", "src/test/res/business.semantics");
			stmtBuilder = new DATranscxt(new DASemantext("local-sqlite", cfgs));
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	void testStart() throws SQLException, TransException {
		initSqlite();
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
				.commit(testUser, stmtBuilder);
		
		Update updt = (Update) res.get("stmt");
		ArrayList<String> sqls = new ArrayList<String>();
		updt.commit(sqls, testUser);
		
		Utils.logi(res.get("stepEvt").toString());
		Utils.logi(res.get("arriveEvt").toString());

		fail("Not yet implemented");
	}

	void testNext() {
	}

	private void initSqlite() {
	}
}
