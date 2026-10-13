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

/** The targetState event: a target changed state. */
public class TargetStateEvent implements JsonValue {

	private String state;
	private String from;
	private String reason;
	private Integer generation;
	private List<String> breakpoints;
	private String description;
	private Integer exitCode;
	private Snapshot snapshot;

	/** The new state; see {@link TargetState}. */
	public String getState() {
		return state;
	}

	public TargetStateEvent setState(String state) {
		this.state = state;
		return this;
	}

	/** The state before, or null for the first. */
	public String getFrom() {
		return from;
	}

	public TargetStateEvent setFrom(String from) {
		this.from = from;
		return this;
	}

	/** Why: "launch", "breakpoint", "step", "pause", "exception", "resume", "exit", "kill" ... */
	public String getReason() {
		return reason;
	}

	public TargetStateEvent setReason(String reason) {
		this.reason = reason;
		return this;
	}

	/** The target's generation after the change; it goes up each time the target pauses. */
	public Integer getGeneration() {
		return generation;
	}

	public TargetStateEvent setGeneration(Integer generation) {
		this.generation = generation;
		return this;
	}

	/** Ids of the breakpoints that were hit. */
	public List<String> getBreakpoints() {
		return breakpoints;
	}

	public TargetStateEvent setBreakpoints(List<String> breakpoints) {
		this.breakpoints = breakpoints;
		return this;
	}

	/** For paused: text to show the user about the stop; for an exception, its message. */
	public String getDescription() {
		return description;
	}

	public TargetStateEvent setDescription(String description) {
		this.description = description;
		return this;
	}

	/** For terminated: the exit code, if there is one. */
	public Integer getExitCode() {
		return exitCode;
	}

	public TargetStateEvent setExitCode(Integer exitCode) {
		this.exitCode = exitCode;
		return this;
	}

	/** For paused, if the snapshot feature is on: what to show. */
	public Snapshot getSnapshot() {
		return snapshot;
	}

	public TargetStateEvent setSnapshot(Snapshot snapshot) {
		this.snapshot = snapshot;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "state", state);
		JsonValues.put(json, "from", from);
		JsonValues.put(json, "reason", reason);
		JsonValues.put(json, "generation", generation);
		JsonValues.put(json, "breakpoints", breakpoints);
		JsonValues.put(json, "description", description);
		JsonValues.put(json, "exitCode", exitCode);
		JsonValues.put(json, "snapshot", JsonValues.toJson(snapshot));
		return json;
	}

	public static TargetStateEvent fromJson(Object json) throws JsonException {
		return fromJson(json, "TargetStateEvent");
	}

	/** @param what how to name this value in an error */
	public static TargetStateEvent fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		TargetStateEvent ret = new TargetStateEvent();
		ret.state = JsonValues.string(m.get("state"), what+".state");
		if( ret.state == null ) {
			throw new JsonException(what+" needs state");
		}
		ret.from = JsonValues.string(m.get("from"), what+".from");
		ret.reason = JsonValues.string(m.get("reason"), what+".reason");
		ret.generation = JsonValues.integer(m.get("generation"), what+".generation");
		ret.breakpoints = JsonValues.list(m.get("breakpoints"), what+".breakpoints", JsonValues::string);
		ret.description = JsonValues.string(m.get("description"), what+".description");
		ret.exitCode = JsonValues.integer(m.get("exitCode"), what+".exitCode");
		ret.snapshot = Snapshot.fromJson(m.get("snapshot"), what+".snapshot");
		return ret;
	}
}
