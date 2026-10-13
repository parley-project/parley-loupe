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

import us.bringardner.parley.dap.json.JsonException;

/**
 * An object id: {@code name@generation}, such as {@code o12@5}. The name is the same for the
 * life of the object; the generation is the target's generation the id was given in. An id
 * with no {@code @} names the object without saying when.
 */
public final class ObjectId {

	private final String name;
	private final int generation;

	/** @param generation or -1 for none */
	public ObjectId(String name, int generation) {
		if( name == null || name.isEmpty() || name.indexOf('@') >= 0 ) {
			throw new IllegalArgumentException("Bad object name: "+name);
		}
		this.name = name;
		this.generation = generation;
	}

	public static ObjectId parse(String id) throws JsonException {
		if( id == null || id.isEmpty()) {
			throw new JsonException("An object id can't be empty");
		}
		int at = id.indexOf('@');
		if( at < 0 ) {
			return new ObjectId(id, -1);
		}
		if( at == 0 || at == id.length()-1 ) {
			throw new JsonException("Bad object id: "+id);
		}
		int gen = 0;
		for(int i=at+1; i < id.length(); i++) {
			char c = id.charAt(i);
			if( c < '0' || c > '9' || gen > (Integer.MAX_VALUE-9)/10 ) {
				throw new JsonException("Bad object id: "+id);
			}
			gen = gen*10+(c-'0');
		}
		return new ObjectId(id.substring(0, at), gen);
	}

	public String getName() {
		return name;
	}

	/** The generation, or -1 if the id has none. */
	public int getGeneration() {
		return generation;
	}

	public boolean hasGeneration() {
		return generation >= 0;
	}

	/** This object in another generation. */
	public ObjectId at(int generation) {
		return new ObjectId(name, generation);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ObjectId && ((ObjectId) o).name.equals(name) && ((ObjectId) o).generation == generation;
	}

	@Override
	public int hashCode() {
		return name.hashCode()*31+generation;
	}

	@Override
	public String toString() {
		return generation < 0 ? name : name+"@"+generation;
	}
}
