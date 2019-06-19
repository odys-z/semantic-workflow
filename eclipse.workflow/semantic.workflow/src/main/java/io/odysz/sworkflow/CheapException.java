package io.odysz.sworkflow;

import io.odysz.semantics.x.SemanticException;

/**Exception when checking workflow invalid.
 * @author ody
 *
 */
public class CheapException extends SemanticException {
	/** * */
	private static final long serialVersionUID = 1L;

	/**Error code for response*/
	public static final String ERR_WF = "wf_err";
	public static final String ERR_WF_Rights = "wf_err_rights";

	// TODO add js client
	public static final String ERR_WF_INTERNAL = "wf_err_internal";

	public static final String ERR_WF_COMPETATION = "wf_err_competing";

	private String code;

	public CheapException(String code, String tmpl, Object... args) {
		super("[" + code + "] " + tmpl, args);
		this.code = code;
	}

	public CheapException(String tmpl, Object...args) {
		super(tmpl, args);
		this.code = ERR_WF;
	}

	public String code() { return code; }
}
