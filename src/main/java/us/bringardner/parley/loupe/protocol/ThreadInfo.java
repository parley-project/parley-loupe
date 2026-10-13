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

/** A thread of a target. */
public class ThreadInfo implements JsonValue {

	private Integer id;
	private String name;
	private String state;

	/** The thread's id. */
	public Integer getId() {
		return id;
	}

	public ThreadInfo setId(Integer id) {
		this.id = id;
		return this;
	}

	/** Its name. */
	public String getName() {
		return name;
	}

	public ThreadInfo setName(String name) {
		this.name = name;
		return this;
	}

	/** "running" or "paused". */
	public String getState() {
		return state;
	}

	public ThreadInfo setState(String state) {
		this.state = state;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "state", state);
		return json;
	}

	public static ThreadInfo fromJson(Object json) throws JsonException {
		return fromJson(json, "ThreadInfo");
	}

	/** @param what how to name this value in an error */
	public static ThreadInfo fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		ThreadInfo ret = new ThreadInfo();
		ret.id = JsonValues.integer(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.name = JsonValues.string(m.get("name"), what+".name");
		ret.state = JsonValues.string(m.get("state"), what+".state");
		return ret;
	}
}
