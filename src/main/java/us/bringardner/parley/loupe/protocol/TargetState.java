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

/**
 * The states of a target, and which changes are allowed.
 * <pre>
 *   (none) -launch-&gt; running &lt;-&gt; paused
 *                       |          |
 *                       +-&gt; terminated &lt;-+      (final)
 * </pre>
 */
public final class TargetState {

	public static final String RUNNING = "running";
	public static final String PAUSED = "paused";
	public static final String TERMINATED = "terminated";

	private TargetState() {
	}

	/**
	 * @param from the current state, or null for a target that doesn't exist yet
	 * @return true if a target in from may go to to
	 */
	public static boolean canMove(String from, String to) {
		if( from == null ) {
			return RUNNING.equals(to);
		}
		switch (from) {
		case RUNNING:
			return PAUSED.equals(to) || TERMINATED.equals(to);
		case PAUSED:
			return RUNNING.equals(to) || TERMINATED.equals(to);
		default:
			return false;
		}
	}

	public static boolean isValid(String state) {
		return RUNNING.equals(state) || PAUSED.equals(state) || TERMINATED.equals(state);
	}
}
