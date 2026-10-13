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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.dap.DapConnection;
import us.bringardner.parley.dap.protocol.Capabilities;
import us.bringardner.parley.dap.protocol.IDebugClient;
import us.bringardner.parley.dap.protocol.InitializeRequestArguments;
import us.bringardner.parley.loupe.AdapterHandler;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.Target;
import us.bringardner.parley.loupe.TargetBackend;

public class TestDapBridgeMain {

	@Test
	public void connectsToAnAdapterOnASocket() throws Exception {
		TargetBackend backend = new TargetBackend() {
			@Override
			public CompletableFuture<Void> launch(Target target, Object args) {
				return CompletableFuture.completedFuture(null);
			}

			@Override
			public boolean supports(String method) {
				return method.equals("terminate");
			}
		};
		try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			Thread acceptor = new Thread(() -> {
				try {
					Socket s = server.accept();
					LoupeConnection.serve(ep -> new AdapterHandler(ep, backend), s.getInputStream(), s.getOutputStream());
				} catch (Exception e) {
					// the test fails on the initialize below
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();

			PipedOutputStream cOut = new PipedOutputStream();
			PipedInputStream dIn = new PipedInputStream(cOut, 1<<16);
			PipedOutputStream dOut = new PipedOutputStream();
			PipedInputStream cIn = new PipedInputStream(dOut, 1<<16);
			DapConnection bridge = DapBridgeMain.run(new String[] {"--connect", "127.0.0.1:"+server.getLocalPort()}, dIn, dOut);
			DapConnection client = DapConnection.client(new IDebugClient() {
			}, cIn, cOut);
			Capabilities caps = client.getAdapter().initialize(new InitializeRequestArguments().setAdapterID("x"))
					.get(5, TimeUnit.SECONDS);
			assertTrue(caps.getSupportsConfigurationDoneRequest());
			assertTrue(caps.getSupportsTerminateRequest());
			assertEquals(Boolean.FALSE, caps.getSupportsCompletionsRequest());
			client.close();
			bridge.close();
		}
	}

	@Test
	public void badArguments() {
		for(String[] args : List.<String[]>of(new String[] {}, new String[] {"--connect"}, new String[] {"--connect", "nohost"},
				new String[] {"--connect", "h:x"}, new String[] {"--exec"}, new String[] {"--what"})) {
			assertThrows(IllegalArgumentException.class, ()->DapBridgeMain.run(args, null, null), String.join(" ", args));
		}
	}
}
