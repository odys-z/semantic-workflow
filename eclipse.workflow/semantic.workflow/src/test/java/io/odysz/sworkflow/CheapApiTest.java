package io.odysz.sworkflow;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

class CheapApiTest {
	static final String wftype = "test-wf01";
	static IUser testUser = new IUser() {
		@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return sqls; }
		@Override public boolean login(Object req) throws TransException { return false; }
		@Override public String sessionId() { return null; }
		@Override public void touch() { }
		@Override public String uid() { return "CheapApiTest"; }
		@Override public SemanticObject logout() { return null; }
		@Override public void writeJsonRespValue(Object writer) throws IOException { }
	};

	static Transcxt stmtBuilder = new DATranscxt(new DASemantext("local-sqlite", null));
	
	@Test
	void testStart() throws SQLException, TransException {
		initSqlite();
		ArrayList<String[]> delConds = new ArrayList<String[]>();
		delConds.add(new String[] {});

		ArrayList<String[]> inserts = new ArrayList<String[]>();
		Update postups = null;
		SemanticObject res = CheapApi.start(wftype)
				.nodeDesc("node desc " + DateFormat.formatime(new Date()))
				.taskNv("reason", "testing")
				.taskNv("oper", "tester")
				.taskChildMulti("taskDetails", delConds, inserts)
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
