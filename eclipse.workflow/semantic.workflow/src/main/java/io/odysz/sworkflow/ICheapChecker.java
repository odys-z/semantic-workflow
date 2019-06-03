package io.odysz.sworkflow;

/**User of CheapEngine implement this to check any auto starting tasks
 * @author ody
 */
public interface ICheapChecker {

	void check(String conn);

	/**The milliseconds interval for checking. CheapEngin use this to schedule the task.
	 * @return milliseconds interval
	 */
	long ms();

}
