# parley-loupe

**Loupe** is a debugging protocol for fsh and fsh-ide, designed as an alternative to the
[Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) (DAP), with a
bridge so VS Code and other DAP clients can still debug a Loupe target.

- A stop carries a snapshot (threads, top frames, locals), so an IDE needs no round trips to
  show it
- Typed values (string, number, array, map, object, function, reference) with object ids that
  carry a generation (`o12@5`), and `objectChanges` to ask what changed
- Breakpoints added and removed one at a time, with ids the client chooses and status events
- Launch arguments checked against a JSON Schema the adapter publishes
- `hello` / `welcome` version and feature negotiation, cancellation, and several targets per
  session designed in
- A state machine per target: `running`, `paused`, `terminated`
- JSON messages, framed as DAP's are (`Content-Length`), over any pair of streams
- No dependencies outside Parley; Java 11 or later; Apache License 2.0

The protocol is described in [PROTOCOL.md](PROTOCOL.md) (a draft).

## An adapter

Implement `TargetBackend` (what the debuggee does) and serve it with an `AdapterHandler`
(what the protocol does: negotiation, checking arguments and states, ids, events):

```java
class MyBackend implements TargetBackend {
    @Override
    public boolean supports(String method) {
        return Set.of(Loupe.RESUME, Loupe.STEP, Loupe.FRAMES, Loupe.CHILDREN).contains(method);
    }

    @Override
    public CompletableFuture<Void> launch(Target target, Object args) {
        // start the debuggee suspended; it must be paused here if target.isStartPaused()
        target.pausedWith("entry", () -> snapshot(target), null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void breakpointAdded(Target target, BreakpointTable.Entry bp) {
        // install it; report what is found
        target.setBreakpointStatus(bp.getId(), BreakpointStatus.VERIFIED, null, null);
    }

    @Override
    public CompletableFuture<Void> resume(Target target) {
        // let it run; when it stops again: target.pausedWith("breakpoint", ..., hitIds)
        return CompletableFuture.completedFuture(null);
    }
    // step, frames, children ...
}

// on stdin/stdout, as a process an IDE or the bridge starts
LoupeConnection.serve(ep -> new AdapterHandler(ep, new MyBackend())
        .setAdapter(new PeerInfo().setName("fsh").setVersion("1"))
        .setFeatures(List.of(Loupe.FEATURE_SNAPSHOT))
        .setLaunchSchema(schema), System.in, System.out).closed().join();
```

A method the backend doesn't list in `supports` fails with `notSupported` before anything
changes, and is left out of the `methods` the welcome tells the client. Build a snapshot with
`pausedWith`, so the object ids in it are those of the generation the target has just entered.

## A client

```java
LoupeConnection c = LoupeConnection.open(handlerForEvents, in, out);
Welcome w = Handshake.hello(c.getEndpoint(), new Hello()
        .setProtocols(List.of(Loupe.PROTOCOL))
        .setFeatures(List.of(Loupe.FEATURE_SNAPSHOT))).get();
c.getEndpoint().request(Loupe.LAUNCH, null, new LaunchParams()
        .setTarget("t1").setArgs(args).setStartPaused(true).toJson()).get();
```

Requests and events are plain JSON values (`LoupeEndpoint.request(method, target, params)`);
the classes in `us.bringardner.parley.loupe.protocol` give them types.

## Debugging from VS Code or another DAP client

`DapBridgeMain` is a DAP adapter that is a Loupe client. An IDE starts it, and it connects to
the Loupe adapter:

```
java -cp parley-loupe.jar:parley-dap.jar:parley-net.jar \
     us.bringardner.parley.loupe.dap.DapBridgeMain --connect localhost:4711
java -cp ...  us.bringardner.parley.loupe.dap.DapBridgeMain --exec fsh --loupe
```

`--exec` starts the command and speaks Loupe on its standard input and output. The bridge
maps launch, attach, breakpoints (with conditions, hit counts and log messages), stepping,
stack, variables, evaluate, completions, setVariable and exceptions; see PROTOCOL.md section 9.
`stopOnEntry` in a launch configuration is handled by the bridge.

## Parley

parley-loupe is part of **Parley**, a family of Java libraries for implementing internet
protocols. It uses parley-dap's JSON reader and writer and its framing.
