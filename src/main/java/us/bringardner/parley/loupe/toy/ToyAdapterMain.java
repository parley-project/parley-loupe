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
package us.bringardner.parley.loupe.toy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import us.bringardner.parley.loupe.AdapterHandler;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.PeerInfo;

/**
 * A Loupe adapter for the toy language. With no arguments it speaks Loupe on its standard
 * input and output (so {@code DapBridgeMain --exec java -cp ... ToyAdapterMain} is a DAP
 * debugger for toy programs); {@code --listen port} accepts one connection at a time on the
 * loopback address instead.
 */
public final class ToyAdapterMain {

	private ToyAdapterMain() {
	}

	/** An adapter handler for a connection; every connection gets its own backend. */
	public static AdapterHandler handler(us.bringardner.parley.loupe.LoupeEndpoint endpoint) {
		return new AdapterHandler(endpoint, new ToyBackend())
				.setAdapter(new PeerInfo().setName("loupe-toy").setVersion("1"))
				.setFeatures(List.of(Loupe.FEATURE_SNAPSHOT))
				.setLaunchSchema(ToyBackend.launchSchema())
				.setExceptionFilters(ToyBackend.exceptionFilters());
	}

	public static void main(String[] args) throws IOException {
		if( args.length == 2 && args[0].equals("--listen")) {
			try (ServerSocket server = new ServerSocket(Integer.parseInt(args[1]), 1, InetAddress.getLoopbackAddress())) {
				for(;;) {
					Socket s = server.accept();
					LoupeConnection c = LoupeConnection.serve(ToyAdapterMain::handler, s.getInputStream(), s.getOutputStream());
					c.closed().join();
					s.close();
				}
			}
		}
		if( args.length != 0 ) {
			System.err.println("usage: ToyAdapterMain [--listen port]");
			System.exit(2);
		}
		LoupeConnection c = LoupeConnection.serve(ToyAdapterMain::handler, System.in, System.out);
		c.closed().join();
	}
}
