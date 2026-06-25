# Global debug mode

OpenAEV ships a global, instance-wide debug mode for diagnosing bugs and performance problems on
on-prem installations. It produces a rich, correlated execution trace and stays fully disabled by
default.

When it is off, the installation behaves exactly as before: no datasource proxy on the query path,
no parameter capture, no extra per-request work and no extra files written.

## What it produces

- **Correlation id and tenant in every log line.** Each request carries a `traceId` (and `spanId`)
  and, for tenant-scoped requests, the `tenant` in the MDC, so all the lines emitted while handling it
  can be grouped together and filtered per tenant. This uses Micrometer Tracing; no tracing backend is
  required, the ids are written to the normal log output.
- **SQL detail.** Every SQL statement (ORM-generated and native) is logged with its execution time
  and its masked parameters, on the dedicated `io.openaev.debug.sql` logger. To keep this high-volume
  output off the console and the production log pipeline, it is written to a rotated file
  (`openaev-debug-sql.log`) under the debug output directory rather than to stdout.
- **ORM insight.** One summary line per request on `io.openaev.debug.orm` (total queries and time),
  flagging N+1 queries (the same SELECT repeated many times, the classic lazy-loading symptom) and
  chatty requests. No configuration of its own; it rides on the SQL detail above. This summary stays
  on the console so it remains visible.
- **JVM profiling.** A bounded Java Flight Recorder (JFR) recording is started, dumped on a timer and
  flushed on shutdown to the debug output directory. JFR is part of the JDK, there is no extra agent.

The scope of the toggle is global: a single flag turns the verbose mode on for the whole instance.
Per-request scoping is out of scope.

## Example output

A single request, `GET /api/scenarios/sc-42`, with debug mode on. Two sinks: the console keeps the
application logs and the ORM summary; the verbose per-statement SQL goes to the rotated file.

Console (application logs + the per-request ORM summary), every line carrying the request's `trace`
and `tenant`:

```text
INFO  [trace=6a3c4dea tenant=0e7c2f1a-tenant-acme] ...ScenarioApi : Loading scenario sc-42
INFO  [trace=6a3c4dea tenant=0e7c2f1a-tenant-acme] ...ScenarioApi : Scenario sc-42 loaded
WARN  [trace=6a3c4dea tenant=0e7c2f1a-tenant-acme] ...debug.orm   : ORM GET /api/scenarios/sc-42: 13 queries (2 distinct), 7ms
  N+1 SUSPECTED: 'select team_name from teams where team_id = ?' executed 12x (6ms total)
```

The rotated SQL file `openaev-debug-sql.log`, one line per statement, with masked parameters:

```text
2026-06-24 23:36:42.165 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme] sql success=true time=1ms statement=insert into users (user_id, user_email, user_password) values (?, ?, ?) params=[{user_id=u-1, user_email=***MASKED***, user_password=***MASKED***}]
2026-06-24 23:36:42.179 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme] sql success=true time=5ms statement=select team_name from teams where team_id = ? params=[{team_id=team-0}]
2026-06-24 23:36:42.181 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme] sql success=true time=0ms statement=select team_name from teams where team_id = ? params=[{team_id=team-1}]
... the same SELECT 10 more times, one per team -- the N+1 the summary flagged
```

What each field means:

- `trace=6a3c4dea...` -- the correlation id shared by every line of the request (application, SQL,
  ORM summary). It is the full 32-hex id in the logs (shortened here for readability). Filter on it to
  reconstruct the whole request.
- `tenant=...` -- the tenant the request targets (the default tenant when the request is not
  tenant-scoped).
- `time=1ms` -- the statement's JDBC execution time, so slow statements stand out.
- `statement=...` -- the real SQL sent to PostgreSQL (Hibernate-generated or native), with `?`
  placeholders.
- `params=[{column=value}]` -- the bound parameters by column. `user_email` and `user_password` are
  `***MASKED***` (email by value pattern, password by sensitive column name); `user_id` and `team_id`
  are not sensitive, so they are shown.
- The `ORM ...` line is the one summary per request: total queries and time, plus `N+1 SUSPECTED` --
  the same SELECT ran once per team (lazy loading), the classic N+1 to fix.

## Reading the SQL log in practice

The example above is filtered to a single request for clarity. The real `openaev-debug-sql.log` is
**not meant to be read top to bottom** -- it is a flat, chronological stream that you query, not browse.
Know this before you open it:

- **Most lines are background noise.** Every statement the datasource runs is logged, including
  scheduled jobs and pollers that have no request context (these show `[trace= tenant=]`). On a live
  instance the handful of lines for your request are a small fraction of the file.
- **Lines are long.** Hibernate selects every column, so a single statement can run past a few hundred
  characters. Use a pager that does not wrap (`less -S`) or filter first.
- **You filter by trace id.** Get the request's trace id from the application log or the response, then
  pull just that request:

```bash
# every statement of one request, in order
grep "trace=<trace-id>" openaev-debug-sql.log | less -S

# only the masked values (sanity-check masking)
grep MASKED openaev-debug-sql.log | less -S

# drop the context-less background noise, keep correlated statements only
grep -E "trace=[0-9a-f]" openaev-debug-sql.log | less -S
```

These limits are inherent to logging every statement globally. Scoping the SQL log to request context
and emitting a per-request summary line as the entry point are tracked as a follow-up (see
[#6384](https://github.com/OpenAEV-Platform/openaev/issues/6384)); until then, the grep-by-trace
workflow above is the intended way to use the file.

## Enabling and disabling

The mode is driven by a single flag, off by default. Enable it (preferably via the environment
variable so it is obvious and easy to revert):

```bash
OPENAEV_DEBUG_ENABLED=true
```

or in `application.properties`:

```properties
openaev.debug.enabled=true
```

Disable it by removing the flag (or setting it to `false`) and restarting.

### Production barrier

In production, turning `openaev.debug.enabled` on is **not enough**: the mode refuses to start unless
a separate override is also set.

```properties
openaev.debug.allow-in-production=true
```

Production is taken to be the absence of a non-production profile (`dev`, `test`, `ci`) -- there is
no explicit `production` profile in the platform. When debug is requested but refused, a clear error
is logged; when it is allowed through the override, a warning is logged. On top of that, a loud banner
is logged at startup and repeated at `warning-interval`, and the verbose tracing can auto-disable
itself after `auto-disable-after` (the datasource proxy is then inert until a restart fully removes
it).

## Configuration reference

All settings live under `openaev.debug.*` and use standard Spring configuration (properties file or
environment variables). Every setting only takes effect when `openaev.debug.enabled=true`.

| Property | Default | Description |
| --- | --- | --- |
| `openaev.debug.enabled` | `false` | Master switch for the whole feature. |
| `openaev.debug.allow-in-production` | `false` | Override required to start the mode in production (no `dev`/`test`/`ci` profile). |
| `openaev.debug.auto-disable-after` | `0` | Auto-disable the verbose tracing after this duration (`0` = never). |
| `openaev.debug.warning-interval` | `5m` | How often the "debug mode is active" warning repeats. |
| `openaev.debug.output-dir` | `./logs/debug` | Writable directory for all debug artifacts: the JFR recordings and the rotated SQL log file. |
| `openaev.debug.sql.enabled` | `true` | Log SQL statements (to the rotated file) with timing and masked parameters. |
| `openaev.debug.sql.slow-query-threshold` | `0ms` | Only log statements slower than this (`0` logs all). |
| `openaev.debug.sql.max-parameter-length` | `200` | Truncate rendered parameter values longer than this (after masking). |
| `openaev.debug.jfr.enabled` | `true` | Start a JFR recording (skipped when the Pyroscope agent is enabled). |
| `openaev.debug.jfr.max-size` | `100MB` | Hard cap on a single recording's on-disk size. |
| `openaev.debug.jfr.max-age` | `1h` | Maximum age of events kept in the buffer. |
| `openaev.debug.jfr.duration` | `10m` | Interval between periodic dumps to a `.jfr` file. |
| `openaev.debug.jfr.settings` | `profile` | Built-in JFR profile: `default` (low overhead) or `profile` (richer, non-trivial overhead). |
| `openaev.debug.jfr.max-dump-files` | `12` | Retention: oldest periodic dumps are deleted past this count. |
| `openaev.debug.jfr.max-total-dump-size` | `500MB` | Retention: oldest periodic dumps are deleted past this total size. |
| `openaev.debug.masking.enabled` | `true` | Mask secrets and personal data before logging. |
| `openaev.debug.masking.mask-all-parameters` | `false` | Deny-by-default: mask every parameter value, keep only column name + type. |
| `openaev.debug.masking.mask` | `***MASKED***` | Replacement token. |
| `openaev.debug.masking.sensitive-keys` | see below | Field/column names whose value is always masked. |
| `openaev.debug.masking.value-patterns` | see below | Regexes whose matches are masked anywhere. |

The correlation ids follow the production barrier automatically: `DebugTracingEnvironmentPostProcessor`
sets `management.tracing.enabled` to `true` only when the mode actually starts (enabled **and**
allowed for the current profile). So the tracer adds nothing when the mode is off, and nothing when
debug is requested but refused in production.

## Data masking

Masking is mandatory and on by default. SQL parameters and log fields can contain secrets, tokens,
credentials and personal data, and masking makes sure they never reach the logs. Two layers apply:

- **Key based.** Any value bound to a field/column whose name matches a sensitive key is masked
  (default keys include `password`, `secret`, `token`, `api_key`, `encryption_key`,
  `encryption_salt`, `authorization`, `client_secret`, ...).
- **Value based.** Configured regular expressions are masked wherever they appear, even with no key
  context (defaults: JSON Web Tokens, `Bearer`/`Basic` authorization values, PEM private key blocks,
  email addresses). The regex scan is bounded (8 KB) so a very long value cannot burn the request
  thread; values are masked first and truncated for display after, so no prefix of a long secret
  leaks.

Both layers are best-effort: a secret in an oddly named column whose value matches no pattern would
still appear. For a hard guarantee, turn on **deny-by-default** mode, which masks every parameter
value (keeping only the column name and type) and additionally blanks every single-quoted string
literal in the statement text, so a secret inlined in a native query does not leak even if it matches
no pattern:

```properties
openaev.debug.masking.mask-all-parameters=true
```

Both lists are configurable. To extend them:

```properties
openaev.debug.masking.sensitive-keys=password,secret,token,my_custom_field
openaev.debug.masking.value-patterns=eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+,\\b\\d{16}\\b
```

## Output and rotation

Everything debug mode writes goes under `openaev.debug.output-dir` (default `./logs/debug`):

- the JFR recordings (`openaev-debug-*.jfr`). A single recording is bounded by `jfr.max-size` and
  `jfr.max-age`; the periodic dumps are kept bounded by a retention pass that deletes the oldest past
  `jfr.max-dump-files` (12) or `jfr.max-total-dump-size` (500 MB);
- the SQL log (`openaev-debug-sql.log`), a rotated file capped at 50 MB per file, 7 files and 500 MB
  total. The per-statement SQL flood is kept off the console / production log pipeline; the ORM
  summary, the activation banner and the JFR status stay on the console.

The application's own logs (with `traceId` and `tenant` in the MDC) keep going to their usual sink.

## Running in a container

The debug output directory needs a writable location. A hardened container with a read-only root
filesystem must mount a writable volume and point the debug output there:

```yaml
services:
  openaev:
    read_only: true
    environment:
      OPENAEV_DEBUG_ENABLED: "true"
      OPENAEV_DEBUG_OUTPUT_DIR: "/var/debug"
    volumes:
      - debug-data:/var/debug
volumes:
  debug-data:
```

If no writable path is available, JFR fails loudly with a clear error and reports a failed state, and
the SQL log falls back to the console instead of the file; the rest of the application keeps running.
Look for log lines starting with `Debug mode: failed to start JFR recording` and `Debug mode: SQL log
directory is not writable`.

## Cost and bounding

- **When off:** near-zero per-request cost. No datasource proxy, no parameter capture, no per-request
  span/correlation work and no extra files. "Near-zero" applies to the **off** state only.
- **When on:** bounded, but not free. The default JFR profile is `profile`, which carries a
  non-trivial sampling overhead; switch to `default` for lighter profiling. The SQL log is a
  size-capped, rotated file; JFR recordings and their dumps are bounded as described above.

## Notes

- The mode does not open any new port or endpoint. It adds no attack surface.
- The `tenant` MDC tag works under both tenant mechanisms: it uses the request's tenant selector (the
  `{tenantId}` path variable, else the `X-Tenant-Ids` header) and falls back to the v1 tenant context
  (the default tenant when unset). Requests that are not tenant-scoped record the default tenant.
- **Profilers, one at a time, by deployment.** Both JFR and Pyroscope ship; only one runs at a time
  (when `pyroscope.agent.enabled=true`, the JFR recording does not start and a clear message is
  logged). Use **JFR on-prem**: it needs no server and writes a local `.jfr` file the customer can
  send back. Use **Pyroscope in the cloud**: it pushes to the existing Pyroscope server, which on-prem
  installs do not have.

## Using debug mode for a cloud incident

The mode is intended for incidents and is acceptable to turn on briefly in the cloud, with these
settings:

- set `openaev.debug.auto-disable-after` (e.g. `2h`) so it switches itself off after the incident
  window;
- keep masking on, and consider `openaev.debug.masking.mask-all-parameters=true` for the strongest
  guarantee;
- be aware that on a **shared multi-tenant** instance the SQL log aggregates queries from all tenants
  for the duration (tenant UUIDs are not masked); the `tenant` tag lets you filter, and access to the
  output directory should be controlled.
