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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;

public class TestSchemaValidator {

	private static final Object LAUNCH = parse("{\"type\":\"object\",\"required\":[\"script\"],\"additionalProperties\":false,"
			+ "\"properties\":{\"script\":{\"type\":\"string\",\"minLength\":1},\"args\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
			+ "\"port\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":65535},\"mode\":{\"enum\":[\"run\",\"debug\"]},"
			+ "\"env\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}}}");

	private static Object parse(String json) {
		try {
			return Json.parse(json);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static List<SchemaValidator.Error> errors(String json) {
		return SchemaValidator.validate(LAUNCH, parse(json));
	}

	@Test
	public void acceptsValidArguments() {
		assertTrue(errors("{\"script\":\"a.fsh\",\"args\":[\"x\"],\"port\":8000,\"mode\":\"debug\",\"env\":{\"A\":\"b\"}}").isEmpty());
		assertTrue(errors("{\"script\":\"a.fsh\",\"port\":8000.0}").isEmpty());
	}

	@Test
	public void pointsAtEachProblem() {
		List<SchemaValidator.Error> e = errors("{\"args\":[\"x\",2],\"port\":0,\"mode\":\"fly\",\"env\":{\"a/b\":1},\"extra\":true}");
		StringBuilder b = new StringBuilder();
		for(SchemaValidator.Error x : e) {
			b.append(x.getPointer()).append(' ');
		}
		assertEquals(6, e.size(), b.toString());
		assertTrue(b.toString().contains("/args/1 "), b.toString());
		assertTrue(b.toString().contains("/port "), b.toString());
		assertTrue(b.toString().contains("/mode "), b.toString());
		assertTrue(b.toString().contains("/env/a~1b "), b.toString());
		assertTrue(b.toString().contains("/extra "), b.toString());
	}

	@Test
	public void checkThrowsInvalidParamsWithTheErrors() {
		LoupeException e = assertThrows(LoupeException.class, ()->SchemaValidator.check(LAUNCH, parse("{\"script\":3}")));
		assertEquals(LoupeException.INVALID_PARAMS, e.getCode());
		List<?> list = (List<?>) e.getData().get("errors");
		assertEquals("/script", ((Map<?,?>) list.get(0)).get("pointer"));
	}

	@Test
	public void combinators() {
		Object s = parse("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}");
		assertTrue(SchemaValidator.validate(s, "a").isEmpty());
		assertTrue(SchemaValidator.validate(s, 3L).isEmpty());
		assertEquals(1, SchemaValidator.validate(s, true).size());
		Object any = parse("{\"anyOf\":[{\"type\":\"number\"},{\"type\":\"null\"}]}");
		assertTrue(SchemaValidator.validate(any, null).isEmpty());
		assertTrue(SchemaValidator.validate(any, 1.5).isEmpty());
		assertEquals(1, SchemaValidator.validate(any, "x").size());
	}

	@Test
	public void refIsRejected() {
		assertThrows(IllegalArgumentException.class, ()->SchemaValidator.validate(parse("{\"$ref\":\"#/x\"}"), 1L));
	}
}
