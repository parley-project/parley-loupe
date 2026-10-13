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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.DapConnection;
import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.dap.protocol.BreakpointEventBody;
import us.bringardner.parley.dap.protocol.Capabilities;
import us.bringardner.parley.dap.protocol.CompletionsArguments;
import us.bringardner.parley.dap.protocol.CompletionsResponseBody;
import us.bringardner.parley.dap.protocol.ConfigurationDoneArguments;
import us.bringardner.parley.dap.protocol.EvaluateArguments;
import us.bringardner.parley.dap.protocol.EvaluateResponseBody;
import us.bringardner.parley.dap.protocol.ContinueArguments;
import us.bringardner.parley.dap.protocol.ContinuedEventBody;
import us.bringardner.parley.dap.protocol.DisconnectArguments;
import us.bringardner.parley.dap.protocol.ExitedEventBody;
import us.bringardner.parley.dap.protocol.IDebugAdapter;
import us.bringardner.parley.dap.protocol.IDebugClient;
import us.bringardner.parley.dap.protocol.InitializeRequestArguments;
import us.bringardner.parley.dap.protocol.LaunchRequestArguments;
import us.bringardner.parley.dap.protocol.NextArguments;
import us.bringardner.parley.dap.protocol.Scope;
import us.bringardner.parley.dap.protocol.ScopesArguments;
import us.bringardner.parley.dap.protocol.SetBreakpointsArguments;
import us.bringardner.parley.dap.protocol.SetBreakpointsResponseBody;
import us.bringardner.parley.dap.protocol.ExceptionInfoArguments;
import us.bringardner.parley.dap.protocol.SetExceptionBreakpointsArguments;
import us.bringardner.parley.dap.protocol.SetVariableArguments;
import us.bringardner.parley.dap.protocol.StackFrame;
import us.bringardner.parley.dap.protocol.StackTraceArguments;
import us.bringardner.parley.dap.protocol.StoppedEventBody;
import us.bringardner.parley.dap.protocol.TerminatedEventBody;
import us.bringardner.parley.dap.protocol.Variable;
import us.bringardner.parley.dap.protocol.VariablesArguments;
import us.bringardner.parley.loupe.AdapterHandler;
import us.bringardner.parley.loupe.BreakpointTable;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.LoupeException;
import us.bringardner.parley.loupe.Target;
import us.bringardner.parley.loupe.TargetBackend;
import us.bringardner.parley.loupe.protocol.BreakpointStatus;
import us.bringardner.parley.loupe.protocol.ChildrenParams;
import us.bringardner.parley.loupe.protocol.CompletionItem;
import us.bringardner.parley.loupe.protocol.CompletionsParams;
import us.bringardner.parley.loupe.protocol.CompletionsResult;
import us.bringardner.parley.loupe.protocol.EvaluateParams;
import us.bringardner.parley.loupe.protocol.EvaluateResult;
import us.bringardner.parley.loupe.protocol.ExceptionFilter;
import us.bringardner.parley.loupe.protocol.ExceptionInfoParams;
import us.bringardner.parley.loupe.protocol.ExceptionInfoResult;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.FramesParams;
import us.bringardner.parley.loupe.protocol.FramesResult;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ScopesParams;
import us.bringardner.parley.loupe.protocol.SetVariableParams;
import us.bringardner.parley.loupe.protocol.SetVariableResult;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.StepParams;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.VariablesResult;

/** A DAP client on one side, a Loupe adapter on the other, the bridge between. */
public class TestDapBridge {

	/** A fake debuggee: starts suspended at the entry; stops and steps when told to. */
	static class Backend implements TargetBackend {
		final List<String> calls = new CopyOnWriteArrayList<>();

		@Override
		public boolean supports(String method) {
			return Set.of(Loupe.RESUME, Loupe.STEP, Loupe.PAUSE, Loupe.TERMINATE, Loupe.THREADS, Loupe.FRAMES,
					Loupe.SCOPES, Loupe.LOCALS, Loupe.CHILDREN, Loupe.EVALUATE, Loupe.COMPLETIONS, Loupe.SET_VARIABLE, Loupe.SET_EXCEPTION_FILTERS, Loupe.EXCEPTION_INFO).contains(method);
		}

		/** The stop all the stack requests answer from. */
		static Snapshot snapshot(Target t) {
			return new Snapshot()
					.setThreads(List.of(new ThreadInfo().setId(1).setName("main").setState("paused")))
					.setThread(1)
					.setFrames(List.of(new Frame().setId(t.objectId("f0")).setName("main")
							.setSource(new us.bringardner.parley.loupe.protocol.Source().setPath("/src/a.fsh")).setLine(3).setColumn(1)))
					.setTotalFrames(1)
					.setLocals(List.of(
							new us.bringardner.parley.loupe.protocol.Variable().setName("n").setValue(Value.of(3L)),
							new us.bringardner.parley.loupe.protocol.Variable().setName("name").setValue(Value.of("fsh")),
							new us.bringardner.parley.loupe.protocol.Variable().setName("xs")
									.setValue(new Value().setT(Value.ARRAY).setId(t.objectId("o1")).setType("int[]").setSize(5))));
		}

		@Override
		public CompletableFuture<Void> launch(Target target, Object args) {
			calls.add("launch "+Json.write(args)+" startPaused="+target.isStartPaused());
			target.pausedWith("entry", () -> snapshot(target), null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void breakpointAdded(Target target, BreakpointTable.Entry bp) {
			calls.add("add "+bp.getId()+" line="+bp.getWhere().getLine()+" cond="+bp.getParams().getCondition()
					+" hit="+bp.getParams().getHitCount()+" log="+bp.getParams().getLogMessage());
			target.setBreakpointStatus(bp.getId(), BreakpointStatus.VERIFIED, null, null);
		}

		@Override
		public void breakpointRemoved(Target target, String id) {
			calls.add("remove "+id);
		}

		@Override
		public CompletableFuture<Void> resume(Target target) {
			calls.add("resume");
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> step(Target target, StepParams params) {
			calls.add("step "+params.getKind()+" "+params.getThread());
			target.pausedWith("step", () -> snapshot(target), null);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> pause(Target target) {
			calls.add("pause");
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> terminate(Target target) {
			calls.add("terminate");
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<java.util.List<ThreadInfo>> threads(Target target) {
			calls.add("threads");
			return CompletableFuture.completedFuture(List.of(new ThreadInfo().setId(1).setName("main")));
		}

		@Override
		public CompletableFuture<FramesResult> frames(Target target, FramesParams p) {
			calls.add("frames");
			return CompletableFuture.completedFuture(new FramesResult().setFrames(snapshot(target).getFrames()).setTotal(1));
		}

		@Override
		public CompletableFuture<ScopesResult> scopes(Target target, ScopesParams p) {
			calls.add("scopes "+p.getFrame());
			return CompletableFuture.completedFuture(new ScopesResult().setScopes(List.of(
					new us.bringardner.parley.loupe.protocol.Scope().setName("locals").setId(target.objectId("s0")),
					new us.bringardner.parley.loupe.protocol.Scope().setName("globals").setId(target.objectId("s1")).setExpensive(true))));
		}

		@Override
		public CompletableFuture<Void> setExceptionFilters(Target target, List<String> filters) {
			calls.add("filters "+filters);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<ExceptionInfoResult> exceptionInfo(Target target, ExceptionInfoParams p) {
			calls.add("exceptionInfo "+p.getThread());
			return CompletableFuture.completedFuture(new ExceptionInfoResult().setId("RangeError").setDescription("index 9 out of range")
					.setTypeName("RangeError").setMessage("index 9 out of range").setStackTrace("at main (a.fsh:3)"));
		}

		@Override
		public CompletableFuture<SetVariableResult> setVariable(Target target, SetVariableParams p) {
			calls.add("set "+p.getId()+" "+p.getName()+" = "+p.getValue());
			return CompletableFuture.completedFuture(new SetVariableResult().setValue(Value.of(Long.parseLong(p.getValue()))));
		}

		@Override
		public CompletableFuture<EvaluateResult> evaluate(Target target, EvaluateParams p) {
			calls.add("evaluate "+p.getExpression()+" "+p.getFrame()+" "+p.getContext());
			if( p.getExpression().equals("nope")) {
				throw new LoupeException(LoupeException.FAILED, "nope is not defined");
			}
			if( p.getExpression().equals("xs")) {
				return CompletableFuture.completedFuture(new EvaluateResult().setValue(
						new Value().setT(Value.ARRAY).setId(target.objectId("o1")).setType("int[]").setSize(5)));
			}
			return CompletableFuture.completedFuture(new EvaluateResult().setValue(Value.of(7L)));
		}

		@Override
		public CompletableFuture<CompletionsResult> completions(Target target, CompletionsParams p) {
			calls.add("completions "+Json.write(p.getText())+" "+p.getOffset());
			return CompletableFuture.completedFuture(new CompletionsResult().setItems(List.of(
					new CompletionItem().setLabel("size").setText("size()").setType("method").setStart(p.getOffset()-1).setLength(1))));
		}

		@Override
		public CompletableFuture<VariablesResult> children(Target target, ChildrenParams p) {
			calls.add("children "+p.getId()+" "+p.getStart()+" "+p.getCount());
			List<us.bringardner.parley.loupe.protocol.Variable> list = new ArrayList<>();
			if( p.getId().startsWith("o1")) {
				int from = p.getStart();
				int to = p.getCount() == null ? 5 : Math.min(5, from+p.getCount());
				for(int i=from; i < to; i++) {
					list.add(new us.bringardner.parley.loupe.protocol.Variable().setName(String.valueOf(i)).setValue(Value.of(i*10L)));
				}
			}
			return CompletableFuture.completedFuture(new VariablesResult().setVariables(list).setTotal(list.size()));
		}
	}

	/** What the DAP client was told, as short strings. */
	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	private final Backend backend = new Backend();
	private volatile AdapterHandler handler;
	private DapConnection dap;
	private LoupeConnection loupe;
	private DapConnection client;
	private IDebugAdapter adapter;

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream dIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream dOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(dOut, 1<<16);
		PipedOutputStream bOut = new PipedOutputStream();
		PipedInputStream lIn = new PipedInputStream(bOut, 1<<16);
		PipedOutputStream lOut = new PipedOutputStream();
		PipedInputStream bIn = new PipedInputStream(lOut, 1<<16);

		loupe = LoupeConnection.serve(ep -> {
			handler = new AdapterHandler(ep, backend).setFeatures(List.of(Loupe.FEATURE_SNAPSHOT))
					.setExceptionFilters(List.of(new ExceptionFilter().setId("all").setLabel("All exceptions"),
							new ExceptionFilter().setId("uncaught").setLabel("Uncaught exceptions").setDefaultOn(true)));
			return handler;
		}, lIn, lOut);
		dap = DapBridge.open(dIn, dOut, bIn, bOut);
		client = DapConnection.client(new IDebugClient() {
			@Override
			public void initialized() {
				events.add("initialized");
			}

			@Override
			public void stopped(StoppedEventBody b) {
				events.add("stopped "+b.getReason()+" thread="+b.getThreadId()+" hit="+b.getHitBreakpointIds());
			}

			@Override
			public void continued(ContinuedEventBody b) {
				events.add("continued");
			}

			@Override
			public void exited(ExitedEventBody b) {
				events.add("exited "+b.getExitCode());
			}

			@Override
			public void terminated(TerminatedEventBody b) {
				events.add("terminated");
			}

			@Override
			public void breakpoint(BreakpointEventBody b) {
				events.add("breakpoint "+b.getReason()+" id="+b.getBreakpoint().getId()+" verified="+b.getBreakpoint().getVerified());
			}
		}, cIn, cOut);
		adapter = client.getAdapter();
	}

	@AfterEach
	public void disconnect() {
		client.close();
		dap.close();
		loupe.close();
	}

	private <T> T get(CompletableFuture<T> f) throws Exception {
		return f.get(5, TimeUnit.SECONDS);
	}

	private String event() throws Exception {
		return events.poll(5, TimeUnit.SECONDS);
	}

	/** The next event that isn't a breakpoint change (the adapter verifies breakpoints as they're added). */
	private String nextStop() throws Exception {
		while( true ) {
			String e = event();
			if( e == null || !e.startsWith("breakpoint ")) {
				return e;
			}
		}
	}

	private void launch(String json) throws Exception {
		Capabilities caps = get(adapter.initialize(new InitializeRequestArguments().setAdapterID("test")));
		assertTrue(caps.getSupportsConfigurationDoneRequest());
		assertTrue(caps.getSupportsConditionalBreakpoints());
		// what the Loupe adapter has is what the client is told
		assertTrue(caps.getSupportsTerminateRequest());
		assertTrue(caps.getSupportsEvaluateForHovers());
		assertTrue(caps.getSupportsCompletionsRequest());
		assertTrue(caps.getSupportsSetVariable());
		assertTrue(caps.getSupportsExceptionInfoRequest());
		assertEquals(2, caps.getExceptionBreakpointFilters().size());
		assertEquals("uncaught", caps.getExceptionBreakpointFilters().get(1).getFilter());
		assertEquals("Uncaught exceptions", caps.getExceptionBreakpointFilters().get(1).getLabel());
		assertTrue(caps.getExceptionBreakpointFilters().get(1).getDefault());
		get(adapter.launch(LaunchRequestArguments.fromJson(Json.parse(json))));
		assertEquals("initialized", event());
	}

	private SetBreakpointsResponseBody setBreakpoints(String path, int... lines) throws Exception {
		StringBuilder b = new StringBuilder("{\"source\":{\"path\":\"").append(path).append("\"},\"breakpoints\":[");
		for(int i=0; i < lines.length; i++) {
			b.append(i == 0 ? "" : ",").append("{\"line\":").append(lines[i]).append('}');
		}
		return get(adapter.setBreakpoints(SetBreakpointsArguments.fromJson(Json.parse(b.append("]}").toString()))));
	}

	@Test
	public void launchIsStartedPausedAndOnlyConfigurationDoneRuns() throws Exception {
		launch("{\"script\":\"a.fsh\",\"noDebug\":false,\"__sessionId\":\"x\",\"stopOnEntry\":false}");
		// the bridge's own members don't reach the adapter
		assertEquals("launch {\"script\":\"a.fsh\"} startPaused=true", backend.calls.get(0));
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
		assertFalse(backend.calls.contains("resume"));

		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		assertTrue(backend.calls.contains("resume"));
		// the client asked for it: no continued event
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void stopOnEntryReportsTheEntryStop() throws Exception {
		launch("{\"script\":\"a.fsh\",\"stopOnEntry\":true}");
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		assertEquals("stopped entry thread=1 hit=null", event());
		assertFalse(backend.calls.contains("resume"));
	}

	@Test
	public void breakpointsAreDiffedAndKeepTheirIds() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		SetBreakpointsResponseBody r = setBreakpoints("/src/a.fsh", 3, 5);
		assertEquals(2, r.getBreakpoints().size());
		assertEquals(1, r.getBreakpoints().get(0).getId());
		assertEquals(2, r.getBreakpoints().get(1).getId());
		assertTrue(r.getBreakpoints().get(0).getVerified());
		assertEquals(3, r.getBreakpoints().get(0).getLine());
		assertEquals("/src/a.fsh", r.getBreakpoints().get(0).getSource().getPath());
		assertTrue(backend.calls.contains("add b1 line=3 cond=null hit=null log=null"));

		// line 5 stays (same id), line 3 goes, line 7 is new
		r = setBreakpoints("/src/a.fsh", 5, 7);
		assertEquals(2, r.getBreakpoints().get(0).getId());
		assertEquals(3, r.getBreakpoints().get(1).getId());
		assertTrue(backend.calls.contains("remove b1"));
		assertTrue(backend.calls.contains("add b3 line=7 cond=null hit=null log=null"));
		assertEquals(1, backend.calls.stream().filter(c -> c.startsWith("add b2")).count());

		// none at all removes the rest
		assertEquals(0, setBreakpoints("/src/a.fsh").getBreakpoints().size());
		assertTrue(backend.calls.contains("remove b2"));
		assertTrue(backend.calls.contains("remove b3"));
	}

	@Test
	public void conditionsHitCountsAndLogMessagesGoThrough() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		SetBreakpointsResponseBody r = get(adapter.setBreakpoints(SetBreakpointsArguments.fromJson(Json.parse(
				"{\"source\":{\"path\":\"/a\"},\"breakpoints\":[{\"line\":1,\"condition\":\"n > 2\",\"hitCondition\":\"4\"},"
				+ "{\"line\":2,\"logMessage\":\"n={n}\"},{\"line\":3,\"hitCondition\":\"> 5\"}]}"))));
		assertTrue(backend.calls.contains("add b1 line=1 cond=n > 2 hit=4 log=null"));
		assertTrue(backend.calls.contains("add b2 line=2 cond=null hit=null log=n={n}"));
		// a hit condition that isn't a count can't be mapped: reported, not sent
		assertFalse(r.getBreakpoints().get(2).getVerified());
		assertTrue(r.getBreakpoints().get(2).getMessage().contains("hit count"));
		assertEquals(2, backend.calls.stream().filter(c -> c.startsWith("add ")).count());
	}

	@Test
	public void aStopIsShownFromTheSnapshotAlone() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		setBreakpoints("/src/a.fsh", 3);
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		backend.calls.clear();

		Target t = handler.getTargets().get("t1");
		t.pausedWith("breakpoint", () -> Backend.snapshot(t), List.of("b1"));
		assertEquals("stopped breakpoint thread=1 hit=[1]", nextStop());

		assertEquals("main", get(adapter.threads()).getThreads().get(0).getName());
		StackFrame frame = get(adapter.stackTrace(StackTraceArguments.fromJson(Json.parse("{\"threadId\":1,\"levels\":20}")))).getStackFrames().get(0);
		assertEquals("main", frame.getName());
		assertEquals(3, frame.getLine());
		assertEquals("a.fsh", frame.getSource().getName());

		List<Scope> scopes = get(adapter.scopes(new ScopesArguments().setFrameId(frame.getId()))).getScopes();
		assertEquals("locals", scopes.get(0).getName());
		assertTrue(scopes.get(1).getExpensive());

		List<Variable> locals = get(adapter.variables(new VariablesArguments().setVariablesReference(scopes.get(0).getVariablesReference()))).getVariables();
		assertEquals("n", locals.get(0).getName());
		assertEquals("3", locals.get(0).getValue());
		assertEquals("\"fsh\"", locals.get(1).getValue());
		assertEquals("int[][5]", locals.get(2).getValue());
		assertEquals(0, locals.get(0).getVariablesReference());
		assertTrue(locals.get(2).getVariablesReference() > 0);
		assertEquals(5, locals.get(2).getIndexedVariables());

		// the array is fetched from the adapter, a page at a time
		List<Variable> items = get(adapter.variables(new VariablesArguments()
				.setVariablesReference(locals.get(2).getVariablesReference()).setStart(1).setCount(2))).getVariables();
		assertEquals("1", items.get(0).getName());
		assertEquals("10", items.get(0).getValue());

		// no threads, frames or locals request went to the adapter: only scopes and children did
		assertEquals(List.of("scopes "+t.objectId("f0"), "children "+t.objectId("o1")+" 1 2"), backend.calls);
	}

	@Test
	public void steppingAndContinuing() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		Target t = handler.getTargets().get("t1");
		t.pausedWith("pause", () -> Backend.snapshot(t), null);
		assertEquals("stopped pause thread=1 hit=null", event());

		get(adapter.next(new NextArguments().setThreadId(1)));
		assertEquals("stopped step thread=1 hit=null", event());
		assertTrue(backend.calls.contains("step over 1"));
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));

		assertTrue(get(adapter.continue_(new ContinueArguments().setThreadId(1))).getAllThreadsContinued());
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));

		// a resume the client didn't ask for is reported (here, the adapter resumes on its own)
		t.paused("breakpoint", null, null);
		assertEquals("stopped breakpoint thread=1 hit=null", event());
		t.running("resume");
		assertEquals("continued", event());
	}

	@Test
	public void theEndOfTheDebuggee() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		handler.getTargets().get("t1").terminated("exit", 7);
		assertEquals("exited 7", event());
		assertEquals("terminated", event());
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void disconnectEndsALaunchedTarget() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.disconnect(new DisconnectArguments()));
		assertTrue(backend.calls.contains("terminate"));
	}

	@Test
	public void errorsFromTheAdapterBecomeDapErrors() throws Exception {
		// not launched: the Loupe adapter has no target
		get(adapter.initialize(new InitializeRequestArguments().setAdapterID("test")));
		ExecutionException e = assertThrows(ExecutionException.class, ()->get(adapter.continue_(new ContinueArguments())));
		assertTrue(e.getCause().getMessage().contains("No target"), e.getCause().getMessage());
		e = assertThrows(ExecutionException.class, ()->get(adapter.variables(new VariablesArguments().setVariablesReference(99))));
		assertTrue(e.getCause().getMessage().contains("Unknown variablesReference"), e.getCause().getMessage());
		assertTrue(e.getCause() instanceof us.bringardner.parley.dap.DapException);
		assertTrue(LoupeException.class != null);
	}

	@Test
	public void exceptionsStopAndCanBeAskedAbout() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.setExceptionBreakpoints(SetExceptionBreakpointsArguments.fromJson(Json.parse("{\"filters\":[\"all\"]}"))));
		assertTrue(backend.calls.contains("filters [all]"));
		get(adapter.configurationDone(new ConfigurationDoneArguments()));

		Target t = handler.getTargets().get("t1");
		t.pausedOnException("index 9 out of range", () -> Backend.snapshot(t));
		assertEquals("stopped exception thread=1 hit=null", event());

		var info = get(adapter.exceptionInfo(new ExceptionInfoArguments().setThreadId(1)));
		assertEquals("RangeError", info.getExceptionId());
		assertEquals("always", info.getBreakMode());
		assertEquals("index 9 out of range", info.getDetails().getMessage());
		assertEquals("at main (a.fsh:3)", info.getDetails().getStackTrace());
	}

	@Test
	public void setVariable() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		Target t = handler.getTargets().get("t1");
		t.pausedWith("breakpoint", () -> Backend.snapshot(t), null);
		assertEquals("stopped breakpoint thread=1 hit=null", event());
		StackFrame frame = get(adapter.stackTrace(StackTraceArguments.fromJson(Json.parse("{\"threadId\":1}")))).getStackFrames().get(0);
		int locals = get(adapter.scopes(new ScopesArguments().setFrameId(frame.getId()))).getScopes().get(0).getVariablesReference();

		var r = get(adapter.setVariable(new SetVariableArguments().setVariablesReference(locals).setName("n").setValue("9")));
		assertEquals("9", r.getValue());
		assertEquals("number", r.getType());
		assertEquals("set "+t.objectId("s0")+" n = 9", backend.calls.get(backend.calls.size()-1));
		ExecutionException e = assertThrows(ExecutionException.class,
				()->get(adapter.setVariable(new SetVariableArguments().setVariablesReference(12345).setName("n").setValue("1"))));
		assertTrue(e.getCause().getMessage().contains("Unknown variablesReference"));
	}

	@Test
	public void evaluateAndCompletions() throws Exception {
		launch("{\"script\":\"a.fsh\"}");
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		Target t = handler.getTargets().get("t1");
		t.pausedWith("breakpoint", () -> Backend.snapshot(t), null);
		assertEquals("stopped breakpoint thread=1 hit=null", event());
		StackFrame frame = get(adapter.stackTrace(StackTraceArguments.fromJson(Json.parse("{\"threadId\":1}")))).getStackFrames().get(0);

		EvaluateResponseBody r = get(adapter.evaluate(new EvaluateArguments().setExpression("n+4").setFrameId(frame.getId()).setContext("hover")));
		assertEquals("7", r.getResult());
		assertEquals("number", r.getType());
		assertEquals(0, r.getVariablesReference());
		assertEquals("evaluate n+4 "+t.objectId("f0")+" hover", backend.calls.get(backend.calls.size()-1));

		// a result with parts can be expanded, and the frame may be left out
		r = get(adapter.evaluate(new EvaluateArguments().setExpression("xs").setContext("repl")));
		assertEquals("int[][5]", r.getResult());
		assertTrue(r.getVariablesReference() > 0);
		assertEquals(5, r.getIndexedVariables());
		assertEquals("evaluate xs null repl", backend.calls.get(backend.calls.size()-1));
		assertEquals(5, get(adapter.variables(new VariablesArguments().setVariablesReference(r.getVariablesReference()))).getVariables().size());

		ExecutionException e = assertThrows(ExecutionException.class,
				()->get(adapter.evaluate(new EvaluateArguments().setExpression("nope").setContext("repl"))));
		assertEquals("nope is not defined", e.getCause().getMessage());
		e = assertThrows(ExecutionException.class,
				()->get(adapter.evaluate(new EvaluateArguments().setExpression("n").setFrameId(9999))));
		assertTrue(e.getCause().getMessage().contains("Unknown frame"), e.getCause().getMessage());

		// the column is 1-based: after "xs." (3 characters) it is 4
		CompletionsResponseBody c = get(adapter.completions(new CompletionsArguments().setText("xs.").setColumn(4).setFrameId(frame.getId())));
		assertEquals("completions \"xs.\" 3", backend.calls.get(backend.calls.size()-1));
		assertEquals("size", c.getTargets().get(0).getLabel());
		assertEquals("size()", c.getTargets().get(0).getText());
		assertEquals("method", c.getTargets().get(0).getType());
		assertEquals(2, c.getTargets().get(0).getStart());

		// a line of a longer text: the offset counts the lines before it
		get(adapter.completions(new CompletionsArguments().setText("a = 1\nxs.").setLine(2).setColumn(4)));
		assertEquals("completions \"a = 1\\nxs.\" 9", backend.calls.get(backend.calls.size()-1));
	}

	@Test
	public void valueText() {
		assertEquals("null", ValueText.of(Value.ofNull()));
		assertEquals("true", ValueText.of(Value.of(true)));
		assertEquals("1.5", ValueText.of(Value.of(1.5)));
		assertEquals("\"a\\\"b\\n\"", ValueText.of(Value.of("a\"b\n")));
		assertEquals("Foo(1)", ValueText.of(Value.ref("o1", "Foo", "Foo(1)")));
		assertEquals("map{2}", ValueText.of(new Value().setT(Value.MAP).setSize(2)));
		assertEquals("function f(a, b)", ValueText.of(new Value().setT(Value.FUNCTION).setName("f").setSignature("(a, b)")));
	}
}
