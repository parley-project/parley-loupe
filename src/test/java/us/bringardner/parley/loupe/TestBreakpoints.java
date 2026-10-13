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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointResult;
import us.bringardner.parley.loupe.protocol.BreakpointStatus;
import us.bringardner.parley.loupe.protocol.BreakpointStatusEvent;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.RemoveBreakpointParams;
import us.bringardner.parley.loupe.protocol.Source;

public class TestBreakpoints {

	private static AddBreakpointParams bp(String id, int line) {
		return new AddBreakpointParams().setId(id)
				.setWhere(new Location().setSource(new Source().setPath("/x.fsh")).setLine(line));
	}

	@Test
	public void messagesRoundTrip() throws Exception {
		AddBreakpointParams p = bp("b1", 10).setCondition("n > 2").setHitCount(3);
		String json = Json.write(p);
		assertEquals("{\"id\":\"b1\",\"where\":{\"source\":{\"path\":\"/x.fsh\"},\"line\":10},\"condition\":\"n > 2\",\"hitCount\":3}", json);
		assertEquals(json, Json.write(AddBreakpointParams.fromJson(Json.parse(json))));

		BreakpointStatusEvent ev = new BreakpointStatusEvent().setId("b1").setStatus(BreakpointStatus.REJECTED).setMessage("No such line");
		assertEquals("{\"id\":\"b1\",\"status\":\"rejected\",\"message\":\"No such line\"}", Json.write(ev));
		assertEquals("b1", RemoveBreakpointParams.fromJson(Json.parse("{\"id\":\"b1\"}")).getId());
		assertEquals("pending", BreakpointResult.fromJson(Json.parse("{\"status\":\"pending\"}")).getStatus());
		assertThrows(JsonException.class, ()->AddBreakpointParams.fromJson(Json.parse("{\"id\":\"b1\"}")));
		assertThrows(JsonException.class, ()->AddBreakpointParams.fromJson(Json.parse("{\"id\":\"b\",\"where\":{\"line\":1}}")));
	}

	@Test
	public void addReplaceRemove() {
		BreakpointTable t = new BreakpointTable();
		assertEquals(BreakpointStatus.PENDING, t.add(bp("b1", 10)).getStatus());
		t.setStatus("b1", BreakpointStatus.VERIFIED, new Location().setSource(new Source().setPath("/x.fsh")).setLine(11), null);
		assertEquals(11, t.get("b1").getWhere().getLine());
		assertEquals(BreakpointStatus.VERIFIED, t.get("b1").getStatus());

		// the same id again replaces it, and starts again
		t.add(bp("b1", 20));
		assertEquals(1, t.size());
		assertEquals(BreakpointStatus.PENDING, t.get("b1").getStatus());
		assertEquals(20, t.get("b1").getWhere().getLine());

		t.add(bp("b2", 5));
		assertEquals(List.of("b1", "b2"), t.list().stream().map(BreakpointTable.Entry::getId).collect(java.util.stream.Collectors.toList()));
		BreakpointStatusEvent ev = t.remove("b1");
		assertEquals(BreakpointStatus.REMOVED, ev.getStatus());
		assertEquals("b1", ev.getId());
		assertEquals(1, t.size());
		assertThrows(LoupeException.class, ()->t.remove("b1"));
	}

	@Test
	public void rejectsBadBreakpoints() {
		BreakpointTable t = new BreakpointTable();
		assertEquals(LoupeException.INVALID_PARAMS, assertThrows(LoupeException.class, ()->t.add(bp("", 1))).getCode());
		assertThrows(LoupeException.class, ()->t.add(bp("b", 0)));
		assertThrows(LoupeException.class, ()->t.add(bp("b", 1).setHitCount(0)));
		assertThrows(LoupeException.class, ()->t.add(new AddBreakpointParams().setId("b")));
		assertThrows(LoupeException.class, ()->t.setStatus("nope", BreakpointStatus.VERIFIED, null, null));
		t.add(bp("b", 1));
		assertThrows(LoupeException.class, ()->t.setStatus("b", BreakpointStatus.REMOVED, null, null));
		assertEquals(1, t.size());
	}

	@Test
	public void hitCountsAndRejected() {
		BreakpointTable t = new BreakpointTable();
		t.add(bp("b1", 10).setHitCount(3));
		assertFalse(t.hit("b1"));
		assertFalse(t.hit("b1"));
		assertTrue(t.hit("b1"));
		assertTrue(t.hit("b1"));
		assertEquals(4, t.get("b1").getHits());

		t.add(bp("b2", 10));
		assertTrue(t.hit("b2"));
		t.setStatus("b2", BreakpointStatus.REJECTED, null, "No code on that line");
		assertFalse(t.hit("b2"));
		assertEquals("No code on that line", t.get("b2").getMessage());
		assertFalse(t.hit("unknown"));
	}
}
