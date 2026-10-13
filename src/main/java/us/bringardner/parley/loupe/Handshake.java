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
package us.bringardner.parley.loupe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.Welcome;

/** Version and feature negotiation: the hello request and what answers it. */
public final class Handshake {

	private Handshake() {
	}

	/**
	 * The adapter's side: picks the newest protocol both ends have and the features they
	 * share.
	 * @param hello what the client sent
	 * @param protocols the versions the adapter speaks
	 * @param features the features it has
	 * @param adapter who it is (may be null)
	 * @param launchSchema a JSON Schema for launch args (may be null)
	 * @throws LoupeException notSupported, with data.protocols, if there's no common version
	 */
	public static Welcome accept(Hello hello, List<String> protocols, List<String> features, PeerInfo adapter, Object launchSchema) {
		String chosen = null;
		int best = -1;
		for(String p : hello.getProtocols()) {
			int v = protocols.contains(p) ? version(p) : -1;
			if( v > best ) {
				best = v;
				chosen = p;
			}
		}
		if( chosen == null ) {
			Map<String,Object> data = new LinkedHashMap<>();
			data.put("protocols", new ArrayList<>(protocols));
			throw new LoupeException(LoupeException.NOT_SUPPORTED, "None of the protocols "+hello.getProtocols()+" is supported", data);
		}
		List<String> shared = new ArrayList<>();
		if( hello.getFeatures() != null ) {
			for(String f : features) {
				if( hello.getFeatures().contains(f) && !shared.contains(f)) {
					shared.add(f);
				}
			}
		}
		return new Welcome().setProtocol(chosen).setAdapter(adapter).setFeatures(shared).setLaunchSchema(launchSchema);
	}

	/** The N of "loupe/N", or 0 for a name that isn't in that form (so it still ranks above none). */
	static int version(String protocol) {
		if( protocol.startsWith("loupe/")) {
			try {
				return Integer.parseInt(protocol.substring(6));
			} catch (NumberFormatException e) {
				// not a number
			}
		}
		return 0;
	}

	/**
	 * A handler's answer to a request for the hello method: parses it, negotiates, and
	 * returns what to send back.
	 * @throws LoupeException invalidParams for params that aren't a hello
	 */
	public static Object answer(Object params, List<String> protocols, List<String> features, PeerInfo adapter, Object launchSchema) {
		Hello hello;
		try {
			hello = Hello.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( hello == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "hello needs params");
		}
		return accept(hello, protocols, features, adapter, launchSchema).toJson();
	}

	/**
	 * The client's side: sends hello and parses the welcome.
	 * @return the welcome, or failed with LoupeException (notSupported if no common version)
	 */
	public static CompletableFuture<Welcome> hello(LoupeEndpoint endpoint, Hello hello) {
		return endpoint.request(Loupe.HELLO, null, hello.toJson()).thenApply(r -> {
			try {
				Welcome w = Welcome.fromJson(r);
				if( w == null ) {
					throw new JsonException("The welcome is empty");
				}
				return w;
			} catch (JsonException e) {
				throw new LoupeException(LoupeException.FAILED, e.getMessage());
			}
		});
	}
}
