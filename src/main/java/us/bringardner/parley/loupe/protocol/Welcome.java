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
import java.util.List;
import java.util.Map;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.dap.json.JsonValue;
import us.bringardner.parley.dap.json.JsonValues;

/** The adapter's answer to {@link Hello}. */
public class Welcome implements JsonValue {

	private String protocol;
	private PeerInfo adapter;
	private List<String> features;
	private Object launchSchema;

	/** The protocol version chosen. */
	public String getProtocol() {
		return protocol;
	}

	public Welcome setProtocol(String protocol) {
		this.protocol = protocol;
		return this;
	}

	/** The adapter. */
	public PeerInfo getAdapter() {
		return adapter;
	}

	public Welcome setAdapter(PeerInfo adapter) {
		this.adapter = adapter;
		return this;
	}

	/** The features both ends have: only these may be used in the session. */
	public List<String> getFeatures() {
		return features;
	}

	public Welcome setFeatures(List<String> features) {
		this.features = features;
		return this;
	}

	/** A JSON Schema for the args of launch (see SchemaValidator), or null. */
	public Object getLaunchSchema() {
		return launchSchema;
	}

	public Welcome setLaunchSchema(Object launchSchema) {
		this.launchSchema = launchSchema;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "protocol", protocol);
		JsonValues.put(json, "adapter", JsonValues.toJson(adapter));
		JsonValues.put(json, "features", features);
		JsonValues.put(json, "launchSchema", launchSchema);
		return json;
	}

	public static Welcome fromJson(Object json) throws JsonException {
		return fromJson(json, "Welcome");
	}

	/** @param what how to name this value in an error */
	public static Welcome fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Welcome ret = new Welcome();
		ret.protocol = JsonValues.string(m.get("protocol"), what+".protocol");
		if( ret.protocol == null ) {
			throw new JsonException(what+" needs protocol");
		}
		ret.adapter = PeerInfo.fromJson(m.get("adapter"), what+".adapter");
		ret.features = JsonValues.list(m.get("features"), what+".features", JsonValues::string);
		if( ret.features == null ) {
			throw new JsonException(what+" needs features");
		}
		ret.launchSchema = JsonValues.any(m.get("launchSchema"), what+".launchSchema");
		return ret;
	}
}
