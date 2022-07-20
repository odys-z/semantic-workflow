package io.odysz.sworkflow;

import java.io.IOException;
import java.sql.SQLException;

import org.xml.sax.SAXException;

import io.odysz.module.rs.AnResultset;
import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

public class CheapTransBuild extends DATranscxt {
	@Override
	public Query select(String tabl, String... alias) {
		Query q = super.select(tabl, alias);
		q.doneOp((conn, sqls) -> {
			AnResultset rs = Connects.select(conn.connId(), sqls.get(0));
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

	/**Build transact builder, initialize semantics in xtabl.<br>
	 * This constructor can only been called after super class {@link DATranscxt} initialized with DB metas.
	 * @param connId
	 * @param xtabl
	 * @param debug 
	 * @throws SQLException 
	 * @throws SemanticException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	public CheapTransBuild(String connId, XMLTable xtabl, boolean debug)
			throws TransException, SQLException, SAXException, IOException {
		// super(connId, Connects.loadMeta(connId));
		super(connId); // metas must already loaded
		try {
			initConfigs(connId, xtabl, debug);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public ISemantext instancontxt(IUser usr) throws TransException {
		return super.instancontxt(Connects.uri2conn("/cheapflow"), usr);
	}

}
