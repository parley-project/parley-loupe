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

import java.io.IOException;
import java.util.List;

import us.bringardner.parley.loupe.protocol.BreakpointStatusEvent;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.ObjectId;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.TargetStateEvent;

/**
 * One debuggee, as the adapter keeps it: its id (chosen by the client), state, generation
 * and breakpoints. Each change of state or breakpoint status goes out as its event, in the
 * order it happened.
 */
public final class Target {

	private final String id;
	private final TargetRegistry registry;
	private final TargetStateMachine machine = new TargetStateMachine();
	private final BreakpointTable breakpoints = new BreakpointTable();
	private volatile Object userData;

	Target(String id, TargetRegistry registry) {
		this.id = id;
		this.registry = registry;
	}

	public String getId() {
		return id;
	}

	/** The state, or null before the target was launched. */
	public String getState() {
		return machine.getState();
	}

	public int getGeneration() {
		return machine.getGeneration();
	}

	public boolean isTerminated() {
		return machine.isTerminated();
	}

	public BreakpointTable getBreakpoints() {
		return breakpoints;
	}

	/** For the backend's use: whatever it keeps for a target (the process, a connection ...). */
	public Object getUserData() {
		return userData;
	}

	public void setUserData(Object userData) {
		this.userData = userData;
	}

	/**
	 * The id of an object of this target in the current generation: name@generation.
	 * Values the backend sends carry ids made with this.
	 */
	public String objectId(String name) {
		return new ObjectId(name, machine.getGeneration()).toString();
	}

	/** Checks the target is in one of these states (see TargetStateMachine.require). */
	public void require(String... states) {
		machine.require(states);
	}

	/**
	 * The target runs (it was resumed, or stepped).
	 * @throws LoupeException wrongState if it isn't paused
	 */
	public TargetStateEvent running(String reason) {
		synchronized (this) {
			TargetStateEvent e = machine.move(TargetState.RUNNING, reason);
			send(e);
			return e;
		}
	}

	/**
	 * The target stopped. The snapshot is sent only if the session negotiated the snapshot
	 * feature.
	 * @param reason "breakpoint", "step", "pause", "exception", "entry" ...
	 * @param snapshot what to show, or null
	 * @param breakpointIds the breakpoints that were hit, or null
	 * @throws LoupeException wrongState if it isn't running
	 */
	public TargetStateEvent paused(String reason, Snapshot snapshot, List<String> breakpointIds) {
		synchronized (this) {
			TargetStateEvent e = machine.move(TargetState.PAUSED, reason);
			e.setBreakpoints(breakpointIds);
			if( registry.hasFeature(Loupe.FEATURE_SNAPSHOT)) {
				e.setSnapshot(snapshot);
			}
			send(e);
			return e;
		}
	}

	/**
	 * The target ended; this is final.
	 * @param exitCode or null
	 * @throws LoupeException wrongState if it has already
	 */
	public TargetStateEvent terminated(String reason, Integer exitCode) {
		synchronized (this) {
			TargetStateEvent e = machine.move(TargetState.TERMINATED, reason);
			e.setExitCode(exitCode);
			send(e);
			return e;
		}
	}

	/**
	 * Records what was found for a breakpoint and tells the client.
	 * @see BreakpointTable#setStatus
	 */
	public BreakpointStatusEvent setBreakpointStatus(String id, String status, Location where, String message) {
		synchronized (this) {
			BreakpointStatusEvent e = breakpoints.setStatus(id, status, where, message);
			send(e);
			return e;
		}
	}

	void send(BreakpointStatusEvent e) {
		registry.send(Loupe.BREAKPOINT_STATUS, id, e.toJson());
	}

	private void send(TargetStateEvent e) {
		registry.send(Loupe.TARGET_STATE, id, e.toJson());
	}

	@Override
	public String toString() {
		return "Target "+id+" ("+getState()+")";
	}

	// an event that can't be sent means the connection is closed, which ends the targets anyway
	static void ignore(IOException e) {
	}
}
