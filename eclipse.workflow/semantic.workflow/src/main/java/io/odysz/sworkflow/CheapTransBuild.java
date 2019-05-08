package io.odysz.sworkflow;

import java.io.IOException;

import org.xml.sax.SAXException;

import io.odysz.module.rs.SResultset;
import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;

public class CheapTransBuild extends DATranscxt {
	@Override
	public Query select(String tabl, String... alias) {
		Query q = super.select(tabl, alias);
		q.doneOp((conn, sqls) -> {
			SResultset rs = Connects.select(conn.connId(), sqls.get(0));
			return new SemanticObject().rs(rs, rs.total());
		});
		return q;
	}
	
	public Insert insert(String tabl, IUser usr) {
		Insert i = super.insert(tabl);
		i.doneOp((conn, sqls) -> {
			int[] r = Connects.commit(conn.connId(), usr, sqls);
			return conn.resulves().put("inserted", r);
		});
		return i;
	}
	
	public Update update(String tabl, IUser usr) {
		Update u = super.update(tabl);
		u.doneOp((conn, sqls) -> {
			int[] r = Connects.commit(conn.connId(), usr, sqls);
			return conn.resulves().put("updated", r);
		});
		return u;
	}

	/**Build transact builder, initialize semantics in xtabl.
	 * @param connId
	 * @param xtabl
	 */
	public CheapTransBuild(String connId, XMLTable xtabl) {
		super(connId);
		try {
			initConfigs(connId, xtabl);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

}
