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

/** The client's first request: which protocols and features it can use. */
public class Hello implements JsonValue {

	private List<String> protocols;
	private PeerInfo client;
	private List<String> features;

	/** Protocol versions it can speak ("loupe/1"), in any order. */
	public List<String> getProtocols() {
		return protocols;
	}

	public Hello setProtocols(List<String> protocols) {
		this.protocols = protocols;
		return this;
	}

	/** The client. */
	public PeerInfo getClient() {
		return client;
	}

	public Hello setClient(PeerInfo client) {
		this.client = client;
		return this;
	}

	/** Optional features it can use; see {@link Loupe}. */
	public List<String> getFeatures() {
		return features;
	}

	public Hello setFeatures(List<String> features) {
		this.features = features;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "protocols", protocols);
		JsonValues.put(json, "client", JsonValues.toJson(client));
		JsonValues.put(json, "features", features);
		return json;
	}

	public static Hello fromJson(Object json) throws JsonException {
		return fromJson(json, "Hello");
	}

	/** @param what how to name this value in an error */
	public static Hello fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Hello ret = new Hello();
		ret.protocols = JsonValues.list(m.get("protocols"), what+".protocols", JsonValues::string);
		if( ret.protocols == null ) {
			throw new JsonException(what+" needs protocols");
		}
		ret.client = PeerInfo.fromJson(m.get("client"), what+".client");
		ret.features = JsonValues.list(m.get("features"), what+".features", JsonValues::string);
		return ret;
	}
}
