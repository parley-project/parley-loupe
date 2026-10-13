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

/** The kinds of step. */
public final class StepKind {

	/** To the next line of this function, running what it calls. */
	public static final String OVER = "over";
	/** Into a function the line calls. */
	public static final String IN = "in";
	/** Until this function returns. */
	public static final String OUT = "out";

	private StepKind() {
	}

	public static boolean isValid(String kind) {
		return OVER.equals(kind) || IN.equals(kind) || OUT.equals(kind);
	}
}
