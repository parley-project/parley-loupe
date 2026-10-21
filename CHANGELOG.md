# Changelog

## parley-loupe 1.1.0 (unreleased)

## parley-loupe 1.0.0 (2026-10-21)

First version.

- The Loupe protocol ([PROTOCOL.md](PROTOCOL.md)): JSON messages framed with `Content-Length`
  over any pair of streams; `hello` / `welcome` version and feature negotiation; a state machine
  per target (`running`, `paused`, `terminated`); stops that carry a snapshot (threads, top
  frames, locals); typed values with object ids that carry a generation, and `objectChanges`;
  breakpoints added and removed one at a time with status events; cancellation.
- `AdapterHandler` and `TargetBackend` to write an adapter: negotiation, argument and state
  checks, ids and events are handled; a backend implements what the debuggee does.
- `LoupeConnection`, `LoupeEndpoint` and `Handshake` for both ends; typed messages in
  `us.bringardner.parley.loupe.protocol`.
- `SchemaValidator`: launch arguments checked against the JSON Schema the adapter publishes.
- `dap.DapBridge` and `DapBridgeMain`: a DAP adapter that is a Loupe client, so VS Code and other
  DAP clients can debug a Loupe target.
- A complete reference backend for a tiny language (`ToyLang`, `ToyBackend`, `ToyAdapterMain`) is
  kept with the tests, as an example to read and run; it isn't part of the published jar.
