# Loupe protocol (draft 0)

Loupe is the debugging protocol for fsh and fsh-ide. It keeps what DAP does well and fixes
what costs a client round trips or guesses. A bridge (`us.bringardner.parley.loupe.dap`)
lets VS Code and other DAP clients debug a Loupe target.

Status: a draft to react to. Section 10 lists what is not decided.

## 1. Transport and framing

JSON messages, UTF-8, each preceded by `Content-Length: N\r\n\r\n` exactly as in DAP and
LSP, so `DapFraming` is reused as is (stdin/stdout, a socket, or parley-net's channels).

## 2. Envelope

JSON-RPC 2.0 shaped, without the `"jsonrpc"` member. DAP's `seq`/`request_seq` pairing goes
away; the sender of a request picks its `id`.

```
request   {"id":7, "method":"addBreakpoint", "target":"t1", "params":{...}}
response  {"id":7, "result":{...}}
error     {"id":7, "error":{"code":"invalidParams", "message":"...", "data":{...}}}
event     {"event":"paused", "target":"t1", "params":{...}}
```

- `id` is an integer or a string, unique among the sender's outstanding requests.
- `target` is present on every message about a target and absent on session-level ones
  (`hello`, `launch`, `cancel`).
- Both ends may send requests (reverse requests are ordinary requests); both may send events.
- Error `code` is a short string from a fixed set (`invalidParams`, `unknownMethod`,
  `unknownTarget`, `wrongState`, `cancelled`, `notSupported`, `failed`); `data` is free.

## 3. Session start: `hello` / `welcome`

```
-> {"id":1, "method":"hello", "params":{
     "protocols":["loupe/1"],
     "client":{"name":"fsh-ide","version":"1.0"},
     "features":["snapshot","objectChanges"]}}
<- {"id":1, "result":{
     "protocol":"loupe/1",
     "adapter":{"name":"fsh","version":"1.0"},
     "features":["snapshot","objectChanges"],
     "launchSchema":{ ...JSON Schema... },
     "attachSchema":{ ...JSON Schema... }}}
```

- The adapter picks the newest protocol both ends list, or fails with `notSupported` and
  `data.protocols`.
- `features` in the result is the intersection: only those may be used in the session.
  Methods and event fields not tied to a feature are in every `loupe/1`.
- Nothing but `hello` is accepted before it.

## 4. Targets and their state machine

A target is one debuggee (a script, a process, a remote shell). The client creates it:

```
-> {"id":2, "method":"launch", "params":{"target":"t1", "args":{...}}}
```

`target` is chosen by the client. `args` is validated by the adapter against `launchSchema`;
failures come back as `invalidParams` with the JSON Pointer of each bad member in
`data.errors` (the target id is not used up by a request that fails here). `attach` is the
same, checked against `attachSchema` (absent from the welcome if the adapter can't attach,
and `attach` then fails with `notSupported`).

The target is `running` (event `targetState`, reason `launch` or `attach`) before the adapter
starts the debuggee, so it may pause at once, to stop on entry. The response is
`{"target":"t1","state":"running"}` once the debuggee has started. If starting fails, the
target goes to `terminated` (reason `launchFailed` or `attachFailed`) and the request fails;
the id stays used, as for any terminated target.

```
              launch
  (none) ------------> running <---------+
                        |  ^             |
      breakpoint, step  |  | resume,     |
      done, pause,      |  | step        |
      exception         v  |             |
                       paused -----------+
                        |
       exit, kill       v         (from running or paused)
                    terminated
```

Every change is announced with `targetState` (`state`, `from`, `reason`). A request that
needs another state fails with `wrongState` and `data.state`. `terminated` is final; the id
may not be reused in the session.

### Execution control

| Request | Needs | Effect |
|---|---|---|
| `resume` | paused | the target runs: `targetState` running (reason `resume`) |
| `step {kind, thread?}` | paused | `kind` is `over`, `in` or `out`; the target runs (reason `step`) and pauses again when the step is done, reason `step` |
| `pause` | running | asks the target to stop; it does so with `targetState` paused (reason `pause`) |
| `terminate` | running or paused | ends the debuggee: `targetState` terminated (reason `kill`, or the adapter's own with an `exitCode`) |

A request in the wrong state fails with `wrongState` and `data.state`; one the adapter
doesn't implement fails with `notSupported` and changes nothing. The response comes when
the adapter has accepted the request, not when the target stops again: the stop is the
event. If the adapter can't start a `resume` or `step` after the target was announced as
running, the target is announced as paused again (reason `resumeFailed` or `stepFailed`,
a new generation) and the request fails.

## 5. Stop snapshot

`targetState` to `paused` carries what the IDE shows next, so it need not ask:

```
{"event":"targetState","target":"t1","params":{
  "state":"paused","from":"running","reason":"breakpoint","breakpoints":["b1"],
  "generation":12,
  "snapshot":{
    "threads":[{"id":1,"name":"main","state":"paused"}],
    "thread":1,
    "frames":[{"id":"f1@12","name":"main","source":{"path":"/x.fsh"},"line":10,"column":3}],
    "locals":[{"name":"n","value":{"t":"number","v":3,"kind":"int"}}]}}}
```

`frames` holds the top `snapshotFrames` frames (default 20; `snapshotLimits` on `launch`),
`locals` those of the first frame. More are fetched with `frames`, `locals`, `scopes`.

## 5a. Stack and value requests

Asked while the target is `paused` (`threads` also while `running`); in any other state they
fail with `wrongState`. An adapter that doesn't implement one fails it with `notSupported`.

| Request | Params | Result |
|---|---|---|
| `threads` | | `{threads:[{id,name,state}]}` |
| `frames` | `{thread?, start?, count?}` | `{frames:[Frame], total?}`, top frame first |
| `scopes` | `{frame}` | `{scopes:[{name, id, expensive?}]}` |
| `locals` | `{frame, start?, count?}` | `{variables:[{name, value}], total?}` |
| `children` | `{id, start?, count?}` | `{variables:[{name, value}], total?}`: the elements, entries or fields of an array, map or object, or the variables of a scope |
| `objectChanges` | `{since}` | `{generation, changed:[name], gone:[name]}`; needs the `objectChanges` feature, and `since` from 0 to the current generation |

`start` counts from 0 and defaults to 0; `count` left out means as many as the adapter will
give, and `total` says how many there are if it knows. Negative values are `invalidParams`.

An id with a generation (`f0@12`, `o1@12`) must be of the target's current one; an id of an
earlier pause fails with `staleId` and `data {id, generation}` giving the current
generation. An id without a generation (`o1`) names the object in whichever generation the
target is in.

## 6. Values and object ids

Values are typed, never display strings:

| `t` | members |
|---|---|
| `null` | |
| `bool` | `v` |
| `number` | `v`, `kind` (`int`, `float`, `big`; `big` has `v` as a decimal string) |
| `string` | `v`, `length` and `truncated` if cut |
| `array` | `id`, `type`, `length`, `items` (first few; or omitted) |
| `map` | `id`, `type`, `size`, `entries` (first few: `[key, value]`) |
| `object` | `id`, `type`, `fields` (first few) |
| `function` | `id`, `name`, `signature`, `source` |
| `ref` | `id`, `type`, `summary` (an object not expanded) |

Anything with an `id` can be expanded with `children {id, start, count}`.

An object id is `name@generation`, e.g. `o12@5`. `generation` increases by one each time
the target pauses. The `name` part is stable for the life of the object, so the IDE can
keep a tree open across steps. A fully qualified id is valid while the target is paused in
that generation; asking after it moved on gets `staleId`, and the client asks for `name`
at the current generation or uses `objectChanges`:

```
-> {"id":9,"method":"objectChanges","target":"t1","params":{"since":11}}
<- {"id":9,"result":{"generation":12,"changed":["o12","o40"],"gone":["o3"]}}
```

(needs feature `objectChanges`).

## 7. Breakpoints

One at a time, with ids the client chooses:

```
-> {"id":3,"method":"addBreakpoint","target":"t1","params":{
     "id":"b1","where":{"source":{"path":"/x.fsh"},"line":10},
     "condition":"n > 2","hitCount":3,"logMessage":null}}
<- {"id":3,"result":{"status":"pending"}}
event  {"event":"breakpointStatus","target":"t1","params":{
     "id":"b1","status":"verified","where":{"source":{"path":"/x.fsh"},"line":11},"message":null}}
-> {"id":4,"method":"removeBreakpoint","target":"t1","params":{"id":"b1"}}
```

`status` is `pending`, `verified`, `rejected` (with `message`) or `removed`. Adding an id
that exists replaces it. There is no replace-all-in-a-file request.

## 8. Cancellation and multiple targets

`{"id":8,"method":"cancel","params":{"requestId":7}}`: the request 7 ends with error
`cancelled` (or with its normal result if it had already finished); `cancel` itself
answers `{}`. Every request that can take time may be cancelled.

A session holds any number of targets. `target` routes; there is no global "current
thread" or "current frame" state in the adapter.

## 9. Mapping to DAP (the bridge)

| DAP | Loupe |
|---|---|
| `initialize` | `hello` |
| `launch` / `attach` | `launch` / `attach` (target id made by the bridge) |
| `setBreakpoints` (a file's whole list) | diff against the bridge's list: `addBreakpoint` / `removeBreakpoint` |
| `stopped` | `targetState` to `paused`; the bridge caches the snapshot and answers `threads`, `stackTrace`, `scopes`, `variables` from it when it can |
| `variablesReference` | the bridge's table of object ids |
| `continue`, `next`, `stepIn`, `stepOut`, `pause` | `resume`, `step {kind}`, `pause` |
| `terminated`, `exited` | `targetState` to `terminated` |

DAP cannot say "what changed since", so `objectChanges` is not used by the bridge.

## 10. Decided and open

Decided:

1. **Envelope:** the JSON-RPC shape of section 2, with a `LoupeEndpoint` and `LoupeConnection`
   of our own. `DapFraming` is reused as is; `DapEndpoint` and `DapConnection` are not (their
   envelope and dispatchers are DAP's).
2. **`launchSchema`:** JSON Schema, checked by our own `SchemaValidator` (no dependency). It
   covers `type`, `enum`, `const`, `properties`, `required`, `additionalProperties`, `items`,
   `minimum`, `maximum`, `minLength`, `maxLength`, `pattern`, `minItems`, `maxItems`, `allOf`,
   `anyOf`, `oneOf`; other keywords are ignored and `$ref` is not supported.
3. **Generations:** counted per target (section 6).

Open:

4. Evaluate, completions, watch expressions, exceptions, modules, memory/disassembly: not
   in this draft. Which does fsh-ide need first?
5. Shared module for JSON and framing (`parley-json`): until then parley-loupe depends on
   parley-dap, which the bridge needs anyway.
