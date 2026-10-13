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

/** The params of locals. */
public class LocalsParams implements JsonValue {

	private String frame;
	private Integer start;
	private Integer count;

	/** The id of a frame ("f1@12"). */
	public String getFrame() {
		return frame;
	}

	public LocalsParams setFrame(String frame) {
		this.frame = frame;
		return this;
	}

	/** The first variable wanted, from 0; 0 if absent. */
	public Integer getStart() {
		return start;
	}

	public LocalsParams setStart(Integer start) {
		this.start = start;
		return this;
	}

	/** How many; all the adapter will give if absent. */
	public Integer getCount() {
		return count;
	}

	public LocalsParams setCount(Integer count) {
		this.count = count;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "frame", frame);
		JsonValues.put(json, "start", start);
		JsonValues.put(json, "count", count);
		return json;
	}

	public static LocalsParams fromJson(Object json) throws JsonException {
		return fromJson(json, "LocalsParams");
	}

	/** @param what how to name this value in an error */
	public static LocalsParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		LocalsParams ret = new LocalsParams();
		ret.frame = JsonValues.string(m.get("frame"), what+".frame");
		if( ret.frame == null ) {
			throw new JsonException(what+" needs frame");
		}
		ret.start = JsonValues.integer(m.get("start"), what+".start");
		ret.count = JsonValues.integer(m.get("count"), what+".count");
		return ret;
	}
}
