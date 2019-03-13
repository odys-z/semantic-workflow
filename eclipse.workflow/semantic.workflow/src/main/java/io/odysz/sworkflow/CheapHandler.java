package io.odysz.sworkflow;

/**Don't just modify code here. <br>
 * Business code should be another class, e.g. com.ir.ifire.wf.EventHandler,
 * rather than modify handler here.<br>
 * This class should be clean for sample snippet.
 * @author ody
 */
public class CheapHandler implements ICheapEventHandler {
	
	@Override
	public void onTimeout(CheapEvent evnt) {
		// if (CheapEngin.debug)
		// Don't comment this line to disable error message.
		// To disable this message, delete timeout event handler name in ir_wfdef.timeoutRoute.
		// User timeout event handler is optional.
		System.out.println(String.format(
					"This is a sample for timeout handler. wfId: %s, nodeId: %s, node-instance: %s, task-id: %s, target-route: %s",
					evnt.wfId(), evnt.currentNodeId(), evnt.instId(), evnt.taskId(), evnt.nextNodeId()));
		
		// FIXME move this lines to a business class, e.g. com.ir.ifre.wf.EventHandler, which is ifire business handler.
		// String wfId, String nodeId, String instId, String taskId, String routeId
	}
	@Override
	public void onNext(CheapEvent evnt) {
		
	}
	@Override
	public void onArrive(CheapEvent evnt) {}
}
