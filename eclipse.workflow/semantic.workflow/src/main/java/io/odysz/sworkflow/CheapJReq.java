package io.odysz.sworkflow;

import java.util.List;

import io.odysz.semantics.SemanticObject;
import io.odysz.sworkflow.EnginDesign.Req;
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
}
