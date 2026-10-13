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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.StepParams;

public class TestExecutionControl {

	/** Pauses on entry; steps stop at once; pause stops at once; resume just runs. */
	static class Backend implements TargetBackend {
		volatile Set<String> supported = Set.of(Loupe.RESUME, Loupe.STEP, Loupe.PAUSE, Loupe.TERMINATE);
		volatile boolean exitWithCode;

		@Override
		public boolean supports(String method) {
			return supported.contains(method);
		}

		@Override
		public CompletableFuture<Void> launch(Target target, Object args) {
			target.paused("entry", null, null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> resume(Target target) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> step(Target target, StepParams params) {
			if( params.getThread() != null && params.getThread() == 99 ) {
				throw new LoupeException(LoupeException.INVALID_PARAMS, "No thread 99");
			}
			target.paused("step", null, null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> pause(Target target) {
			target.paused("pause", null, null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> terminate(Target target) {
			if( exitWithCode ) {
				target.terminated("exit", 3);
			}
			return CompletableFuture.completedFuture(null);
		}
	}

	private final Backend backend = new Backend();
	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	private LoupeConnection adapter;
	private LoupeConnection client;

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(aOut, 1<<16);
		adapter = LoupeConnection.serve(ep -> new AdapterHandler(ep, backend), aIn, aOut);
		client = LoupeConnection.open(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@Override
			public void event(String event, String target, Object params) {
				events.add(Json.write(params));
			}
		}, cIn, cOut);
		call("hello", null, new Hello().setProtocols(List.of("loupe/1")).toJson());
		call("launch", null, Map.of("target", "t1", "args", Map.of()));
		assertTrue(event().contains("\"state\":\"running\""));
		assertTrue(event().contains("\"reason\":\"entry\""));
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

	private String event() throws Exception {
		return events.poll(5, TimeUnit.SECONDS);
	}

	@Test
	public void resumeAndStep() throws Exception {
		assertEquals(Map.of(), call("resume", "t1", null));
		assertEquals("{\"state\":\"running\",\"from\":\"paused\",\"reason\":\"resume\",\"generation\":1}", event());
		// running: can't resume, can't step
		LoupeException e = fails("resume", "t1", null);
		assertEquals(LoupeException.WRONG_STATE, e.getCode());
		assertEquals(Map.of("state", "running"), e.getData());
		assertEquals(LoupeException.WRONG_STATE, fails("step", "t1", Map.of("kind", "over")).getCode());

		// pause, then step: the stop of the step is a new generation
		assertEquals(Map.of(), call("pause", "t1", null));
		assertEquals("{\"state\":\"paused\",\"from\":\"running\",\"reason\":\"pause\",\"generation\":2}", event());
		assertEquals(Map.of(), call("step", "t1", Map.of("kind", "in", "thread", 1L)));
		assertEquals("{\"state\":\"running\",\"from\":\"paused\",\"reason\":\"step\",\"generation\":2}", event());
		assertEquals("{\"state\":\"paused\",\"from\":\"running\",\"reason\":\"step\",\"generation\":3}", event());
		assertEquals(LoupeException.WRONG_STATE, fails("pause", "t1", null).getCode());
	}

	@Test
	public void badStepRequests() throws Exception {
		assertEquals(LoupeException.INVALID_PARAMS, fails("step", "t1", Map.of("kind", "sideways")).getCode());
		assertEquals(LoupeException.INVALID_PARAMS, fails("step", "t1", Map.of()).getCode());
		assertEquals(LoupeException.INVALID_PARAMS, fails("step", "t1", null).getCode());
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void aStepThatCantStartPutsTheTargetBack() throws Exception {
		LoupeException e = fails("step", "t1", Map.of("kind", "over", "thread", 99L));
		assertEquals(LoupeException.INVALID_PARAMS, e.getCode());
		assertTrue(event().contains("\"state\":\"running\""));
		String back = event();
		assertTrue(back.contains("\"state\":\"paused\"") && back.contains("\"reason\":\"stepFailed\""), back);
		// and it can be stepped again
		assertEquals(Map.of(), call("step", "t1", Map.of("kind", "over")));
	}

	@Test
	public void terminateKillsWhatTheBackendDidNotEnd() throws Exception {
		assertEquals(Map.of(), call("terminate", "t1", null));
		assertEquals("{\"state\":\"terminated\",\"from\":\"paused\",\"reason\":\"kill\",\"generation\":1}", event());
		assertEquals(LoupeException.WRONG_STATE, fails("terminate", "t1", null).getCode());
		assertEquals(LoupeException.WRONG_STATE, fails("resume", "t1", null).getCode());
	}

	@Test
	public void terminateKeepsTheBackendsExitCode() throws Exception {
		backend.exitWithCode = true;
		assertEquals(Map.of(), call("terminate", "t1", null));
		assertEquals("{\"state\":\"terminated\",\"from\":\"paused\",\"reason\":\"exit\",\"generation\":1,\"exitCode\":3}", event());
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void unsupportedMethodsLeaveTheTargetAlone() throws Exception {
		backend.supported = Set.of();
		for(String m : new String[] {"resume", "pause", "terminate"}) {
			assertEquals(LoupeException.NOT_SUPPORTED, fails(m, "t1", null).getCode(), m);
		}
		assertEquals(LoupeException.NOT_SUPPORTED, fails("step", "t1", Map.of("kind", "over")).getCode());
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("kind", "over");
		backend.supported = Set.of(Loupe.STEP);
		assertEquals(Map.of(), call("step", "t1", m));
	}

	@Test
	public void unknownTarget() throws Exception {
		assertEquals(LoupeException.UNKNOWN_TARGET, fails("resume", "nope", null).getCode());
		assertEquals(LoupeException.UNKNOWN_TARGET, fails("pause", null, null).getCode());
	}
}
