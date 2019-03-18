package io.odysz.sworkflow;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import io.odysz.semantics.x.SemanticException;

class CheapJReqTest {

	@Test
	void test() throws SQLException, SemanticException {
		CheapJReq jreq = CheapJReq.start(CheapApiTest.wftype);
		CheapApi cheap = CheapJReq.parse(jreq);
		cheap.commit(CheapApiTest.testUser, CheapApiTest.stmtBuilder);

	}

}
