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

import us.bringardner.parley.loupe.protocol.StepParams;

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

	/**
	 * Which of the methods resume, step, pause and terminate (names in
	 * {@link us.bringardner.parley.loupe.protocol.Loupe}) the backend implements. The others
	 * are refused with notSupported before the target's state is touched.
	 */
	default boolean supports(String method) {
		return false;
	}

	/**
	 * Lets the paused target run. The target is already running when this is called; when the
	 * debuggee stops again the backend calls {@link Target#paused}, which throws wrongState
	 * if the target isn't running (a stop that raced with something else). If the future
	 * fails, the target goes back to paused (reason resumeFailed) and the request fails.
	 */
	default CompletableFuture<Void> resume(Target target) {
		return notSupported();
	}

	/**
	 * Runs the paused target for one step of params.getKind(); like resume (reason of the
	 * failure stepFailed). The backend calls {@link Target#paused} with reason "step" when
	 * the step is done. A thread it doesn't know is an invalidParams failure.
	 */
	default CompletableFuture<Void> step(Target target, StepParams params) {
		return notSupported();
	}

	/**
	 * Asks the running target to stop. The future completes when the request is accepted; the
	 * stop itself is the backend's call to {@link Target#paused} (reason "pause").
	 */
	default CompletableFuture<Void> pause(Target target) {
		return notSupported();
	}

	/**
	 * Ends the debuggee. The future completes when it is ending; if the target hasn't been
	 * terminated by then (through {@link Target#terminated}, which may give an exit code), the
	 * handler terminates it with reason "kill".
	 */
	default CompletableFuture<Void> terminate(Target target) {
		return notSupported();
	}

	private static CompletableFuture<Void> notSupported() {
		CompletableFuture<Void> f = new CompletableFuture<>();
		f.completeExceptionally(new LoupeException(LoupeException.NOT_SUPPORTED, "Not supported by this adapter"));
		return f;
	}
}
