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

/** The names Loupe fixes: protocol versions, features, methods and events. */
public final class Loupe {

	/** The protocol version this library speaks. */
	public static final String PROTOCOL = "loupe/1";

	/** Features (Hello.features, Welcome.features). */
	public static final String FEATURE_SNAPSHOT = "snapshot";
	public static final String FEATURE_OBJECT_CHANGES = "objectChanges";

	/** Methods every adapter has. */
	public static final String HELLO = "hello";
	public static final String LAUNCH = "launch";
	public static final String ATTACH = "attach";
	public static final String CANCEL = "cancel";

	/** Events. */
	public static final String TARGET_STATE = "targetState";
	public static final String BREAKPOINT_STATUS = "breakpointStatus";

	private Loupe() {
	}
}
