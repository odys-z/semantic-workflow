package io.odysz.sworkflow;

public interface ICheapEventHandler {

	/**Handle extra user action here. (Timeout stepping already handled by CheapEngin)
	 * @param evnt
	 */
	void onTimeout(CheapEvent evnt);
	void onNext(CheapEvent evnt);
	void onArrive(CheapEvent evnt);
}
