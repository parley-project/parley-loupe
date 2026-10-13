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

/** The params of launch and attach. */
public class LaunchParams implements JsonValue {

	private String target;
	private Object args;
	private Boolean startPaused;

	/** The id the client gives the new target. */
	public String getTarget() {
		return target;
	}

	public LaunchParams setTarget(String target) {
		this.target = target;
		return this;
	}

	/** Launch arguments, to be checked against the launchSchema. */
	public Object getArgs() {
		return args;
	}

	public LaunchParams setArgs(Object args) {
		this.args = args;
		return this;
	}

	/** The target must be paused (reason "entry") before it runs anything, so the client can set breakpoints first; it then resumes. */
	public Boolean getStartPaused() {
		return startPaused;
	}

	public LaunchParams setStartPaused(Boolean startPaused) {
		this.startPaused = startPaused;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "target", target);
		JsonValues.put(json, "args", args);
		JsonValues.put(json, "startPaused", startPaused);
		return json;
	}

	public static LaunchParams fromJson(Object json) throws JsonException {
		return fromJson(json, "LaunchParams");
	}

	/** @param what how to name this value in an error */
	public static LaunchParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		LaunchParams ret = new LaunchParams();
		ret.target = JsonValues.string(m.get("target"), what+".target");
		if( ret.target == null ) {
			throw new JsonException(what+" needs target");
		}
		ret.args = JsonValues.any(m.get("args"), what+".args");
		ret.startPaused = JsonValues.bool(m.get("startPaused"), what+".startPaused");
		return ret;
	}
}
