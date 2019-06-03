package io.odysz.sworkflow;

import java.sql.SQLException;

import io.odysz.common.Utils;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.x.TransException;

public class CheapEvent extends SemanticObject {
	public enum Evtype { arrive, start, step, close, timeout }

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

	/**When this is creating by cheap engine, there is not node instance id.
	 * After sqls be committed, resolve it from semantext.
	 * @param wfId
	 * @param evtype event type
	 * @param current
	 * @param next
	 * @param taskId a {@link Resulving} value to be resulved or a known task id value
	 * @param newInstId a {@link Resulving} value to be resulved or a known instance id value
	 * @param rq
	 * @param cmd
	 */
	public CheapEvent(String wfId, Evtype evtype, CheapNode current,
			CheapNode next, Object taskId, Object newInstId, Req rq, String cmd) {
		put("__wfId", wfId);
		put("__etype", evtype);
		put("__currentNode", current);
		put("__nextNode", next);
		put("__taskId", taskId);
		put("__instId", newInstId);
		put("__req", rq);
		put("__cmd", cmd);
	}

	public String wfId() { return (String) get("__wfId"); }

	public String currentNodeId() { return ((CheapNode)get("__currentNode")).nodeId(); }

	public String nextNodeId() { return ((CheapNode)get("__nextNode")).nodeId(); }

	public String instId() { 
		Object v = get("__instId");
		if (v instanceof Resulving) {
			Utils.warn("Instance Id must been resulved before being used, there must logic error in caller");
			return ((Resulving)v).resulved(null);
		}
		else return (String)v;
	}

	public String taskId() {
		Object v = get("__taskId");
		if (v instanceof Resulving) {
			Utils.warn("Task Id must been resulved before being used, there must logic error in caller");
			return ((Resulving)v).resulved(null);
		}
		else return (String)v;
	}

	public String arriveCondt() { return ((CheapNode)get("__nextNode")).arrivCondt(); }

	public Req req() { return (Req) get("__req"); }

	public String cmd() { return (String) get("__cmd"); }

	public String evtype() { return ((Evtype)get("__etype")).name(); }

	/**Resulve taskId, instance Id, etc.
	 * @param smtxt
	 * @return this
	public CheapEvent resulve(ISemantext smtxt) {
		String taskId = taskId();
		String instId = instId();
		if (taskId != null)
			put("__taskId", smtxt.resulvedVal(taskId));
		if (instId != null)
			put("__instId", smtxt.resulvedVal(instId));
		return this;
	}
	 */

	/**resulve value
	 * @param smtxt
	 * @return this
	 */
	public CheapEvent resulve(ISemantext smtxt) {
		Object taskId = get("__taskId");
		if (taskId instanceof Resulving)
			put("__taskId", ((Resulving)taskId).resulved(smtxt));

		Object instId = get("__instId");
		if (instId instanceof Resulving)
			put("__instId", ((Resulving)instId).resulved(smtxt));
		return this;
	}

}
