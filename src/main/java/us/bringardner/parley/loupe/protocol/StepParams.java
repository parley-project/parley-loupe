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

/** The params of step. */
public class StepParams implements JsonValue {

	private String kind;
	private Integer thread;

	/** See {@link StepKind}. */
	public String getKind() {
		return kind;
	}

	public StepParams setKind(String kind) {
		this.kind = kind;
		return this;
	}

	/** The thread to step; the one that stopped if absent. */
	public Integer getThread() {
		return thread;
	}

	public StepParams setThread(Integer thread) {
		this.thread = thread;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "kind", kind);
		JsonValues.put(json, "thread", thread);
		return json;
	}

	public static StepParams fromJson(Object json) throws JsonException {
		return fromJson(json, "StepParams");
	}

	/** @param what how to name this value in an error */
	public static StepParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		StepParams ret = new StepParams();
		ret.kind = JsonValues.string(m.get("kind"), what+".kind");
		if( ret.kind == null ) {
			throw new JsonException(what+" needs kind");
		}
		ret.thread = JsonValues.integer(m.get("thread"), what+".thread");
		return ret;
	}
}
