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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.loupe.toy.ToyLang.ParseError;
import us.bringardner.parley.loupe.toy.ToyLang.RunError;

public class TestToyLang {

	private static List<String> run(String src) throws Exception {
		List<String> out = new ArrayList<>();
		new ToyLang.Interp(ToyLang.parse(src), new ToyLang.Hook() {
			@Override
			public void beforeStatement(ToyLang.Interp in, ToyLang.Stmt s) {
			}

			@Override
			public void onError(ToyLang.Interp in, RunError e) {
			}

			@Override
			public void print(String text) {
				out.add(text.trim());
			}
		}).run();
		return out;
	}

	@Test
	public void printsWhatItComputes() throws Exception {
		assertEquals(List.of("24", "[10, 2, 3]", "ab3", "true"), run(String.join("\n",
				"def fact(n)", "  if n <= 1", "    return 1", "  end", "  return n * fact(n - 1)", "end",
				"xs = [1, 2, 3]", "print fact(4)", "xs[0] = fact(4) - 14", "print xs",
				"print \"a\" + \"b\" + len(xs)", "print not (1 > 2) and 3 == 3")));
	}

	@Test
	public void loopsAndElse() throws Exception {
		assertEquals(List.of("0", "1", "2", "done"), run(String.join("\n",
				"i = 0", "while i < 3", "  print i", "  i = i + 1", "end",
				"if i == 4", "  print \"no\"", "else", "  print \"done\"", "end")));
	}

	@Test
	public void reportsMistakes() {
		for(String bad : new String[] {"x = ", "end", "if 1", "def f(", "x = 1 2", "else", "def f()\ndef g()\nend\nend", "print \"a"}) {
			assertThrows(ParseError.class, ()->ToyLang.parse(bad), bad);
		}
		RunError e = assertThrows(RunError.class, ()->run("x = [1]\nprint x[3]"));
		assertEquals(2, e.getLine());
		assertTrue(e.getMessage().contains("out of range"), e.getMessage());
		assertThrows(RunError.class, ()->run("print y"));
		assertThrows(RunError.class, ()->run("print 1 / 0"));
		assertThrows(RunError.class, ()->run("def f()\n f()\nend\nf()"));
	}

	@Test
	public void findsCodeLines() throws Exception {
		ToyLang.Program p = ToyLang.parse("# c\n\nx = 1\n\ny = 2\n");
		assertEquals(3, p.codeLineAtOrAfter(1));
		assertEquals(5, p.codeLineAtOrAfter(4));
		assertEquals(-1, p.codeLineAtOrAfter(6));
	}
}
