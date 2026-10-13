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

/** The result of objectChanges. */
public class ObjectChangesResult implements JsonValue {

	private Integer generation;
	private List<String> changed;
	private List<String> gone;

	/** The target's generation now. */
	public Integer getGeneration() {
		return generation;
	}

	public ObjectChangesResult setGeneration(Integer generation) {
		this.generation = generation;
		return this;
	}

	/** Names (not ids) of the objects that changed or came into being since. */
	public List<String> getChanged() {
		return changed;
	}

	public ObjectChangesResult setChanged(List<String> changed) {
		this.changed = changed;
		return this;
	}

	/** Names of the objects that no longer exist. */
	public List<String> getGone() {
		return gone;
	}

	public ObjectChangesResult setGone(List<String> gone) {
		this.gone = gone;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "generation", generation);
		JsonValues.put(json, "changed", changed);
		JsonValues.put(json, "gone", gone);
		return json;
	}

	public static ObjectChangesResult fromJson(Object json) throws JsonException {
		return fromJson(json, "ObjectChangesResult");
	}

	/** @param what how to name this value in an error */
	public static ObjectChangesResult fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		ObjectChangesResult ret = new ObjectChangesResult();
		ret.generation = JsonValues.integer(m.get("generation"), what+".generation");
		ret.changed = JsonValues.list(m.get("changed"), what+".changed", JsonValues::string);
		if( ret.changed == null ) {
			throw new JsonException(what+" needs changed");
		}
		ret.gone = JsonValues.list(m.get("gone"), what+".gone", JsonValues::string);
		if( ret.gone == null ) {
			throw new JsonException(what+" needs gone");
		}
		return ret;
	}
}
