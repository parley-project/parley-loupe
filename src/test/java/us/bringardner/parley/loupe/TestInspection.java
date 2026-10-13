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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.loupe.protocol.ChildrenParams;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.FramesParams;
import us.bringardner.parley.loupe.protocol.FramesResult;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LocalsParams;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ObjectChangesParams;
import us.bringardner.parley.loupe.protocol.ObjectChangesResult;
import us.bringardner.parley.loupe.protocol.Scope;
import us.bringardner.parley.loupe.protocol.ScopesParams;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.Variable;
import us.bringardner.parley.loupe.protocol.VariablesResult;

public class TestInspection {

	/** A stack of five frames, and an array o1 of five numbers. */
	static class Backend implements TargetBackend {
		volatile Set<String> supported = Set.of(Loupe.RESUME, Loupe.PAUSE, Loupe.THREADS, Loupe.FRAMES, Loupe.SCOPES,
				Loupe.LOCALS, Loupe.CHILDREN, Loupe.OBJECT_CHANGES);
		final List<String> seen = new ArrayList<>();

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
		public CompletableFuture<Void> pause(Target target) {
			target.paused("pause", null, null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<List<ThreadInfo>> threads(Target target) {
			return CompletableFuture.completedFuture(List.of(new ThreadInfo().setId(1).setName("main").setState(target.getState())));
		}

		@Override
		public CompletableFuture<FramesResult> frames(Target target, FramesParams p) {
			seen.add("frames "+p.getThread()+" "+p.getStart()+" "+p.getCount());
			List<Frame> all = new ArrayList<>();
			for(int i=0; i < 5; i++) {
				all.add(new Frame().setId(target.objectId("f"+i)).setName("fn"+i));
			}
			int from = Math.min(p.getStart(), all.size());
			int to = p.getCount() == null ? all.size() : Math.min(all.size(), from+p.getCount());
			return CompletableFuture.completedFuture(new FramesResult().setFrames(all.subList(from, to)).setTotal(all.size()));
		}

		@Override
		public CompletableFuture<ScopesResult> scopes(Target target, ScopesParams p) {
			seen.add("scopes "+p.getFrame());
			return CompletableFuture.completedFuture(new ScopesResult()
					.setScopes(List.of(new Scope().setName("locals").setId(target.objectId("s0")))));
		}

		@Override
		public CompletableFuture<VariablesResult> locals(Target target, LocalsParams p) {
			seen.add("locals "+p.getFrame()+" "+p.getStart());
			return CompletableFuture.completedFuture(new VariablesResult().setTotal(2).setVariables(List.of(
					new Variable().setName("n").setValue(Value.of(3L)),
					new Variable().setName("xs").setValue(new Value().setT(Value.ARRAY).setId(target.objectId("o1")).setSize(5)))));
		}

		@Override
		public CompletableFuture<VariablesResult> children(Target target, ChildrenParams p) {
			seen.add("children "+p.getId()+" "+p.getStart()+" "+p.getCount());
			List<Variable> all = new ArrayList<>();
			for(int i=0; i < 5; i++) {
				all.add(new Variable().setName(String.valueOf(i)).setValue(Value.of(i*10L)));
			}
			int from = Math.min(p.getStart(), all.size());
			int to = p.getCount() == null ? all.size() : Math.min(all.size(), from+p.getCount());
			return CompletableFuture.completedFuture(new VariablesResult().setVariables(all.subList(from, to)).setTotal(5));
		}

		@Override
		public CompletableFuture<ObjectChangesResult> objectChanges(Target target, ObjectChangesParams p) {
			seen.add("changes "+p.getSince());
			return CompletableFuture.completedFuture(new ObjectChangesResult().setChanged(List.of("o1")).setGone(List.of()));
		}
	}

	private final Backend backend = new Backend();
	private volatile AdapterHandler lastHandler;
	private LoupeConnection adapter;
	private LoupeConnection client;

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(aOut, 1<<16);
		adapter = LoupeConnection.serve(ep -> {
			lastHandler = new AdapterHandler(ep, backend).setFeatures(List.of(Loupe.FEATURE_OBJECT_CHANGES));
			return lastHandler;
		}, aIn, aOut);
		client = LoupeConnection.open(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@Override
			public void event(String event, String target, Object params) {
			}
		}, cIn, cOut);
		call("hello", null, new Hello().setProtocols(List.of("loupe/1")).setFeatures(List.of(Loupe.FEATURE_OBJECT_CHANGES)).toJson());
		call("launch", null, Map.of("target", "t1", "args", Map.of()));
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

	private static Map<String,Object> map(Object... kv) {
		Map<String,Object> m = new LinkedHashMap<>();
		for(int i=0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i+1]);
		}
		return m;
	}

	@Test
	public void frames() throws Exception {
		Object r = call("frames", "t1", map("start", 1L, "count", 2L));
		assertEquals("{\"frames\":[{\"id\":\"f1@1\",\"name\":\"fn1\"},{\"id\":\"f2@1\",\"name\":\"fn2\"}],\"total\":5}", Json.write(r));
		assertEquals("frames null 1 2", backend.seen.get(0));
		// no params at all is still a request with defaults
		assertEquals(List.of("frames null 0 null"), seenAfter(()->call("frames", "t1", map())));
		assertEquals(LoupeException.INVALID_PARAMS, fails("frames", "t1", map("start", -1L)).getCode());
		assertEquals(List.of("frames null 0 null"), seenAfter(()->call("frames", "t1", null)));
	}

	private List<String> seenAfter(ThrowingRunnable r) throws Exception {
		int n = backend.seen.size();
		r.run();
		return new ArrayList<>(backend.seen.subList(n, backend.seen.size()));
	}

	interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	public void scopesLocalsAndChildren() throws Exception {
		Object s = call("scopes", "t1", map("frame", "f0@1"));
		assertEquals("{\"scopes\":[{\"name\":\"locals\",\"id\":\"s0@1\"}]}", Json.write(s));
		Object l = call("locals", "t1", map("frame", "f0@1"));
		assertTrue(Json.write(l).contains("\"xs\""), Json.write(l));
		assertEquals("locals f0@1 0", backend.seen.get(backend.seen.size()-1));

		Object c = call("children", "t1", map("id", "o1@1", "start", 3L, "count", 10L));
		assertEquals("{\"variables\":[{\"name\":\"3\",\"value\":{\"t\":\"number\",\"v\":30,\"kind\":\"int\"}},"
				+ "{\"name\":\"4\",\"value\":{\"t\":\"number\",\"v\":40,\"kind\":\"int\"}}],\"total\":5}", Json.write(c));
		// an id with no generation names the object in whatever generation it is
		call("children", "t1", map("id", "o1"));
		assertEquals("children o1 0 null", backend.seen.get(backend.seen.size()-1));
		assertEquals(LoupeException.INVALID_PARAMS, fails("children", "t1", map("id", "o1@")).getCode());
	}

	@Test
	public void staleIdsAreRefusedWithTheCurrentGeneration() throws Exception {
		call("resume", "t1", null);
		call("pause", "t1", null);
		assertEquals(2, adapterTarget().getGeneration());
		LoupeException e = fails("scopes", "t1", map("frame", "f0@1"));
		assertEquals(LoupeException.STALE_ID, e.getCode());
		assertEquals(map("id", "f0@1", "generation", 2L), e.getData());
		assertEquals(LoupeException.STALE_ID, fails("children", "t1", map("id", "o1@1")).getCode());
		assertEquals(LoupeException.STALE_ID, fails("locals", "t1", map("frame", "f0@3")).getCode());
		// current, or with no generation, is fine
		call("scopes", "t1", map("frame", "f0@2"));
		call("scopes", "t1", map("frame", "f0"));
	}

	private Target adapterTarget() {
		return lastHandler.getTargets().get("t1");
	}

	@Test
	public void valuesNeedAPausedTarget() throws Exception {
		call("resume", "t1", null);
		LoupeException e = fails("frames", "t1", map());
		assertEquals(LoupeException.WRONG_STATE, e.getCode());
		assertEquals(map("state", "running"), e.getData());
		assertEquals(LoupeException.WRONG_STATE, fails("children", "t1", map("id", "o1")).getCode());
		// threads are fine either way
		Object t = call("threads", "t1", null);
		assertEquals("{\"threads\":[{\"id\":1,\"name\":\"main\",\"state\":\"running\"}]}", Json.write(t));
	}

	@Test
	public void objectChanges() throws Exception {
		call("resume", "t1", null);
		call("pause", "t1", null);
		Object r = call("objectChanges", "t1", map("since", 1L));
		assertEquals("{\"generation\":2,\"changed\":[\"o1\"],\"gone\":[]}", Json.write(r));
		assertEquals(LoupeException.INVALID_PARAMS, fails("objectChanges", "t1", map("since", 3L)).getCode());
		assertEquals(LoupeException.INVALID_PARAMS, fails("objectChanges", "t1", map("since", -1L)).getCode());
	}

	@Test
	public void unsupportedAndUnknown() throws Exception {
		backend.supported = Set.of();
		assertEquals(LoupeException.NOT_SUPPORTED, fails("frames", "t1", map()).getCode());
		assertEquals(LoupeException.NOT_SUPPORTED, fails("threads", "t1", null).getCode());
		backend.supported = Set.of(Loupe.FRAMES);
		assertEquals(LoupeException.UNKNOWN_TARGET, fails("frames", "nope", map()).getCode());
	}
}
