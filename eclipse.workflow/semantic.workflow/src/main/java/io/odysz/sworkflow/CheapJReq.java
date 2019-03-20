package io.odysz.sworkflow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.EnginDesign.WfProtocol;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.x.TransException;

/**A bridge between JMessage/JBody and cheap engine API.
 * @author odys-z@github.com
 */
public class CheapJReq extends SemanticObject {
	Req req;
	public CheapJReq(Req cmd) {
		this.req = cmd;
	}
	
	public CheapJReq postUpdts(CheapJReq posts) throws TransException {
		add("posts", posts);
		return this;
	}
	
	public CheapJReq delTaskDetails(String tabl, List<String[]> nvs) {
		put("delDetailTabl", tabl);
		put("delDetails", nvs);
		return this;
	}
	
	public CheapJReq insertDetails(String tabl, List<String[]> nvs) {
		put("insDetailTabl", tabl);
		put("insDetails", nvs);
		return this;
	}

	/**Create a new message that starting a workflow, usually for test and jclient.java.
	 * @param wftype
	 */
	public static CheapJReq start(String wftype) {
		return new CheapJReq(Req.start);
	}
	
	/**Create a new api instance that handling a workflow request.
	 * @param jreq
	 * @return
	 */
	public static CheapApi parse(SemanticObject jbody) {
		return null;
	}

	/**Format multi-details request into SemanticObject.
	 * @param st
	 * @param tabl
	 * @param delConds
	 * @param inserts
	 * @return formated SemanticObject
	 */
	public static SemanticObject formatMulti(Transcxt st, String tabl,
			ArrayList<String[]> delConds, ArrayList<String[]> inserts) {
		SemanticObject jmultis = new SemanticObject();
		// del
		if (delConds != null) {
			Delete jdel = st.delete(tabl);
			for (String[] cond : delConds) {
				jdel.where(cond[0], cond[1], cond[2]);
			}
			jmultis.put("del", jdel);
		}
		
		// insert
		if (inserts != null) {
			Insert jinss = st.insert(tabl);
			for (String[] nv : inserts) {
				jinss.nv(nv[0], nv[1]);
			}
			jmultis.put("insert", jinss);
		}

		return jmultis;
	}
	
	public static SemanticObject formatAllRoutes(IUser usr, HashMap<Req, String[]> routes) throws SQLException {
		//wf.checkRights(usr, this, null); -- TODO move back to engine
		SemanticObject r = convert(routes);
		return r.code(WfProtocol.ok);
	}

	/**Redundant?
	 * @param kvs
	 * @return SemanticObject with props of map[req-name, k-v]
	 * @throws SQLException
	 */
	public static SemanticObject convert(HashMap<Req, String[]> kvs) throws SQLException {
		HashMap<Req, String[]> routes;
		if (kvs != null) {
			routes = new HashMap<Req, String[]>(kvs.size());
			for (Req k : kvs.keySet()) {
				String[] kv = kvs.get(k);
				if (kv == null)
					continue;
				routes.put(k, kv);
			}
		} else
			routes = new HashMap<Req, String[]>(0);

		return new SemanticObject().put(WfProtocol.routes, routes);
	}


}
