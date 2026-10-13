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
package us.bringardner.parley.loupe.protocol;

/** Why an expression is evaluated; an adapter may treat the contexts differently (a hover must have no side effects). */
public final class EvaluateContext {

	/** A watch expression: evaluated at every stop. */
	public static final String WATCH = "watch";
	/** Typed in a console. It may change the program's state. */
	public static final String REPL = "repl";
	/** The user hovers over text. It must not change the program's state. */
	public static final String HOVER = "hover";
	/** Copied to the clipboard: wanted as text that can be pasted back. */
	public static final String CLIPBOARD = "clipboard";

	private EvaluateContext() {
	}
}
