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

/** The statuses of a breakpoint. */
public final class BreakpointStatus {

	/** Added, and the adapter hasn't yet found where it goes (a script not loaded yet ...). */
	public static final String PENDING = "pending";
	/** Bound to a place it can stop at. */
	public static final String VERIFIED = "verified";
	/** Can't be set; the message says why. It stays in the table until it's removed. */
	public static final String REJECTED = "rejected";
	/** Gone; the last status event it gets. */
	public static final String REMOVED = "removed";

	private BreakpointStatus() {
	}
}
