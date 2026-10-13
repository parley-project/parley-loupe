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

/** A place in source code. */
public class Location implements JsonValue {

	private Source source;
	private Integer line;
	private Integer column;

	/** The file. */
	public Source getSource() {
		return source;
	}

	public Location setSource(Source source) {
		this.source = source;
		return this;
	}

	/** The line, from 1. */
	public Integer getLine() {
		return line;
	}

	public Location setLine(Integer line) {
		this.line = line;
		return this;
	}

	/** The column, from 1; null for the whole line. */
	public Integer getColumn() {
		return column;
	}

	public Location setColumn(Integer column) {
		this.column = column;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "source", JsonValues.toJson(source));
		JsonValues.put(json, "line", line);
		JsonValues.put(json, "column", column);
		return json;
	}

	public static Location fromJson(Object json) throws JsonException {
		return fromJson(json, "Location");
	}

	/** @param what how to name this value in an error */
	public static Location fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Location ret = new Location();
		ret.source = Source.fromJson(m.get("source"), what+".source");
		if( ret.source == null ) {
			throw new JsonException(what+" needs source");
		}
		ret.line = JsonValues.integer(m.get("line"), what+".line");
		if( ret.line == null ) {
			throw new JsonException(what+" needs line");
		}
		ret.column = JsonValues.integer(m.get("column"), what+".column");
		return ret;
	}
}
