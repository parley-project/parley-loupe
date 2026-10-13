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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestConnection {

	/** Records events, answers requests from a table. */
	static class Peer implements LoupeEndpoint.Handler {
		final BlockingQueue<String> events = new LinkedBlockingQueue<>();
		final Map<String,CompletableFuture<Object>> slow = new java.util.concurrent.ConcurrentHashMap<>();
		final BlockingQueue<String> started = new LinkedBlockingQueue<>();

		@Override
		public CompletableFuture<Object> request(String method, String target, Object params) {
			switch (method) {
			case "echo":
				return CompletableFuture.completedFuture(Map.of("target", String.valueOf(target), "params", String.valueOf(params)));
			case "fail":
				throw new LoupeException(LoupeException.WRONG_STATE, "Not paused", Map.of("state", "running"));
			case "slow":
				CompletableFuture<Object> f = new CompletableFuture<>();
				slow.put(String.valueOf(target), f);
				started.add(String.valueOf(target));
				return f;
			default:
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}
		}

		@Override
		public void event(String event, String target, Object params) {
			events.add(event+" "+target+" "+params);
		}
	}

	private final Peer a = new Peer();
	private final Peer b = new Peer();
	private LoupeConnection ca;
	private LoupeConnection cb;

	@BeforeEach
	public void connect() throws Exception {
		PipedOutputStream aOut = new PipedOutputStream();
		PipedInputStream bIn = new PipedInputStream(aOut, 1<<16);
		PipedOutputStream bOut = new PipedOutputStream();
		PipedInputStream aIn = new PipedInputStream(bOut, 1<<16);
		ca = LoupeConnection.open(a, aIn, aOut);
		cb = LoupeConnection.open(b, bIn, bOut);
	}

	@AfterEach
	public void disconnect() {
		ca.close();
		cb.close();
	}

	@Test
	public void requestAndResult() throws Exception {
		Object r = ca.getEndpoint().request("echo", "t1", Map.of("n", 1L)).get(5, TimeUnit.SECONDS);
		assertEquals(Map.of("target", "t1", "params", "{n=1}"), r);
		// both ends can ask
		r = cb.getEndpoint().request("echo", null, null).get(5, TimeUnit.SECONDS);
		assertEquals("null", ((Map<?,?>) r).get("target"));
	}

	@Test
	public void errorsCarryCodeMessageAndData() throws Exception {
		ExecutionException e = assertThrows(ExecutionException.class, ()->ca.getEndpoint().request("fail", "t1", null).get(5, TimeUnit.SECONDS));
		LoupeException le = (LoupeException) e.getCause();
		assertEquals(LoupeException.WRONG_STATE, le.getCode());
		assertEquals("Not paused", le.getMessage());
		assertEquals(Map.of("state", "running"), le.getData());
		e = assertThrows(ExecutionException.class, ()->ca.getEndpoint().request("nope", null, null).get(5, TimeUnit.SECONDS));
		assertEquals(LoupeException.UNKNOWN_METHOD, ((LoupeException) e.getCause()).getCode());
	}

	@Test
	public void eventsArriveInOrder() throws Exception {
		ca.getEndpoint().event("targetState", "t1", Map.of("state", "paused"));
		ca.getEndpoint().event("breakpointStatus", "t1", null);
		assertEquals("targetState t1 {state=paused}", b.events.poll(5, TimeUnit.SECONDS));
		assertEquals("breakpointStatus t1 null", b.events.poll(5, TimeUnit.SECONDS));
	}

	@Test
	public void cancellingTheFutureCancelsTheRequest() throws Exception {
		CompletableFuture<Object> f = ca.getEndpoint().request("slow", "t1", null);
		assertEquals("t1", b.started.poll(5, TimeUnit.SECONDS));
		f.cancel(true);
		long end = System.currentTimeMillis()+5000;
		while( !b.slow.get("t1").isCancelled() && System.currentTimeMillis() < end ) {
			Thread.sleep(10);
		}
		assertTrue(b.slow.get("t1").isCancelled());
	}

	@Test
	public void cancelRequestEndsTheRequestWithCancelled() throws Exception {
		// cancel from the client by hand, with the id the endpoint chose (the first is 1)
		CompletableFuture<Object> f = ca.getEndpoint().request("slow", "t2", null);
		assertEquals("t2", b.started.poll(5, TimeUnit.SECONDS));
		Object r = ca.getEndpoint().request("cancel", null, Map.of("requestId", 1L)).get(5, TimeUnit.SECONDS);
		assertEquals(Map.of(), r);
		CompletionException e = assertThrows(CompletionException.class, f::join);
		assertEquals(LoupeException.CANCELLED, ((LoupeException) e.getCause()).getCode());
		assertTrue(b.slow.get("t2").isCancelled());
	}

	@Test
	public void closingFailsWhatIsWaiting() throws Exception {
		CompletableFuture<Object> f = ca.getEndpoint().request("slow", "t3", null);
		assertEquals("t3", b.started.poll(5, TimeUnit.SECONDS));
		cb.close();
		ExecutionException e = assertThrows(ExecutionException.class, ()->f.get(5, TimeUnit.SECONDS));
		assertTrue(e.getCause() instanceof java.io.IOException, String.valueOf(e.getCause()));
		ca.closed().get(5, TimeUnit.SECONDS);
		assertEquals(0, ca.getEndpoint().getPendingCount());
	}

	@Test
	public void stringIdsAndBadInputAreHandled() throws Exception {
		List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
		LoupeEndpoint ep = new LoupeEndpoint(json -> errors.add(new Exception(json)), a);
		ep.setErrorListener(errors::add);
		ep.receive("{\"id\":\"x-1\",\"method\":\"echo\",\"target\":\"t\"}");
		assertEquals(1, errors.size());
		assertTrue(errors.get(0).getMessage().contains("\"id\":\"x-1\""), errors.get(0).getMessage());
		assertTrue(errors.get(0).getMessage().contains("\"result\""), errors.get(0).getMessage());
		errors.clear();
		ep.receive("[1]");
		ep.receive("{\"hello\":1}");
		ep.receive("{\"id\":99,\"result\":{}}");
		assertEquals(3, errors.size());
		assertNull(a.events.poll());
	}
}
