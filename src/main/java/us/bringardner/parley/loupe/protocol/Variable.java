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

/** A name and its value. */
public class Variable implements JsonValue {

	private String name;
	private Value value;

	/** The name. */
	public String getName() {
		return name;
	}

	public Variable setName(String name) {
		this.name = name;
		return this;
	}

	/** The value. */
	public Value getValue() {
		return value;
	}

	public Variable setValue(Value value) {
		this.value = value;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "value", JsonValues.toJson(value));
		return json;
	}

	public static Variable fromJson(Object json) throws JsonException {
		return fromJson(json, "Variable");
	}

	/** @param what how to name this value in an error */
	public static Variable fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Variable ret = new Variable();
		ret.name = JsonValues.string(m.get("name"), what+".name");
		if( ret.name == null ) {
			throw new JsonException(what+" needs name");
		}
		ret.value = Value.fromJson(m.get("value"), what+".value");
		if( ret.value == null ) {
			throw new JsonException(what+" needs value");
		}
		return ret;
	}
}
