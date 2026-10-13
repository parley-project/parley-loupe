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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LaunchParams;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ObjectId;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.Source;
import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.TargetStateEvent;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.Variable;
import us.bringardner.parley.loupe.protocol.Welcome;

public class TestProtocol {

	@Test
	public void helloAndWelcomeRoundTrip() throws Exception {
		Hello h = new Hello().setProtocols(List.of("loupe/1")).setClient(new PeerInfo().setName("fsh-ide").setVersion("1.0"))
				.setFeatures(List.of(Loupe.FEATURE_SNAPSHOT));
		String json = Json.write(h);
		assertEquals("{\"protocols\":[\"loupe/1\"],\"client\":{\"name\":\"fsh-ide\",\"version\":\"1.0\"},\"features\":[\"snapshot\"]}", json);
		Hello back = Hello.fromJson(Json.parse(json));
		assertEquals(List.of("loupe/1"), back.getProtocols());
		assertEquals("fsh-ide", back.getClient().getName());

		Welcome w = new Welcome().setProtocol("loupe/1").setFeatures(List.of()).setLaunchSchema(Json.parse("{\"type\":\"object\"}"));
		assertEquals(Map.of("type", "object"), Welcome.fromJson(Json.parse(Json.write(w))).getLaunchSchema());
	}

	@Test
	public void requiredMembersAreChecked() {
		assertThrows(JsonException.class, ()->Hello.fromJson(Json.parse("{}")));
		assertThrows(JsonException.class, ()->Hello.fromJson(Json.parse("{\"protocols\":\"loupe/1\"}")));
		assertThrows(JsonException.class, ()->Hello.fromJson(Json.parse("{\"protocols\":[1]}")));
		assertThrows(JsonException.class, ()->LaunchParams.fromJson(Json.parse("{\"args\":{}}")));
		JsonException e = assertThrows(JsonException.class, ()->Hello.fromJson(Json.parse("{\"protocols\":[\"a\"],\"client\":{}}")));
		assertTrue(e.getMessage().contains("Hello.client"), e.getMessage());
	}

	@Test
	public void snapshotRoundTrip() throws Exception {
		Snapshot s = new Snapshot()
				.setThreads(List.of(new ThreadInfo().setId(1).setName("main").setState(TargetState.PAUSED)))
				.setThread(1)
				.setFrames(List.of(new Frame().setId("f1@12").setName("main").setSource(new Source().setPath("/x.fsh")).setLine(10).setColumn(3)))
				.setLocals(List.of(new Variable().setName("n").setValue(Value.of(3L))));
		TargetStateEvent ev = new TargetStateEvent().setState(TargetState.PAUSED).setFrom(TargetState.RUNNING)
				.setReason("breakpoint").setGeneration(12).setBreakpoints(List.of("b1")).setSnapshot(s);
		String json = Json.write(ev);
		TargetStateEvent back = TargetStateEvent.fromJson(Json.parse(json));
		assertEquals(json, Json.write(back));
		assertEquals("f1@12", back.getSnapshot().getFrames().get(0).getId());
		assertEquals("int", back.getSnapshot().getLocals().get(0).getValue().getKind());
		assertEquals(3L, back.getSnapshot().getLocals().get(0).getValue().getV());
	}

	@Test
	public void valuesAreTyped() throws Exception {
		Value map = new Value().setT(Value.MAP).setId("o2@5").setType("Map").setSize(2)
				.setEntries(List.of(List.of(Value.of("a"), Value.of(1.5)), List.of(Value.of("b"), Value.ofNull())));
		Value back = Value.fromJson(Json.parse(Json.write(map)));
		assertEquals(2, back.getEntries().size());
		assertEquals("a", back.getEntries().get(0).get(0).getV());
		assertEquals(Value.FLOAT, back.getEntries().get(0).get(1).getKind());
		assertNull(back.getEntries().get(1).get(1).getV());
		assertEquals("{\"t\":\"number\",\"v\":\"123456789012345678901234\",\"kind\":\"big\"}",
				Json.write(Value.of(new BigInteger("123456789012345678901234"))));
		assertEquals("{\"t\":\"bool\",\"v\":false}", Json.write(Value.of(false)));
		assertEquals("{\"t\":\"ref\",\"id\":\"o9@5\",\"type\":\"Foo\",\"summary\":\"Foo(1)\"}", Json.write(Value.ref("o9@5", "Foo", "Foo(1)")));
		assertThrows(JsonException.class, ()->Value.fromJson(Json.parse("{\"v\":1}")));
	}

	@Test
	public void objectIds() throws Exception {
		ObjectId id = ObjectId.parse("o12@5");
		assertEquals("o12", id.getName());
		assertEquals(5, id.getGeneration());
		assertEquals("o12@6", id.at(6).toString());
		assertFalse(ObjectId.parse("o12").hasGeneration());
		assertEquals("o12", ObjectId.parse("o12").toString());
		for(String bad : new String[] {"", "@5", "o@", "o@x", "o@-1", "o@99999999999", "o@1@2"}) {
			assertThrows(JsonException.class, ()->ObjectId.parse(bad), bad);
		}
	}

	@Test
	public void stateMachine() {
		TargetStateMachine m = new TargetStateMachine();
		assertNull(m.getState());
		assertThrows(LoupeException.class, ()->m.move(TargetState.PAUSED, "pause"));
		TargetStateEvent e = m.move(TargetState.RUNNING, "launch");
		assertNull(e.getFrom());
		assertEquals(0, e.getGeneration());
		e = m.move(TargetState.PAUSED, "breakpoint");
		assertEquals(TargetState.RUNNING, e.getFrom());
		assertEquals(1, e.getGeneration());
		LoupeException ex = assertThrows(LoupeException.class, ()->m.move(TargetState.PAUSED, "pause"));
		assertEquals(LoupeException.WRONG_STATE, ex.getCode());
		assertEquals(Map.of("state", "paused"), ex.getData());
		m.require(TargetState.PAUSED);
		assertThrows(LoupeException.class, ()->m.require(TargetState.RUNNING));
		m.move(TargetState.RUNNING, "resume");
		assertEquals(2, m.move(TargetState.PAUSED, "step").getGeneration());
		m.move(TargetState.TERMINATED, "exit");
		assertTrue(m.isTerminated());
		assertThrows(LoupeException.class, ()->m.move(TargetState.RUNNING, "launch"));
		assertEquals(2, m.getGeneration());
	}

	@Test
	public void negotiation() {
		Hello h = new Hello().setProtocols(Arrays.asList("loupe/3", "loupe/2", "loupe/1"))
				.setFeatures(Arrays.asList("snapshot", "objectChanges", "future"));
		Welcome w = Handshake.accept(h, List.of("loupe/1", "loupe/2"), List.of("snapshot", "other", "objectChanges"),
				new PeerInfo().setName("fsh"), null);
		assertEquals("loupe/2", w.getProtocol());
		assertEquals(List.of("snapshot", "objectChanges"), w.getFeatures());

		// no features asked for: none shared
		assertEquals(List.of(), Handshake.accept(new Hello().setProtocols(List.of("loupe/1")), List.of("loupe/1"), List.of("snapshot"), null, null).getFeatures());

		LoupeException e = assertThrows(LoupeException.class,
				()->Handshake.accept(new Hello().setProtocols(List.of("loupe/9")), List.of("loupe/1"), List.of(), null, null));
		assertEquals(LoupeException.NOT_SUPPORTED, e.getCode());
		assertEquals(List.of("loupe/1"), e.getData().get("protocols"));
	}

	@Test
	public void helloOverAConnection() throws Exception {
		LoupeEndpoint.Handler adapter = new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				if( Loupe.HELLO.equals(method)) {
					return CompletableFuture.completedFuture(Handshake.answer(params, List.of(Loupe.PROTOCOL),
							List.of(Loupe.FEATURE_SNAPSHOT), new PeerInfo().setName("fsh").setVersion("2"),
							Map.of("type", "object", "required", List.of("script"))));
				}
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@Override
			public void event(String event, String target, Object params) {
			}
		};
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(aOut, 1<<16);
		try (LoupeConnection a = LoupeConnection.open(adapter, aIn, aOut);
				LoupeConnection c = LoupeConnection.open(null, cIn, cOut)) {
			Welcome w = Handshake.hello(c.getEndpoint(), new Hello().setProtocols(List.of("loupe/1", "loupe/7"))
					.setFeatures(List.of(Loupe.FEATURE_SNAPSHOT, Loupe.FEATURE_OBJECT_CHANGES))).get(5, TimeUnit.SECONDS);
			assertEquals("loupe/1", w.getProtocol());
			assertEquals(List.of("snapshot"), w.getFeatures());
			assertEquals("fsh", w.getAdapter().getName());

			// the schema it published checks launch args
			Object schema = w.getLaunchSchema();
			assertTrue(SchemaValidator.validate(schema, Map.of("script", "a.fsh")).isEmpty());
			assertEquals(1, SchemaValidator.validate(schema, Map.of()).size());

			ExecutionException e = assertThrows(ExecutionException.class,
					()->Handshake.hello(c.getEndpoint(), new Hello().setProtocols(List.of("loupe/7"))).get(5, TimeUnit.SECONDS));
			assertEquals(LoupeException.NOT_SUPPORTED, ((LoupeException) e.getCause()).getCode());
		}
	}
}
