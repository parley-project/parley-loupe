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

/** What the IDE shows when a target pauses, sent with the event so it needn't ask. */
public class Snapshot implements JsonValue {

	private List<ThreadInfo> threads;
	private Integer thread;
	private List<Frame> frames;
	private Integer totalFrames;
	private List<Variable> locals;

	/** All the threads. */
	public List<ThreadInfo> getThreads() {
		return threads;
	}

	public Snapshot setThreads(List<ThreadInfo> threads) {
		this.threads = threads;
		return this;
	}

	/** The id of the thread that stopped. */
	public Integer getThread() {
		return thread;
	}

	public Snapshot setThread(Integer thread) {
		this.thread = thread;
		return this;
	}

	/** The top frames of that thread. */
	public List<Frame> getFrames() {
		return frames;
	}

	public Snapshot setFrames(List<Frame> frames) {
		this.frames = frames;
		return this;
	}

	/** How many frames the thread's stack has, if known; when it is no more than frames.size() the snapshot has them all. */
	public Integer getTotalFrames() {
		return totalFrames;
	}

	public Snapshot setTotalFrames(Integer totalFrames) {
		this.totalFrames = totalFrames;
		return this;
	}

	/** The locals of the first frame. */
	public List<Variable> getLocals() {
		return locals;
	}

	public Snapshot setLocals(List<Variable> locals) {
		this.locals = locals;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "threads", JsonValues.toJson(threads, JsonValues::toJson));
		JsonValues.put(json, "thread", thread);
		JsonValues.put(json, "frames", JsonValues.toJson(frames, JsonValues::toJson));
		JsonValues.put(json, "totalFrames", totalFrames);
		JsonValues.put(json, "locals", JsonValues.toJson(locals, JsonValues::toJson));
		return json;
	}

	public static Snapshot fromJson(Object json) throws JsonException {
		return fromJson(json, "Snapshot");
	}

	/** @param what how to name this value in an error */
	public static Snapshot fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Snapshot ret = new Snapshot();
		ret.threads = JsonValues.list(m.get("threads"), what+".threads", ThreadInfo::fromJson);
		ret.thread = JsonValues.integer(m.get("thread"), what+".thread");
		ret.frames = JsonValues.list(m.get("frames"), what+".frames", Frame::fromJson);
		ret.totalFrames = JsonValues.integer(m.get("totalFrames"), what+".totalFrames");
		ret.locals = JsonValues.list(m.get("locals"), what+".locals", Variable::fromJson);
		return ret;
	}
}
