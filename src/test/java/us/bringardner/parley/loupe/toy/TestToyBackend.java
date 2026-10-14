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
package us.bringardner.parley.loupe.toy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.LoupeEndpoint;
import us.bringardner.parley.loupe.LoupeException;
import us.bringardner.parley.loupe.protocol.EvaluateResult;
import us.bringardner.parley.loupe.protocol.FramesResult;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.VariablesResult;

/** The toy backend, driven by a raw Loupe client the way an IDE would. */
public class TestToyBackend {

	static final String PROGRAM = String.join("\n",
			"def fact(n)",						// 1
			"    if n <= 1",					// 2
			"        return 1",					// 3
			"    end",							// 4
			"    return n * fact(n - 1)",		// 5
			"end",								// 6
			"xs = [1, 2, 3]",					// 7
			"total = fact(4)",					// 8
			"xs[0] = total",					// 9
			"print xs",							// 10
			"print xs[7]");						// 11

	private LoupeConnection adapter;
	private LoupeConnection client;
	private Map<String,Object> welcome;
	private final BlockingQueue<Map<String,Object>> events = new LinkedBlockingQueue<>();

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(aOut, 1<<16);
		adapter = LoupeConnection.serve(ToyAdapterMain::handler, aIn, aOut);
		client = LoupeConnection.open(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@SuppressWarnings("unchecked")
			@Override
			public void event(String event, String target, Object params) {
				Map<String,Object> m = new java.util.LinkedHashMap<>((Map<String,Object>) params);
				m.put("event", event);
				events.add(m);
			}
		}, cIn, cOut);
		welcome = call("hello", null, new Hello().setProtocols(List.of("loupe/1")).setFeatures(List.of(Loupe.FEATURE_SNAPSHOT)).toJson());
	}

	@AfterEach
	public void disconnect() {
		client.close();
		adapter.close();
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> call(String method, String target, Object params) throws Exception {
		return (Map<String,Object>) client.getEndpoint().request(method, target, params).get(5, TimeUnit.SECONDS);
	}

	private Map<String,Object> t1(String method, Object params) throws Exception {
		return call(method, "t1", params);
	}

	/** The next event of this kind; the others (output, breakpointStatus) are kept in order for outputs(). */
	private final List<String> output = new ArrayList<>();

	private Map<String,Object> next(String event, String state) throws Exception {
		for(;;) {
			Map<String,Object> e = events.poll(5, TimeUnit.SECONDS);
			assertTrue(e != null, "no "+event+" "+state);
			if( e.get("event").equals("output")) {
				output.add(e.get("category")+": "+e.get("text"));
			}
			if( e.get("event").equals(event) && (state == null || state.equals(e.get("state")))) {
				return e;
			}
		}
	}

	private void launch(boolean startPaused) throws Exception {
		call("launch", null, Map.of("target", "t1", "startPaused", startPaused,
				"args", Map.of("source", PROGRAM, "name", "fact.toy")));
	}

	private Map<String,Object> breakpoint(String id, int line) throws Exception {
		Map<String,Object> where = Map.of("source", Map.of("path", "fact.toy"), "line", line);
		return t1("addBreakpoint", Map.of("id", id, "where", where));
	}

	@Test
	public void welcomePublishesWhatTheAdapterNeeds() throws Exception {
		Map<String,Object> w = welcome;
		assertEquals("loupe/1", w.get("protocol"));
		assertTrue(((List<?>) w.get("methods")).containsAll(List.of("resume", "step", "evaluate", "completions", "setVariable")));
		assertTrue(w.get("launchSchema") instanceof Map);
		assertEquals(1, ((List<?>) w.get("exceptionFilters")).size());
	}

	@Test
	public void rejectsALaunchWithNoProgram() throws Exception {
		ExecutionException e = assertThrows(ExecutionException.class,
				()->call("launch", null, Map.of("target", "t1", "args", Map.of("name", "x"))));
		assertTrue(e.getCause().getMessage().contains("program or a source"), e.getCause().getMessage());
		e = assertThrows(ExecutionException.class,
				()->call("launch", null, Map.of("target", "t2", "args", Map.of("source", "x = "))));
		assertTrue(e.getCause().getMessage().contains("line 1"), e.getCause().getMessage());
	}

	@Test
	public void debugsAProgram() throws Exception {
		launch(true);
		// stopped at the entry, before the first statement, with a snapshot
		Map<String,Object> entry = next("targetState", "paused");
		assertEquals("entry", entry.get("reason"));
		@SuppressWarnings("unchecked")
		Map<String,Object> snap = (Map<String,Object>) entry.get("snapshot");
		assertEquals(1, ((List<?>) snap.get("frames")).size());

		// a breakpoint on a line that holds no code moves to the next one that does
		// (this backend finds it at once, so the answer already says verified)
		assertEquals("verified", breakpoint("b1", 8).get("status"));
		Map<String,Object> verified = next("breakpointStatus", null);
		assertEquals("verified", verified.get("status"));
		breakpoint("b2", 5);
		next("breakpointStatus", null);

		t1("resume", Map.of());
		next("targetState", "running");
		Map<String,Object> stop = next("targetState", "paused");
		assertEquals("breakpoint", stop.get("reason"));
		assertEquals(List.of("b1"), stop.get("breakpoints"));

		// line 8 is about to run: xs exists, total does not
		FramesResult frames = FramesResult.fromJson(t1("frames", Map.of("thread", 1, "start", 0)));
		assertEquals(1, frames.getTotal());
		assertEquals(8, frames.getFrames().get(0).getLine());
		String frame = frames.getFrames().get(0).getId();
		VariablesResult locals = VariablesResult.fromJson(t1("locals", Map.of("frame", frame, "start", 0)));
		assertEquals(1, locals.getVariables().size());
		Value xs = locals.getVariables().get(0).getValue();
		assertEquals(Value.ARRAY, xs.getT());
		assertEquals(3, xs.getSize());
		VariablesResult items = VariablesResult.fromJson(t1("children", Map.of("id", xs.getId(), "start", 1, "count", 1)));
		assertEquals(3, items.getTotal());
		assertEquals("1", items.getVariables().get(0).getName());
		assertEquals(2L, items.getVariables().get(0).getValue().getV());

		// evaluate and complete in the frame
		EvaluateResult r = EvaluateResult.fromJson(t1("evaluate", Map.of("expression", "len(xs) * 2", "frame", frame, "context", "watch")));
		assertEquals(6L, r.getValue().getV());
		ExecutionException e = assertThrows(ExecutionException.class,
				()->t1("evaluate", Map.of("expression", "nope + 1", "frame", frame)));
		assertTrue(e.getCause().getMessage().contains("'nope' is not defined"), e.getCause().getMessage());
		e = assertThrows(ExecutionException.class,
				()->t1("evaluate", Map.of("expression", "fact(3)", "frame", frame)));
		assertTrue(e.getCause().getMessage().contains("can't be called"), e.getCause().getMessage());
		Map<String,Object> c = t1("completions", Map.of("text", "x", "offset", 1, "frame", frame));
		assertEquals("xs", ((Map<?,?>) ((List<?>) c.get("items")).get(0)).get("label"));

		// change a variable
		Map<String,Object> set = t1("setVariable", Map.of("id", xs.getId(), "name", "2", "value", "30"));
		assertEquals(30L, ((Map<?,?>) set.get("value")).get("v"));

		// step into the call: a second frame, and the line is the one in fact
		t1("step", Map.of("kind", "in"));
		next("targetState", "running");
		Map<String,Object> s = next("targetState", "paused");
		assertEquals("step", s.get("reason"));
		frames = FramesResult.fromJson(t1("frames", Map.of("thread", 1, "start", 0)));
		assertEquals(2, frames.getTotal());
		assertEquals("fact", frames.getFrames().get(0).getName());
		assertEquals(2, frames.getFrames().get(0).getLine());
		ScopesResult scopes = ScopesResult.fromJson(t1("scopes", Map.of("frame", frames.getFrames().get(0).getId())));
		assertEquals(List.of("locals", "globals"), List.of(scopes.getScopes().get(0).getName(), scopes.getScopes().get(1).getName()));
		VariablesResult globals = VariablesResult.fromJson(t1("children", Map.of("id", scopes.getScopes().get(1).getId())));
		assertEquals("xs", globals.getVariables().get(0).getName());
		// the recursion is cut by the breakpoint in fact, which is hit with n=4 first
		t1("resume", Map.of());
		next("targetState", "running");
		stop = next("targetState", "paused");
		assertEquals(List.of("b2"), stop.get("breakpoints"));

		// ending the session from a stop kills the program
		t1("terminate", Map.of());
		Map<String,Object> end = next("targetState", "terminated");
		assertEquals("kill", end.get("reason"));
	}

	@Test
	public void stopsOnTheErrorAndEndsWithItsExitCode() throws Exception {
		launch(false);
		// not started paused: it runs on its own to the error at line 11
		Map<String,Object> stop = next("targetState", "paused");
		assertEquals("exception", stop.get("reason"));
		assertTrue(String.valueOf(stop.get("description")).contains("Index 7 is out of range"), String.valueOf(stop));
		Map<String,Object> info = t1("exceptionInfo", Map.of("thread", 1));
		assertEquals("RunError", info.get("id"));
		FramesResult frames = FramesResult.fromJson(t1("frames", Map.of("thread", 1, "start", 0)));
		assertEquals(11, frames.getFrames().get(0).getLine());

		t1("resume", Map.of());
		next("targetState", "running");
		Map<String,Object> end = next("targetState", "terminated");
		assertEquals(1L, end.get("exitCode"));
		assertTrue(output.contains("stdout: [24, 2, 3]\n"), output.toString());
		assertTrue(output.stream().anyMatch(o -> o.startsWith("stderr: Index 7 is out of range")), output.toString());
	}

	@Test
	public void errorsDontStopWhenTheFilterIsOff() throws Exception {
		launch(true);
		next("targetState", "paused");
		t1("setExceptionFilters", Map.of("filters", List.of()));
		t1("resume", Map.of());
		Map<String,Object> end = next("targetState", "terminated");
		assertEquals(1L, end.get("exitCode"));
	}

	@Test
	public void stepOverAndOutStayInTheirFrame() throws Exception {
		launch(true);
		next("targetState", "paused");
		breakpoint("b", 8);
		next("breakpointStatus", null);
		t1("resume", Map.of());
		next("targetState", "paused");
		// over the call: the next stop is line 9 of the same frame
		t1("step", Map.of("kind", "over"));
		next("targetState", "paused");
		FramesResult f = FramesResult.fromJson(t1("frames", Map.of("thread", 1, "start", 0)));
		assertEquals(1, f.getTotal());
		assertEquals(9, f.getFrames().get(0).getLine());
		// total is 24 now
		String frame = f.getFrames().get(0).getId();
		assertEquals(24L, EvaluateResult.fromJson(t1("evaluate", Map.of("expression", "total", "frame", frame))).getValue().getV());
	}

	@Test
	public void conditionsHitCountsAndLogMessages() throws Exception {
		launch(true);
		next("targetState", "paused");
		Map<String,Object> where = Map.of("source", Map.of("path", "fact.toy"), "line", 3);
		// fact(4) -> fact(3) -> fact(2) -> fact(1): line 3 runs once, with n == 1
		t1("addBreakpoint", Map.of("id", "never", "where", where, "condition", "n > 1"));
		t1("addBreakpoint", Map.of("id", "logged", "where", Map.of("source", Map.of("path", "fact.toy"), "line", 5),
				"logMessage", "recursing"));
		t1("addBreakpoint", Map.of("id", "once", "where", where, "condition", "n == 1", "hitCount", 1));
		t1("resume", Map.of());
		Map<String,Object> stop = next("targetState", "paused");
		assertEquals("breakpoint", stop.get("reason"));
		assertEquals(List.of("once"), stop.get("breakpoints"));
		assertEquals(3, output.stream().filter(o -> o.equals("console: recursing\n")).count());
	}

	@Test
	public void aSecondProgramIsRefused() throws Exception {
		launch(true);
		next("targetState", "paused");
		ExecutionException e = assertThrows(ExecutionException.class,
				()->call("launch", null, Map.of("target", "t2", "args", Map.of("source", "x = 1"))));
		assertTrue(e.getCause().getMessage().contains("one program"), e.getCause().getMessage());
	}
}
