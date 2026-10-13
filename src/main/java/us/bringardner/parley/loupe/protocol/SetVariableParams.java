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

/** The params of setVariable. */
public class SetVariableParams implements JsonValue {

	private String id;
	private String name;
	private String value;

	/** The object id of the container: an object, map, array or scope. */
	public String getId() {
		return id;
	}

	public SetVariableParams setId(String id) {
		this.id = id;
		return this;
	}

	/** The name of the variable in it (an index for an array). */
	public String getName() {
		return name;
	}

	public SetVariableParams setName(String name) {
		this.name = name;
		return this;
	}

	/** An expression, in the debuggee's language, for the new value. */
	public String getValue() {
		return value;
	}

	public SetVariableParams setValue(String value) {
		this.value = value;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "value", value);
		return json;
	}

	public static SetVariableParams fromJson(Object json) throws JsonException {
		return fromJson(json, "SetVariableParams");
	}

	/** @param what how to name this value in an error */
	public static SetVariableParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		SetVariableParams ret = new SetVariableParams();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.name = JsonValues.string(m.get("name"), what+".name");
		if( ret.name == null ) {
			throw new JsonException(what+" needs name");
		}
		ret.value = JsonValues.string(m.get("value"), what+".value");
		if( ret.value == null ) {
			throw new JsonException(what+" needs value");
		}
		return ret;
	}
}
