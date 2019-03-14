package io.odysz.sworkflow;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class CheapJReqTest {

	@Test
	void test() throws CheapException, SQLException {
		CheapJReq jreq = CheapJReq.start(CheapApiTest.wftype);
		CheapApi cheap = CheapJReq.parse(jreq);
		cheap.commit(CheapApiTest.testUser);

		fail("Not yet implemented");
	}

}
