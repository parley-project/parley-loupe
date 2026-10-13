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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The targets of one session, by the ids the client chose. A target's id can't be used
 * again in the session, even after it terminated.
 */
public final class TargetRegistry {

	private final LoupeEndpoint endpoint;
	private final Map<String,Target> targets = new LinkedHashMap<>();
	private volatile Set<String> features = Set.of();

	public TargetRegistry(LoupeEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	/** The features the session negotiated (Welcome.features). */
	public void setFeatures(Collection<String> features) {
		this.features = Set.copyOf(features);
	}

	public boolean hasFeature(String feature) {
		return features.contains(feature);
	}

	/**
	 * Makes a target.
	 * @throws LoupeException invalidParams if the id is empty or has been used
	 */
	public synchronized Target create(String id) {
		if( id == null || id.isEmpty()) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "A target needs an id");
		}
		if( targets.containsKey(id)) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "The target id "+id+" has been used");
		}
		Target t = new Target(id, this);
		targets.put(id, t);
		return t;
	}

	/**
	 * The target with this id.
	 * @throws LoupeException unknownTarget
	 */
	public synchronized Target get(String id) {
		Target t = id == null ? null : targets.get(id);
		if( t == null ) {
			throw new LoupeException(LoupeException.UNKNOWN_TARGET, id == null ? "The request needs a target" : "No target "+id);
		}
		return t;
	}

	public synchronized List<Target> list() {
		return new ArrayList<>(targets.values());
	}

	void send(String event, String target, Object params) {
		try {
			endpoint.event(event, target, params);
		} catch (IOException e) {
			Target.ignore(e);
		}
	}
}
