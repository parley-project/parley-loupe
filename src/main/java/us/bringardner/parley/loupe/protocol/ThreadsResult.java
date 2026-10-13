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

/** The result of threads. */
public class ThreadsResult implements JsonValue {

	private List<ThreadInfo> threads;

	/** All the threads. */
	public List<ThreadInfo> getThreads() {
		return threads;
	}

	public ThreadsResult setThreads(List<ThreadInfo> threads) {
		this.threads = threads;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "threads", JsonValues.toJson(threads, JsonValues::toJson));
		return json;
	}

	public static ThreadsResult fromJson(Object json) throws JsonException {
		return fromJson(json, "ThreadsResult");
	}

	/** @param what how to name this value in an error */
	public static ThreadsResult fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		ThreadsResult ret = new ThreadsResult();
		ret.threads = JsonValues.list(m.get("threads"), what+".threads", ThreadInfo::fromJson);
		if( ret.threads == null ) {
			throw new JsonException(what+" needs threads");
		}
		return ret;
	}
}
