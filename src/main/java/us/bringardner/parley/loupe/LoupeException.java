/**
 * <PRE>
 *
 * Copyright Tony Bringardner 1998, 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *
 *	@author Tony Bringardner
 *
 *
 */
package us.bringardner.parley.loupe;

import java.util.Map;

/**
 * An error a Loupe request ends with: a short code, a message, and optional structured data.
 * Thrown by a handler (or what its future fails with) for an error response, and what a
 * request's future fails with when the other end answered with one.
 */
public class LoupeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public static final String INVALID_PARAMS = "invalidParams";
	public static final String UNKNOWN_METHOD = "unknownMethod";
	public static final String UNKNOWN_TARGET = "unknownTarget";
	public static final String WRONG_STATE = "wrongState";
	public static final String CANCELLED = "cancelled";
	public static final String NOT_SUPPORTED = "notSupported";
	public static final String STALE_ID = "staleId";
	public static final String FAILED = "failed";

	private final String code;
	private final transient Map<String,Object> data;

	public LoupeException(String code, String message) {
		this(code, message, null);
	}

	public LoupeException(String code, String message, Map<String,Object> data) {
		super(message);
		this.code = code;
		this.data = data;
	}

	/** One of the codes above (or one the other end made up). */
	public String getCode() {
		return code;
	}

	/** Extra JSON members, or null. */
	public Map<String,Object> getData() {
		return data;
	}
}
