package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.common.Configs;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.WfDeftabl;
import io.odysz.sworkflow.EnginDesign.Wfrole;
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
	String bRefCol;
	/** e.g. task.taskType (FK to wf.wfId) */
	String bCateCol;
	/** e.g. f01 for falt workflow */
	String node1;
	
	/** e.g. e_inspect_tasks.startNode can be defined as "f01:startNode" (table name in bTabl field)<br>
	 * With this configuration, e_inspect_tasks.startNode always been set as a FK to c_process_processing.recId*/
	HashMap<String, String> backRefs;

	HashMap<String, CheapNode> nodes;
	
	/**Starting virtual node that not configured in DB*/
	private CheapNode virtualNode;

	public CheapWorkflow(String wfId, String wfName, String bTabl, String bRecId, String bRefCol,
			String bCateCol, String node1, String backRefs) throws SQLException, TransException {
		this.wfId = wfId;
		this.wfName = wfName;
		this.bTabl = bTabl;
		this.bRecId = bRecId;
		this.bRefCol = bRefCol;
		this.bCateCol = bCateCol;
		this.node1 = node1;
		this.backRefs = parseBackrefs(backRefs);

		// load configs
		// select w.*, group_concat(wr.roleId) from ir_wfdef w join ir_wfrole wr on w.nodeId = wr.nodeId where w.wfId = 'falt' group by w.nodeId;
//		String sql = String.format("select w.*, group_concat(wr.%1$s) %1$s from %2$s w left outer join %3$s wr on w.%4$s = wr.%5$s where w.%6$s = '%7$s' group by w.%4$s",
//				EnginDesign.Wfrole.roleId(), EnginDesign.WfDeftabl.tabl(), EnginDesign.Wfrole.tabl(), EnginDesign.WfDeftabl.nid(),
//				EnginDesign.Wfrole.nid(),    EnginDesign.WfDeftabl.wfId(), wfId);
//		SResultset rs = q.commit(sqls, usrInfo);

		SResultset rs = (SResultset) CheapEngin.transBuilder
				.select(WfDeftabl.tabl())
				.rs(CheapEngin.transBuilder.staticContext());
		rs.beforeFirst();

		nodes = new HashMap<String, CheapNode>(rs.getRowCount());
		while (rs.next()) {
			// CheapNode(CheapWorkflow wf, String nid, String ncode, String nname, String route, String onEvents)
			CheapNode n = new CheapNode(this,
					rs.getString(WfDeftabl.nid()),
					rs.getString(WfDeftabl.ncode()),
					rs.getString(WfDeftabl.nname()),
					rs.getString(WfDeftabl.cmdRoute()),
					rs.getString(WfDeftabl.onEvents()),
					rs.getInt(WfDeftabl.outTime(), 0),
					rs.getString(WfDeftabl.timeoutRoute()),
					rs.getString(Wfrole.roleId()));
			nodes.put(rs.getString(WfDeftabl.nid()), n);
		}
	}

	private HashMap<String, String> parseBackrefs(String backRefs) {
		if (backRefs != null && backRefs.trim().length() > 0) {
			String[] refss = backRefs.trim().split(",");
			HashMap<String, String> refMap = new HashMap<String, String>(refss.length);
			for (String ref : refss) {
				try {
					String[] rss = ref.split(":");
					refMap.put(rss[0].trim(), rss[1].trim());
				}
				catch (Exception ex) {
					System.err.println("WARN: - can't parse back refernce: " + ref);
					continue;
				}
			}
			return refMap;
		}
		return null;
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
				EnginDesign.Instabl.nodeFk(), EnginDesign.Instabl.tabl(), EnginDesign.Instabl.instId(), instId);
		SResultset rs = Connects.select(sql);
		if (rs.beforeFirst().next()) {
			String nodeId = rs.getString("nodeId");
			return nodes.get(nodeId);
		}
		return null;
	}

	public CheapNode start() throws SQLException {
		// design memo, handling virtual/new node arriving.
		if (virtualNode == null)
			virtualNode = createVirtualNode(this);
		return virtualNode;
	}

	/**Create a virtual node before starting node.
	 * @param wf
	 * @return
	 * @throws SQLException
	 */
	private static CheapNode createVirtualNode(CheapWorkflow wf) throws SQLException {
		// FIXME add method: getVirtualNode0()
		return new CheapNode(wf, wf.wfId + virtNodeSuffix, EnginDesign.Wftabl.virtualNCode(),
				"You can't see this", // node name
				String.format("next:%1$s:next,start:%1$s:start", wf.node1), // route: always to the beginning
				null,		// no arrive event for virtual - always already arrived
				0, null,	// no timeout
				wf.nodes.get(wf.node1).rolestr()); // virtual node has the same rights as the start node
	}

	public void checkRights(IUser usr, CheapNode currentNode, CheapNode nextNode) throws CheapException {
		if (usr instanceof CheapRobot)
			return;
		if (currentNode != null)
			if (currentNode.roles() == null || !currentNode.roles().contains(usr.get("wfRole")))
				throw new CheapException(String.format(Configs.getCfg("cheap-workflow", "t-no-rights"), usr.get("wfRole")));
	}
}
