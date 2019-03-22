package io.odysz.sworkflow;

import java.io.IOException;

import org.xml.sax.SAXException;

import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;

public class CheapTransBuild extends DATranscxt {
	@Override
	public Query select(String tabl, String... alias) {
		Query q = super.select(tabl, alias);
		q.doneOp(sqls -> Connects.select(sqls.get(0)));
		return q;
	}
	
	public Insert insert(String tabl, IUser usr) {
		Insert i = super.insert(tabl);
		i.doneOp(sqls -> Connects.commit(usr, sqls));
		return i;
	}
	
	public Update update(String tabl, IUser usr) {
		Update u = super.update(tabl);
		u.doneOp(sqls -> Connects.commit(usr, sqls));
		return u;
	}

	public CheapTransBuild(ISemantext semantext) {
		super(semantext);
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
