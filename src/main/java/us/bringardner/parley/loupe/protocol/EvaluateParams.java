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

/** The params of evaluate. */
public class EvaluateParams implements JsonValue {

	private String expression;
	private String frame;
	private String context;

	/** The expression, in the debuggee's language. */
	public String getExpression() {
		return expression;
	}

	public EvaluateParams setExpression(String expression) {
		this.expression = expression;
		return this;
	}

	/** The id of the frame to evaluate in; the top frame of the stopped thread if absent. */
	public String getFrame() {
		return frame;
	}

	public EvaluateParams setFrame(String frame) {
		this.frame = frame;
		return this;
	}

	/** Why: see {@link EvaluateContext}. */
	public String getContext() {
		return context;
	}

	public EvaluateParams setContext(String context) {
		this.context = context;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "expression", expression);
		JsonValues.put(json, "frame", frame);
		JsonValues.put(json, "context", context);
		return json;
	}

	public static EvaluateParams fromJson(Object json) throws JsonException {
		return fromJson(json, "EvaluateParams");
	}

	/** @param what how to name this value in an error */
	public static EvaluateParams fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		EvaluateParams ret = new EvaluateParams();
		ret.expression = JsonValues.string(m.get("expression"), what+".expression");
		if( ret.expression == null ) {
			throw new JsonException(what+" needs expression");
		}
		ret.frame = JsonValues.string(m.get("frame"), what+".frame");
		ret.context = JsonValues.string(m.get("context"), what+".context");
		return ret;
	}
}
