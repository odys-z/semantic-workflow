package io.odysz.sworkflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.x.TransException;

public class CheapRobot implements IUser {
	@Override
	public TableMeta meta() { return null; }

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return null;
	}

	@Override
	public boolean login(Object req) throws TransException {
		return false;
	}
	
	@Override
	public String sessionId() {
		return null;
	}

	@Override
	public void touch() { }

	@Override
	public String uid() {
		return "wf-robot";
	}

	@Override
	public SemanticObject logout() {
		return null;
	}

	@Override
	public void writeJsonRespValue(Object writer) throws IOException {}

	@Override
	public IUser logAct(String funcName, String funcId) {
		return this;
	}

	@Override
	public String sessionKey() {
		return null;
	}

	@Override
	public IUser sessionKey(String skey) {
		return null;
	}

	@Override
	public IUser notify(Object note) throws TransException {
		return null;
	}

	@Override
	public List<Object> notifies() { return null; }
}
