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

/** The params of addBreakpoint. */
public class AddBreakpointParams implements JsonValue {

	private String id;
	private Location where;
	private String condition;
	private Integer hitCount;
	private String logMessage;

	/** The breakpoint's id, chosen by the client; an id that exists is replaced. */
	public String getId() {
		return id;
	}

	public AddBreakpointParams setId(String id) {
		this.id = id;
		return this;
	}

	/** Where to stop. */
	public Location getWhere() {
		return where;
	}

	public AddBreakpointParams setWhere(Location where) {
		this.where = where;
		return this;
	}

	/** Stop only when this expression is true. */
	public String getCondition() {
		return condition;
	}

	public AddBreakpointParams setCondition(String condition) {
		this.condition = condition;
		return this;
	}

	/** Stop only on this hit (and not before). */
	public Integer getHitCount() {
		return hitCount;
	}

	public AddBreakpointParams setHitCount(Integer hitCount) {
		this.hitCount = hitCount;
		return this;
	}

	/** Log this instead of stopping. */
	public String getLogMessage() {
		return logMessage;
	}

	public AddBreakpointParams setLogMessage(String logMessage) {
		this.logMessage = logMessage;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "where", JsonValues.toJson(where));
		JsonValues.put(json, "condition", condition);
		JsonValues.put(json, "hitCount", hitCount);
		JsonValues.put(json, "logMessage", logMessage);
		return json;
	}

	public static AddBreakpointParams fromJson(Object json) throws JsonException {
		return fromJson(json, "AddBreakpointParams");
	}

	/** @param what how to name this value in an error */
	public static AddBreakpointParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		AddBreakpointParams ret = new AddBreakpointParams();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.where = Location.fromJson(m.get("where"), what+".where");
		if( ret.where == null ) {
			throw new JsonException(what+" needs where");
		}
		ret.condition = JsonValues.string(m.get("condition"), what+".condition");
		ret.hitCount = JsonValues.integer(m.get("hitCount"), what+".hitCount");
		ret.logMessage = JsonValues.string(m.get("logMessage"), what+".logMessage");
		return ret;
	}
}
