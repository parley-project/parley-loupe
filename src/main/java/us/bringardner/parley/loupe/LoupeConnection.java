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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import us.bringardner.parley.dap.DapFraming;

/**
 * A Loupe conversation over a pair of streams: a socket's, or a process's stdin and stdout.
 * Both ends are made the same way; see {@link LoupeEndpoint} for the messages.
 * <pre>
 * LoupeConnection c = LoupeConnection.open(handler, in, out);
 * Object welcome = c.getEndpoint().request("hello", null, params).get();
 * </pre>
 * Messages are read on a thread of the connection's own. They are framed as DAP's are
 * (Content-Length, a blank line, UTF-8 JSON).
 */
public final class LoupeConnection implements Closeable {

	private static final AtomicInteger count = new AtomicInteger();

	private final InputStream in;
	private final OutputStream out;
	private final LoupeEndpoint endpoint;
	private final CompletableFuture<Void> closed = new CompletableFuture<>();
	private volatile Consumer<Throwable> errorListener = e -> {};
	private Thread reader;

	private LoupeConnection(LoupeEndpoint.Handler handler, InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
		this.endpoint = new LoupeEndpoint(json -> {
			synchronized (out) {
				DapFraming.write(out, json);
			}
		}, handler);
		endpoint.setErrorListener(e -> errorListener.accept(e));
	}

	/** Starts reading at once. The handler may be null, to be set on the endpoint before the other end sends. */
	public static LoupeConnection open(LoupeEndpoint.Handler handler, InputStream in, OutputStream out) {
		LoupeConnection c = new LoupeConnection(handler, in, out);
		c.reader = new Thread(c::read, "Loupe reader "+count.incrementAndGet());
		c.reader.setDaemon(true);
		c.reader.start();
		return c;
	}

	private void read() {
		Throwable why = null;
		try {
			while( true ) {
				String json = DapFraming.read(in);
				if( json == null ) {
					break;
				}
				endpoint.receive(json);
			}
		} catch (IOException | RuntimeException e) {
			why = e;
		}
		endpoint.closed(why);
		closeStreams();
		closed.complete(null);
	}

	/** Told about messages that couldn't be handled and responses that couldn't be sent. */
	public void setErrorListener(Consumer<Throwable> listener) {
		this.errorListener = listener == null ? e -> {} : listener;
	}

	public LoupeEndpoint getEndpoint() {
		return endpoint;
	}

	/** Completes when the connection has closed (the other end went, or close()). */
	public CompletableFuture<Void> closed() {
		return closed;
	}

	public boolean isClosed() {
		return closed.isDone();
	}

	@Override
	public void close() {
		closeStreams();
		Thread r = reader;
		if( r != null && r != Thread.currentThread()) {
			try {
				r.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void closeStreams() {
		try {
			in.close();
		} catch (IOException e) {
			// closed
		}
		try {
			out.close();
		} catch (IOException e) {
			// closed
		}
	}
}
