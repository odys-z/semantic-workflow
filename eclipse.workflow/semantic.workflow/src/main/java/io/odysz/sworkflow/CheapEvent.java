package io.odysz.sworkflow;

import java.sql.SQLException;

import io.odysz.semantics.ISemantext;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.x.TransException;

public class CheapEvent extends SemanticObject {
	public enum Evtype { arrive, start, step, close }

	@Override
	public String toString() {
		return String.format("{<io.odysz.semantics.ISemantext>\nCheapEvent wf: %s,\ncurrentNodeId: %s, nextNode: %s, instId: %s, taskId: %s, req: %s, cmd: %s}",
				wfId(), currentNodeId(), nextNodeId(), instId(), taskId(), req(), cmd());
	}
	
	public CheapEvent(SemanticObject jobj) throws SQLException, TransException {
		super.props = jobj.props();
		
		put("__etype", Evtype.valueOf((String)remove("__etype")));

		String curtnd = (String) remove("__currentNode");
		if (curtnd != null)
			put("__currentNode", new CheapNode(curtnd));

		String nextnd = (String) remove("__nextNode");
		if (nextnd != null)
			put("__nextNode", new CheapNode(nextnd));

	}
//	private String wfId;
//	private Evtype etype;
//	private CheapNode currentNode;
//	private CheapNode nextNode;
//	private String instId;
//	private String taskId;
//	private Req req;
//	private String cmd;

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
//		this.wfId = wfId;
//		this.etype = evtype;
//		this.currentNode = current;
//		this.nextNode = next;
//		this.taskId = taskId;
//		this.instId = instId;
//		this.req = rq;
//		this.cmd = cmd;

		put("__wfId", wfId);
		put("__etype", evtype);
		put("__currentNode", current);
		put("__nextNode", next);
		put("__taskId", taskId);
		put("__instId", instId);
		put("__req", rq);
		put("__cmd", cmd);
	}

	public String wfId() { return (String) get("__wfId"); }

	public String currentNodeId() { return ((CheapNode)get("__currentNode")).nodeId(); }

	public String nextNodeId() { return ((CheapNode)get("__nextNode")).nodeId(); }

	public String instId() { return (String) get("__instId"); }

	public String taskId() { return (String) get("__taskId"); }

	public String arriveCondt() { return ((CheapNode)get("__nextNode")).arrivCondt(); }

	public Req req() { return (Req) get("__req"); }

	public String cmd() { return (String) get("__cmd"); }

	public String evtype() { return ((Evtype)get("__etype")).name(); }

	/**Resulve taskId, instance Id, etc.
	 * @param smtxt
	 * @return
	 */
	public CheapEvent resulve(ISemantext smtxt) {
		String taskId = taskId();
		String instId = instId();
		if (taskId != null)
			put("__taskId", smtxt.resulvedVal(taskId));
		if (instId != null)
			put("__instId", smtxt.resulvedVal(instId));
		return this;
	}

//	public static CheapEvent fromJson(String json) {
//
//		CheapEvent evt = new CheapEvent(object, etype, currentNode, currentNode, object, object, req, object);
//		return evt;
//	}

}
