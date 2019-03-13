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

	public CheapException(String tmpl, Object... args) {
		super(tmpl, args);
	}
}
