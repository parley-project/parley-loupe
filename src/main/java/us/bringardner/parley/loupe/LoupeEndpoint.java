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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import us.bringardner.parley.dap.json.Json;
import us.bringardner.parley.dap.json.JsonException;

/**
 * One end of a Loupe conversation, whatever carries it. Both ends are the same: each can send
 * requests and events and each handles the other's.
 * <pre>
 * request   {"id":7, "method":"addBreakpoint", "target":"t1", "params":{...}}
 * response  {"id":7, "result":{...}}
 * error     {"id":7, "error":{"code":"invalidParams", "message":"...", "data":{...}}}
 * event     {"event":"paused", "target":"t1", "params":{...}}
 * </pre>
 * The transport calls {@link #receive(String)} with each message's JSON and gives the
 * endpoint a {@link Sender} that writes one.
 * <p>
 * Incoming messages are handled in order, on the thread that calls receive (or the executor).
 * A handler returns a future for its response so it needn't block; the response is sent when
 * the future completes. A handler must not wait for a response to a request of its own on the
 * receiving thread.
 * <p>
 * The {@code cancel} request ({@code {"requestId":7}}) is answered by the endpoint itself:
 * the future of request 7 is cancelled, and 7 ends with error {@code cancelled}. Cancelling
 * the future a call to {@link #request} returned sends one.
 */
public class LoupeEndpoint {

	/** Writes one message (the JSON text of a request, response or event). */
	public interface Sender {
		void send(String json) throws IOException;
	}

	/** What the other end asks and tells. */
	public interface Handler {
		/**
		 * A request.
		 * @param target the target it's about, or null
		 * @param params its params (parsed JSON, or null)
		 * @return the result (JSON value, null for none), or a future failed with a
		 *         LoupeException for an error response
		 */
		CompletableFuture<Object> request(String method, String target, Object params);

		/** An event. */
		void event(String event, String target, Object params);
	}

	private final Sender sender;
	private volatile Handler handler;
	private final AtomicLong ids = new AtomicLong();
	private final Map<Object,CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
	private final Map<Object,CompletableFuture<Object>> handling = new ConcurrentHashMap<>();
	private volatile Executor executor;
	private volatile Consumer<Throwable> errorListener = e -> {};
	private volatile boolean closed;
	private final Object sendLock = new Object();

	public LoupeEndpoint(Sender sender, Handler handler) {
		this.sender = sender;
		this.handler = handler;
	}

	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	/** Handle incoming messages on this executor instead of the receiving thread: still one at a time, in order. */
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	/** Told about messages that can't be handled (bad JSON, a response nobody waits for ...). */
	public void setErrorListener(Consumer<Throwable> listener) {
		this.errorListener = listener == null ? e -> {} : listener;
	}

	public boolean isClosed() {
		return closed;
	}

	// ---- sending

	private void send(Map<String,Object> m) throws IOException {
		if( closed ) {
			throw new IOException("The Loupe connection is closed");
		}
		synchronized (sendLock) {
			sender.send(Json.write(m));
		}
	}

	/**
	 * Sends a request.
	 * @param target the target it's about, or null
	 * @return its result (parsed JSON, null if none); failed with LoupeException for an error
	 *         response, or IOException if it couldn't be sent or the connection closed.
	 *         Cancelling it asks the other end to cancel the request.
	 */
	public CompletableFuture<Object> request(String method, String target, Object params) {
		CompletableFuture<Object> f = new CompletableFuture<>();
		Long id = ids.incrementAndGet();
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("id", id);
		m.put("method", method);
		if( target != null ) {
			m.put("target", target);
		}
		if( params != null ) {
			m.put("params", params);
		}
		pending.put(id, f);
		try {
			send(m);
		} catch (IOException | RuntimeException e) {
			pending.remove(id);
			f.completeExceptionally(e);
			return f;
		}
		f.whenComplete((r, e) -> {
			if( f.isCancelled() && pending.remove(id) != null ) {
				Map<String,Object> p = new LinkedHashMap<>();
				p.put("requestId", id);
				request("cancel", null, p);
			}
		});
		return f;
	}

	/** Sends an event. @throws IOException if it couldn't be sent */
	public void event(String event, String target, Object params) throws IOException {
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("event", event);
		if( target != null ) {
			m.put("target", target);
		}
		if( params != null ) {
			m.put("params", params);
		}
		send(m);
	}

	private void respond(Object id, Object result, Throwable error) {
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("id", id);
		try {
			if( error == null ) {
				m.put("result", result == null ? new LinkedHashMap<String,Object>() : result);
			} else {
				m.put("error", errorJson(unwrap(error)));
			}
			send(m);
		} catch (IOException | RuntimeException e) {
			errorListener.accept(e);
		}
	}

	private static Map<String,Object> errorJson(Throwable e) {
		Map<String,Object> m = new LinkedHashMap<>();
		String code = LoupeException.FAILED;
		String message = e.getMessage();
		if( e instanceof LoupeException ) {
			LoupeException le = (LoupeException) e;
			code = le.getCode();
			if( le.getData() != null ) {
				m.put("data", le.getData());
			}
		} else if( e instanceof UnsupportedOperationException ) {
			code = LoupeException.NOT_SUPPORTED;
		} else if( e instanceof java.util.concurrent.CancellationException ) {
			code = LoupeException.CANCELLED;
		}
		if( message == null || message.isBlank()) {
			message = code;
		}
		Map<String,Object> ret = new LinkedHashMap<>();
		ret.put("code", code);
		ret.put("message", message);
		ret.putAll(m);
		return ret;
	}

	static Throwable unwrap(Throwable e) {
		while( (e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null ) {
			e = e.getCause();
		}
		return e;
	}

	// ---- receiving

	/** A message arrived (its JSON text). */
	public void receive(String json) {
		Executor ex = executor;
		if( ex == null ) {
			handle(json);
			return;
		}
		// one at a time, in arrival order, whatever threads the executor has
		inbox.add(json);
		drainOn(ex);
	}

	/** Messages waiting for the executor, in arrival order. */
	private final java.util.Queue<String> inbox = new java.util.concurrent.ConcurrentLinkedQueue<>();
	/** True while a task on the executor is handling the inbox. */
	private final java.util.concurrent.atomic.AtomicBoolean draining = new java.util.concurrent.atomic.AtomicBoolean();

	private void drainOn(Executor ex) {
		if( draining.compareAndSet(false, true) ) {
			try {
				ex.execute(this::drain);
			} catch (RuntimeException rejected) {
				draining.set(false);
				throw rejected;
			}
		}
	}

	private void drain() {
		try {
			String json;
			while( (json = inbox.poll()) != null ) {
				handle(json);
			}
		} finally {
			draining.set(false);
			if( !inbox.isEmpty() ) {
				Executor ex = executor;
				if( ex != null ) {
					drainOn(ex); // one came in as this task finished
				} else {
					String json;
					while( (json = inbox.poll()) != null ) {
						handle(json);
					}
				}
			}
		}
	}

	private void handle(String json) {
		Map<String,Object> m;
		try {
			m = Json.parseObject(json);
		} catch (JsonException e) {
			errorListener.accept(e);
			return;
		}
		try {
			if( m.containsKey("method")) {
				handleRequest(m);
			} else if( m.containsKey("event")) {
				Handler h = handler;
				if( h != null ) {
					h.event(text(m.get("event"), "event"), optionalText(m.get("target"), "target"), m.get("params"));
				}
			} else if( m.containsKey("result") || m.containsKey("error")) {
				handleResponse(m);
			} else {
				throw new JsonException("Not a Loupe request, response or event");
			}
		} catch (JsonException | RuntimeException e) {
			errorListener.accept(e);
		}
	}

	private static String text(Object o, String what) throws JsonException {
		if( !(o instanceof String)) {
			throw new JsonException(what+" must be a string");
		}
		return (String) o;
	}

	private static String optionalText(Object o, String what) throws JsonException {
		return o == null ? null : text(o, what);
	}

	private static boolean validId(Object id) {
		return id instanceof Long || id instanceof String;
	}

	private void handleRequest(Map<String,Object> m) throws JsonException {
		Object id = m.get("id");
		if( !validId(id)) {
			throw new JsonException("A request needs an integer or string id");
		}
		String method = text(m.get("method"), "method");
		String target;
		try {
			target = optionalText(m.get("target"), "target");
		} catch (JsonException e) {
			respond(id, null, new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage()));
			return;
		}
		if( method.equals("cancel")) {
			handleCancel(id, m.get("params"));
			return;
		}
		Handler h = handler;
		CompletableFuture<Object> f;
		try {
			f = h == null ? null : h.request(method, target, m.get("params"));
			if( h == null ) {
				f = failed(new LoupeException(LoupeException.UNKNOWN_METHOD, "No handler for "+method));
			} else if( f == null ) {
				f = CompletableFuture.completedFuture(null);
			}
		} catch (Throwable e) {
			f = failed(e);
		}
		CompletableFuture<Object> running = f;
		handling.put(id, running);
		running.whenComplete((result, error) -> {
			if( handling.remove(id) != null ) {
				respond(id, result, error);
			}
		});
	}

	private void handleCancel(Object id, Object params) {
		Object target = params instanceof Map ? ((Map<?,?>) params).get("requestId") : null;
		CompletableFuture<Object> f = target == null ? null : handling.remove(target);
		if( f != null ) {
			f.cancel(true);
			respond(target, null, new LoupeException(LoupeException.CANCELLED, "Cancelled"));
		}
		respond(id, null, null);
	}

	private void handleResponse(Map<String,Object> m) throws JsonException {
		Object id = m.get("id");
		CompletableFuture<Object> f = validId(id) ? pending.remove(id) : null;
		if( f == null ) {
			Object e = m.get("error");
			if( e instanceof Map && LoupeException.CANCELLED.equals(((Map<?,?>) e).get("code"))) {
				// the answer to a request we cancelled
				return;
			}
			throw new JsonException("A response to no request: "+id);
		}
		Object error = m.get("error");
		if( error == null ) {
			f.complete(m.get("result"));
			return;
		}
		String code = LoupeException.FAILED;
		String message = null;
		Map<String,Object> data = null;
		if( error instanceof Map ) {
			Map<?,?> e = (Map<?,?>) error;
			code = e.get("code") instanceof String ? (String) e.get("code") : code;
			message = e.get("message") instanceof String ? (String) e.get("message") : null;
			if( e.get("data") instanceof Map ) {
				@SuppressWarnings("unchecked")
				Map<String,Object> d = (Map<String,Object>) e.get("data");
				data = d;
			}
		}
		f.completeExceptionally(new LoupeException(code, message == null ? code : message, data));
	}

	private static CompletableFuture<Object> failed(Throwable e) {
		CompletableFuture<Object> f = new CompletableFuture<>();
		f.completeExceptionally(e);
		return f;
	}

	/** The connection closed: requests still waiting fail. */
	public void closed(Throwable why) {
		closed = true;
		IOException e = new IOException("The Loupe connection closed", why);
		for(CompletableFuture<Object> f : pending.values()) {
			f.completeExceptionally(e);
		}
		pending.clear();
		for(CompletableFuture<Object> f : handling.values()) {
			f.cancel(true);
		}
		handling.clear();
	}

	/** Requests sent that have no response yet. */
	public int getPendingCount() {
		return pending.size();
	}
}
