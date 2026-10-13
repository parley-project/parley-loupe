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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import us.bringardner.parley.dap.DapConnection;
import us.bringardner.parley.dap.DapEndpoint;
import us.bringardner.parley.dap.DapException;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.dap.json.JsonValue;
import us.bringardner.parley.dap.json.JsonValues;
import us.bringardner.parley.dap.protocol.AttachRequestArguments;
import us.bringardner.parley.dap.protocol.Breakpoint;
import us.bringardner.parley.dap.protocol.BreakpointEventBody;
import us.bringardner.parley.dap.protocol.Capabilities;
import us.bringardner.parley.dap.protocol.CompletionItem;
import us.bringardner.parley.dap.protocol.CompletionsArguments;
import us.bringardner.parley.dap.protocol.CompletionsResponseBody;
import us.bringardner.parley.dap.protocol.ConfigurationDoneArguments;
import us.bringardner.parley.dap.protocol.ContinueArguments;
import us.bringardner.parley.dap.protocol.ContinueResponseBody;
import us.bringardner.parley.dap.protocol.ContinuedEventBody;
import us.bringardner.parley.dap.protocol.DisconnectArguments;
import us.bringardner.parley.dap.protocol.EvaluateArguments;
import us.bringardner.parley.dap.protocol.EvaluateResponseBody;
import us.bringardner.parley.dap.protocol.ExceptionBreakpointsFilter;
import us.bringardner.parley.dap.protocol.ExceptionDetails;
import us.bringardner.parley.dap.protocol.ExceptionInfoArguments;
import us.bringardner.parley.dap.protocol.ExceptionInfoResponseBody;
import us.bringardner.parley.dap.protocol.ExitedEventBody;
import us.bringardner.parley.dap.protocol.IDebugAdapter;
import us.bringardner.parley.dap.protocol.IDebugClient;
import us.bringardner.parley.dap.protocol.InitializeRequestArguments;
import us.bringardner.parley.dap.protocol.LaunchRequestArguments;
import us.bringardner.parley.dap.protocol.NextArguments;
import us.bringardner.parley.dap.protocol.PauseArguments;
import us.bringardner.parley.dap.protocol.Scope;
import us.bringardner.parley.dap.protocol.ScopesArguments;
import us.bringardner.parley.dap.protocol.ScopesResponseBody;
import us.bringardner.parley.dap.protocol.SetBreakpointsArguments;
import us.bringardner.parley.dap.protocol.SetExceptionBreakpointsArguments;
import us.bringardner.parley.dap.protocol.SetExceptionBreakpointsResponseBody;
import us.bringardner.parley.dap.protocol.SetBreakpointsResponseBody;
import us.bringardner.parley.dap.protocol.Source;
import us.bringardner.parley.dap.protocol.SourceBreakpoint;
import us.bringardner.parley.dap.protocol.SetVariableArguments;
import us.bringardner.parley.dap.protocol.SetVariableResponseBody;
import us.bringardner.parley.dap.protocol.StackFrame;
import us.bringardner.parley.dap.protocol.StackTraceArguments;
import us.bringardner.parley.dap.protocol.StackTraceResponseBody;
import us.bringardner.parley.dap.protocol.StepInArguments;
import us.bringardner.parley.dap.protocol.StepOutArguments;
import us.bringardner.parley.dap.protocol.StoppedEventBody;
import us.bringardner.parley.dap.protocol.TerminateArguments;
import us.bringardner.parley.dap.protocol.TerminatedEventBody;
import us.bringardner.parley.dap.protocol.ThreadsResponseBody;
import us.bringardner.parley.dap.protocol.Variable;
import us.bringardner.parley.dap.protocol.VariablesArguments;
import us.bringardner.parley.dap.protocol.VariablesResponseBody;
import us.bringardner.parley.loupe.Handshake;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.LoupeEndpoint;
import us.bringardner.parley.loupe.LoupeException;
import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointResult;
import us.bringardner.parley.loupe.protocol.BreakpointStatus;
import us.bringardner.parley.loupe.protocol.BreakpointStatusEvent;
import us.bringardner.parley.loupe.protocol.ChildrenParams;
import us.bringardner.parley.loupe.protocol.CompletionsParams;
import us.bringardner.parley.loupe.protocol.CompletionsResult;
import us.bringardner.parley.loupe.protocol.EvaluateParams;
import us.bringardner.parley.loupe.protocol.EvaluateResult;
import us.bringardner.parley.loupe.protocol.ExceptionFilter;
import us.bringardner.parley.loupe.protocol.ExceptionInfoParams;
import us.bringardner.parley.loupe.protocol.ExceptionInfoResult;
import us.bringardner.parley.loupe.protocol.SetExceptionFiltersParams;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.FramesParams;
import us.bringardner.parley.loupe.protocol.FramesResult;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LaunchParams;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;
import us.bringardner.parley.loupe.protocol.RemoveBreakpointParams;
import us.bringardner.parley.loupe.protocol.ScopesParams;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.SetVariableParams;
import us.bringardner.parley.loupe.protocol.SetVariableResult;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.StepKind;
import us.bringardner.parley.loupe.protocol.StepParams;
import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.TargetStateEvent;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.ThreadsResult;
import us.bringardner.parley.loupe.protocol.VariablesResult;

/**
 * Lets a DAP client (VS Code, Eclipse, vim ...) debug a Loupe target: it is a DAP adapter
 * that is a Loupe client.
 * <pre>
 * DAP client &lt;-- DAP --&gt; DapBridge &lt;-- Loupe --&gt; Loupe adapter
 * </pre>
 * <b>Session.</b> One DAP session is one Loupe target, with the id {@code t1}.
 * <ul>
 * <li>{@code initialize} sends hello (asking for the snapshot feature).</li>
 * <li>{@code launch} and {@code attach} send launch or attach with {@code startPaused}, so
 * breakpoints can be set before anything runs. The arguments are the request's own members,
 * but {@code noDebug}, {@code restart}, members starting with {@code __} and
 * {@code stopOnEntry} (which the bridge handles) are left out. The {@code initialized} event
 * follows the launch response.</li>
 * <li>{@code configurationDone} resumes the target, or, with {@code stopOnEntry}, reports
 * the stop at the entry.</li>
 * </ul>
 * <b>Ids.</b> DAP's thread ids are Loupe's. Frame, variable and breakpoint ids are numbers
 * the bridge hands out and maps to Loupe's ids; the first two are forgotten each time the
 * target's state changes, as DAP allows.
 * <p>
 * <b>Round trips.</b> The snapshot in a stop is used for threads, the stack trace (when it
 * holds the frames asked for) and the locals of the top frame, so a stop costs the client no
 * requests to Loupe in the usual case.
 * <p>
 * <b>Evaluate and completions</b> go to the Loupe methods of the same names; the
 * capabilities the client is told follow the methods the Loupe adapter says it has.
 * <p>
 * <b>Exceptions:</b> the adapter's exception filters become DAP's exception breakpoint
 * filters, {@code setExceptionBreakpoints} sends the chosen ones, and {@code exceptionInfo}
 * is mapped. Filter conditions and exception options are not.
 * <p>
 * <b>Not mapped:</b> function and data breakpoints, modules, memory: they answer
 * notSupported.
 * <p>
 * {@code setVariable} works on the variables the client was given by a scope or a variable:
 * the reference names the Loupe container.
 * <p>
 * Use {@link #open} to connect a pair of DAP streams to a pair of Loupe streams.
 */
public final class DapBridge implements IDebugAdapter {

	/** The id of the one target of a session. */
	public static final String TARGET = "t1";

	/** What a variablesReference stands for. */
	private static final class Ref {
		final String id;
		final String scope;
		final String frame;

		Ref(String id, String scope, String frame) {
			this.id = id;
			this.scope = scope;
			this.frame = frame;
		}
	}

	/** A breakpoint as the bridge keeps it. */
	private static final class Bp {
		final int dapId;
		final String loupeId;
		final Source source;
		final int line;
		final Integer column;
		final String condition;
		final String hitCondition;
		final String logMessage;
		Integer hitCount;
		boolean sent;
		boolean verified;
		int actualLine;
		String message;

		Bp(int dapId, Source source, SourceBreakpoint sb) {
			this.dapId = dapId;
			this.loupeId = "b"+dapId;
			this.source = source;
			this.line = sb.getLine() == null ? 0 : sb.getLine();
			this.column = sb.getColumn();
			this.condition = sb.getCondition();
			this.hitCondition = sb.getHitCondition();
			this.logMessage = sb.getLogMessage();
			this.actualLine = line;
		}

		boolean same(SourceBreakpoint sb) {
			return line == (sb.getLine() == null ? 0 : sb.getLine())
					&& java.util.Objects.equals(column, sb.getColumn())
					&& java.util.Objects.equals(condition, sb.getCondition())
					&& java.util.Objects.equals(hitCondition, sb.getHitCondition())
					&& java.util.Objects.equals(logMessage, sb.getLogMessage());
		}
	}

	private final IDebugClient client;
	private final LoupeEndpoint loupe;
	private volatile Runnable onDisconnect;

	// all of these are guarded by this
	private boolean launched;
	private boolean attached;
	private boolean configured;
	private boolean stopOnEntry;
	private boolean ownMove;
	private boolean terminatedSent;
	private String state;
	private TargetStateEvent pendingStop;
	private Snapshot snapshot;
	private int nextId = 1;
	private final Map<Integer,String> frames = new HashMap<>();
	private final Map<Integer,Ref> refs = new HashMap<>();
	private final Map<String,List<Bp>> breakpoints = new HashMap<>();
	private final Map<String,Bp> byLoupeId = new HashMap<>();
	private int nextBreakpoint = 1;
	private boolean columnsStartAt1 = true;
	private boolean linesStartAt1 = true;
	private boolean hasExceptionFilters;
	private List<String> pendingExceptionFilters;

	/**
	 * @param client the DAP client, for events
	 * @param loupe the connection to the Loupe adapter; its handler must be {@link #loupeHandler()}
	 */
	public DapBridge(IDebugClient client, LoupeEndpoint loupe) {
		this.client = client;
		this.loupe = loupe;
	}

	/** Runs when the DAP client disconnects, after the target has been ended: close the Loupe connection here. */
	public void setOnDisconnect(Runnable r) {
		this.onDisconnect = r;
	}

	/**
	 * Connects the streams of a DAP client with the streams of a Loupe adapter.
	 * @return the DAP side; when it closes, so does the Loupe side
	 */
	public static DapConnection open(InputStream dapIn, OutputStream dapOut, InputStream loupeIn, OutputStream loupeOut) {
		LoupeConnection[] loupeConnection = new LoupeConnection[1];
		DapConnection dap = DapConnection.adapter(client -> {
			DapBridge[] bridge = new DapBridge[1];
			loupeConnection[0] = LoupeConnection.serve(ep -> {
				bridge[0] = new DapBridge(client, ep);
				return bridge[0].loupeHandler();
			}, loupeIn, loupeOut);
			bridge[0].setOnDisconnect(loupeConnection[0]::close);
			loupeConnection[0].closed().thenRun(bridge[0]::loupeClosed);
			return bridge[0];
		}, dapIn, dapOut);
		dap.closed().thenRun(loupeConnection[0]::close);
		return dap;
	}

	// ---- Loupe events

	/** Receives the Loupe adapter's events. */
	public LoupeEndpoint.Handler loupeHandler() {
		return new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, "A debugger client has no "+method);
			}

			@Override
			public void event(String event, String target, Object params) {
				try {
					if( Loupe.TARGET_STATE.equals(event)) {
						onTargetState(TargetStateEvent.fromJson(params));
					} else if( Loupe.BREAKPOINT_STATUS.equals(event)) {
						onBreakpointStatus(BreakpointStatusEvent.fromJson(params));
					}
				} catch (JsonException e) {
					// an event the bridge can't read is ignored
				}
			}
		};
	}

	private void onTargetState(TargetStateEvent ev) {
		StoppedEventBody stopped = null;
		boolean continued = false;
		boolean ended = false;
		synchronized (this) {
			frames.clear();
			refs.clear();
			state = ev.getState();
			if( TargetState.PAUSED.equals(ev.getState())) {
				snapshot = ev.getSnapshot();
				if( !configured ) {
					pendingStop = ev;
				} else {
					stopped = stopped(ev);
				}
			} else if( TargetState.RUNNING.equals(ev.getState())) {
				snapshot = null;
				boolean starting = "launch".equals(ev.getReason()) || "attach".equals(ev.getReason());
				if( ownMove ) {
					ownMove = false;
				} else if( !starting ) {
					continued = true;
				}
			} else if( TargetState.TERMINATED.equals(ev.getState())) {
				snapshot = null;
				ended = !terminatedSent;
				terminatedSent = true;
			}
		}
		if( stopped != null ) {
			client.stopped(stopped);
		}
		if( continued ) {
			client.continued(new ContinuedEventBody().setThreadId(1).setAllThreadsContinued(true));
		}
		if( ended ) {
			if( ev.getExitCode() != null ) {
				client.exited(new ExitedEventBody().setExitCode(ev.getExitCode()));
			}
			client.terminated(new TerminatedEventBody());
		}
	}

	/** The stopped event for a pause (the caller holds the lock). */
	private StoppedEventBody stopped(TargetStateEvent ev) {
		Snapshot s = ev.getSnapshot();
		int thread = 1;
		if( s != null && s.getThread() != null ) {
			thread = s.getThread();
		} else if( s != null && s.getThreads() != null && !s.getThreads().isEmpty()) {
			thread = s.getThreads().get(0).getId();
		}
		String reason = ev.getReason() == null ? "pause" : ev.getReason();
		StoppedEventBody b = new StoppedEventBody().setThreadId(thread).setAllThreadsStopped(true);
		if( ev.getDescription() != null ) {
			b.setText(ev.getDescription());
		}
		switch (reason) {
		case "breakpoint":
		case "step":
		case "pause":
		case "entry":
		case "exception":
			b.setReason(reason);
			break;
		default:
			b.setReason("pause").setDescription(reason);
		}
		if( ev.getBreakpoints() != null && !ev.getBreakpoints().isEmpty()) {
			List<Integer> ids = new ArrayList<>();
			for(String id : ev.getBreakpoints()) {
				Bp bp = byLoupeId.get(id);
				if( bp != null ) {
					ids.add(bp.dapId);
				}
			}
			b.setHitBreakpointIds(ids);
		}
		return b;
	}

	private void onBreakpointStatus(BreakpointStatusEvent ev) {
		Breakpoint b = null;
		synchronized (this) {
			Bp bp = byLoupeId.get(ev.getId());
			if( bp == null || BreakpointStatus.REMOVED.equals(ev.getStatus())) {
				return;
			}
			update(bp, ev.getStatus(), ev.getWhere(), ev.getMessage());
			b = toDap(bp);
		}
		client.breakpoint(new BreakpointEventBody().setReason(BreakpointEventBody.Reason.CHANGED).setBreakpoint(b));
	}

	/** The Loupe connection ended: the debuggee is gone as far as the client can tell. */
	void loupeClosed() {
		boolean send;
		synchronized (this) {
			send = launched && !terminatedSent;
			terminatedSent = true;
		}
		if( send ) {
			client.terminated(new TerminatedEventBody());
		}
	}

	// ---- helpers

	private <T> CompletableFuture<T> ask(String method, JsonValue params, JsonValues.Reader<T> reader) {
		return loupe.request(method, TARGET, params == null ? null : params.toJson()).thenApply(r -> {
			try {
				return reader.read(r, method);
			} catch (JsonException e) {
				throw new CompletionException(e);
			}
		}).handle((r, e) -> {
			if( e != null ) {
				throw new CompletionException(dapError(e));
			}
			return r;
		});
	}

	private static DapException dapError(Throwable e) {
		while( (e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null ) {
			e = e.getCause();
		}
		if( e instanceof DapException ) {
			return (DapException) e;
		}
		if( e instanceof LoupeException ) {
			return new DapException(e.getMessage());
		}
		if( e instanceof IOException ) {
			return new DapException("The connection to the debugger is closed");
		}
		return new DapException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
	}

	private static <T> CompletableFuture<T> failed(String message) {
		CompletableFuture<T> f = new CompletableFuture<>();
		f.completeExceptionally(new DapException(message));
		return f;
	}

	// holds the lock
	private int frameId(String loupeFrame) {
		for(Map.Entry<Integer,String> e : frames.entrySet()) {
			if( e.getValue().equals(loupeFrame)) {
				return e.getKey();
			}
		}
		int id = nextId++;
		frames.put(id, loupeFrame);
		return id;
	}

	private synchronized int newRef(String id, String scope, String frame) {
		int r = nextId++;
		refs.put(r, new Ref(id, scope, frame));
		return r;
	}

	private static Source toDap(us.bringardner.parley.loupe.protocol.Source s) {
		if( s == null ) {
			return null;
		}
		return new Source().setPath(s.getPath()).setName(s.getName() != null ? s.getName() : s.getPath() == null ? null : fileName(s.getPath()));
	}

	private static String fileName(String path) {
		int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return i < 0 ? path : path.substring(i+1);
	}

	// ---- session

	@Override
	public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
		synchronized (this) {
			columnsStartAt1 = args == null || args.getColumnsStartAt1() == null || args.getColumnsStartAt1();
			linesStartAt1 = args == null || args.getLinesStartAt1() == null || args.getLinesStartAt1();
		}
		Hello hello = new Hello().setProtocols(List.of(Loupe.PROTOCOL))
				.setClient(new PeerInfo().setName("parley-loupe DAP bridge"))
				.setFeatures(List.of(Loupe.FEATURE_SNAPSHOT));
		return Handshake.hello(loupe, hello).handle((w, e) -> {
			if( e != null ) {
				throw new CompletionException(dapError(e));
			}
			List<String> methods = w.getMethods() == null ? List.of() : w.getMethods();
			return new Capabilities()
					.setSupportsConfigurationDoneRequest(true)
					.setSupportsConditionalBreakpoints(true)
					.setSupportsHitConditionalBreakpoints(true)
					.setSupportsLogPoints(true)
					.setSupportsTerminateRequest(methods.contains(Loupe.TERMINATE))
					.setSupportsEvaluateForHovers(methods.contains(Loupe.EVALUATE))
					.setSupportsCompletionsRequest(methods.contains(Loupe.COMPLETIONS))
					.setSupportsSetVariable(methods.contains(Loupe.SET_VARIABLE))
					.setSupportsExceptionInfoRequest(methods.contains(Loupe.EXCEPTION_INFO))
					.setExceptionBreakpointFilters(exceptionFilters(w.getExceptionFilters()));
		});
	}

	@Override
	public CompletableFuture<Void> launch(LaunchRequestArguments args) {
		return start(false, args.getExtra());
	}

	@Override
	public CompletableFuture<Void> attach(AttachRequestArguments args) {
		return start(true, args.getExtra());
	}

	private CompletableFuture<Void> start(boolean attach, Map<String,Object> extra) {
		Map<String,Object> loupeArgs = new LinkedHashMap<>();
		boolean stop = false;
		if( extra != null ) {
			for(Map.Entry<String,Object> e : extra.entrySet()) {
				String k = e.getKey();
				if( k.equals("stopOnEntry")) {
					stop = Boolean.TRUE.equals(e.getValue());
				} else if( !k.equals("noDebug") && !k.equals("restart") && !k.startsWith("__")) {
					loupeArgs.put(k, e.getValue());
				}
			}
		}
		boolean stopOnEntryNow = stop;
		AtomicBoolean ok = new AtomicBoolean();
		// the initialized event follows the response, and only a response that succeeded
		DapEndpoint.afterResponse(() -> {
			if( ok.get()) {
				client.initialized();
			}
		});
		LaunchParams p = new LaunchParams().setTarget(TARGET).setArgs(loupeArgs).setStartPaused(true);
		return loupe.request(attach ? Loupe.ATTACH : Loupe.LAUNCH, null, p.toJson()).<Void>handle((r, e) -> {
			if( e != null ) {
				throw new CompletionException(dapError(e));
			}
			synchronized (this) {
				launched = true;
				attached = attach;
				stopOnEntry = stopOnEntryNow;
			}
			ok.set(true);
			sendPending();
			return null;
		});
	}

	/** Breakpoints set before the target existed. */
	private void sendPending() {
		List<Bp> unsent = new ArrayList<>();
		synchronized (this) {
			for(List<Bp> list : breakpoints.values()) {
				for(Bp bp : list) {
					if( !bp.sent && bp.message == null ) {
						bp.sent = true;
						unsent.add(bp);
					}
				}
			}
		}
		for(Bp bp : unsent) {
			add(bp);
		}
		List<String> filters;
		synchronized (this) {
			filters = pendingExceptionFilters;
			pendingExceptionFilters = null;
		}
		if( filters != null ) {
			loupe.request(Loupe.SET_EXCEPTION_FILTERS, TARGET, new SetExceptionFiltersParams().setFilters(filters).toJson());
		}
	}

	private List<ExceptionBreakpointsFilter> exceptionFilters(List<ExceptionFilter> filters) {
		synchronized (this) {
			hasExceptionFilters = filters != null && !filters.isEmpty();
		}
		if( filters == null ) {
			return null;
		}
		List<ExceptionBreakpointsFilter> ret = new ArrayList<>();
		for(ExceptionFilter f : filters) {
			ret.add(new ExceptionBreakpointsFilter().setFilter(f.getId()).setLabel(f.getLabel())
					.setDescription(f.getDescription()).setDefault(f.getDefaultOn()));
		}
		return ret;
	}

	@Override
	public CompletableFuture<SetExceptionBreakpointsResponseBody> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
		List<String> filters = args.getFilters() == null ? new ArrayList<>() : args.getFilters();
		synchronized (this) {
			if( !hasExceptionFilters ) {
				// nothing to choose from (a client sends an empty list anyway)
				return CompletableFuture.completedFuture(new SetExceptionBreakpointsResponseBody());
			}
			if( !launched ) {
				pendingExceptionFilters = filters;
				return CompletableFuture.completedFuture(new SetExceptionBreakpointsResponseBody());
			}
		}
		return ask(Loupe.SET_EXCEPTION_FILTERS, new SetExceptionFiltersParams().setFilters(filters), (r, w) -> r)
				.thenApply(r -> new SetExceptionBreakpointsResponseBody());
	}

	@Override
	public CompletableFuture<ExceptionInfoResponseBody> exceptionInfo(ExceptionInfoArguments args) {
		ExceptionInfoParams p = new ExceptionInfoParams().setThread(args.getThreadId());
		return ask(Loupe.EXCEPTION_INFO, p, ExceptionInfoResult::fromJson).thenApply(r -> new ExceptionInfoResponseBody()
				.setExceptionId(r.getId()).setDescription(r.getDescription())
				.setBreakMode(r.getBreakMode() == null ? "always" : r.getBreakMode())
				.setDetails(new ExceptionDetails().setMessage(r.getMessage()).setTypeName(r.getTypeName())
						.setStackTrace(r.getStackTrace())));
	}

	@Override
	public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
		StoppedEventBody stop = null;
		boolean resume = false;
		synchronized (this) {
			configured = true;
			if( !TargetState.PAUSED.equals(state)) {
				return CompletableFuture.completedFuture(null);
			}
			if( stopOnEntry ) {
				if( pendingStop != null ) {
					stop = stopped(pendingStop);
					pendingStop = null;
				}
			} else {
				pendingStop = null;
				ownMove = true;
				resume = true;
			}
		}
		if( stop != null ) {
			client.stopped(stop);
		}
		if( resume ) {
			return ask(Loupe.RESUME, null, (r, w) -> r).handle((r, e) -> {
				if( e != null ) {
					synchronized (this) {
						ownMove = false;
					}
					throw new CompletionException(dapError(e));
				}
				return null;
			});
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Void> disconnect(DisconnectArguments args) {
		boolean kill;
		boolean live;
		synchronized (this) {
			live = launched && !TargetState.TERMINATED.equals(state);
			kill = args != null && args.getTerminateDebuggee() != null ? args.getTerminateDebuggee() : !attached;
		}
		CompletableFuture<Void> f = live && kill
				? ask(Loupe.TERMINATE, null, (r, w) -> r).handle((r, e) -> (Void) null)
				: CompletableFuture.completedFuture(null);
		return f.thenRun(() -> {
			Runnable r = onDisconnect;
			if( r != null ) {
				r.run();
			}
		});
	}

	@Override
	public CompletableFuture<Void> terminate(TerminateArguments args) {
		return ask(Loupe.TERMINATE, null, (r, w) -> r).thenApply(r -> null);
	}

	@Override
	public CompletableFuture<Void> cancel(us.bringardner.parley.dap.protocol.CancelArguments args) {
		// DAP's request ids aren't Loupe's, and every Loupe request here is short
		return CompletableFuture.completedFuture(null);
	}

	// ---- breakpoints

	@Override
	public CompletableFuture<SetBreakpointsResponseBody> setBreakpoints(SetBreakpointsArguments args) {
		Source src = args.getSource();
		if( src == null || src.getPath() == null && src.getName() == null ) {
			return failed("setBreakpoints needs a source");
		}
		String key = src.getPath() != null ? src.getPath() : src.getName();
		List<SourceBreakpoint> wanted = args.getBreakpoints();
		if( wanted == null ) {
			wanted = new ArrayList<>();
			if( args.getLines() != null ) {
				for(Integer line : args.getLines()) {
					wanted.add(new SourceBreakpoint().setLine(line));
				}
			}
		}

		List<Bp> result = new ArrayList<>();
		List<Bp> toRemove = new ArrayList<>();
		List<Bp> toAdd = new ArrayList<>();
		boolean live;
		synchronized (this) {
			live = launched;
			List<Bp> old = new ArrayList<>(breakpoints.getOrDefault(key, List.of()));
			for(SourceBreakpoint sb : wanted) {
				Bp keep = null;
				for(Bp o : old) {
					if( o.same(sb)) {
						keep = o;
						break;
					}
				}
				if( keep != null ) {
					old.remove(keep);
				} else {
					keep = new Bp(nextBreakpoint++, src, sb);
					byLoupeId.put(keep.loupeId, keep);
					parseHitCondition(keep);
					if( keep.message == null && live ) {
						keep.sent = true;
						toAdd.add(keep);
					}
				}
				result.add(keep);
			}
			for(Bp o : old) {
				byLoupeId.remove(o.loupeId);
				if( o.sent ) {
					toRemove.add(o);
				}
			}
			breakpoints.put(key, result);
		}

		List<CompletableFuture<?>> pending = new ArrayList<>();
		for(Bp bp : toRemove) {
			pending.add(loupe.request(Loupe.REMOVE_BREAKPOINT, TARGET, new RemoveBreakpointParams().setId(bp.loupeId).toJson())
					.handle((r, e) -> null));
		}
		for(Bp bp : toAdd) {
			pending.add(add(bp));
		}
		return CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).thenApply(v -> {
			SetBreakpointsResponseBody body = new SetBreakpointsResponseBody();
			List<Breakpoint> list = new ArrayList<>();
			synchronized (this) {
				for(Bp bp : result) {
					list.add(toDap(bp));
				}
			}
			return body.setBreakpoints(list);
		});
	}

	/** A hit condition is a plain count; anything else the bridge can't map is a rejected breakpoint. */
	private static void parseHitCondition(Bp bp) {
		String hc = bp.hitCondition == null ? null : bp.hitCondition.trim();
		if( hc == null || hc.isEmpty()) {
			return;
		}
		try {
			int n = Integer.parseInt(hc);
			if( n < 1 ) {
				throw new NumberFormatException();
			}
			bp.hitCount = n;
		} catch (NumberFormatException e) {
			bp.message = "Only a hit count (a whole number) is supported, not \""+bp.hitCondition+"\"";
		}
	}

	/** Sends addBreakpoint and records what comes back. */
	private CompletableFuture<Void> add(Bp bp) {
		Location where = new Location()
				.setSource(new us.bringardner.parley.loupe.protocol.Source().setPath(bp.source.getPath()).setName(bp.source.getName()))
				.setLine(bp.line).setColumn(bp.column);
		AddBreakpointParams p = new AddBreakpointParams().setId(bp.loupeId).setWhere(where)
				.setCondition(bp.condition).setHitCount(bp.hitCount).setLogMessage(bp.logMessage);
		return loupe.request(Loupe.ADD_BREAKPOINT, TARGET, p.toJson()).handle((r, e) -> {
			synchronized (this) {
				if( e != null ) {
					bp.verified = false;
					bp.message = dapError(e).getMessage();
					return null;
				}
				try {
					BreakpointResult br = BreakpointResult.fromJson(r);
					update(bp, br.getStatus(), br.getWhere(), br.getMessage());
				} catch (JsonException ex) {
					bp.message = ex.getMessage();
				}
			}
			return null;
		});
	}

	// holds the lock
	private static void update(Bp bp, String status, Location where, String message) {
		bp.verified = BreakpointStatus.VERIFIED.equals(status);
		if( where != null && where.getLine() != null ) {
			bp.actualLine = where.getLine();
		}
		bp.message = message;
	}

	// holds the lock
	private static Breakpoint toDap(Bp bp) {
		return new Breakpoint().setId(bp.dapId).setVerified(bp.verified).setSource(bp.source)
				.setLine(bp.actualLine).setColumn(bp.column).setMessage(bp.message);
	}

	// ---- execution

	private CompletableFuture<Void> move(String method, JsonValue params) {
		synchronized (this) {
			ownMove = true;
		}
		return ask(method, params, (r, w) -> r).handle((r, e) -> {
			if( e != null ) {
				synchronized (this) {
					ownMove = false;
				}
				throw new CompletionException(dapError(e));
			}
			return (Void) null;
		});
	}

	@Override
	public CompletableFuture<ContinueResponseBody> continue_(ContinueArguments args) {
		return move(Loupe.RESUME, null).thenApply(v -> new ContinueResponseBody().setAllThreadsContinued(true));
	}

	@Override
	public CompletableFuture<Void> next(NextArguments args) {
		return move(Loupe.STEP, new StepParams().setKind(StepKind.OVER).setThread(args.getThreadId()));
	}

	@Override
	public CompletableFuture<Void> stepIn(StepInArguments args) {
		return move(Loupe.STEP, new StepParams().setKind(StepKind.IN).setThread(args.getThreadId()));
	}

	@Override
	public CompletableFuture<Void> stepOut(StepOutArguments args) {
		return move(Loupe.STEP, new StepParams().setKind(StepKind.OUT).setThread(args.getThreadId()));
	}

	@Override
	public CompletableFuture<Void> pause(PauseArguments args) {
		return ask(Loupe.PAUSE, null, (r, w) -> r).thenApply(r -> null);
	}

	// ---- stack and values

	@Override
	public CompletableFuture<ThreadsResponseBody> threads() {
		Snapshot s;
		synchronized (this) {
			s = TargetState.PAUSED.equals(state) ? snapshot : null;
			if( !launched ) {
				return CompletableFuture.completedFuture(new ThreadsResponseBody().setThreads(new ArrayList<>()));
			}
		}
		if( s != null && s.getThreads() != null ) {
			return CompletableFuture.completedFuture(threads(s.getThreads()));
		}
		return ask(Loupe.THREADS, null, ThreadsResult::fromJson).thenApply(r -> threads(r.getThreads()));
	}

	private static ThreadsResponseBody threads(List<ThreadInfo> list) {
		List<us.bringardner.parley.dap.protocol.Thread> ret = new ArrayList<>();
		for(ThreadInfo t : list) {
			ret.add(new us.bringardner.parley.dap.protocol.Thread().setId(t.getId()).setName(t.getName() == null ? "thread "+t.getId() : t.getName()));
		}
		return new ThreadsResponseBody().setThreads(ret);
	}

	@Override
	public CompletableFuture<StackTraceResponseBody> stackTrace(StackTraceArguments args) {
		int start = args.getStartFrame() == null ? 0 : args.getStartFrame();
		Integer levels = args.getLevels() == null || args.getLevels() <= 0 ? null : args.getLevels();
		Integer thread = args.getThreadId();
		synchronized (this) {
			Snapshot s = TargetState.PAUSED.equals(state) ? snapshot : null;
			if( s != null && s.getFrames() != null && (s.getThread() == null || s.getThread().equals(thread))
					&& s.getTotalFrames() != null && s.getTotalFrames() <= s.getFrames().size()) {
				List<Frame> all = s.getFrames();
				int from = Math.min(start, all.size());
				int to = levels == null ? all.size() : Math.min(all.size(), from+levels);
				return CompletableFuture.completedFuture(stack(all.subList(from, to), all.size()));
			}
		}
		FramesParams p = new FramesParams().setThread(thread).setStart(start).setCount(levels);
		return ask(Loupe.FRAMES, p, FramesResult::fromJson).thenApply(r -> stack(r.getFrames(), r.getTotal()));
	}

	private StackTraceResponseBody stack(List<Frame> list, Integer total) {
		List<StackFrame> ret = new ArrayList<>();
		synchronized (this) {
			for(Frame f : list) {
				ret.add(new StackFrame().setId(frameId(f.getId())).setName(f.getName()).setSource(toDap(f.getSource()))
						.setLine(f.getLine() == null ? 0 : f.getLine()).setColumn(f.getColumn() == null ? 1 : f.getColumn()));
			}
		}
		return new StackTraceResponseBody().setStackFrames(ret).setTotalFrames(total);
	}

	@Override
	public CompletableFuture<ScopesResponseBody> scopes(ScopesArguments args) {
		String frame;
		synchronized (this) {
			frame = frames.get(args.getFrameId());
		}
		if( frame == null ) {
			return failed("Unknown frame "+args.getFrameId());
		}
		return ask(Loupe.SCOPES, new ScopesParams().setFrame(frame), ScopesResult::fromJson).thenApply(r -> {
			List<Scope> ret = new ArrayList<>();
			for(us.bringardner.parley.loupe.protocol.Scope s : r.getScopes()) {
				Scope d = new Scope().setName(s.getName()).setVariablesReference(newRef(s.getId(), s.getName(), frame))
						.setExpensive(Boolean.TRUE.equals(s.getExpensive()));
				if( "locals".equalsIgnoreCase(s.getName())) {
					d.setPresentationHint(Scope.PresentationHint.LOCALS);
				}
				ret.add(d);
			}
			return new ScopesResponseBody().setScopes(ret);
		});
	}

	@Override
	public CompletableFuture<VariablesResponseBody> variables(VariablesArguments args) {
		Ref ref;
		List<us.bringardner.parley.loupe.protocol.Variable> fromSnapshot = null;
		int start = args.getStart() == null ? 0 : args.getStart();
		synchronized (this) {
			ref = refs.get(args.getVariablesReference());
			if( ref != null && "locals".equalsIgnoreCase(ref.scope) && start == 0 && args.getCount() == null
					&& TargetState.PAUSED.equals(state) && snapshot != null && snapshot.getLocals() != null
					&& snapshot.getFrames() != null && !snapshot.getFrames().isEmpty()
					&& ref.frame.equals(snapshot.getFrames().get(0).getId())) {
				fromSnapshot = snapshot.getLocals();
			}
		}
		if( ref == null ) {
			return failed("Unknown variablesReference "+args.getVariablesReference());
		}
		if( fromSnapshot != null ) {
			return CompletableFuture.completedFuture(variables(fromSnapshot));
		}
		Integer count = args.getCount() == null || args.getCount() <= 0 ? null : args.getCount();
		ChildrenParams p = new ChildrenParams().setId(ref.id).setStart(start).setCount(count);
		return ask(Loupe.CHILDREN, p, VariablesResult::fromJson).thenApply(r -> variables(r.getVariables()));
	}

	private VariablesResponseBody variables(List<us.bringardner.parley.loupe.protocol.Variable> list) {
		List<Variable> ret = new ArrayList<>();
		for(us.bringardner.parley.loupe.protocol.Variable v : list) {
			ret.add(toVariable(v.getName(), v.getValue()));
		}
		return new VariablesResponseBody().setVariables(ret);
	}

	/** A value as DAP shows it: its text, type, and a reference if it can be expanded. */
	private Variable toVariable(String name, us.bringardner.parley.loupe.protocol.Value val) {
		Variable d = new Variable().setName(name).setValue(ValueText.of(val)).setType(ValueText.type(val));
		if( ValueText.expandable(val)) {
			d.setVariablesReference(newRef(val.getId(), null, null));
			if( us.bringardner.parley.loupe.protocol.Value.ARRAY.equals(val.getT())) {
				d.setIndexedVariables(val.getSize());
			} else {
				d.setNamedVariables(val.getSize());
			}
		} else {
			d.setVariablesReference(0);
		}
		return d;
	}

	@Override
	public CompletableFuture<SetVariableResponseBody> setVariable(SetVariableArguments args) {
		Ref ref;
		synchronized (this) {
			ref = refs.get(args.getVariablesReference());
		}
		if( ref == null ) {
			return failed("Unknown variablesReference "+args.getVariablesReference());
		}
		SetVariableParams p = new SetVariableParams().setId(ref.id).setName(args.getName()).setValue(args.getValue());
		return ask(Loupe.SET_VARIABLE, p, SetVariableResult::fromJson).thenApply(r -> {
			Variable v = toVariable(args.getName(), r.getValue());
			return new SetVariableResponseBody().setValue(v.getValue()).setType(v.getType())
					.setVariablesReference(v.getVariablesReference()).setNamedVariables(v.getNamedVariables())
					.setIndexedVariables(v.getIndexedVariables());
		});
	}

	// ---- evaluate and completions

	/** The Loupe id of a frame the client names, or null for none; an unknown frame is an error. */
	private synchronized String frameFor(Integer frameId) {
		if( frameId == null ) {
			return null;
		}
		String f = frames.get(frameId);
		if( f == null ) {
			throw new DapException("Unknown frame "+frameId);
		}
		return f;
	}

	@Override
	public CompletableFuture<EvaluateResponseBody> evaluate(EvaluateArguments args) {
		EvaluateParams p;
		try {
			p = new EvaluateParams().setExpression(args.getExpression()).setFrame(frameFor(args.getFrameId()))
					.setContext(args.getContext());
		} catch (DapException e) {
			return failed(e.getMessage());
		}
		return ask(Loupe.EVALUATE, p, EvaluateResult::fromJson).thenApply(r -> {
			Variable v = toVariable("", r.getValue());
			return new EvaluateResponseBody().setResult(v.getValue()).setType(v.getType())
					.setVariablesReference(v.getVariablesReference()).setNamedVariables(v.getNamedVariables())
					.setIndexedVariables(v.getIndexedVariables());
		});
	}

	@Override
	public CompletableFuture<CompletionsResponseBody> completions(CompletionsArguments args) {
		String text = args.getText() == null ? "" : args.getText();
		int offset;
		CompletionsParams p;
		try {
			synchronized (this) {
				int columnBase = columnsStartAt1 ? 1 : 0;
				int lineBase = linesStartAt1 ? 1 : 0;
				int lineStart = 0;
				if( args.getLine() != null ) {
					// a multi-line text: the line is counted in lines of the text
					int line = args.getLine()-lineBase;
					for(int i=0; i < line && lineStart >= 0; i++) {
						int nl = text.indexOf('\n', lineStart);
						lineStart = nl < 0 ? -1 : nl+1;
					}
					if( lineStart < 0 ) {
						lineStart = text.length();
					}
				}
				offset = Math.max(0, Math.min(text.length(), args.getColumn() == null ? text.length() : lineStart+args.getColumn()-columnBase));
			}
			p = new CompletionsParams().setText(text).setOffset(offset).setFrame(frameFor(args.getFrameId()));
		} catch (DapException e) {
			return failed(e.getMessage());
		}
		return ask(Loupe.COMPLETIONS, p, CompletionsResult::fromJson).thenApply(r -> {
			List<CompletionItem> items = new ArrayList<>();
			for(us.bringardner.parley.loupe.protocol.CompletionItem i : r.getItems()) {
				items.add(new CompletionItem().setLabel(i.getLabel()).setText(i.getText()).setDetail(i.getDetail())
						.setType(i.getType()).setStart(i.getStart()).setLength(i.getLength()));
			}
			return new CompletionsResponseBody().setTargets(items);
		});
	}
}
