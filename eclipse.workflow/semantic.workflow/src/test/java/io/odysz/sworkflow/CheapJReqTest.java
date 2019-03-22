package io.odysz.sworkflow;

import java.sql.SQLException;

import org.junit.Test;

import io.odysz.transact.x.TransException;

class CheapJReqTest {

	@Test
	void test() throws SQLException, TransException {
		CheapJReq jreq = CheapJReq.start(CheapApiTest.wftype);
		CheapApi cheap = CheapJReq.parse(jreq);
		cheap.commit(CheapApiTest.usr, CheapApiTest.testTrans);

	}

}
