package io.odysz.sworkflow;

import io.odysz.anson.Anson;
import io.odysz.common.Utils;
import io.odysz.semantics.ISemantext;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.Resulving;

public class CheapEvent extends Anson {
	public enum Evtype { arrive, start, step, close, timeout }

	private String wfId;
	private Evtype evtype;
	private CheapNode current;
	private CheapNode next;
	private Object taskId;
	private Object instId;
	private String prevInstId;
	private Req req;
	private String cmd;
	private Query queryCompete;

	@Override
	public String toString() {
		return String.format("{<io.odysz.semantics.ISemantext>\nCheapEvent wf: %s,\n"
				+ "currentNodeId: %s, nextNode: %s, instId: %s, taskId: %s,\n"
				+ "req: %s, cmd: %s}",
					wfId(), currentNodeId(), nextNodeId(), instId(), taskId(),
					req(), cmd());
	}
	
//	public CheapEvent(AnsonResp dat) throws SQLException, TransException {
//		super.props = dat.props();
//		
//		put("__etype", Evtype.valueOf((String)remove("__etype")));
//
//		String curtnd = (String) remove("__currentNode");
//		if (curtnd != null)
//			put("__currentNode", new CheapNode(curtnd));
//
//		String nextnd = (String) remove("__nextNode");
//		if (nextnd != null)
//			put("__nextNode", new CheapNode(nextnd));
//
//	}

	/**When this is creating by cheap engine, there is not node instance id.
	 * After sqls be committed, resolve it from semantext.
	 * @param wfId
	 * @param evtype event type
	 * @param current
	 * @param next
	 * @param taskId a {@link Resulving} value to be resulved or a known task id value
	 * @param prevInstId
	 * @param newInstId a {@link Resulving} value to be resulved or a known instance id value
	 * @param rq
	 * @param cmd
	 */
	public CheapEvent(String wfId, Evtype evtype, CheapNode current,
			CheapNode next, Object taskId, String prevInstId, Resulving newInstId, Req rq, String cmd) {
//		put("__wfId", wfId);
//		put("__etype", evtype);
//		put("__currentNode", current);
//		put("__nextNode", next);
//		put("__taskId", taskId);
//		put("__instId", newInstId);
//		put("__prevInstId", prevInstId);
//		put("__req", rq);
//		put("__cmd", cmd);

		this.wfId = wfId;
		this.evtype = evtype;
		this.current = current;
		this.next = next;
		this.taskId = taskId;
		this.instId = newInstId;
		this.prevInstId = prevInstId;
		this.req = rq;
		this.cmd = cmd;
	}

	public String wfId() { return wfId; }

	public String currentNodeId() {
//		return has("__currentNode") ?
//				((CheapNode)get("__currentNode")).nodeId() : null;
		return this.current == null ? null
				: this.current.nodeId();
	}

	public String nextNodeId() { 
//		return has("__nextNode") ?
//				((CheapNode)get("__nextNode")).nodeId() : null;
		return this.next == null ?
				null : next.nodeId();
	}

	public String prevInstId() { 
//		return has("__prevInstId") ?
//				((CheapNode)get("__prevInstId")).nodeId() : null;
		return this.prevInstId;
	}

	public String instId() { 
		// Object v = get("__instId");
		if (instId instanceof Resulving) {
			Utils.warn("Instance Id must been resulved before being used, there must logic error in caller");
			return ((Resulving)instId).resulved(null);
		}
		else return (String)instId;
	}

	public String taskId() {
//		Object v = get("__taskId");
//		if (v instanceof Resulving) {
//			Utils.warn("Task Id must been resulved before being used, there must logic error in caller");
//			return ((Resulving)v).resulved(null);
//		}
//		else return (String)v;

		if (taskId instanceof Resulving) {
			Utils.warn("Task Id must been resulved before being used, there must logic error in caller");
			return ((Resulving)taskId).resulved(null);
		}
		else return (String)taskId;

	}

	public String arriveCondt() {
//		return has("__nextNode") ? ((CheapNode)get("__nextNode")).arrivCondt() : null;
		return next == null ? null : next.arrivCondt();
	}

	public CheapEvent qryCompetition(Query q) {
//		return (CheapEvent) put("__query_competate", q) ;
		this.queryCompete = q;
		return this;
	}

	public Query qryCompetition() {
//		return has("__query_competate") ?
//				(Query)get("__query_competate") : null;
		return queryCompete;
	}

	public Req req() { return req; }

	public String cmd() { return cmd; }

	public String evtype() {
//		return has("__etype") ? ((Evtype)get("__etype")).name()
//				: null;
		return evtype == null ? null : evtype.name();
	}

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
//		Object taskId = get("__taskId");
		if (taskId instanceof Resulving)
			// put("__taskId", ((Resulving)taskId).resulved(smtxt));
			taskId = ((Resulving) taskId).resulved(smtxt);

		// Object instId = get("__instId");
		if (instId instanceof Resulving)
			// put("__instId", ((Resulving)instId).resulved(smtxt));
			instId = ((Resulving) instId).resulved(smtxt);
		return this;
	}

}
