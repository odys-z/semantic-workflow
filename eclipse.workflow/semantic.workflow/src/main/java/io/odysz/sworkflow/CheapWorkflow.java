package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.common.LangExt;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.CheapNode.VirtualNode;
import io.odysz.sworkflow.EnginDesign.WfMeta;
import io.odysz.transact.x.TransException;

public class CheapWorkflow {
	public static final String virtNodeSuffix = "-virt01";
	
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
	private String instabl;

	private CheapNode startingNode;

	/**
	 * @param wfId
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
				.rs(CheapEngin.trcs.basiContext());
		rs.beforeFirst();

		nodes = new HashMap<String, CheapNode>(rs.getRowCount());
		while (rs.next()) {
			String nid = rs.getString(WfMeta.nid);
			CheapNode n = new CheapNode(this,
					nid,
					rs.getString(WfMeta.ncode),
					rs.getString(WfMeta.nname),
					rs.getString(WfMeta.arriveCondit),
					rs.getInt(WfMeta.outTime, 0),
					rs.getString(WfMeta.timeoutRoute),
					rs.getString(WfMeta.onEvents),
					rs.getString(WfMeta.nodeRigths));
			nodes.put(rs.getString(WfMeta.nid), n);
		}
		
		startingNode = nodes.get(node1);
	}

	public SemanticObject getDef() {
		SemanticObject bDef = new SemanticObject();
		bDef.put(EnginDesign.WfProtocol.wfid, wfId);
		bDef.put(EnginDesign.WfProtocol.wfName, wfName);
		bDef.put(EnginDesign.WfProtocol.wfnode1, node1);
		bDef.put(EnginDesign.WfProtocol.bTabl, bTabl);
		return bDef;
	}

	public CheapNode getNode(String nodeId) {
		return nodes == null ? null : nodes.get(nodeId);
	}

	public CheapNode getNodeByInst(String instId) throws SQLException {
		String sql = String.format("select %s nodeId from %s i where i.%s = '%s'",
				EnginDesign.Instabl.nodeFk, instabl(), EnginDesign.Instabl.instId, instId);
		SResultset rs = Connects.select(sql);
		if (rs.beforeFirst().next()) {
			String nodeId = rs.getString("nodeId");
			return nodes.get(nodeId);
		}
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

	/**Check user rights for req.
	 * @param usr
	 * @param currentNode
	 * @param nextNode
	 * @param req
	 * @throws CheapException no such right to commit a requested command
	 * @throws SQLException Database accessing failed
	 */
	public void checkRights(IUser usr, CheapNode currentNode, CheapNode nextNode, String cmd) throws CheapException, SQLException {
		if (usr instanceof CheapRobot)
			return;
		if (currentNode != null)
			if (!currentNode.rights(nextNode, cmd, usr).contains(cmd))
				throw new CheapException(txt("t-no-rights"), usr.uid());
	}

	/**Get configured text string in workflow-meta.xml/table='txt'
	 * @param key
	 * @return configured txt
	 */
	private String txt(String key) {
		return key + " zh: %s";
	}

}
