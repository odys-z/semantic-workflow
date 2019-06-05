package io.odysz.sworkflow;

import io.odysz.common.Utils;

/**Don't just modify code here. <br>
 * Business code should be another class, e.g. com.ir.ifire.wf.EventHandler,
 * rather than modify handler here.<br>
 * This class should be clean for sample snippet.
 * @author odys-z@github.com
 */
public class CheapHandler implements ICheapEventHandler {
	
	@Override
	public void onTimeout(CheapEvent e) {
		Utils.logi("On cheap timeout\n\twfId: %s, nodeId: %s, node-instance: %s, task-id: %s, target-route: %s",
					e.wfId(), e.currentNodeId(), e.instId(), e.taskId(), e.nextNodeId());
	}

	@Override
	public void onCmd(CheapEvent e) {
		Utils.logi("On cheap command Event\n\twfId: %s, nodeId: %s, node-instance: %s, cmd: %s, task-id: %s, target-route: %s",
					e.wfId(), e.currentNodeId(), e.instId(), e.cmd(), e.taskId(), e.nextNodeId());
	}

	@Override
	public void onArrive(CheapEvent e) {
		Utils.logi("On cheap arriving Event\n\twfId: %s, nodeId: %s, node-instance: %s, prive nodes: %s, task-id: %s, target-route: %s",
					e.wfId(), e.currentNodeId(), e.instId(), e.arriveCondt(), e.taskId(), e.nextNodeId());
	}
}
