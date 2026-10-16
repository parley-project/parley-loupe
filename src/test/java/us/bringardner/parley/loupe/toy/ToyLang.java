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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny line-oriented language, there so the Loupe adapter code has something real to
 * debug. One statement to a line, blocks end with {@code end}:
 * <pre>
 * # a comment
 * def fact(n)
 *     if n &lt;= 1
 *         return 1
 *     end
 *     return n * fact(n - 1)
 * end
 * xs = [1, 2, 3]
 * xs[0] = fact(5)
 * i = 0
 * while i &lt; len(xs)
 *     print xs[i]
 *     i = i + 1
 * end
 * throw "done"
 * </pre>
 * Values are integers, strings, booleans (true, false) and lists. Operators: {@code + - * / %
 * &lt; &gt; &lt;= &gt;= == != and or not}. Built in: {@code len(x)}, {@code sleep(ms)}.
 */
public final class ToyLang {

	private ToyLang() {
	}

	/** A mistake in the program's text. */
	public static class ParseError extends Exception {
		private static final long serialVersionUID = 1L;
		private final int line;

		ParseError(String message, int line) {
			super("line "+line+": "+message);
			this.line = line;
		}

		public int getLine() {
			return line;
		}
	}

	/** A mistake while the program runs. */
	public static class RunError extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final int line;
		boolean reported;

		RunError(String message, int line) {
			super(message);
			this.line = line;
		}

		public int getLine() {
			return line;
		}
	}

	// ---- syntax tree

	/** Something to evaluate. */
	public abstract static class Expr {
		abstract Object eval(Env env);
	}

	/** One statement; {@link #line} counts from 1. */
	public abstract static class Stmt {
		final int line;

		Stmt(int line) {
			this.line = line;
		}

		public int getLine() {
			return line;
		}

		abstract void exec(Interp in);
	}

	/** A parsed program. */
	public static final class Program {
		final List<Stmt> body;
		final Map<String,Def> functions = new LinkedHashMap<>();
		final List<Integer> lines = new ArrayList<>();
		final String[] text;

		Program(List<Stmt> body, String[] text) {
			this.body = body;
			this.text = text;
		}

		/** The first line at or after this one that has a statement, or -1. */
		public int codeLineAtOrAfter(int line) {
			for(int l : lines) {
				if( l >= line ) {
					return l;
				}
			}
			return -1;
		}

		public String[] getText() {
			return text;
		}

		/** The names of the program's functions. */
		public java.util.Set<String> functionNames() {
			return functions.keySet();
		}
	}

	// ---- running

	/** One call: its name, its variables and the statement it's at. */
	public static final class Frame {
		final String name;
		final Map<String,Object> vars = new LinkedHashMap<>();
		int line;

		Frame(String name, int line) {
			this.name = name;
			this.line = line;
		}

		public String getName() {
			return name;
		}

		public int getLine() {
			return line;
		}

		public Map<String,Object> getVars() {
			return vars;
		}
	}

	/** What the debugger sees of the running program. */
	public interface Hook {
		/** Before every statement. May block; throw to end the program. */
		void beforeStatement(Interp in, Stmt s);

		/** A RunError is about to end the program; the stack is as it was. */
		void onError(Interp in, RunError e);

		void print(String text);
	}

	/** The variables an expression sees: a frame, and the globals behind it. */
	public static final class Env {
		final Interp in;
		final Frame frame;
		final boolean pure;

		Env(Interp in, Frame frame, boolean pure) {
			this.in = in;
			this.frame = frame;
			this.pure = pure;
		}

		Object get(String name, int line) {
			if( frame.vars.containsKey(name)) {
				return frame.vars.get(name);
			}
			Frame g = in.stack.get(0);
			if( g.vars.containsKey(name)) {
				return g.vars.get(name);
			}
			throw new RunError("Name '"+name+"' is not defined", line);
		}
	}

	/** The interpreter; its stack is read by the debugger while the program is stopped. */
	public static final class Interp {
		final Program program;
		final Hook hook;
		final List<Frame> stack = new ArrayList<>();
		private static final int MAX_DEPTH = 200;

		public Interp(Program program, Hook hook) {
			this.program = program;
			this.hook = hook;
			stack.add(new Frame("main", 0));
		}

		/** The frames, outermost (main) first. Only read it while the program is stopped. */
		public List<Frame> getStack() {
			return stack;
		}

		public Program getProgram() {
			return program;
		}

		Frame top() {
			return stack.get(stack.size()-1);
		}

		/** Runs the program to its end. */
		public void run() {
			block(program.body);
		}

		void block(List<Stmt> body) {
			for(Stmt s : body) {
				execute(s);
			}
		}

		void execute(Stmt s) {
			top().line = s.line;
			hook.beforeStatement(this, s);
			try {
				s.exec(this);
			} catch (RunError e) {
				if( !e.reported ) {
					e.reported = true;
					hook.onError(this, e);
				}
				throw e;
			}
		}

		Object eval(Expr e) {
			return e.eval(new Env(this, top(), false));
		}

		Object call(String name, List<Object> args, int line) {
			Def f = program.functions.get(name);
			if( f == null ) {
				throw new RunError("Function '"+name+"' is not defined", line);
			}
			if( f.params.size() != args.size()) {
				throw new RunError(name+" takes "+f.params.size()+" arguments, not "+args.size(), line);
			}
			if( stack.size() >= MAX_DEPTH ) {
				throw new RunError("The stack is too deep", line);
			}
			Frame fr = new Frame(name, f.line);
			for(int i=0; i < args.size(); i++) {
				fr.vars.put(f.params.get(i), args.get(i));
			}
			stack.add(fr);
			try {
				block(f.body);
				return null;
			} catch (Return r) {
				return r.value;
			} finally {
				stack.remove(stack.size()-1);
			}
		}
	}

	static final class Return extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final transient Object value;

		Return(Object value) {
			super(null, null, false, false);
			this.value = value;
		}
	}

	// ---- statements

	static final class Assign extends Stmt {
		final String name;
		final Expr index;
		final Expr value;

		Assign(int line, String name, Expr index, Expr value) {
			super(line);
			this.name = name;
			this.index = index;
			this.value = value;
		}

		@Override
		void exec(Interp in) {
			Object v = in.eval(value);
			Frame f = in.top();
			if( index == null ) {
				f.vars.put(name, v);
				return;
			}
			Object target = new Env(in, f, false).get(name, line);
			setElement(target, in.eval(index), v, line);
		}
	}

	static void setElement(Object target, Object index, Object v, int line) {
		if( !(target instanceof List) ) {
			throw new RunError("Only a list can be indexed", line);
		}
		@SuppressWarnings("unchecked")
		List<Object> l = (List<Object>) target;
		int i = checkIndex(l, index, line);
		l.set(i, v);
	}

	static int checkIndex(List<?> l, Object index, int line) {
		if( !(index instanceof Long) ) {
			throw new RunError("An index must be an integer", line);
		}
		long i = (Long) index;
		if( i < 0 || i >= l.size()) {
			throw new RunError("Index "+i+" is out of range (length "+l.size()+")", line);
		}
		return (int) i;
	}

	static final class Print extends Stmt {
		final Expr value;

		Print(int line, Expr value) {
			super(line);
			this.value = value;
		}

		@Override
		void exec(Interp in) {
			in.hook.print(show(in.eval(value))+"\n");
		}
	}

	static final class ExprStmt extends Stmt {
		final Expr value;

		ExprStmt(int line, Expr value) {
			super(line);
			this.value = value;
		}

		@Override
		void exec(Interp in) {
			in.eval(value);
		}
	}

	static final class ReturnStmt extends Stmt {
		final Expr value;

		ReturnStmt(int line, Expr value) {
			super(line);
			this.value = value;
		}

		@Override
		void exec(Interp in) {
			throw new Return(value == null ? null : in.eval(value));
		}
	}

	static final class Throw extends Stmt {
		final Expr value;

		Throw(int line, Expr value) {
			super(line);
			this.value = value;
		}

		@Override
		void exec(Interp in) {
			throw new RunError(show(in.eval(value)), line);
		}
	}

	static final class If extends Stmt {
		final Expr cond;
		final List<Stmt> then = new ArrayList<>();
		final List<Stmt> otherwise = new ArrayList<>();

		If(int line, Expr cond) {
			super(line);
			this.cond = cond;
		}

		@Override
		void exec(Interp in) {
			in.block(truthy(in.eval(cond)) ? then : otherwise);
		}
	}

	static final class While extends Stmt {
		final Expr cond;
		final List<Stmt> body = new ArrayList<>();

		While(int line, Expr cond) {
			super(line);
			this.cond = cond;
		}

		@Override
		void exec(Interp in) {
			while(truthy(in.eval(cond))) {
				in.block(body);
				// the condition is a stop too, as it is when stepping through a loop
				in.top().line = line;
				in.hook.beforeStatement(in, this);
			}
		}
	}

	static final class Def extends Stmt {
		final String name;
		final List<String> params;
		final List<Stmt> body = new ArrayList<>();

		Def(int line, String name, List<String> params) {
			super(line);
			this.name = name;
			this.params = params;
		}

		@Override
		void exec(Interp in) {
			// functions are known before the program starts
		}
	}

	// ---- expressions

	static final class Lit extends Expr {
		final Object value;

		Lit(Object value) {
			this.value = value;
		}

		@Override
		Object eval(Env env) {
			return value;
		}
	}

	static final class Name extends Expr {
		final String name;
		final int line;

		Name(String name, int line) {
			this.name = name;
			this.line = line;
		}

		@Override
		Object eval(Env env) {
			return env.get(name, line);
		}
	}

	static final class ListLit extends Expr {
		final List<Expr> items;

		ListLit(List<Expr> items) {
			this.items = items;
		}

		@Override
		Object eval(Env env) {
			List<Object> l = new ArrayList<>();
			for(Expr e : items) {
				l.add(e.eval(env));
			}
			return l;
		}
	}

	static final class Index extends Expr {
		final Expr target;
		final Expr index;
		final int line;

		Index(Expr target, Expr index, int line) {
			this.target = target;
			this.index = index;
			this.line = line;
		}

		@Override
		Object eval(Env env) {
			Object t = target.eval(env);
			Object i = index.eval(env);
			if( t instanceof String) {
				String s = (String) t;
				if( !(i instanceof Long) || (Long) i < 0 || (Long) i >= s.length()) {
					throw new RunError("Index "+i+" is out of range (length "+s.length()+")", line);
				}
				return String.valueOf(s.charAt((int)(long)(Long) i));
			}
			if( !(t instanceof List) ) {
				throw new RunError("Only a list or a string can be indexed", line);
			}
			List<?> l = (List<?>) t;
			return l.get(checkIndex(l, i, line));
		}
	}

	static final class Unary extends Expr {
		final String op;
		final Expr operand;
		final int line;

		Unary(String op, Expr operand, int line) {
			this.op = op;
			this.operand = operand;
			this.line = line;
		}

		@Override
		Object eval(Env env) {
			Object v = operand.eval(env);
			if( op.equals("not")) {
				return !truthy(v);
			}
			if( !(v instanceof Long) ) {
				throw new RunError("Can't negate "+show(v), line);
			}
			return -(Long) v;
		}
	}

	static final class Binary extends Expr {
		final String op;
		final Expr left;
		final Expr right;
		final int line;

		Binary(String op, Expr left, Expr right, int line) {
			this.op = op;
			this.left = left;
			this.right = right;
			this.line = line;
		}

		@Override
		Object eval(Env env) {
			Object l = left.eval(env);
			if( op.equals("and")) {
				return truthy(l) && truthy(right.eval(env));
			}
			if( op.equals("or")) {
				return truthy(l) || truthy(right.eval(env));
			}
			Object r = right.eval(env);
			switch(op) {
			case "==":
				return l == null ? r == null : l.equals(r);
			case "!=":
				return !(l == null ? r == null : l.equals(r));
			case "+":
				if( l instanceof String || r instanceof String ) {
					return show(l)+show(r);
				}
				if( l instanceof List && r instanceof List ) {
					List<Object> all = new ArrayList<>((List<?>) l);
					all.addAll((List<?>) r);
					return all;
				}
				break;
			default:
				break;
			}
			if( !(l instanceof Long) || !(r instanceof Long) ) {
				throw new RunError("Can't use "+op+" on "+show(l)+" and "+show(r), line);
			}
			long a = (Long) l;
			long b = (Long) r;
			switch(op) {
			case "+":
				return a+b;
			case "-":
				return a-b;
			case "*":
				return a*b;
			case "/":
				if( b == 0 ) {
					throw new RunError("Division by zero", line);
				}
				return a/b;
			case "%":
				if( b == 0 ) {
					throw new RunError("Division by zero", line);
				}
				return a%b;
			case "<":
				return a < b;
			case ">":
				return a > b;
			case "<=":
				return a <= b;
			case ">=":
				return a >= b;
			default:
				throw new RunError("Unknown operator "+op, line);
			}
		}
	}

	static final class Call extends Expr {
		final String name;
		final List<Expr> args;
		final int line;

		Call(String name, List<Expr> args, int line) {
			this.name = name;
			this.args = args;
			this.line = line;
		}

		@Override
		Object eval(Env env) {
			List<Object> values = new ArrayList<>();
			for(Expr e : args) {
				values.add(e.eval(env));
			}
			if( name.equals("len")) {
				if( values.size() == 1 && values.get(0) instanceof List ) {
					return (long) ((List<?>) values.get(0)).size();
				}
				if( values.size() == 1 && values.get(0) instanceof String ) {
					return (long) ((String) values.get(0)).length();
				}
				throw new RunError("len takes a list or a string", line);
			}
			if( env.pure ) {
				throw new RunError("A function can't be called while the program is stopped", line);
			}
			if( name.equals("sleep")) {
				if( values.size() != 1 || !(values.get(0) instanceof Long) ) {
					throw new RunError("sleep takes a number of milliseconds", line);
				}
				try {
					Thread.sleep((Long) values.get(0));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RunError("Interrupted", line);
				}
				return null;
			}
			return env.in.call(name, values, line);
		}
	}

	// ---- values

	/** The program's idea of true: anything but false, 0, the empty string and list, and null. */
	public static boolean truthy(Object v) {
		if( v == null ) {
			return false;
		}
		if( v instanceof Boolean ) {
			return (Boolean) v;
		}
		if( v instanceof Long ) {
			return (Long) v != 0;
		}
		if( v instanceof String ) {
			return !((String) v).isEmpty();
		}
		if( v instanceof List ) {
			return !((List<?>) v).isEmpty();
		}
		return true;
	}

	/** Text for a value, as print writes it. */
	public static String show(Object v) {
		if( v == null ) {
			return "null";
		}
		if( v instanceof List ) {
			StringBuilder b = new StringBuilder("[");
			boolean first = true;
			for(Object o : (List<?>) v) {
				if( !first ) {
					b.append(", ");
				}
				first = false;
				b.append(o instanceof String ? "\""+o+"\"" : show(o));
			}
			return b.append(']').toString();
		}
		return String.valueOf(v);
	}

	// ---- parsing

	/** Reads a program. */
	public static Program parse(String source) throws ParseError {
		String[] text = source.split("\r?\n", -1);
		List<Stmt> top = new ArrayList<>();
		Program p = new Program(top, text);
		// each open block: the statement and the list its statements go to
		List<Object[]> open = new ArrayList<>();
		List<Stmt> current = top;
		for(int i=0; i < text.length; i++) {
			int line = i+1;
			String t = text[i].trim();
			if( t.isEmpty() || t.startsWith("#")) {
				continue;
			}
			Parser ps = new Parser(t, line);
			String word = ps.peekWord();
			Stmt s;
			if( word.equals("end")) {
				ps.word();
				ps.finish();
				if( open.isEmpty()) {
					throw new ParseError("end without a block", line);
				}
				current = (List<Stmt>) open.remove(open.size()-1)[1];
				continue;
			} else if( word.equals("else")) {
				ps.word();
				ps.finish();
				if( open.isEmpty() || !(open.get(open.size()-1)[0] instanceof If)) {
					throw new ParseError("else without if", line);
				}
				current = ((If) open.get(open.size()-1)[0]).otherwise;
				continue;
			} else if( word.equals("def")) {
				ps.word();
				String name = ps.ident();
				List<String> params = new ArrayList<>();
				ps.expect("(");
				if( !ps.accept(")")) {
					do {
						params.add(ps.ident());
					} while(ps.accept(","));
					ps.expect(")");
				}
				ps.finish();
				if( !open.isEmpty() || p.functions.containsKey(name)) {
					throw new ParseError(open.isEmpty() ? "Function "+name+" is already defined" : "Functions can't be nested", line);
				}
				Def d = new Def(line, name, params);
				p.functions.put(name, d);
				s = d;
				add(current, s, p);
				open.add(new Object[] {d, current});
				current = d.body;
				continue;
			} else if( word.equals("if")) {
				ps.word();
				If f = new If(line, ps.expression());
				ps.finish();
				s = f;
				add(current, s, p);
				open.add(new Object[] {f, current});
				current = f.then;
				continue;
			} else if( word.equals("while")) {
				ps.word();
				While w = new While(line, ps.expression());
				ps.finish();
				s = w;
				add(current, s, p);
				open.add(new Object[] {w, current});
				current = w.body;
				continue;
			} else if( word.equals("print")) {
				ps.word();
				s = new Print(line, ps.expression());
			} else if( word.equals("return")) {
				ps.word();
				s = new ReturnStmt(line, ps.atEnd() ? null : ps.expression());
			} else if( word.equals("throw")) {
				ps.word();
				s = new Throw(line, ps.expression());
			} else {
				s = ps.assignmentOrCall();
			}
			ps.finish();
			add(current, s, p);
		}
		if( !open.isEmpty()) {
			Object o = open.get(open.size()-1)[0];
			throw new ParseError("A block is never closed", ((Stmt) o).line);
		}
		return p;
	}

	private static void add(List<Stmt> to, Stmt s, Program p) {
		to.add(s);
		p.lines.add(s.line);
	}

	/** Parses an expression typed while debugging. */
	public static Expr parseExpression(String text) throws ParseError {
		Parser ps = new Parser(text.trim(), 1);
		Expr e = ps.expression();
		ps.finish();
		return e;
	}

	/** Evaluates a parsed expression in a frame; it can't call the program's functions. */
	public static Object evaluate(Interp in, Frame frame, Expr e) {
		return e.eval(new Env(in, frame, true));
	}

	static final class Parser {
		private final String s;
		private final int line;
		private int pos;

		Parser(String s, int line) {
			this.s = s;
			this.line = line;
		}

		private void skip() {
			while(pos < s.length() && s.charAt(pos) == ' ' || pos < s.length() && s.charAt(pos) == '\t') {
				pos++;
			}
		}

		boolean atEnd() {
			skip();
			return pos >= s.length();
		}

		void finish() throws ParseError {
			if( !atEnd()) {
				throw new ParseError("Unexpected "+s.substring(pos), line);
			}
		}

		String peekWord() {
			int save = pos;
			String w = readWord();
			pos = save;
			return w;
		}

		private String readWord() {
			skip();
			int start = pos;
			while(pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
				pos++;
			}
			return s.substring(start, pos);
		}

		String word() {
			return readWord();
		}

		String ident() throws ParseError {
			String w = readWord();
			if( w.isEmpty() || Character.isDigit(w.charAt(0))) {
				throw new ParseError("A name was expected", line);
			}
			return w;
		}

		boolean accept(String sym) {
			skip();
			if( s.startsWith(sym, pos)) {
				pos += sym.length();
				return true;
			}
			return false;
		}

		void expect(String sym) throws ParseError {
			if( !accept(sym)) {
				throw new ParseError("Expected "+sym, line);
			}
		}

		Stmt assignmentOrCall() throws ParseError {
			int save = pos;
			String name = ident();
			Expr index = null;
			if( accept("[")) {
				index = expression();
				expect("]");
			}
			skip();
			if( pos < s.length() && s.charAt(pos) == '=' && !s.startsWith("==", pos)) {
				pos++;
				return new Assign(line, name, index, expression());
			}
			pos = save;
			return new ExprStmt(line, expression());
		}

		Expr expression() throws ParseError {
			return or();
		}

		private boolean keyword(String k) {
			skip();
			if( s.startsWith(k, pos)) {
				int end = pos+k.length();
				if( end >= s.length() || !Character.isLetterOrDigit(s.charAt(end))) {
					pos = end;
					return true;
				}
			}
			return false;
		}

		private Expr or() throws ParseError {
			Expr l = and();
			while(keyword("or")) {
				l = new Binary("or", l, and(), line);
			}
			return l;
		}

		private Expr and() throws ParseError {
			Expr l = not();
			while(keyword("and")) {
				l = new Binary("and", l, not(), line);
			}
			return l;
		}

		private Expr not() throws ParseError {
			if( keyword("not")) {
				return new Unary("not", not(), line);
			}
			return comparison();
		}

		private Expr comparison() throws ParseError {
			Expr l = sum();
			for(;;) {
				String op = null;
				for(String o : new String[] {"==", "!=", "<=", ">=", "<", ">"}) {
					if( accept(o)) {
						op = o;
						break;
					}
				}
				if( op == null ) {
					return l;
				}
				l = new Binary(op, l, sum(), line);
			}
		}

		private Expr sum() throws ParseError {
			Expr l = product();
			for(;;) {
				if( accept("+")) {
					l = new Binary("+", l, product(), line);
				} else if( accept("-")) {
					l = new Binary("-", l, product(), line);
				} else {
					return l;
				}
			}
		}

		private Expr product() throws ParseError {
			Expr l = unary();
			for(;;) {
				if( accept("*")) {
					l = new Binary("*", l, unary(), line);
				} else if( accept("/")) {
					l = new Binary("/", l, unary(), line);
				} else if( accept("%")) {
					l = new Binary("%", l, unary(), line);
				} else {
					return l;
				}
			}
		}

		private Expr unary() throws ParseError {
			if( accept("-")) {
				return new Unary("-", unary(), line);
			}
			return postfix();
		}

		private Expr postfix() throws ParseError {
			Expr e = primary();
			while(accept("[")) {
				Expr i = expression();
				expect("]");
				e = new Index(e, i, line);
			}
			return e;
		}

		private Expr primary() throws ParseError {
			skip();
			if( pos >= s.length()) {
				throw new ParseError("An expression was expected", line);
			}
			char c = s.charAt(pos);
			if( Character.isDigit(c)) {
				int start = pos;
				while(pos < s.length() && Character.isDigit(s.charAt(pos))) {
					pos++;
				}
				try {
					return new Lit(Long.parseLong(s.substring(start, pos)));
				} catch (NumberFormatException e) {
					throw new ParseError("Number too large", line);
				}
			}
			if( c == '"') {
				StringBuilder b = new StringBuilder();
				pos++;
				while(pos < s.length() && s.charAt(pos) != '"') {
					char ch = s.charAt(pos++);
					if( ch == '\\' && pos < s.length()) {
						char n = s.charAt(pos++);
						b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
					} else {
						b.append(ch);
					}
				}
				if( pos >= s.length()) {
					throw new ParseError("A string is not closed", line);
				}
				pos++;
				return new Lit(b.toString());
			}
			if( c == '(') {
				pos++;
				Expr e = expression();
				expect(")");
				return e;
			}
			if( c == '[') {
				pos++;
				List<Expr> items = new ArrayList<>();
				if( !accept("]")) {
					do {
						items.add(expression());
					} while(accept(","));
					expect("]");
				}
				return new ListLit(items);
			}
			String w = ident();
			if( w.equals("true") || w.equals("false")) {
				return new Lit(Boolean.valueOf(w));
			}
			if( w.equals("null")) {
				return new Lit(null);
			}
			if( accept("(")) {
				List<Expr> args = new ArrayList<>();
				if( !accept(")")) {
					do {
						args.add(expression());
					} while(accept(","));
					expect(")");
				}
				return new Call(w, args, line);
			}
			return new Name(w, line);
		}
	}
}
