package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.common.LangExt;
import io.odysz.module.rs.SResultset;
import io.odysz.sworkflow.CheapNode.VirtualNode;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.x.TransException;

public class CheapWorkflow {
	String wfId;  
	String wfName;  
	/**business table name */
	String bTabl;
	/**business record id field */
	String bRecId;
	/** e.g. task status */
	String bTaskStateRef;
	/** e.g. task.taskType (FK to wf.wfId) */
	String bCateCol;
	/** e.g. f01 for falt workflow */
	String node1;
	
	/** Business task record can referring some special state,
	 * so there may be more column than current state
		e.g. task.node0 referring to a stating node instance.<br>
		This is the column names define, in format:<br>
		[node-id:task-col, ...]<br>
	 *  e.g. e_inspect_tasks.startNode can be defined as "f01:startNode" (table name in bTabl field)<br>
	 * With this configuration, e_inspect_tasks.startNode always been set as a FK to c_process_processing.recId*/
	HashMap<String, String> bNodeInstRefs;

	HashMap<String, CheapNode> nodes;
	
	/**Starting virtual node that not configured in DB*/
	private VirtualNode virtualNode;

	/** node instance table name, configured in oz_workflow.instabl */
	String instabl;

	private CheapNode startingNode;

	/**
	 * @param nodeWfId
	 * @param wfName
	 * @param nInstabl node instance table name, configured in oz_workflow.instabl
	 * @param bTabl
	 * @param bRecId
	 * @param bTaskState
	 * @param bCateCol
	 * @param node1
	 * @param bNodeInstRefs
	 * @throws SQLException
	 * @throws TransException
	 */
	public CheapWorkflow(String wfId, String wfName, String nInstabl, String bTabl, String bRecId, String bTaskState,
			String bCateCol, String node1, String bNodeInstRefs) throws SQLException, TransException {
		this.wfId = wfId;
		this.wfName = wfName;
		this.instabl = nInstabl;
		this.bTabl = bTabl;
		this.bRecId = bRecId;
		this.bTaskStateRef = bTaskState;
		this.bCateCol = bCateCol;
		this.node1 = node1;
		this.bNodeInstRefs = LangExt.parseMap(bNodeInstRefs);

		// load configs
		SResultset rs = (SResultset) CheapEngin.trcs
				.select(WfMeta.nodeTabl)
				.rs(CheapEngin.trcs.basictx());
		rs.beforeFirst();

		nodes = new HashMap<String, CheapNode>(rs.getRowCount());
		while (rs.next()) {
			String nid = rs.getString(WfMeta.nid);
			CheapNode n = new CheapNode(this,
					nid,
					rs.getString(WfMeta.ncode),
					rs.getString(WfMeta.nname),
					rs.getString(WfMeta.narriveCondit),
					rs.getInt(WfMeta.noutTime, 0),
					rs.getString(WfMeta.ntimeoutRoute),
					rs.getString(WfMeta.nonEvents),
					rs.getString(WfMeta.ncmdRigths));
			nodes.put(rs.getString(WfMeta.nid), n);
		}
		
		startingNode = nodes.get(node1);
	}

//	public SemanticObject getDef() {
//		SemanticObject bDef = new SemanticObject();
//		bDef.put(EnginDesign.WfProtocol.wfid, wfId);
//		bDef.put(EnginDesign.WfProtocol.wfName, wfName);
//		bDef.put(EnginDesign.WfProtocol.wfnode1, node1);
//		bDef.put(EnginDesign.WfProtocol.bTabl, bTabl);
//		return bDef;
//	}

	public CheapNode getNode(String nodeId) {
		return nodes == null ? null : nodes.get(nodeId);
	}

	/**
	 * @param trcs
	 * @param busiId
	 * @return 0: busiId(taskId), 1: instance-id (instabl.intId), 2: nodeId
	 * @throws TransException
	 * @throws SQLException
	 */
	public String[] getInstByTask(CheapTransBuild trcs, String busiId) throws TransException, SQLException {
		SResultset rs = (SResultset) trcs
				.select(bTabl, "b")
				// join task_nodes i on i.taskId = b.taskId and i.taskId = '000004'
				.j(instabl, "i",  String.format(
						"i.taskId = b.%s and i.taskId = '%s' and b.wfState = i.instId",
						bRecId, busiId))
				.col("i.instId", "instId")
				.col("b." + bRecId, "busiId")
				.col("i.nodeId", "nodeId")
				.rs(trcs.basictx());

		if (rs.beforeFirst().next())
			return new String[] {
					rs.getString("busiId"),
					rs.getString("instId"),
					rs.getString("nodeId")};
		return null;
	}

	/**Different workflow can have different nodes instance table.
	 * Use this to get correct instance table name.
	 * @return instance table name for this workflow template.
	 */
	String instabl() { return instabl; }

	public VirtualNode start() throws SQLException, TransException {
		// design memo, handling virtual/new node arriving.
		if (virtualNode == null)
			virtualNode = new VirtualNode(this, startingNode);
		return virtualNode;
	}

	/**Get configured text string in workflow-meta.xml/table='txt'
	 * @param key
	 * @return configured txt
	 */
	String txt(String key) {
		return key + " zh: %s";
	}

}
