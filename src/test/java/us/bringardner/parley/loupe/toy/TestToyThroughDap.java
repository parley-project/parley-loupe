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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.DapConnection;
import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.dap.protocol.Capabilities;
import us.bringardner.parley.dap.protocol.ConfigurationDoneArguments;
import us.bringardner.parley.dap.protocol.ContinueArguments;
import us.bringardner.parley.dap.protocol.EvaluateArguments;
import us.bringardner.parley.dap.protocol.ExitedEventBody;
import us.bringardner.parley.dap.protocol.IDebugAdapter;
import us.bringardner.parley.dap.protocol.IDebugClient;
import us.bringardner.parley.dap.protocol.InitializeRequestArguments;
import us.bringardner.parley.dap.protocol.LaunchRequestArguments;
import us.bringardner.parley.dap.protocol.NextArguments;
import us.bringardner.parley.dap.protocol.OutputEventBody;
import us.bringardner.parley.dap.protocol.ScopesArguments;
import us.bringardner.parley.dap.protocol.SetBreakpointsArguments;
import us.bringardner.parley.dap.protocol.StackFrame;
import us.bringardner.parley.dap.protocol.StackTraceArguments;
import us.bringardner.parley.dap.protocol.StoppedEventBody;
import us.bringardner.parley.dap.protocol.Variable;
import us.bringardner.parley.dap.protocol.VariablesArguments;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.dap.DapBridge;

/** A DAP client debugging a toy program: the bridge and the toy adapter, with nothing faked. */
public class TestToyThroughDap {

	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	private final BlockingQueue<String> outputs = new LinkedBlockingQueue<>();

	private static <T> T get(CompletableFuture<T> f) throws Exception {
		return f.get(5, TimeUnit.SECONDS);
	}

	private String event(String prefix) throws Exception {
		for(;;) {
			String e = events.poll(5, TimeUnit.SECONDS);
			assertTrue(e != null, "no "+prefix);
			if( e.startsWith(prefix)) {
				return e;
			}
		}
	}

	@Test
	public void anIdeSessionFromLaunchToTheEnd() throws Exception {
		PipedOutputStream cOut = new PipedOutputStream();
		PipedInputStream dIn = new PipedInputStream(cOut, 1<<16);
		PipedOutputStream dOut = new PipedOutputStream();
		PipedInputStream cIn = new PipedInputStream(dOut, 1<<16);
		PipedOutputStream bOut = new PipedOutputStream();
		PipedInputStream lIn = new PipedInputStream(bOut, 1<<16);
		PipedOutputStream lOut = new PipedOutputStream();
		PipedInputStream bIn = new PipedInputStream(lOut, 1<<16);
		LoupeConnection loupe = LoupeConnection.serve(ToyAdapterMain::handler, lIn, lOut);
		DapConnection bridge = DapBridge.open(dIn, dOut, bIn, bOut);
		DapConnection client = DapConnection.client(new IDebugClient() {
			@Override
			public void initialized() {
				events.add("initialized");
			}

			@Override
			public void stopped(StoppedEventBody b) {
				events.add("stopped "+b.getReason()+" "+b.getText());
			}

			@Override
			public void output(OutputEventBody b) {
				outputs.add(b.getCategory()+" "+b.getOutput());
			}

			@Override
			public void exited(ExitedEventBody b) {
				events.add("exited "+b.getExitCode());
			}

			@Override
			public void terminated(us.bringardner.parley.dap.protocol.TerminatedEventBody b) {
				events.add("terminated");
			}
		}, cIn, cOut);
		try {
			IDebugAdapter dap = client.getAdapter();
			Capabilities caps = get(dap.initialize(new InitializeRequestArguments().setAdapterID("toy")));
			assertTrue(caps.getSupportsEvaluateForHovers());
			assertEquals("errors", caps.getExceptionBreakpointFilters().get(0).getFilter());

			String source = TestToyBackend.PROGRAM;
			get(dap.launch(LaunchRequestArguments.fromJson(Json.parse(
					"{\"source\":"+Json.write(source)+",\"name\":\"fact.toy\"}"))));
			assertEquals("initialized", event("initialized"));
			get(dap.setBreakpoints(SetBreakpointsArguments.fromJson(Json.parse(
					"{\"source\":{\"path\":\"fact.toy\"},\"breakpoints\":[{\"line\":8}]}"))));
			get(dap.configurationDone(new ConfigurationDoneArguments()));

			assertEquals("stopped breakpoint null", event("stopped"));
			StackFrame frame = get(dap.stackTrace(StackTraceArguments.fromJson(Json.parse("{\"threadId\":1}")))).getStackFrames().get(0);
			assertEquals(8, frame.getLine());
			assertEquals("fact.toy", frame.getSource().getName());
			int locals = get(dap.scopes(new ScopesArguments().setFrameId(frame.getId()))).getScopes().get(0).getVariablesReference();
			List<Variable> vars = get(dap.variables(new VariablesArguments().setVariablesReference(locals))).getVariables();
			assertEquals("xs", vars.get(0).getName());
			assertTrue(vars.get(0).getVariablesReference() > 0);
			assertEquals(3, vars.get(0).getIndexedVariables());
			assertEquals("3", get(dap.evaluate(new EvaluateArguments().setExpression("len(xs)").setFrameId(frame.getId())
					.setContext("watch"))).getResult());

			// over the call to fact, to the next line of the same frame
			get(dap.next(new NextArguments().setThreadId(1)));
			assertEquals("stopped step null", event("stopped"));
			frame = get(dap.stackTrace(StackTraceArguments.fromJson(Json.parse("{\"threadId\":1}")))).getStackFrames().get(0);
			assertEquals(9, frame.getLine());

			// on to the error at line 11, which the program stops on before it ends
			get(dap.continue_(new ContinueArguments().setThreadId(1)));
			String stop = event("stopped");
			assertTrue(stop.startsWith("stopped exception"), stop);
			assertTrue(stop.contains("Index 7 is out of range"), stop);
			assertEquals("stdout [24, 2, 3]\n", outputs.poll(5, TimeUnit.SECONDS));

			get(dap.continue_(new ContinueArguments().setThreadId(1)));
			String err = outputs.poll(5, TimeUnit.SECONDS);
			assertTrue(err.startsWith("stderr Index 7 is out of range"), err);
			assertEquals("exited 1", event("exited"));
			assertEquals("terminated", event("terminated"));
		} finally {
			client.close();
			bridge.close();
			loupe.close();
		}
	}
}
