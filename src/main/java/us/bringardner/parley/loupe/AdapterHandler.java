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

import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointResult;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LaunchParams;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.RemoveBreakpointParams;
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
 * Nothing but hello is accepted before hello. A launch or attach makes the target and puts
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
		registry.setFeatures(w.getFeatures());
		welcomed = true;
		return w.toJson();
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
}
