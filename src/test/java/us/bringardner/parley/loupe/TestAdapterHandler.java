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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.loupe.protocol.BreakpointStatus;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.Source;
import us.bringardner.parley.loupe.protocol.Welcome;

public class TestAdapterHandler {

	private static final Object LAUNCH_SCHEMA = parse("{\"type\":\"object\",\"required\":[\"script\"],\"additionalProperties\":false,"
			+ "\"properties\":{\"script\":{\"type\":\"string\"},\"stopOnEntry\":{\"type\":\"boolean\"}}}");

	private static Object parse(String json) {
		try {
			return Json.parse(json);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	/** Remembers what it was asked; launches fail for the script "boom". */
	static class Backend implements TargetBackend {
		final List<String> calls = new CopyOnWriteArrayList<>();
		volatile boolean verifyAtOnce;

		@Override
		public CompletableFuture<Void> launch(Target target, Object args) {
			calls.add("launch "+target.getId()+" "+args);
			Map<?,?> m = (Map<?,?>) args;
			if( "boom".equals(m.get("script"))) {
				throw new LoupeException(LoupeException.FAILED, "Can't start boom");
			}
			if( Boolean.TRUE.equals(m.get("stopOnEntry"))) {
				target.paused("entry", new Snapshot().setFrames(List.of(new Frame().setId("f1@1").setName("main"))), null);
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void breakpointAdded(Target target, BreakpointTable.Entry bp) {
			calls.add("added "+bp.getId());
			if( verifyAtOnce ) {
				target.setBreakpointStatus(bp.getId(), BreakpointStatus.VERIFIED,
						new Location().setSource(bp.getWhere().getSource()).setLine(bp.getWhere().getLine()+1), null);
			}
		}

		@Override
		public void breakpointRemoved(Target target, String id) {
			calls.add("removed "+id);
		}
	}

	private final Backend backend = new Backend();
	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	private LoupeConnection adapter;
	private LoupeConnection client;
	private AdapterHandler handler;

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(aOut, 1<<16);
		adapter = LoupeConnection.serve(ep -> {
			handler = new AdapterHandler(ep, backend)
					.setAdapter(new PeerInfo().setName("test"))
					.setFeatures(List.of(Loupe.FEATURE_SNAPSHOT))
					.setLaunchSchema(LAUNCH_SCHEMA);
			return handler;
		}, aIn, aOut);
		client = LoupeConnection.open(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@Override
			public void event(String event, String target, Object params) {
				events.add(event+" "+target+" "+Json.write(params));
			}
		}, cIn, cOut);
	}

	@AfterEach
	public void disconnect() {
		client.close();
		adapter.close();
	}

	private Object call(String method, String target, Object params) throws Exception {
		return client.getEndpoint().request(method, target, params).get(5, TimeUnit.SECONDS);
	}

	private LoupeException fails(String method, String target, Object params) {
		ExecutionException e = assertThrows(ExecutionException.class, ()->call(method, target, params));
		return (LoupeException) e.getCause();
	}

	private Welcome hello(String... features) throws Exception {
		return Welcome.fromJson(call("hello", null, new Hello().setProtocols(List.of("loupe/1")).setFeatures(List.of(features)).toJson()));
	}

	private static Map<String,Object> launch(String target, Object args) {
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("target", target);
		m.put("args", args);
		return m;
	}

	private String event() throws Exception {
		return events.poll(5, TimeUnit.SECONDS);
	}

	@Test
	public void nothingButHelloBeforeHello() throws Exception {
		assertEquals(LoupeException.WRONG_STATE, fails("launch", null, launch("t1", Map.of("script", "a"))).getCode());
		assertEquals(LoupeException.WRONG_STATE, fails("whatever", null, null).getCode());
		hello();
		assertEquals(LoupeException.WRONG_STATE, fails("hello", null, new Hello().setProtocols(List.of("loupe/1")).toJson()).getCode());
	}

	@Test
	public void welcomePublishesTheSchemasAndTheSharedFeatures() throws Exception {
		Welcome w = hello(Loupe.FEATURE_SNAPSHOT, Loupe.FEATURE_OBJECT_CHANGES);
		assertEquals(List.of("snapshot"), w.getFeatures());
		assertEquals(LAUNCH_SCHEMA, w.getLaunchSchema());
		assertNull(w.getAttachSchema());
		assertEquals("test", w.getAdapter().getName());
		// the methods that are always there; this backend supports no others, and can't attach
		assertEquals(List.of("launch", "addBreakpoint", "removeBreakpoint", "cancel"), w.getMethods());
	}

	@Test
	public void launchChecksArgsThenRuns() throws Exception {
		hello();
		LoupeException e = fails("launch", null, launch("t1", Map.of("script", 3L)));
		assertEquals(LoupeException.INVALID_PARAMS, e.getCode());
		assertEquals("/script", ((Map<?,?>) ((List<?>) e.getData().get("errors")).get(0)).get("pointer"));
		assertTrue(backend.calls.isEmpty());
		assertEquals(0, handler.getTargets().list().size());

		Object r = call("launch", null, launch("t1", Map.of("script", "a.fsh")));
		assertEquals(Map.of("target", "t1", "state", "running"), r);
		assertEquals("targetState t1 {\"state\":\"running\",\"reason\":\"launch\",\"generation\":0}", event());
		assertEquals(List.of("launch t1 {script=a.fsh}"), backend.calls);

		// an id is used once
		assertEquals(LoupeException.INVALID_PARAMS, fails("launch", null, launch("t1", Map.of("script", "a.fsh"))).getCode());
	}

	@Test
	public void aFailedLaunchTerminatesTheTarget() throws Exception {
		hello();
		LoupeException e = fails("launch", null, launch("t1", Map.of("script", "boom")));
		assertEquals("Can't start boom", e.getMessage());
		assertTrue(event().contains("\"state\":\"running\""));
		assertEquals("targetState t1 {\"state\":\"terminated\",\"from\":\"running\",\"reason\":\"launchFailed\",\"generation\":0}", event());
		assertTrue(handler.getTargets().get("t1").isTerminated());
	}

	@Test
	public void theBackendMayPauseAtOnceAndTheSnapshotFollowsTheFeature() throws Exception {
		hello(Loupe.FEATURE_SNAPSHOT);
		call("launch", null, launch("t1", Map.of("script", "a", "stopOnEntry", true)));
		events.poll(5, TimeUnit.SECONDS); // running
		String paused = event();
		assertTrue(paused.contains("\"state\":\"paused\"") && paused.contains("\"reason\":\"entry\"") && paused.contains("\"generation\":1"), paused);
		assertTrue(paused.contains("\"snapshot\":{\"frames\":[{\"id\":\"f1@1\",\"name\":\"main\"}]}"), paused);
	}

	@Test
	public void noSnapshotWithoutTheFeature() throws Exception {
		hello();
		call("launch", null, launch("t1", Map.of("script", "a", "stopOnEntry", true)));
		events.poll(5, TimeUnit.SECONDS);
		assertTrue(!event().contains("snapshot"));
	}

	@Test
	public void startPausedNeedsABackendThatPauses() throws Exception {
		hello();
		// stopOnEntry makes this backend pause; without it the launch keeps running, which startPaused forbids
		Map<String,Object> p = launch("t1", Map.of("script", "a", "stopOnEntry", true));
		p.put("startPaused", true);
		assertEquals(Map.of("target", "t1", "state", "paused"), call("launch", null, p));
		assertTrue(handler.getTargets().get("t1").isStartPaused());

		Map<String,Object> q = launch("t2", Map.of("script", "a"));
		q.put("startPaused", true);
		assertEquals(LoupeException.NOT_SUPPORTED, fails("launch", null, q).getCode());
		assertTrue(handler.getTargets().get("t2").isTerminated());
	}

	@Test
	public void attachNeedsAnAttachSchemaAndABackendThatCan() throws Exception {
		hello();
		assertEquals(LoupeException.NOT_SUPPORTED, fails("attach", null, launch("t1", Map.of())).getCode());

		handler.setAttachSchema(parse("{\"type\":\"object\"}"));
		// the default backend can't attach: the target ends
		assertEquals(LoupeException.NOT_SUPPORTED, fails("attach", null, launch("t1", Map.of("pid", 1L))).getCode());
		assertTrue(handler.getTargets().get("t1").isTerminated());
	}

	@Test
	public void breakpointsGoThroughTheBackendAndTheTable() throws Exception {
		hello();
		call("launch", null, launch("t1", Map.of("script", "a")));
		events.poll(5, TimeUnit.SECONDS);

		Map<String,Object> bp = Map.of("id", "b1", "where", Map.of("source", Map.of("path", "/a"), "line", 10L));
		assertEquals(Map.of("status", "pending"), call("addBreakpoint", "t1", bp));
		assertEquals(List.of("launch t1 {script=a}", "added b1"), new ArrayList<>(backend.calls));

		// the backend settles it later: an event
		handler.getTargets().get("t1").setBreakpointStatus("b1", BreakpointStatus.VERIFIED, null, null);
		assertEquals("breakpointStatus t1 {\"id\":\"b1\",\"status\":\"verified\",\"where\":{\"source\":{\"path\":\"/a\"},\"line\":10}}", event());

		// or at once: the result says so
		backend.verifyAtOnce = true;
		Map<String,Object> bp2 = Map.of("id", "b2", "where", Map.of("source", Map.of("path", "/a"), "line", 20L));
		Object r = call("addBreakpoint", "t1", bp2);
		assertEquals("{\"status\":\"verified\",\"where\":{\"source\":{\"path\":\"/a\"},\"line\":21}}", Json.write(r));
		assertTrue(event().contains("\"id\":\"b2\""));

		assertEquals(Map.of(), call("removeBreakpoint", "t1", Map.of("id", "b1")));
		assertEquals("breakpointStatus t1 {\"id\":\"b1\",\"status\":\"removed\",\"where\":{\"source\":{\"path\":\"/a\"},\"line\":10}}", event());
		assertEquals("removed b1", backend.calls.get(backend.calls.size()-1));
		assertEquals(LoupeException.INVALID_PARAMS, fails("removeBreakpoint", "t1", Map.of("id", "b1")).getCode());
	}

	@Test
	public void unknownTargetAndTerminatedTarget() throws Exception {
		hello();
		Map<String,Object> bp = Map.of("id", "b1", "where", Map.of("source", Map.of("path", "/a"), "line", 1L));
		assertEquals(LoupeException.UNKNOWN_TARGET, fails("addBreakpoint", "nope", bp).getCode());
		assertEquals(LoupeException.UNKNOWN_TARGET, fails("addBreakpoint", null, bp).getCode());
		call("launch", null, launch("t1", Map.of("script", "a")));
		handler.getTargets().get("t1").terminated("exit", 0);
		LoupeException e = fails("addBreakpoint", "t1", bp);
		assertEquals(LoupeException.WRONG_STATE, e.getCode());
		assertEquals(Map.of("state", "terminated"), e.getData());
	}

	@Test
	public void otherMethodsGoToTheFallback() throws Exception {
		hello();
		assertEquals(LoupeException.UNKNOWN_METHOD, fails("customThing", "t1", null).getCode());
		handler.setFallback(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				return CompletableFuture.completedFuture(Map.of("method", method));
			}

			@Override
			public void event(String event, String target, Object params) {
			}
		});
		assertEquals(Map.of("method", "customThing"), call("customThing", "t1", null));
	}
}
