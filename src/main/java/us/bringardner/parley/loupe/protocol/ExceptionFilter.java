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

/** A kind of exception the target can stop on (all, uncaught ...), chosen by the adapter. */
public class ExceptionFilter implements JsonValue {

	private String id;
	private String label;
	private String description;
	private Boolean defaultOn;

	/** The name used in setExceptionFilters. */
	public String getId() {
		return id;
	}

	public ExceptionFilter setId(String id) {
		this.id = id;
		return this;
	}

	/** What a user sees. */
	public String getLabel() {
		return label;
	}

	public ExceptionFilter setLabel(String label) {
		this.label = label;
		return this;
	}

	/** More about the filter. */
	public String getDescription() {
		return description;
	}

	public ExceptionFilter setDescription(String description) {
		this.description = description;
		return this;
	}

	/** On until the client says otherwise; a client may switch it on at the start. */
	public Boolean getDefaultOn() {
		return defaultOn;
	}

	public ExceptionFilter setDefaultOn(Boolean defaultOn) {
		this.defaultOn = defaultOn;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "label", label);
		JsonValues.put(json, "description", description);
		JsonValues.put(json, "defaultOn", defaultOn);
		return json;
	}

	public static ExceptionFilter fromJson(Object json) throws JsonException {
		return fromJson(json, "ExceptionFilter");
	}

	/** @param what how to name this value in an error */
	public static ExceptionFilter fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		ExceptionFilter ret = new ExceptionFilter();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.label = JsonValues.string(m.get("label"), what+".label");
		if( ret.label == null ) {
			throw new JsonException(what+" needs label");
		}
		ret.description = JsonValues.string(m.get("description"), what+".description");
		ret.defaultOn = JsonValues.bool(m.get("defaultOn"), what+".defaultOn");
		return ret;
	}
}
