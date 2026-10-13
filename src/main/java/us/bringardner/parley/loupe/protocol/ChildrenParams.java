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

/** The params of children. */
public class ChildrenParams implements JsonValue {

	private String id;
	private Integer start;
	private Integer count;

	/** The object id of an array, map, object or scope. */
	public String getId() {
		return id;
	}

	public ChildrenParams setId(String id) {
		this.id = id;
		return this;
	}

	/** The first child wanted, from 0; 0 if absent. */
	public Integer getStart() {
		return start;
	}

	public ChildrenParams setStart(Integer start) {
		this.start = start;
		return this;
	}

	/** How many; all the adapter will give if absent. */
	public Integer getCount() {
		return count;
	}

	public ChildrenParams setCount(Integer count) {
		this.count = count;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "start", start);
		JsonValues.put(json, "count", count);
		return json;
	}

	public static ChildrenParams fromJson(Object json) throws JsonException {
		return fromJson(json, "ChildrenParams");
	}

	/** @param what how to name this value in an error */
	public static ChildrenParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		ChildrenParams ret = new ChildrenParams();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.start = JsonValues.integer(m.get("start"), what+".start");
		ret.count = JsonValues.integer(m.get("count"), what+".count");
		return ret;
	}
}
