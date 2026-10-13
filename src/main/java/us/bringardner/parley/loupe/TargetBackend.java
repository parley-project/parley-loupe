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

import java.util.concurrent.CompletableFuture;

/**
 * What an adapter does for the targets: start (or attach to) a debuggee, and install and
 * remove its breakpoints. {@link AdapterHandler} calls it after checking the request.
 * <p>
 * The backend changes a target's state through the {@link Target} it's given
 * ({@link Target#paused}, {@link Target#running}, {@link Target#terminated}), which also
 * sends the events.
 */
public interface TargetBackend {

	/**
	 * Starts a debuggee. The target is already running when this is called (so it may pause
	 * at once, to stop on entry); if the future fails, the target is terminated and the
	 * launch request fails with the same error.
	 * @param args the launch arguments, already checked against the launchSchema (an empty
	 *        object if the client sent none)
	 */
	CompletableFuture<Void> launch(Target target, Object args);

	/** Attaches to a running debuggee; like launch. Not supported unless overridden. */
	default CompletableFuture<Void> attach(Target target, Object args) {
		CompletableFuture<Void> f = new CompletableFuture<>();
		f.completeExceptionally(new LoupeException(LoupeException.NOT_SUPPORTED, "This adapter can't attach"));
		return f;
	}

	/**
	 * A breakpoint was added (or replaced). It is pending: report what is found with
	 * {@link Target#setBreakpointStatus}, now or later.
	 */
	default void breakpointAdded(Target target, BreakpointTable.Entry breakpoint) {
	}

	/** A breakpoint was removed. */
	default void breakpointRemoved(Target target, String id) {
	}
}
