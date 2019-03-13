package io.odysz.sworkflow;

import java.io.IOException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;

public class CheapRobot implements IUser {

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return sqls;
	}

	@Override public boolean login(Object req) throws TransException { return false; }
	@Override public String sessionId() { return null; }
	@Override public void touch() { }

	@Override public String uid() { return "wf-robot"; }

	@Override public String get(String prop) { return prop; }

	@Override public IUser set(String prop, Object v) { return this; }
	@Override public SemanticObject logout() { return null; }
	@Override public void writeJsonRespValue(Object writer) throws IOException { }
}
