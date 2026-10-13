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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.dap.json.JsonValue;
import us.bringardner.parley.dap.json.JsonValues;
import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointResult;
import us.bringardner.parley.loupe.protocol.ChildrenParams;
import us.bringardner.parley.loupe.protocol.CompletionsParams;
import us.bringardner.parley.loupe.protocol.EvaluateParams;
import us.bringardner.parley.loupe.protocol.FramesParams;
import us.bringardner.parley.loupe.protocol.LocalsParams;
import us.bringardner.parley.loupe.protocol.ObjectChangesParams;
import us.bringardner.parley.loupe.protocol.ObjectId;
import us.bringardner.parley.loupe.protocol.ScopesParams;
import us.bringardner.parley.loupe.protocol.SetVariableParams;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.ThreadsResult;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LaunchParams;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.RemoveBreakpointParams;
import us.bringardner.parley.loupe.protocol.StepKind;
import us.bringardner.parley.loupe.protocol.StepParams;
import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.Welcome;

/**
 * The adapter's side of a session: answers hello, launch, attach, addBreakpoint and
 * removeBreakpoint, using a {@link TargetBackend} to do the work, and passes every other
 * method to a fallback handler.
 * <pre>
 * LoupeConnection.serve(endpoint -&gt; new AdapterHandler(endpoint, backend)
 *         .setAdapter(new PeerInfo().setName("fsh").setVersion("1"))
 *         .setLaunchSchema(schema), in, out);
 * </pre>
 * It also answers resume, step, pause and terminate for the methods the backend says it
 * {@link TargetBackend#supports supports}. Nothing but hello is accepted before hello. A launch or attach makes the target and puts
 * it in {@code running} (event {@code targetState}) before the backend starts it; if the
 * backend fails, the target is terminated and the request fails.
 */
public class AdapterHandler implements LoupeEndpoint.Handler {

	private final TargetRegistry registry;
	private final TargetBackend backend;
	private volatile List<String> protocols = List.of(Loupe.PROTOCOL);
	private volatile List<String> features = List.of();
	private volatile PeerInfo adapter;
	private volatile Object launchSchema;
	private volatile Object attachSchema;
	private volatile LoupeEndpoint.Handler fallback;
	private volatile boolean welcomed;

	public AdapterHandler(LoupeEndpoint endpoint, TargetBackend backend) {
		this.registry = new TargetRegistry(endpoint);
		this.backend = backend;
	}

	/** The protocol versions the adapter speaks (default: loupe/1). */
	public AdapterHandler setProtocols(List<String> protocols) {
		this.protocols = List.copyOf(protocols);
		return this;
	}

	/** The features the adapter has (default: none). */
	public AdapterHandler setFeatures(List<String> features) {
		this.features = List.copyOf(features);
		return this;
	}

	public AdapterHandler setAdapter(PeerInfo adapter) {
		this.adapter = adapter;
		return this;
	}

	/** A JSON Schema for the args of launch, published in the welcome. */
	public AdapterHandler setLaunchSchema(Object schema) {
		this.launchSchema = schema;
		return this;
	}

	/** A JSON Schema for the args of attach, published in the welcome (null: can't attach). */
	public AdapterHandler setAttachSchema(Object schema) {
		this.attachSchema = schema;
		return this;
	}

	/** Gets the methods and events this class doesn't handle. Use {@link #getTargets()} to find their target. */
	public AdapterHandler setFallback(LoupeEndpoint.Handler fallback) {
		this.fallback = fallback;
		return this;
	}

	public TargetRegistry getTargets() {
		return registry;
	}

	@Override
	public CompletableFuture<Object> request(String method, String target, Object params) {
		if( Loupe.HELLO.equals(method)) {
			return completed(hello(params));
		}
		if( !welcomed ) {
			throw new LoupeException(LoupeException.WRONG_STATE, "Say hello first");
		}
		switch (method) {
		case Loupe.LAUNCH:
			return start(params, false);
		case Loupe.ATTACH:
			return start(params, true);
		case Loupe.ADD_BREAKPOINT:
			return completed(addBreakpoint(target, params));
		case Loupe.REMOVE_BREAKPOINT:
			return completed(removeBreakpoint(target, params));
		case Loupe.RESUME:
			return resume(target);
		case Loupe.STEP:
			return step(target, params);
		case Loupe.PAUSE:
			return pause(target);
		case Loupe.TERMINATE:
			return terminate(target);
		case Loupe.THREADS:
			return threads(target);
		case Loupe.FRAMES:
			// every member is optional: no params is the same as {}
			return inspect(target, method, params == null ? new LinkedHashMap<String,Object>() : params, FramesParams::fromJson,
					(t, p) -> page(p.getStart(), p.getCount(), p::setStart),
					(t, p) -> backend.frames(t, p));
		case Loupe.SCOPES:
			return inspect(target, method, params, ScopesParams::fromJson,
					(t, p) -> fresh(t, p.getFrame()),
					(t, p) -> backend.scopes(t, p));
		case Loupe.LOCALS:
			return inspect(target, method, params, LocalsParams::fromJson,
					(t, p) -> {
						fresh(t, p.getFrame());
						page(p.getStart(), p.getCount(), p::setStart);
					},
					(t, p) -> backend.locals(t, p));
		case Loupe.CHILDREN:
			return inspect(target, method, params, ChildrenParams::fromJson,
					(t, p) -> {
						fresh(t, p.getId());
						page(p.getStart(), p.getCount(), p::setStart);
					},
					(t, p) -> backend.children(t, p));
		case Loupe.OBJECT_CHANGES:
			return objectChanges(target, params);
		case Loupe.EVALUATE:
			return inspect(target, method, params, EvaluateParams::fromJson,
					(t, p) -> {
						if( p.getExpression().isBlank()) {
							throw new LoupeException(LoupeException.INVALID_PARAMS, "The expression is empty");
						}
						if( p.getFrame() != null ) {
							fresh(t, p.getFrame());
						}
					},
					(t, p) -> backend.evaluate(t, p));
		case Loupe.SET_VARIABLE:
			return inspect(target, method, params, SetVariableParams::fromJson,
					(t, p) -> fresh(t, p.getId()),
					(t, p) -> backend.setVariable(t, p));
		case Loupe.COMPLETIONS:
			return inspect(target, method, params, CompletionsParams::fromJson,
					(t, p) -> {
						if( p.getOffset() < 0 || p.getOffset() > p.getText().length()) {
							throw new LoupeException(LoupeException.INVALID_PARAMS, "offset must be from 0 to "+p.getText().length());
						}
						if( p.getFrame() != null ) {
							fresh(t, p.getFrame());
						}
					},
					(t, p) -> backend.completions(t, p));
		default:
			LoupeEndpoint.Handler f = fallback;
			if( f == null ) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, "Unknown method "+method);
			}
			return f.request(method, target, params);
		}
	}

	@Override
	public void event(String event, String target, Object params) {
		LoupeEndpoint.Handler f = fallback;
		if( f != null ) {
			f.event(event, target, params);
		}
	}

	private static CompletableFuture<Object> completed(Object o) {
		return CompletableFuture.completedFuture(o);
	}

	private Object hello(Object params) {
		if( welcomed ) {
			throw new LoupeException(LoupeException.WRONG_STATE, "Hello was already said");
		}
		Hello hello;
		try {
			hello = Hello.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( hello == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "hello needs params");
		}
		Welcome w = Handshake.accept(hello, protocols, features, adapter, launchSchema);
		w.setAttachSchema(attachSchema);
		w.setMethods(methods());
		registry.setFeatures(w.getFeatures());
		welcomed = true;
		return w.toJson();
	}

	/** The methods this adapter answers: the ones that are always there, and what the backend supports. */
	private List<String> methods() {
		List<String> ret = new java.util.ArrayList<>(List.of(Loupe.LAUNCH, Loupe.ADD_BREAKPOINT, Loupe.REMOVE_BREAKPOINT, Loupe.CANCEL));
		if( attachSchema != null ) {
			ret.add(Loupe.ATTACH);
		}
		for(String m : List.of(Loupe.RESUME, Loupe.STEP, Loupe.PAUSE, Loupe.TERMINATE, Loupe.THREADS, Loupe.FRAMES,
				Loupe.SCOPES, Loupe.LOCALS, Loupe.CHILDREN, Loupe.OBJECT_CHANGES, Loupe.EVALUATE, Loupe.COMPLETIONS, Loupe.SET_VARIABLE)) {
			if( backend.supports(m)) {
				ret.add(m);
			}
		}
		return ret;
	}

	private CompletableFuture<Object> start(Object params, boolean attach) {
		LaunchParams p;
		try {
			p = LaunchParams.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "The request needs params");
		}
		Object schema = attach ? attachSchema : launchSchema;
		if( attach && schema == null ) {
			throw new LoupeException(LoupeException.NOT_SUPPORTED, "This adapter can't attach");
		}
		Object args = p.getArgs() == null ? new LinkedHashMap<String,Object>() : p.getArgs();
		if( schema != null ) {
			SchemaValidator.check(schema, args);
		}
		Target t = registry.create(p.getTarget());
		t.setStartPaused(Boolean.TRUE.equals(p.getStartPaused()));
		t.running(attach ? "attach" : "launch");
		CompletableFuture<Void> f;
		try {
			f = attach ? backend.attach(t, args) : backend.launch(t, args);
		} catch (RuntimeException e) {
			f = new CompletableFuture<>();
			f.completeExceptionally(e);
		}
		return f.handle((v, e) -> {
			if( e != null ) {
				if( !t.isTerminated()) {
					t.terminated(attach ? "attachFailed" : "launchFailed", null);
				}
				throw e instanceof CompletionException ? (CompletionException) e : new CompletionException(e);
			}
			if( t.isStartPaused() && !TargetState.PAUSED.equals(t.getState())) {
				// it was asked to wait for the client's breakpoints, and is running without them
				LoupeException le = new LoupeException(LoupeException.NOT_SUPPORTED, "The adapter did not start the target paused");
				if( !t.isTerminated()) {
					t.terminated(attach ? "attachFailed" : "launchFailed", null);
				}
				throw le;
			}
			Map<String,Object> ret = new LinkedHashMap<>();
			ret.put("target", t.getId());
			ret.put("state", t.getState());
			return ret;
		});
	}

	private Object addBreakpoint(String target, Object params) {
		Target t = registry.get(target);
		AddBreakpointParams p;
		try {
			p = AddBreakpointParams.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "The request needs params");
		}
		t.require("running", "paused");
		t.getBreakpoints().add(p);
		BreakpointTable.Entry entry = t.getBreakpoints().get(p.getId());
		backend.breakpointAdded(t, entry);
		// the backend may have settled it already
		return new BreakpointResult().setStatus(entry.getStatus())
				.setWhere(entry.getWhere() == p.getWhere() ? null : entry.getWhere())
				.setMessage(entry.getMessage()).toJson();
	}

	private Object removeBreakpoint(String target, Object params) {
		Target t = registry.get(target);
		RemoveBreakpointParams p;
		try {
			p = RemoveBreakpointParams.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "The request needs params");
		}
		t.require("running", "paused");
		t.send(t.getBreakpoints().remove(p.getId()));
		backend.breakpointRemoved(t, p.getId());
		return new LinkedHashMap<String,Object>();
	}

	// ---- execution control

	private Target controllable(String target, String method) {
		Target t = registry.get(target);
		if( !backend.supports(method)) {
			throw new LoupeException(LoupeException.NOT_SUPPORTED, "This adapter doesn't "+method);
		}
		return t;
	}

	private static CompletableFuture<Object> done(CompletableFuture<Void> f, java.util.function.Consumer<Throwable> onError) {
		return f.handle((v, e) -> {
			if( e != null ) {
				onError.accept(e);
				throw e instanceof CompletionException ? (CompletionException) e : new CompletionException(e);
			}
			return new LinkedHashMap<String,Object>();
		});
	}

	private static CompletableFuture<Void> call(Supplier<CompletableFuture<Void>> action) {
		try {
			CompletableFuture<Void> f = action.get();
			return f == null ? CompletableFuture.completedFuture(null) : f;
		} catch (RuntimeException e) {
			CompletableFuture<Void> f = new CompletableFuture<>();
			f.completeExceptionally(e);
			return f;
		}
	}

	/** The target leaves a stop: running first (the backend may stop it again at once), back to paused if starting fails. */
	private CompletableFuture<Object> leaveStop(Target t, String reason, Supplier<CompletableFuture<Void>> action) {
		t.running(reason);
		return done(call(action), e -> {
			synchronized (t) {
				if( TargetState.RUNNING.equals(t.getState())) {
					t.paused(reason+"Failed", null, null);
				}
			}
		});
	}

	private CompletableFuture<Object> resume(String target) {
		Target t = controllable(target, Loupe.RESUME);
		t.require(TargetState.PAUSED);
		return leaveStop(t, "resume", () -> backend.resume(t));
	}

	private CompletableFuture<Object> step(String target, Object params) {
		Target t = controllable(target, Loupe.STEP);
		StepParams p;
		try {
			p = StepParams.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null || !StepKind.isValid(p.getKind())) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "kind must be over, in or out");
		}
		t.require(TargetState.PAUSED);
		return leaveStop(t, "step", () -> backend.step(t, p));
	}

	private CompletableFuture<Object> pause(String target) {
		Target t = controllable(target, Loupe.PAUSE);
		t.require(TargetState.RUNNING);
		return done(call(() -> backend.pause(t)), e -> {
		});
	}

	private CompletableFuture<Object> terminate(String target) {
		Target t = controllable(target, Loupe.TERMINATE);
		t.require(TargetState.RUNNING, TargetState.PAUSED);
		return call(() -> backend.terminate(t)).handle((v, e) -> {
			if( e != null ) {
				throw e instanceof CompletionException ? (CompletionException) e : new CompletionException(e);
			}
			synchronized (t) {
				if( !t.isTerminated()) {
					t.terminated("kill", null);
				}
			}
			return new LinkedHashMap<String,Object>();
		});
	}

	// ---- the stack and values

	/** Checks a request's params; may throw LoupeException. */
	private interface Check<P> {
		void check(Target target, P params);
	}

	/**
	 * A request about the stopped target: the method must be supported, the params parse,
	 * the target be paused, and the ids in them be current.
	 */
	private <P, R extends JsonValue> CompletableFuture<Object> inspect(String target, String method, Object params,
			JsonValues.Reader<P> reader, Check<P> check, BiFunction<Target,P,CompletableFuture<R>> call) {
		Target t = controllable(target, method);
		P p;
		try {
			p = reader.read(params, method);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, method+" needs params");
		}
		t.require(TargetState.PAUSED);
		check.check(t, p);
		CompletableFuture<R> f = call.apply(t, p);
		if( f == null ) {
			throw new LoupeException(LoupeException.FAILED, "The adapter has no answer for "+method);
		}
		return f.thenApply(r -> r == null ? null : (Object) r.toJson());
	}

	/** An id with a generation must be of the target's current one. */
	private static void fresh(Target t, String id) {
		ObjectId oid;
		try {
			oid = ObjectId.parse(id);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		int now = t.getGeneration();
		if( oid.hasGeneration() && oid.getGeneration() != now ) {
			Map<String,Object> data = new LinkedHashMap<>();
			data.put("id", id);
			data.put("generation", now);
			throw new LoupeException(LoupeException.STALE_ID, id+" is from generation "+oid.getGeneration()+", the target is at "+now, data);
		}
	}

	/** start defaults to 0; neither may be negative. */
	private static void page(Integer start, Integer count, java.util.function.Consumer<Integer> setStart) {
		if( start != null && start < 0 || count != null && count < 0 ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "start and count can't be negative");
		}
		if( start == null ) {
			setStart.accept(0);
		}
	}

	private CompletableFuture<Object> threads(String target) {
		Target t = controllable(target, Loupe.THREADS);
		t.require(TargetState.RUNNING, TargetState.PAUSED);
		CompletableFuture<List<ThreadInfo>> f = backend.threads(t);
		return f.thenApply(list -> new ThreadsResult().setThreads(list).toJson());
	}

	private CompletableFuture<Object> objectChanges(String target, Object params) {
		Target t = controllable(target, Loupe.OBJECT_CHANGES);
		if( !registry.hasFeature(Loupe.FEATURE_OBJECT_CHANGES)) {
			throw new LoupeException(LoupeException.NOT_SUPPORTED, "objectChanges wasn't negotiated");
		}
		ObjectChangesParams p;
		try {
			p = ObjectChangesParams.fromJson(params);
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, e.getMessage());
		}
		if( p == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "objectChanges needs params");
		}
		t.require(TargetState.PAUSED);
		int now = t.getGeneration();
		if( p.getSince() < 0 || p.getSince() > now ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "since must be from 0 to "+now);
		}
		return backend.objectChanges(t, p).thenApply(r -> {
			if( r.getGeneration() == null ) {
				r.setGeneration(now);
			}
			return r.toJson();
		});
	}
}
