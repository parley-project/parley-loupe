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

/** The result of frames. */
public class FramesResult implements JsonValue {

	private List<Frame> frames;
	private Integer total;

	/** The frames, top first. */
	public List<Frame> getFrames() {
		return frames;
	}

	public FramesResult setFrames(List<Frame> frames) {
		this.frames = frames;
		return this;
	}

	/** How many frames the stack has, if known. */
	public Integer getTotal() {
		return total;
	}

	public FramesResult setTotal(Integer total) {
		this.total = total;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "frames", JsonValues.toJson(frames, JsonValues::toJson));
		JsonValues.put(json, "total", total);
		return json;
	}

	public static FramesResult fromJson(Object json) throws JsonException {
		return fromJson(json, "FramesResult");
	}

	/** @param what how to name this value in an error */
	public static FramesResult fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		FramesResult ret = new FramesResult();
		ret.frames = JsonValues.list(m.get("frames"), what+".frames", Frame::fromJson);
		if( ret.frames == null ) {
			throw new JsonException(what+" needs frames");
		}
		ret.total = JsonValues.integer(m.get("total"), what+".total");
		return ret;
	}
}
