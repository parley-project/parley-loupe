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

/** The output event: text the debuggee (or the adapter) wrote. */
public class OutputEvent implements JsonValue {

	/** Values of category. */
	public static final String STDOUT = "stdout";
	public static final String STDERR = "stderr";
	public static final String CONSOLE = "console";

	private String category;
	private String text;

	/** {@link #STDOUT}, {@link #STDERR} or {@link #CONSOLE} (the adapter's own remarks); stdout if left out. */
	public String getCategory() {
		return category;
	}

	public OutputEvent setCategory(String category) {
		this.category = category;
		return this;
	}

	/** What was written, line ends included. */
	public String getText() {
		return text;
	}

	public OutputEvent setText(String text) {
		this.text = text;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "category", category);
		JsonValues.put(json, "text", text);
		return json;
	}

	public static OutputEvent fromJson(Object json) throws JsonException {
		return fromJson(json, "OutputEvent");
	}

	/** @param what how to name this value in an error */
	public static OutputEvent fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		OutputEvent ret = new OutputEvent();
		ret.category = JsonValues.string(m.get("category"), what+".category");
		ret.text = JsonValues.string(m.get("text"), what+".text");
		if( ret.text == null ) {
			throw new JsonException(what+" needs text");
		}
		return ret;
	}
}
