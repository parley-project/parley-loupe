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

/** A value, typed. {@code t} says which members are there; see PROTOCOL.md section 6. */
public class Value implements JsonValue {

	/** Values of t. */
	public static final String NULL = "null";
	public static final String BOOL = "bool";
	public static final String NUMBER = "number";
	public static final String STRING = "string";
	public static final String ARRAY = "array";
	public static final String MAP = "map";
	public static final String OBJECT = "object";
	public static final String FUNCTION = "function";
	public static final String REF = "ref";

	/** Values of kind. */
	public static final String INT = "int";
	public static final String FLOAT = "float";
	public static final String BIG = "big";

	private String t;
	private Object v;
	private String kind;
	private Integer length;
	private Boolean truncated;
	private String id;
	private String type;
	private Integer size;
	private List<Value> items;
	private List<List<Value>> entries;
	private List<Variable> fields;
	private String name;
	private String signature;
	private Source source;
	private String summary;

	/** The type: one of the constants of {@link Value}. */
	public String getT() {
		return t;
	}

	public Value setT(String t) {
		this.t = t;
		return this;
	}

	/** The value of a null, bool, number or string. */
	public Object getV() {
		return v;
	}

	public Value setV(Object v) {
		this.v = v;
		return this;
	}

	/** For a number: "int", "float" or "big" (big has v as a decimal string). */
	public String getKind() {
		return kind;
	}

	public Value setKind(String kind) {
		this.kind = kind;
		return this;
	}

	/** For a string: its full length. */
	public Integer getLength() {
		return length;
	}

	public Value setLength(Integer length) {
		this.length = length;
		return this;
	}

	/** For a string: v was cut. */
	public Boolean getTruncated() {
		return truncated;
	}

	public Value setTruncated(Boolean truncated) {
		this.truncated = truncated;
		return this;
	}

	/** For a value with parts: its object id ("o12@5"). */
	public String getId() {
		return id;
	}

	public Value setId(String id) {
		this.id = id;
		return this;
	}

	/** The name of the value's type in the debuggee. */
	public String getType() {
		return type;
	}

	public Value setType(String type) {
		this.type = type;
		return this;
	}

	/** For an array or map: the number of items. */
	public Integer getSize() {
		return size;
	}

	public Value setSize(Integer size) {
		this.size = size;
		return this;
	}

	/** For an array: the first few items. */
	public List<Value> getItems() {
		return items;
	}

	public Value setItems(List<Value> items) {
		this.items = items;
		return this;
	}

	/** For a map: the first few [key, value] pairs. */
	public List<List<Value>> getEntries() {
		return entries;
	}

	public Value setEntries(List<List<Value>> entries) {
		this.entries = entries;
		return this;
	}

	/** For an object: the first few fields. */
	public List<Variable> getFields() {
		return fields;
	}

	public Value setFields(List<Variable> fields) {
		this.fields = fields;
		return this;
	}

	/** For a function: its name. */
	public String getName() {
		return name;
	}

	public Value setName(String name) {
		this.name = name;
		return this;
	}

	/** For a function: its parameters. */
	public String getSignature() {
		return signature;
	}

	public Value setSignature(String signature) {
		this.signature = signature;
		return this;
	}

	/** For a function: where it is defined. */
	public Source getSource() {
		return source;
	}

	public Value setSource(Source source) {
		this.source = source;
		return this;
	}

	/** For a ref: a short text for display. */
	public String getSummary() {
		return summary;
	}

	public Value setSummary(String summary) {
		this.summary = summary;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "t", t);
		JsonValues.put(json, "v", v);
		JsonValues.put(json, "kind", kind);
		JsonValues.put(json, "length", length);
		JsonValues.put(json, "truncated", truncated);
		JsonValues.put(json, "id", id);
		JsonValues.put(json, "type", type);
		JsonValues.put(json, "size", size);
		JsonValues.put(json, "items", JsonValues.toJson(items, JsonValues::toJson));
		JsonValues.put(json, "entries", JsonValues.toJson(entries, l -> JsonValues.toJson(l, JsonValues::toJson)));
		JsonValues.put(json, "fields", JsonValues.toJson(fields, JsonValues::toJson));
		JsonValues.put(json, "name", name);
		JsonValues.put(json, "signature", signature);
		JsonValues.put(json, "source", JsonValues.toJson(source));
		JsonValues.put(json, "summary", summary);
		return json;
	}

	public static Value fromJson(Object json) throws JsonException {
		return fromJson(json, "Value");
	}

	/** @param what how to name this value in an error */
	public static Value fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		Value ret = new Value();
		ret.t = JsonValues.string(m.get("t"), what+".t");
		if( ret.t == null ) {
			throw new JsonException(what+" needs t");
		}
		ret.v = JsonValues.any(m.get("v"), what+".v");
		ret.kind = JsonValues.string(m.get("kind"), what+".kind");
		ret.length = JsonValues.integer(m.get("length"), what+".length");
		ret.truncated = JsonValues.bool(m.get("truncated"), what+".truncated");
		ret.id = JsonValues.string(m.get("id"), what+".id");
		ret.type = JsonValues.string(m.get("type"), what+".type");
		ret.size = JsonValues.integer(m.get("size"), what+".size");
		ret.items = JsonValues.list(m.get("items"), what+".items", Value::fromJson);
		ret.entries = JsonValues.list(m.get("entries"), what+".entries", (o, w) -> JsonValues.list(o, w, Value::fromJson));
		ret.fields = JsonValues.list(m.get("fields"), what+".fields", Variable::fromJson);
		ret.name = JsonValues.string(m.get("name"), what+".name");
		ret.signature = JsonValues.string(m.get("signature"), what+".signature");
		ret.source = Source.fromJson(m.get("source"), what+".source");
		ret.summary = JsonValues.string(m.get("summary"), what+".summary");
		return ret;
	}

	public static Value ofNull() {
		return new Value().setT(NULL);
	}

	public static Value of(boolean v) {
		return new Value().setT(BOOL).setV(v);
	}

	public static Value of(long v) {
		return new Value().setT(NUMBER).setKind(INT).setV(v);
	}

	public static Value of(double v) {
		return new Value().setT(NUMBER).setKind(FLOAT).setV(v);
	}

	public static Value of(java.math.BigInteger v) {
		return new Value().setT(NUMBER).setKind(BIG).setV(v.toString());
	}

	public static Value of(String v) {
		return new Value().setT(STRING).setV(v).setLength(v.length());
	}

	/** An object, not expanded: expand it with the children request. */
	public static Value ref(String id, String type, String summary) {
		return new Value().setT(REF).setId(id).setType(type).setSummary(summary);
	}
}
