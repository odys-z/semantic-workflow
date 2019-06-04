package io.odysz.sworkflow;

import java.sql.SQLException;

import io.odysz.semantics.x.SemanticException;

/**User of CheapEngine implement this to check any auto starting tasks
 * @author ody
 */
public interface ICheapChecker {

	/**Do the checking on the wfId - got when created.
	 * @param conn
	 * @return actually checked cases (workflow type)
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	int check(String conn) throws SemanticException, SQLException;

	/**The milliseconds interval for checking. CheapEngin use this to schedule the task.
	 * @return milliseconds interval
	 */
	long ms();

	/**get the workflow id*/
	String wfId(); // { return wfid; }

}
