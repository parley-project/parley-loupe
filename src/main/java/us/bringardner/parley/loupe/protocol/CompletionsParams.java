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

/** The params of completions. */
public class CompletionsParams implements JsonValue {

	private String text;
	private Integer offset;
	private String frame;

	/** The text typed so far (one line or several). */
	public String getText() {
		return text;
	}

	public CompletionsParams setText(String text) {
		this.text = text;
		return this;
	}

	/** The cursor, as a character offset into text from 0. */
	public Integer getOffset() {
		return offset;
	}

	public CompletionsParams setOffset(Integer offset) {
		this.offset = offset;
		return this;
	}

	/** The id of the frame to complete in; the top frame if absent. */
	public String getFrame() {
		return frame;
	}

	public CompletionsParams setFrame(String frame) {
		this.frame = frame;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "text", text);
		JsonValues.put(json, "offset", offset);
		JsonValues.put(json, "frame", frame);
		return json;
	}

	public static CompletionsParams fromJson(Object json) throws JsonException {
		return fromJson(json, "CompletionsParams");
	}

	/** @param what how to name this value in an error */
	public static CompletionsParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		CompletionsParams ret = new CompletionsParams();
		ret.text = JsonValues.string(m.get("text"), what+".text");
		if( ret.text == null ) {
			throw new JsonException(what+" needs text");
		}
		ret.offset = JsonValues.integer(m.get("offset"), what+".offset");
		if( ret.offset == null ) {
			throw new JsonException(what+" needs offset");
		}
		ret.frame = JsonValues.string(m.get("frame"), what+".frame");
		return ret;
	}
}
