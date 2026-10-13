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
package us.bringardner.parley.loupe.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.dap.DapConnection;

/**
 * The DAP bridge as a program an IDE can start: it talks DAP on its standard input and
 * output, and Loupe to an adapter it connects to.
 * <pre>
 * java -cp ... us.bringardner.parley.loupe.dap.DapBridgeMain --connect localhost:4711
 * java -cp ... us.bringardner.parley.loupe.dap.DapBridgeMain --exec fsh --loupe
 * </pre>
 * {@code --connect host:port} opens a socket to a running adapter. {@code --exec command
 * args...} starts the command (everything after --exec) and speaks Loupe on its standard
 * input and output; its standard error is passed to this program's.
 * <p>
 * Standard output carries the protocol, so nothing else is written to it.
 */
public final class DapBridgeMain {

	private DapBridgeMain() {
	}

	public static void main(String[] args) throws Exception {
		DapConnection c;
		try {
			c = run(args, System.in, System.out);
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.err.println("usage: DapBridgeMain --connect host:port | --exec command [args...]");
			System.exit(2);
			return;
		}
		c.closed().join();
	}

	/**
	 * Connects the DAP streams to the Loupe adapter the arguments name.
	 * @throws IllegalArgumentException for arguments that don't
	 * @return the DAP side of the bridge; it closes when either end goes
	 */
	public static DapConnection run(String[] args, InputStream dapIn, OutputStream dapOut) throws IOException {
		if( args.length >= 2 && args[0].equals("--connect") && args.length == 2 ) {
			String spec = args[1];
			int colon = spec.lastIndexOf(':');
			if( colon <= 0 || colon == spec.length()-1 ) {
				throw new IllegalArgumentException("Expected host:port, not "+spec);
			}
			int port;
			try {
				port = Integer.parseInt(spec.substring(colon+1));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Bad port in "+spec);
			}
			Socket s = new Socket(spec.substring(0, colon), port);
			return DapBridge.open(dapIn, dapOut, s.getInputStream(), s.getOutputStream());
		}
		if( args.length >= 2 && args[0].equals("--exec")) {
			List<String> command = new ArrayList<>();
			for(int i=1; i < args.length; i++) {
				command.add(args[i]);
			}
			Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
			DapConnection c = DapBridge.open(dapIn, dapOut, p.getInputStream(), p.getOutputStream());
			c.closed().thenRun(p::destroy);
			return c;
		}
		throw new IllegalArgumentException("Expected --connect host:port or --exec command");
	}
}
