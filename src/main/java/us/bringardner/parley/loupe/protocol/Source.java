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

/** A place source code comes from. */
public class Source implements JsonValue {

	private String path;
	private String name;

	/** A path the IDE can open. */
	public String getPath() {
		return path;
	}

	public Source setPath(String path) {
		this.path = path;
		return this;
	}

	/** A short name for display. */
	public String getName() {
		return name;
	}

	public Source setName(String name) {
		this.name = name;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "path", path);
		JsonValues.put(json, "name", name);
		return json;
	}

	public static Source fromJson(Object json) throws JsonException {
		return fromJson(json, "Source");
	}

	/** @param what how to name this value in an error */
	public static Source fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Source ret = new Source();
		ret.path = JsonValues.string(m.get("path"), what+".path");
		ret.name = JsonValues.string(m.get("name"), what+".name");
		return ret;
	}
}
