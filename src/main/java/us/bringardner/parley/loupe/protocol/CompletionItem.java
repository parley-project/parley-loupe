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

/** One way to complete the text. */
public class CompletionItem implements JsonValue {

	private String label;
	private String text;
	private String detail;
	private String type;
	private Integer start;
	private Integer length;

	/** What the list shows. */
	public String getLabel() {
		return label;
	}

	public CompletionItem setLabel(String label) {
		this.label = label;
		return this;
	}

	/** What to insert; the label if absent. */
	public String getText() {
		return text;
	}

	public CompletionItem setText(String text) {
		this.text = text;
		return this;
	}

	/** More about the item. */
	public String getDetail() {
		return detail;
	}

	public CompletionItem setDetail(String detail) {
		this.detail = detail;
		return this;
	}

	/** "method", "function", "field", "variable", "class", "module", "property", "keyword", "value", "text" ... */
	public String getType() {
		return type;
	}

	public CompletionItem setType(String type) {
		this.type = type;
		return this;
	}

	/** Where in the text the replaced part starts; the start of the word at the cursor if absent. */
	public Integer getStart() {
		return start;
	}

	public CompletionItem setStart(Integer start) {
		this.start = start;
		return this;
	}

	/** How many characters are replaced; up to the cursor if absent. */
	public Integer getLength() {
		return length;
	}

	public CompletionItem setLength(Integer length) {
		this.length = length;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "label", label);
		JsonValues.put(json, "text", text);
		JsonValues.put(json, "detail", detail);
		JsonValues.put(json, "type", type);
		JsonValues.put(json, "start", start);
		JsonValues.put(json, "length", length);
		return json;
	}

	public static CompletionItem fromJson(Object json) throws JsonException {
		return fromJson(json, "CompletionItem");
	}

	/** @param what how to name this value in an error */
	public static CompletionItem fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		CompletionItem ret = new CompletionItem();
		ret.label = JsonValues.string(m.get("label"), what+".label");
		if( ret.label == null ) {
			throw new JsonException(what+" needs label");
		}
		ret.text = JsonValues.string(m.get("text"), what+".text");
		ret.detail = JsonValues.string(m.get("detail"), what+".detail");
		ret.type = JsonValues.string(m.get("type"), what+".type");
		ret.start = JsonValues.integer(m.get("start"), what+".start");
		ret.length = JsonValues.integer(m.get("length"), what+".length");
		return ret;
	}
}
