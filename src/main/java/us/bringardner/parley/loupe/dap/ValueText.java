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
package us.bringardner.parley.loupe.dap;

import us.bringardner.parley.loupe.protocol.Value;

/** How the bridge shows a Loupe value as the text a DAP client displays. */
final class ValueText {

	private ValueText() {
	}

	/** The text for a value. */
	static String of(Value v) {
		if( v == null || v.getT() == null ) {
			return "";
		}
		switch (v.getT()) {
		case Value.NULL:
			return "null";
		case Value.BOOL:
			return String.valueOf(v.getV());
		case Value.NUMBER:
			return String.valueOf(v.getV());
		case Value.STRING:
			return quote(String.valueOf(v.getV()))+(Boolean.TRUE.equals(v.getTruncated()) ? "…" : "");
		case Value.ARRAY:
			return type(v, "array")+"["+(v.getSize() == null ? "" : v.getSize())+"]";
		case Value.MAP:
			return type(v, "map")+"{"+(v.getSize() == null ? "" : v.getSize())+"}";
		case Value.OBJECT:
			return v.getSummary() != null ? v.getSummary() : type(v, "object");
		case Value.FUNCTION:
			String name = v.getName() == null ? "function" : v.getName();
			return "function "+name+(v.getSignature() == null ? "()" : v.getSignature());
		case Value.REF:
			return v.getSummary() != null ? v.getSummary() : type(v, "object");
		default:
			return String.valueOf(v.getV());
		}
	}

	static String type(Value v) {
		return v.getType() != null ? v.getType() : v.getT();
	}

	private static String type(Value v, String fallback) {
		return v.getType() != null ? v.getType() : fallback;
	}

	/** Whether the value has parts to expand (it has an id of an array, map, object or ref). */
	static boolean expandable(Value v) {
		if( v == null || v.getId() == null ) {
			return false;
		}
		String t = v.getT();
		return Value.ARRAY.equals(t) || Value.MAP.equals(t) || Value.OBJECT.equals(t) || Value.REF.equals(t);
	}

	private static String quote(String s) {
		StringBuilder b = new StringBuilder(s.length()+2).append('"');
		for(int i=0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"': b.append("\\\""); break;
			case '\\': b.append("\\\\"); break;
			case '\n': b.append("\\n"); break;
			case '\r': b.append("\\r"); break;
			case '\t': b.append("\\t"); break;
			default: b.append(c);
			}
		}
		return b.append('"').toString();
	}
}
