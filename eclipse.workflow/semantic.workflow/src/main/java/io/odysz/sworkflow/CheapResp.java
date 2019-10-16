package io.odysz.sworkflow;

import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.module.rs.AnResultset;
import io.odysz.transact.sql.Statement;

public class CheapResp extends Anson {

	@AnsonField(ignoreTo=true)
	Statement<?> statment;

	CheapEvent event;



	public CheapResp statment(Statement<?> stmt) {
		statment = stmt;
		return this;
	}

	public CheapResp event(CheapEvent evt) {
		event = evt;
		return this;
	}


	ICheapEventHandler stepHandler;
	public CheapResp stepHandler(ICheapEventHandler handler) {
		stepHandler = handler;
		return this;
	}

	public ICheapEventHandler rmStepHandler() {
		ICheapEventHandler h = stepHandler;
		stepHandler = null;
		return h;
	}


	ICheapEventHandler arriveHandler;
	public CheapResp arriHandler(ICheapEventHandler handler) {
		arriveHandler = handler;
		return this;
	}

	public ICheapEventHandler rmArriveHandler() {
		ICheapEventHandler h = arriveHandler;
		arriveHandler = null;
		return h;
	}

	public CheapEvent event() {
		return event;
	}


	HashMap<String, String> rights;
	public CheapResp rights(HashMap<String, String> rights) {
		this.rights = rights;
		return this;
	}


	private AnResultset anRs;
	public AnResultset rs() {
		return anRs;
	}



}
