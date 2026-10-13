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

/** One frame of a stack. */
public class Frame implements JsonValue {

	private String id;
	private String name;
	private Source source;
	private Integer line;
	private Integer column;

	/** The frame's id, as an object id: "f1@12". */
	public String getId() {
		return id;
	}

	public Frame setId(String id) {
		this.id = id;
		return this;
	}

	/** The function. */
	public String getName() {
		return name;
	}

	public Frame setName(String name) {
		this.name = name;
		return this;
	}

	/** Where it is. */
	public Source getSource() {
		return source;
	}

	public Frame setSource(Source source) {
		this.source = source;
		return this;
	}

	/** The line, from 1. */
	public Integer getLine() {
		return line;
	}

	public Frame setLine(Integer line) {
		this.line = line;
		return this;
	}

	/** The column, from 1. */
	public Integer getColumn() {
		return column;
	}

	public Frame setColumn(Integer column) {
		this.column = column;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "source", JsonValues.toJson(source));
		JsonValues.put(json, "line", line);
		JsonValues.put(json, "column", column);
		return json;
	}

	public static Frame fromJson(Object json) throws JsonException {
		return fromJson(json, "Frame");
	}

	/** @param what how to name this value in an error */
	public static Frame fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Frame ret = new Frame();
		ret.id = JsonValues.string(m.get("id"), what+".id");
		if( ret.id == null ) {
			throw new JsonException(what+" needs id");
		}
		ret.name = JsonValues.string(m.get("name"), what+".name");
		if( ret.name == null ) {
			throw new JsonException(what+" needs name");
		}
		ret.source = Source.fromJson(m.get("source"), what+".source");
		ret.line = JsonValues.integer(m.get("line"), what+".line");
		ret.column = JsonValues.integer(m.get("column"), what+".column");
		return ret;
	}
}
