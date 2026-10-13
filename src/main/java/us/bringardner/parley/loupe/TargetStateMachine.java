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

import java.util.List;
import java.util.Map;

import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.TargetStateEvent;

/**
 * The state of one target, and its generation. An adapter keeps one per target and
 * announces each change with the event {@link #move} returns.
 * <p>
 * The generation counts the times the target has paused; it is 0 until the first.
 * Safe to use from several threads.
 */
public final class TargetStateMachine {

	private String state;
	private int generation;

	/** The state, or null before the first move (the target doesn't exist yet). */
	public synchronized String getState() {
		return state;
	}

	public synchronized int getGeneration() {
		return generation;
	}

	public synchronized boolean isTerminated() {
		return TargetState.TERMINATED.equals(state);
	}

	/**
	 * Changes state.
	 * @param reason why (for the event)
	 * @return the event to send; its generation is the new one, and the caller adds a
	 *         snapshot or breakpoint ids if it has them
	 * @throws LoupeException wrongState if the change isn't allowed, with data.state the
	 *         current one
	 */
	public synchronized TargetStateEvent move(String to, String reason) {
		if( !TargetState.canMove(state, to)) {
			Map<String,Object> data = state == null ? Map.of() : Map.of("state", state);
			throw new LoupeException(LoupeException.WRONG_STATE,
					state == null ? "The target hasn't been launched" : "The target is "+state+", it can't be "+to, data);
		}
		String from = state;
		state = to;
		if( TargetState.PAUSED.equals(to)) {
			generation++;
		}
		return new TargetStateEvent().setState(to).setFrom(from).setReason(reason).setGeneration(generation);
	}

	/**
	 * Checks that the target is in one of the states a request needs.
	 * @throws LoupeException wrongState, with data.state
	 */
	public synchronized void require(String... allowed) {
		for(String s : allowed) {
			if( s.equals(state)) {
				return;
			}
		}
		Map<String,Object> data = state == null ? Map.of() : Map.of("state", state);
		throw new LoupeException(LoupeException.WRONG_STATE,
				"The request needs the target to be "+String.join(" or ", List.of(allowed))+(state == null ? "" : ", it is "+state), data);
	}
}
