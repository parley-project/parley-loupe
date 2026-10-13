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
package us.bringardner.parley.loupe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks a parsed JSON value against a JSON Schema, for the launch arguments an adapter
 * publishes as {@code launchSchema}.
 * <p>
 * The part of JSON Schema that describing arguments needs: {@code type} (one name or a list),
 * {@code enum}, {@code const}, {@code properties}, {@code required},
 * {@code additionalProperties} (false or a schema), {@code items}, {@code minimum},
 * {@code maximum}, {@code minLength}, {@code maxLength}, {@code pattern}, {@code minItems},
 * {@code maxItems}, {@code allOf}, {@code anyOf} and {@code oneOf}. Other keywords
 * ({@code $schema}, {@code title}, {@code description}, {@code default} ...) are ignored.
 * {@code $ref} is not supported: a schema that uses it makes {@link #validate} throw
 * IllegalArgumentException, so the mistake is the schema author's to see, not hidden.
 * <p>
 * Values are what Json.parse makes: Map, List, String, Long, BigInteger, Double, Boolean, null.
 */
public final class SchemaValidator {

	/** One thing wrong: where (a JSON Pointer into the value, "" for the value itself) and what. */
	public static final class Error {
		private final String pointer;
		private final String message;

		Error(String pointer, String message) {
			this.pointer = pointer;
			this.message = message;
		}

		public String getPointer() {
			return pointer;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public String toString() {
			return (pointer.isEmpty() ? "/" : pointer)+": "+message;
		}
	}

	private SchemaValidator() {
	}

	/** @return what is wrong with value, empty if it's valid */
	public static List<Error> validate(Object schema, Object value) {
		List<Error> errors = new ArrayList<>();
		check(schema, value, "", errors);
		return errors;
	}

	/**
	 * @throws LoupeException invalidParams, with data.errors a list of {pointer, message}
	 */
	public static void check(Object schema, Object value) {
		List<Error> errors = validate(schema, value);
		if( !errors.isEmpty()) {
			List<Object> list = new ArrayList<>();
			for(Error e : errors) {
				Map<String,Object> m = new LinkedHashMap<>();
				m.put("pointer", e.getPointer());
				m.put("message", e.getMessage());
				list.add(m);
			}
			Map<String,Object> data = new LinkedHashMap<>();
			data.put("errors", list);
			throw new LoupeException(LoupeException.INVALID_PARAMS, errors.get(0).toString(), data);
		}
	}

	private static String child(String pointer, String name) {
		return pointer+"/"+name.replace("~", "~0").replace("/", "~1");
	}

	private static void check(Object schema, Object v, String at, List<Error> errors) {
		if( schema instanceof Boolean ) {
			if( !((Boolean) schema)) {
				errors.add(new Error(at, "Not allowed"));
			}
			return;
		}
		if( !(schema instanceof Map)) {
			throw new IllegalArgumentException("A schema is an object or a boolean, not "+schema);
		}
		Map<?,?> s = (Map<?,?>) schema;
		if( s.containsKey("$ref")) {
			throw new IllegalArgumentException("$ref isn't supported");
		}

		Object type = s.get("type");
		if( type != null && !typeMatches(type, v)) {
			errors.add(new Error(at, "Expected "+type+" but found "+typeOf(v)));
			return;
		}
		if( s.containsKey("const") && !same(s.get("const"), v)) {
			errors.add(new Error(at, "Must be "+s.get("const")));
		}
		if( s.get("enum") instanceof List ) {
			boolean found = false;
			for(Object o : (List<?>) s.get("enum")) {
				if( same(o, v)) {
					found = true;
					break;
				}
			}
			if( !found ) {
				errors.add(new Error(at, "Must be one of "+s.get("enum")));
			}
		}

		if( v instanceof Number ) {
			double d = ((Number) v).doubleValue();
			if( s.get("minimum") instanceof Number && d < ((Number) s.get("minimum")).doubleValue()) {
				errors.add(new Error(at, "Must be at least "+s.get("minimum")));
			}
			if( s.get("maximum") instanceof Number && d > ((Number) s.get("maximum")).doubleValue()) {
				errors.add(new Error(at, "Must be at most "+s.get("maximum")));
			}
		}
		if( v instanceof String ) {
			String str = (String) v;
			int len = str.codePointCount(0, str.length());
			if( s.get("minLength") instanceof Number && len < ((Number) s.get("minLength")).intValue()) {
				errors.add(new Error(at, "Must have at least "+s.get("minLength")+" characters"));
			}
			if( s.get("maxLength") instanceof Number && len > ((Number) s.get("maxLength")).intValue()) {
				errors.add(new Error(at, "Must have at most "+s.get("maxLength")+" characters"));
			}
			if( s.get("pattern") instanceof String ) {
				try {
					if( !Pattern.compile((String) s.get("pattern")).matcher(str).find()) {
						errors.add(new Error(at, "Must match "+s.get("pattern")));
					}
				} catch (PatternSyntaxException e) {
					throw new IllegalArgumentException("Bad pattern "+s.get("pattern"), e);
				}
			}
		}
		if( v instanceof List ) {
			List<?> list = (List<?>) v;
			if( s.get("minItems") instanceof Number && list.size() < ((Number) s.get("minItems")).intValue()) {
				errors.add(new Error(at, "Must have at least "+s.get("minItems")+" items"));
			}
			if( s.get("maxItems") instanceof Number && list.size() > ((Number) s.get("maxItems")).intValue()) {
				errors.add(new Error(at, "Must have at most "+s.get("maxItems")+" items"));
			}
			if( s.containsKey("items")) {
				for(int i=0; i < list.size(); i++) {
					check(s.get("items"), list.get(i), at+"/"+i, errors);
				}
			}
		}
		if( v instanceof Map ) {
			Map<?,?> obj = (Map<?,?>) v;
			Map<?,?> props = s.get("properties") instanceof Map ? (Map<?,?>) s.get("properties") : null;
			if( s.get("required") instanceof List ) {
				for(Object name : (List<?>) s.get("required")) {
					if( !obj.containsKey(name)) {
						errors.add(new Error(at, "Missing required member "+name));
					}
				}
			}
			for(Map.Entry<?,?> e : obj.entrySet()) {
				String name = String.valueOf(e.getKey());
				if( props != null && props.containsKey(name)) {
					check(props.get(name), e.getValue(), child(at, name), errors);
				} else if( s.containsKey("additionalProperties")) {
					Object ap = s.get("additionalProperties");
					if( ap instanceof Boolean && !((Boolean) ap)) {
						errors.add(new Error(child(at, name), "Unknown member"));
					} else if( ap instanceof Map ) {
						check(ap, e.getValue(), child(at, name), errors);
					}
				}
			}
		}

		if( s.get("allOf") instanceof List ) {
			for(Object sub : (List<?>) s.get("allOf")) {
				check(sub, v, at, errors);
			}
		}
		if( s.get("anyOf") instanceof List ) {
			int ok = 0;
			for(Object sub : (List<?>) s.get("anyOf")) {
				if( validate(sub, v).isEmpty()) {
					ok++;
				}
			}
			if( ok == 0 ) {
				errors.add(new Error(at, "Matches none of the alternatives"));
			}
		}
		if( s.get("oneOf") instanceof List ) {
			int ok = 0;
			for(Object sub : (List<?>) s.get("oneOf")) {
				if( validate(sub, v).isEmpty()) {
					ok++;
				}
			}
			if( ok != 1 ) {
				errors.add(new Error(at, ok == 0 ? "Matches none of the alternatives" : "Matches more than one alternative"));
			}
		}
	}

	private static String typeOf(Object v) {
		if( v == null ) {
			return "null";
		} else if( v instanceof Boolean ) {
			return "boolean";
		} else if( v instanceof String ) {
			return "string";
		} else if( v instanceof Map ) {
			return "object";
		} else if( v instanceof List ) {
			return "array";
		} else if( v instanceof Long || v instanceof BigInteger ) {
			return "integer";
		}
		return "number";
	}

	private static boolean typeMatches(Object type, Object v) {
		if( type instanceof List ) {
			for(Object t : (List<?>) type) {
				if( typeMatches(t, v)) {
					return true;
				}
			}
			return false;
		}
		String actual = typeOf(v);
		if( actual.equals(type)) {
			return true;
		}
		if( "number".equals(type)) {
			return actual.equals("integer");
		}
		// 1.0 is an integer in JSON Schema
		return "integer".equals(type) && v instanceof Double && ((Double) v) == Math.rint((Double) v);
	}

	/** Equal as JSON: 1 and 1.0 are the same number. */
	private static boolean same(Object a, Object b) {
		if( a instanceof Number && b instanceof Number ) {
			return ((Number) a).doubleValue() == ((Number) b).doubleValue()
					&& (!(a instanceof BigInteger) && !(b instanceof BigInteger) || a.equals(b));
		}
		if( a instanceof List && b instanceof List ) {
			List<?> x = (List<?>) a;
			List<?> y = (List<?>) b;
			if( x.size() != y.size()) {
				return false;
			}
			for(int i=0; i < x.size(); i++) {
				if( !same(x.get(i), y.get(i))) {
					return false;
				}
			}
			return true;
		}
		if( a instanceof Map && b instanceof Map ) {
			Map<?,?> x = (Map<?,?>) a;
			Map<?,?> y = (Map<?,?>) b;
			if( !x.keySet().equals(y.keySet())) {
				return false;
			}
			for(Object k : x.keySet()) {
				if( !same(x.get(k), y.get(k))) {
					return false;
				}
			}
			return true;
		}
		return a == null ? b == null : a.equals(b);
	}
}
