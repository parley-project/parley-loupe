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
package us.bringardner.parley.loupe.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.dap.json.JsonValue;
import us.bringardner.parley.dap.json.JsonValues;

/** The breakpointStatus event: a breakpoint changed status. */
public class BreakpointStatusEvent implements JsonValue {

	private String id;
	private String status;
	private Location where;
	private String message;

	/** The breakpoint's id. */
	public String getId() {
		return id;
	}

	public BreakpointStatusEvent setId(String id) {
		this.id = id;
		return this;
	}

	/** See {@link BreakpointStatus}. */
	public String getStatus() {
		return status;
	}

	public BreakpointStatusEvent setStatus(String status) {
		this.status = status;
		return this;
	}

	/** Where it really is. */
	public Location getWhere() {
		return where;
	}

	public BreakpointStatusEvent setWhere(Location where) {
		this.where = where;
		return this;
	}

	/** Why, for rejected. */
	public String getMessage() {
		return message;
	}

	public BreakpointStatusEvent setMessage(String message) {
		this.message = message;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "status", status);
		JsonValues.put(json, "where", JsonValues.toJson(where));
		JsonValues.put(json, "message", message);
		return json;
	}

	public static BreakpointStatusEvent fromJson(Object json) throws JsonException {
		return fromJson(json, "BreakpointStatusEvent");
	}

	/** @param what how to name this value in an error */
	public static BreakpointStatusEvent fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		BreakpointStatusEvent ret = new BreakpointStatusEvent();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.status = JsonValues.string(m.get("status"), what+".status");
		if( ret.status == null ) {
			throw new JsonException(what+" needs status");
		}
		ret.where = Location.fromJson(m.get("where"), what+".where");
		ret.message = JsonValues.string(m.get("message"), what+".message");
		return ret;
	}
}
