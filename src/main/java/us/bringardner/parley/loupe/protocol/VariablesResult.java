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

/** The result of locals and children. */
public class VariablesResult implements JsonValue {

	private List<Variable> variables;
	private Integer total;

	/** The variables asked for. */
	public List<Variable> getVariables() {
		return variables;
	}

	public VariablesResult setVariables(List<Variable> variables) {
		this.variables = variables;
		return this;
	}

	/** How many there are, if known. */
	public Integer getTotal() {
		return total;
	}

	public VariablesResult setTotal(Integer total) {
		this.total = total;
		return this;
	}

	@Override
	public Object toJson() {
		Map<String,Object> json = new LinkedHashMap<>();
		JsonValues.put(json, "variables", JsonValues.toJson(variables, JsonValues::toJson));
		JsonValues.put(json, "total", total);
		return json;
	}

	public static VariablesResult fromJson(Object json) throws JsonException {
		return fromJson(json, "VariablesResult");
	}

	/** @param what how to name this value in an error */
	public static VariablesResult fromJson(Object json, String what) throws JsonException {
		if( json == null ) {
			return null;
		}
		Map<String,Object> m = JsonValues.object(json, what);
		VariablesResult ret = new VariablesResult();
		ret.variables = JsonValues.list(m.get("variables"), what+".variables", Variable::fromJson);
		if( ret.variables == null ) {
			throw new JsonException(what+" needs variables");
		}
		ret.total = JsonValues.integer(m.get("total"), what+".total");
		return ret;
	}
}
