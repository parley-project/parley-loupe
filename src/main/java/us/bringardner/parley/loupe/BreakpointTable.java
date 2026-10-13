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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointResult;
import us.bringardner.parley.loupe.protocol.BreakpointStatus;
import us.bringardner.parley.loupe.protocol.BreakpointStatusEvent;
import us.bringardner.parley.loupe.protocol.Location;

/**
 * The breakpoints of one target, by the ids the client chose. An adapter keeps one per
 * target: addBreakpoint and removeBreakpoint go through it, the adapter reports what it found
 * with {@link #setStatus}, and {@link #hit} decides whether a breakpoint that was reached
 * should stop.
 * <p>
 * Safe to use from several threads.
 */
public final class BreakpointTable {

	/** A breakpoint and how it stands. */
	public static final class Entry {
		private final AddBreakpointParams params;
		private String status = BreakpointStatus.PENDING;
		private Location where;
		private String message;
		private int hits;

		Entry(AddBreakpointParams params) {
			this.params = params;
			this.where = params.getWhere();
		}

		public String getId() {
			return params.getId();
		}

		/** What the client asked for. */
		public AddBreakpointParams getParams() {
			return params;
		}

		public synchronized String getStatus() {
			return status;
		}

		/** Where it is: where it was asked for, until the adapter says otherwise. */
		public synchronized Location getWhere() {
			return where;
		}

		public synchronized String getMessage() {
			return message;
		}

		/** The times it was reached since it was added. */
		public synchronized int getHits() {
			return hits;
		}
	}

	private final Map<String,Entry> entries = new LinkedHashMap<>();

	/**
	 * Adds a breakpoint, replacing one with the same id (which starts again at pending).
	 * @return the result for the addBreakpoint response
	 * @throws LoupeException invalidParams if the id is empty, the line is below 1, or the hit
	 *         count is below 1
	 */
	public BreakpointResult add(AddBreakpointParams params) {
		validate(params);
		Entry e = new Entry(params);
		synchronized (this) {
			entries.put(params.getId(), e);
		}
		return new BreakpointResult().setStatus(BreakpointStatus.PENDING);
	}

	private static void validate(AddBreakpointParams p) {
		if( p.getId() == null || p.getId().isEmpty()) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "A breakpoint needs an id");
		}
		Location w = p.getWhere();
		if( w == null || w.getSource() == null || w.getLine() == null ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "A breakpoint needs a source and a line");
		}
		if( w.getLine() < 1 || w.getColumn() != null && w.getColumn() < 1 ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "Lines and columns count from 1");
		}
		if( p.getHitCount() != null && p.getHitCount() < 1 ) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "hitCount must be at least 1");
		}
	}

	/**
	 * Removes a breakpoint.
	 * @return the status event to send (status removed)
	 * @throws LoupeException invalidParams for an id that isn't there
	 */
	public BreakpointStatusEvent remove(String id) {
		Entry e;
		synchronized (this) {
			e = entries.remove(id);
		}
		if( e == null ) {
			throw unknown(id);
		}
		return new BreakpointStatusEvent().setId(id).setStatus(BreakpointStatus.REMOVED).setWhere(e.getWhere());
	}

	/**
	 * The adapter found where a breakpoint is (verified), or can't set it (rejected), or is
	 * still looking (pending).
	 * @param where where it really is, or null for where it was asked for
	 * @param message why, for rejected
	 * @return the status event to send
	 * @throws LoupeException invalidParams for an id that isn't there, or a status that isn't
	 *         pending, verified or rejected
	 */
	public BreakpointStatusEvent setStatus(String id, String status, Location where, String message) {
		if( !BreakpointStatus.PENDING.equals(status) && !BreakpointStatus.VERIFIED.equals(status)
				&& !BreakpointStatus.REJECTED.equals(status)) {
			throw new LoupeException(LoupeException.INVALID_PARAMS, "A breakpoint can't be set to "+status);
		}
		Entry e = get(id);
		if( e == null ) {
			throw unknown(id);
		}
		synchronized (e) {
			e.status = status;
			if( where != null ) {
				e.where = where;
			}
			e.message = message;
			return new BreakpointStatusEvent().setId(id).setStatus(status).setWhere(e.where).setMessage(message);
		}
	}

	/**
	 * A breakpoint was reached: counts the hit.
	 * @return true if the target should stop: the breakpoint exists, isn't rejected, and has
	 *         been reached hitCount times (or has no hitCount). The condition is the adapter's
	 *         to evaluate, before calling this.
	 */
	public boolean hit(String id) {
		Entry e = get(id);
		if( e == null ) {
			return false;
		}
		synchronized (e) {
			if( BreakpointStatus.REJECTED.equals(e.status)) {
				return false;
			}
			e.hits++;
			Integer n = e.params.getHitCount();
			return n == null || e.hits >= n;
		}
	}

	public synchronized Entry get(String id) {
		return entries.get(id);
	}

	public synchronized List<Entry> list() {
		return new ArrayList<>(entries.values());
	}

	public synchronized int size() {
		return entries.size();
	}

	private static LoupeException unknown(String id) {
		return new LoupeException(LoupeException.INVALID_PARAMS, "No breakpoint with id "+id);
	}
}
