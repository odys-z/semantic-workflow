package io.odysz.sworkflow;

import java.sql.SQLException;

import org.junit.Test;
import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.Dataset;
import io.odysz.transact.x.TransException;

/**Test {@link CheapChecker#checkTimeout()}
 * @author odys-z@github.com
 */
public class CheapCheckerTest {
	@Test
	public void testCheckTimeout() throws SQLException, SAXException, TransException {
		// This also trigger CheapApiTest.initSqlite()
		CheapApiTest api = new CheapApiTest();
		api.test_3_Next();

		Dataset ds;
		{
			String[] sqls = new String[4];
			sqls[DatasetCfg.ixSqlit] = "select (CAST(strftime('%s', CURRENT_TIMESTAMP) as integer) - CAST(strftime('%s', i.opertime) as integer) )/60 expMin, \n" + 
					"		i.opertime, n.timeouts, n.timeoutRoute, n.wfId, i.nodeId nodeId, i.taskId taskId, i.instId\n" + 
					"		from task_nodes i join oz_wfnodes n on i.nodeId = n.nodeId and n.timeouts > 0 and i.handlingCmd is null\n" + 
					"		where CAST(strftime('%s', CURRENT_TIMESTAMP) as integer) - CAST(strftime('%s', i.opertime) as integer) > n.timeouts";

			ds = new Dataset("t01", null, sqls, null);
		}

		CheapChecker chkr = new CheapChecker(CheapApiTest.conn, "t01", 2000, ds);
		int c = chkr.checkTimeout();
		Utils.logi(String.valueOf(c));
	}
}


