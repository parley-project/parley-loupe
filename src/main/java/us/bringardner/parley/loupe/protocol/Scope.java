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

/** A group of variables of a frame: the locals, the globals ... */
public class Scope implements JsonValue {

	private String name;
	private String id;
	private Boolean expensive;

	/** What the scope is called. */
	public String getName() {
		return name;
	}

	public Scope setName(String name) {
		this.name = name;
		return this;
	}

	/** Its object id: ask for the variables with children. */
	public String getId() {
		return id;
	}

	public Scope setId(String id) {
		this.id = id;
		return this;
	}

	/** Getting its variables is slow, so ask only if the user wants them. */
	public Boolean getExpensive() {
		return expensive;
	}

	public Scope setExpensive(Boolean expensive) {
		this.expensive = expensive;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "expensive", expensive);
		return json;
	}

	public static Scope fromJson(Object json) throws JsonException {
		return fromJson(json, "Scope");
	}

	/** @param what how to name this value in an error */
	public static Scope fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Scope ret = new Scope();
		ret.name = JsonValues.string(m.get("name"), what+".name");
		if( ret.name == null ) {
			throw new JsonException(what+" needs name");
		}
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.expensive = JsonValues.bool(m.get("expensive"), what+".expensive");
		return ret;
	}
}
