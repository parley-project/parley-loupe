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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.BreakpointTable;
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
import us.bringardner.parley.loupe.protocol.LocalsParams;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ObjectId;
import us.bringardner.parley.loupe.protocol.Scope;
import us.bringardner.parley.loupe.protocol.ScopesParams;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.SetVariableParams;
import us.bringardner.parley.loupe.protocol.SetVariableResult;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.Source;
import us.bringardner.parley.loupe.protocol.StepKind;
import us.bringardner.parley.loupe.protocol.StepParams;
import us.bringardner.parley.loupe.protocol.ThreadInfo;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.Variable;
import us.bringardner.parley.loupe.protocol.VariablesResult;
import us.bringardner.parley.loupe.toy.ToyLang.Expr;
import us.bringardner.parley.loupe.toy.ToyLang.Interp;
import us.bringardner.parley.loupe.toy.ToyLang.ParseError;
import us.bringardner.parley.loupe.toy.ToyLang.Program;
import us.bringardner.parley.loupe.toy.ToyLang.RunError;
import us.bringardner.parley.loupe.toy.ToyLang.Stmt;

/**
 * A complete {@link TargetBackend}, for the {@link ToyLang} language: the reference for what
 * a backend does, and something to run the adapter, the bridge and an IDE against.
 * <p>
 * One backend serves one target. It runs the program on its own thread; before each statement
 * that thread asks whether to stop (a breakpoint, a step, a pause request, the entry stop) and
 * if so announces the stop and waits until it is told to go on. Everything a client asks
 * while the target is stopped is answered from the interpreter's stack, which nothing is
 * changing.
 * <p>
 * Launch arguments: {@code program} (a path) or {@code source} (the text) with
 * {@code name} for what to call it.
 */
public class ToyBackend implements TargetBackend {

	/** The id of the one filter, on by default: any runtime error stops the program. */
	public static final String FILTER_ERRORS = "errors";

	public static List<ExceptionFilter> exceptionFilters() {
		return List.of(new ExceptionFilter().setId(FILTER_ERRORS).setLabel("Runtime errors")
				.setDescription("Stop when the program fails, before it ends").setDefaultOn(true));
	}

	/** A JSON Schema for the launch arguments. */
	public static Object launchSchema() {
		Map<String,Object> props = new LinkedHashMap<>();
		props.put("program", Map.of("type", "string", "description", "Path of the program to run"));
		props.put("source", Map.of("type", "string", "description", "The program's text, instead of a path"));
		props.put("name", Map.of("type", "string", "description", "What to call the program when it's given as source"));
		Map<String,Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", props);
		schema.put("additionalProperties", false);
		return schema;
	}

	private static final class Stop extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Stop() {
			super(null, null, false, false);
		}
	}

	private final Object lock = new Object();
	// guarded by lock
	private boolean holding;
	private boolean killed;
	private boolean pauseRequested;
	private String stepKind;
	private int stepDepth;
	private Set<String> filters = Set.of(FILTER_ERRORS);
	private String lastError;
	private Interp interp;
	private Program program;
	private Source source;
	private final Map<String,Object> handles = new LinkedHashMap<>();
	private final Map<Object,String> handleOf = new IdentityHashMap<>();
	private volatile Thread thread;

	@Override
	public boolean supports(String method) {
		switch(method) {
		case Loupe.RESUME:
		case Loupe.STEP:
		case Loupe.PAUSE:
		case Loupe.TERMINATE:
		case Loupe.THREADS:
		case Loupe.FRAMES:
		case Loupe.SCOPES:
		case Loupe.LOCALS:
		case Loupe.CHILDREN:
		case Loupe.EVALUATE:
		case Loupe.COMPLETIONS:
		case Loupe.SET_VARIABLE:
		case Loupe.SET_EXCEPTION_FILTERS:
		case Loupe.EXCEPTION_INFO:
			return true;
		default:
			return false;
		}
	}

	// ---- launch

	@SuppressWarnings("unchecked")
	@Override
	public CompletableFuture<Void> launch(Target target, Object args) {
		if( interp != null ) {
			throw new LoupeException(LoupeException.FAILED, "This adapter runs one program per connection");
		}
		Map<String,Object> a = args instanceof Map ? (Map<String,Object>) args : Map.of();
		String text;
		String name = (String) a.get("name");
		try {
			if( a.get("source") != null ) {
				text = (String) a.get("source");
				if( name == null ) {
					name = "toy.toy";
				}
				source = new Source().setPath(name).setName(name);
			} else if( a.get("program") != null ) {
				String path = (String) a.get("program");
				text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
				source = new Source().setPath(path).setName(Paths.get(path).getFileName().toString());
			} else {
				throw new LoupeException(LoupeException.INVALID_PARAMS, "Give a program or a source");
			}
			program = ToyLang.parse(text);
		} catch (IOException e) {
			throw new LoupeException(LoupeException.FAILED, "Can't read the program: "+e.getMessage());
		} catch (ParseError e) {
			throw new LoupeException(LoupeException.FAILED, e.getMessage());
		}
		interp = new Interp(program, new Hook(target));
		synchronized (lock) {
			holding = target.isStartPaused();
		}
		Thread t = new Thread(() -> runProgram(target), "toy-program");
		t.setDaemon(true);
		thread = t;
		if( target.isStartPaused()) {
			target.pausedWith("entry", () -> snapshot(target), null);
		}
		t.start();
		return CompletableFuture.completedFuture(null);
	}

	private void runProgram(Target target) {
		Integer exit = 0;
		String reason = "exit";
		try {
			interp.run();
		} catch (Stop e) {
			reason = "kill";
			exit = null;
		} catch (RunError e) {
			Hook h = (Hook) interpHook();
			h.print("stderr", e.getMessage()+" (line "+e.getLine()+")\n");
			exit = 1;
		}
		synchronized (lock) {
			if( killed ) {
				return;	// the handler ends the target
			}
		}
		try {
			target.terminated(reason, exit);
		} catch (LoupeException e) {
			// ended already
		}
	}

	private ToyLang.Hook interpHook() {
		return hook;
	}

	private volatile ToyLang.Hook hook;

	// ---- the interpreter's side

	private final class Hook implements ToyLang.Hook {
		private final Target target;

		Hook(Target target) {
			this.target = target;
			ToyBackend.this.hook = this;
		}

		@Override
		public void print(String text) {
			print("stdout", text);
		}

		void print(String category, String text) {
			target.output(category, text);
		}

		@Override
		public void beforeStatement(Interp in, Stmt s) {
			List<String> hit = new ArrayList<>();
			String reason;
			synchronized (lock) {
				waitWhileHolding();
				reason = reasonToStop(in, s, hit);
				if( reason == null ) {
					return;
				}
				holding = true;
				pauseRequested = false;
				stepKind = null;
			}
			stopAndWait(reason, null, hit);
		}

		@Override
		public void onError(Interp in, RunError e) {
			synchronized (lock) {
				lastError = e.getMessage();
				if( killed || !filters.contains(FILTER_ERRORS)) {
					return;
				}
				holding = true;
				pauseRequested = false;
				stepKind = null;
			}
			stopAndWait("exception", e.getMessage()+" (line "+e.getLine()+")", List.of());
		}

		private void stopAndWait(String reason, String description, List<String> hit) {
			synchronized (lock) {
				handles.clear();
				handleOf.clear();
			}
			try {
				if( description != null ) {
					target.pausedOnException(description, () -> snapshot(target));
				} else {
					target.pausedWith(reason, () -> snapshot(target), hit.isEmpty() ? null : hit);
				}
			} catch (LoupeException e) {
				// the target was terminated under us
				synchronized (lock) {
					holding = false;
					killed = true;
					lock.notifyAll();
				}
				throw new Stop();
			}
			synchronized (lock) {
				waitWhileHolding();
			}
		}

		// called with lock held
		private void waitWhileHolding() {
			while(holding && !killed) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					killed = true;
				}
			}
			if( killed ) {
				throw new Stop();
			}
		}

		// called with lock held
		private String reasonToStop(Interp in, Stmt s, List<String> hit) {
			BreakpointTable table = target.getBreakpoints();
			for(BreakpointTable.Entry e : table.list()) {
				Location w = e.getWhere();
				if( !BreakpointStatus.VERIFIED.equals(e.getStatus()) || w == null || w.getLine() != s.getLine()) {
					continue;
				}
				String cond = e.getParams().getCondition();
				if( cond != null && !cond.isEmpty()) {
					try {
						Expr x = ToyLang.parseExpression(cond);
						if( !ToyLang.truthy(ToyLang.evaluate(in, in.getStack().get(in.getStack().size()-1), x))) {
							continue;
						}
					} catch (ParseError | RunError ex) {
						print("console", "Breakpoint condition '"+cond+"': "+ex.getMessage()+"\n");
					}
				}
				if( !table.hit(e.getId())) {
					continue;
				}
				String log = e.getParams().getLogMessage();
				if( log != null ) {
					print("console", log+"\n");
					continue;
				}
				hit.add(e.getId());
			}
			if( !hit.isEmpty()) {
				return "breakpoint";
			}
			if( pauseRequested ) {
				return "pause";
			}
			if( stepKind != null ) {
				int depth = in.getStack().size();
				switch(stepKind) {
				case StepKind.IN:
					return "step";
				case StepKind.OVER:
					return depth <= stepDepth ? "step" : null;
				case StepKind.OUT:
					return depth < stepDepth ? "step" : null;
				default:
					return null;
				}
			}
			return null;
		}
	}

	// ---- snapshot and values (the program is stopped, so its stack is stable)

	private Snapshot snapshot(Target target) {
		List<Frame> frames = frameList(target);
		return new Snapshot().setThreads(List.of(thread(target))).setThread(1).setFrames(frames)
				.setTotalFrames(frames.size()).setLocals(variables(target, interp.getStack().get(interp.getStack().size()-1).getVars()));
	}

	private ThreadInfo thread(Target target) {
		return new ThreadInfo().setId(1).setName("main").setState(target.getState());
	}

	private List<Frame> frameList(Target target) {
		List<ToyLang.Frame> stack = interp.getStack();
		List<Frame> ret = new ArrayList<>();
		for(int i=0; i < stack.size(); i++) {
			ToyLang.Frame f = stack.get(stack.size()-1-i);
			ret.add(new Frame().setId(target.objectId("f"+i)).setName(f.getName()).setSource(source).setLine(f.getLine()).setColumn(1));
		}
		return ret;
	}

	private List<Variable> variables(Target target, Map<String,Object> vars) {
		List<Variable> ret = new ArrayList<>();
		for(Map.Entry<String,Object> e : vars.entrySet()) {
			ret.add(new Variable().setName(e.getKey()).setValue(value(target, e.getValue())));
		}
		return ret;
	}

	private Value value(Target target, Object v) {
		if( v == null ) {
			return Value.ofNull();
		}
		if( v instanceof Boolean ) {
			return Value.of((Boolean) v);
		}
		if( v instanceof Long ) {
			return Value.of((long) (Long) v);
		}
		if( v instanceof String ) {
			return Value.of((String) v);
		}
		List<?> l = (List<?>) v;
		String id;
		synchronized (lock) {
			id = handleOf.get(v);
			if( id == null ) {
				id = "o"+handles.size();
				handles.put(id, v);
				handleOf.put(v, id);
			}
		}
		return new Value().setT(Value.ARRAY).setId(target.objectId(id)).setType("list").setSize(l.size())
				.setSummary(ToyLang.show(v));
	}

	private ToyLang.Frame frame(String id, boolean allowNull) {
		List<ToyLang.Frame> stack = interp.getStack();
		if( id == null ) {
			return stack.get(stack.size()-1);
		}
		int i = -1;
		try {
			String n = ObjectId.parse(id).getName();
			if( n.startsWith("f") || n.startsWith("s")) {
				i = Integer.parseInt(n.substring(1));
			}
		} catch (JsonException | NumberFormatException e) {
			// handled below
		}
		if( i < 0 || i >= stack.size()) {
			if( allowNull ) {
				return null;
			}
			throw new LoupeException(LoupeException.INVALID_PARAMS, "No frame "+id);
		}
		return stack.get(stack.size()-1-i);
	}

	private static <T> CompletableFuture<T> done(T v) {
		return CompletableFuture.completedFuture(v);
	}

	@Override
	public CompletableFuture<List<ThreadInfo>> threads(Target target) {
		return done(List.of(thread(target)));
	}

	@Override
	public CompletableFuture<FramesResult> frames(Target target, FramesParams p) {
		List<Frame> all = frameList(target);
		return done(new FramesResult().setFrames(page(all, p.getStart(), p.getCount())).setTotal(all.size()));
	}

	private static <T> List<T> page(List<T> all, Integer start, Integer count) {
		int from = Math.min(start == null ? 0 : start, all.size());
		int to = count == null ? all.size() : Math.min(all.size(), from+count);
		return all.subList(from, to);
	}

	@Override
	public CompletableFuture<ScopesResult> scopes(Target target, ScopesParams p) {
		ToyLang.Frame f = frame(p.getFrame(), false);
		List<Scope> scopes = new ArrayList<>();
		int index = interp.getStack().size()-1-interp.getStack().indexOf(f);
		scopes.add(new Scope().setName("locals").setId(target.objectId("s"+index)));
		if( f != interp.getStack().get(0)) {
			scopes.add(new Scope().setName("globals").setId(target.objectId("g")));
		}
		return done(new ScopesResult().setScopes(scopes));
	}

	@Override
	public CompletableFuture<VariablesResult> locals(Target target, LocalsParams p) {
		List<Variable> all = variables(target, frame(p.getFrame(), false).getVars());
		return done(new VariablesResult().setVariables(page(all, p.getStart(), p.getCount())).setTotal(all.size()));
	}

	@SuppressWarnings("unchecked")
	@Override
	public CompletableFuture<VariablesResult> children(Target target, ChildrenParams p) {
		Object container = container(p.getId());
		List<Variable> all;
		if( container instanceof ToyLang.Frame ) {
			all = variables(target, ((ToyLang.Frame) container).getVars());
		} else {
			all = new ArrayList<>();
			List<Object> l = (List<Object>) container;
			for(int i=0; i < l.size(); i++) {
				all.add(new Variable().setName(String.valueOf(i)).setValue(value(target, l.get(i))));
			}
		}
		return done(new VariablesResult().setVariables(page(all, p.getStart(), p.getCount())).setTotal(all.size()));
	}

	/** A frame (for a scope id) or a list (for a value's id). */
	private Object container(String id) {
		String n;
		try {
			n = ObjectId.parse(id).getName();
		} catch (JsonException e) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "Bad id "+id);
		}
		if( n.equals("g")) {
			return interp.getStack().get(0);
		}
		if( n.startsWith("s")) {
			return frame(id, false);
		}
		Object o;
		synchronized (lock) {
			o = handles.get(n);
		}
		if( o == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "No object "+id);
		}
		return o;
	}

	@Override
	public CompletableFuture<EvaluateResult> evaluate(Target target, EvaluateParams p) {
		return done(new EvaluateResult().setValue(value(target, evaluateIn(frame(p.getFrame(), false), p.getExpression()))));
	}

	private Object evaluateIn(ToyLang.Frame f, String text) {
		try {
			return ToyLang.evaluate(interp, f, ToyLang.parseExpression(text));
		} catch (ParseError | RunError e) {
			throw new LoupeException(LoupeException.FAILED, e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public CompletableFuture<SetVariableResult> setVariable(Target target, SetVariableParams p) {
		Object c = container(p.getId());
		ToyLang.Frame f = c instanceof ToyLang.Frame ? (ToyLang.Frame) c : interp.getStack().get(interp.getStack().size()-1);
		Object v = evaluateIn(f, p.getValue());
		if( c instanceof ToyLang.Frame ) {
			((ToyLang.Frame) c).getVars().put(p.getName(), v);
		} else {
			List<Object> l = (List<Object>) c;
			int i;
			try {
				i = Integer.parseInt(p.getName());
			} catch (NumberFormatException e) {
				throw new LoupeException(LoupeException.FAILED, p.getName()+" is not an index");
			}
			if( i < 0 || i >= l.size()) {
				throw new LoupeException(LoupeException.FAILED, "Index "+i+" is out of range");
			}
			l.set(i, v);
		}
		return done(new SetVariableResult().setValue(value(target, v)));
	}

	@Override
	public CompletableFuture<CompletionsResult> completions(Target target, CompletionsParams p) {
		String text = p.getText();
		int end = Math.max(0, Math.min(p.getOffset(), text.length()));
		int start = end;
		while(start > 0 && (Character.isLetterOrDigit(text.charAt(start-1)) || text.charAt(start-1) == '_')) {
			start--;
		}
		String prefix = text.substring(start, end);
		ToyLang.Frame f = frame(p.getFrame(), true);
		Map<String,String> names = new LinkedHashMap<>();
		if( f != null ) {
			for(String n : f.getVars().keySet()) {
				names.put(n, "variable");
			}
		}
		for(String n : interp.getStack().get(0).getVars().keySet()) {
			names.putIfAbsent(n, "variable");
		}
		for(String n : interp.getProgram().functionNames()) {
			names.put(n, "function");
		}
		names.put("len", "function");
		List<CompletionItem> items = new ArrayList<>();
		for(Map.Entry<String,String> e : names.entrySet()) {
			if( e.getKey().startsWith(prefix)) {
				items.add(new CompletionItem().setLabel(e.getKey()).setType(e.getValue()).setStart(start).setLength(end-start));
			}
		}
		return done(new CompletionsResult().setItems(items));
	}

	@Override
	public CompletableFuture<Void> setExceptionFilters(Target target, List<String> filters) {
		synchronized (lock) {
			this.filters = new HashSet<>(filters);
		}
		return done(null);
	}

	@Override
	public CompletableFuture<ExceptionInfoResult> exceptionInfo(Target target, ExceptionInfoParams p) {
		String msg;
		synchronized (lock) {
			msg = lastError;
		}
		if( msg == null ) {
			throw new LoupeException(LoupeException.FAILED, "The program did not stop on an error");
		}
		return done(new ExceptionInfoResult().setId("RunError").setDescription(msg).setTypeName("RunError")
				.setMessage(msg).setBreakMode("always"));
	}

	// ---- breakpoints

	@Override
	public void breakpointAdded(Target target, BreakpointTable.Entry bp) {
		Location w = bp.getWhere();
		Source s = w.getSource();
		boolean same = source != null && (s.getPath() != null && s.getPath().equals(source.getPath())
				|| s.getName() != null && s.getName().equals(source.getName()));
		if( !same ) {
			target.setBreakpointStatus(bp.getId(), BreakpointStatus.REJECTED, null, "This program has no source "+s.getPath());
			return;
		}
		int line = program.codeLineAtOrAfter(w.getLine());
		if( line < 0 ) {
			target.setBreakpointStatus(bp.getId(), BreakpointStatus.REJECTED, null, "There is no code at or after line "+w.getLine());
			return;
		}
		target.setBreakpointStatus(bp.getId(), BreakpointStatus.VERIFIED,
				new Location().setSource(w.getSource()).setLine(line).setColumn(w.getColumn()), null);
	}

	// ---- execution control

	@Override
	public CompletableFuture<Void> resume(Target target) {
		synchronized (lock) {
			stepKind = null;
			holding = false;
			lock.notifyAll();
		}
		return done(null);
	}

	@Override
	public CompletableFuture<Void> step(Target target, StepParams p) {
		if( p.getThread() != null && p.getThread() != 1 ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "No thread "+p.getThread());
		}
		synchronized (lock) {
			stepKind = p.getKind();
			stepDepth = interp.getStack().size();
			holding = false;
			lock.notifyAll();
		}
		return done(null);
	}

	@Override
	public CompletableFuture<Void> pause(Target target) {
		synchronized (lock) {
			pauseRequested = true;
		}
		return done(null);
	}

	@Override
	public CompletableFuture<Void> terminate(Target target) {
		synchronized (lock) {
			killed = true;
			lock.notifyAll();
		}
		Thread t = thread;
		if( t != null ) {
			t.interrupt();	// out of a sleep
		}
		return done(null);
	}
}
