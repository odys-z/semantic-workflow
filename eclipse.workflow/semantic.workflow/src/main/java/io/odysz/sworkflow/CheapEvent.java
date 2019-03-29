package io.odysz.sworkflow;

import io.odysz.semantics.ISemantext;
import io.odysz.sworkflow.EnginDesign.Req;

public class CheapEvent {
	public enum Evtype { arrive, start, step, close }

	@Override
	public String toString() {
		return String.format("{CheapEvent wf: %s,\ncurrentNodeId: %s, nextNode: %s, instId: %s, taskId: %s, req: %s, cmd: %s}",
				wfId, currentNode.nodeId(), nextNode.nodeId(), instId, taskId, req, cmd);
	}

	private String wfId;
	private Evtype etype;
	private CheapNode currentNode;
	private CheapNode nextNode;
	private String instId;
	private String taskId;
	private Req req;
	private String cmd;

	/**When this is creating by cheap engine, there is not node instance id.
	 * After sqls be committed, resolve it from semantext.
	 * @param nodeWfId
	 * @param evtype event type
	 * @param current
	 * @param next
	 * @param taskId
	 * @param instId
	 * @param rq
	 * @param cmd
	 */
	public CheapEvent(String wfId, Evtype evtype, CheapNode current,
			CheapNode next, String taskId, String instId, Req rq, String cmd) {
		this.wfId = wfId;
		this.etype = evtype;
		this.currentNode = current;
		this.nextNode = next;
		this.taskId = taskId;
		this.instId = instId;
		this.req = rq;
		this.cmd = cmd;
	}

	public String wfId() { return wfId; }

	public String currentNodeId() { return currentNode.nodeId(); }

	public String nextNodeId() { return nextNode.nodeId(); }

	public String instId() { return instId; }

	public String taskId() { return taskId; }

	public String arriveCondt() { return nextNode.arrivCondt(); }

	public Req req() { return req; }

	public String cmd() { return cmd; }

	public String evtype() { return etype.name(); }

	/**When starting a new task, it must committed when the task id can be retrieved.<br>
	 * Sometimes the event is figured out by engine on a new starting task.<br>
	 * This needing new task ID (generated by DB) been resolved for the event.
	 * @param newIds
	@SuppressWarnings("unchecked")
	public void resolveTaskId(SemanticObject newIds) {
		if (taskId == null || taskId.startsWith("AUTO"))
			taskId = taskId.replaceAll("^\\s*AUTO", ((ArrayList<String>) newIds.get("rows")).get(0));
	}
	 */

	/**Resulve taskId, instance Id.
	 * @param smtxt
	 * @return
	 */
	public CheapEvent resulve(ISemantext smtxt) {
		taskId = taskId == null ? null : (String) smtxt.resulvedVal(taskId);
		instId = instId == null ? null : (String) smtxt.resulvedVal(instId);
		return this;
	}

}
