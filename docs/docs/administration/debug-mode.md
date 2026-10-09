# Global debug mode

OpenAEV ships a global, instance-wide debug mode for diagnosing bugs and performance problems on
on-prem installations. It produces a rich, correlated execution trace and stays fully disabled by
default.

When it is off, the installation behaves exactly as before: no datasource proxy on the query path,
no parameter capture, no extra per-request work and no extra files written.

## What it produces

- **Correlation id on every log line (tenant and user in the SQL detail).** Each request carries a
  `traceId` (and `spanId`) in the MDC (Mapped Diagnostic Context, the per-thread key/value bag the logging
  library attaches to every line it emits), shown on the console as the `[traceId-spanId]` slot, so all the lines
  emitted while handling it can be grouped together. Requests also carry the `tenant` and the `user`
  (the caller's id, `anonymous` when unauthenticated) in the MDC; both are rendered in the SQL file
  (not the console slot), so the SQL can be filtered per tenant or per user. This uses Micrometer
  Tracing; no tracing backend is required, the ids are written to the normal log output.
- **SQL detail.** Every SQL statement, both ORM (Object-Relational Mapping)-generated and native, is logged with its execution time
  and its masked parameters, on the dedicated `io.openaev.debug.sql` logger. To keep this high-volume
  output off the console and the production log pipeline, it is written to a rotated file
  (`openaev-debug-sql.log`) under the debug output directory rather than to stdout.
- **ORM insight.** One summary per request (a single log event) on `io.openaev.debug.orm` (total
  queries and time, plus the caller as `user=`), flagging N+1 queries (the same SELECT repeated many
  times, the classic lazy-loading symptom) and chatty requests. It rides on the SQL detail above and
  stays on the console by default so it remains visible; on instances whose console is shipped to
  centralised logging, set `openaev.debug.orm.summary-to-file=true` to route it to a rotated file
  (`openaev-debug-orm.log`) instead.
- **JVM (Java Virtual Machine) profiling.** A bounded Java Flight Recorder (JFR) recording is started, dumped on a timer and
  flushed on shutdown to the debug output directory. JFR is part of the JDK (Java Development Kit), there is no extra agent.

The scope of the toggle is global: a single flag turns the verbose mode on for the whole instance.
Per-request scoping is out of scope.

## Example output

A single request, `GET /api/scenarios/sc-42`, with debug mode on. Two sinks: the console keeps the
application logs and the ORM summary; the verbose per-statement SQL goes to the rotated file.

Console (application logs + the per-request ORM summary). Each line carries Spring Boot's correlation
slot `[traceId-spanId]` (ids shortened here for readability):

```text
INFO  [OpenAEV API] [http-nio-exec-3] [6a3c4dea…0b-7bd42e33…] ...ScenarioApi : Loading scenario sc-42
INFO  [OpenAEV API] [http-nio-exec-3] [6a3c4dea…0b-7bd42e33…] ...ScenarioApi : Scenario sc-42 loaded
WARN  [OpenAEV API] [http-nio-exec-3] [6a3c4dea…0b-7bd42e33…] ...debug.orm   : ORM GET /api/scenarios/sc-42: 13 queries (2 distinct), 7ms user=8f2b-user-alice
  N+1 SUSPECTED: 'select team_name from teams where team_id = ?' executed 12x (6ms total)
```

The console slot is just `[traceId-spanId]` (no tenant). The tenant is carried in the MDC and rendered
only in the SQL file below, which uses its own pattern.

The rotated SQL file `openaev-debug-sql.log`, one line per statement, with masked parameters:

```text
2026-06-24 23:36:42.165 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme user=8f2b-user-alice] sql success=true time=1ms statement=insert into users (user_id, user_email, user_password) values (?, ?, ?) params=[{user_id=u-1, user_email=***MASKED***, user_password=***MASKED***}]
2026-06-24 23:36:42.179 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme user=8f2b-user-alice] sql success=true time=5ms statement=select team_name from teams where team_id = ? params=[{team_id=team-0}]
2026-06-24 23:36:42.181 INFO [trace=6a3c4dea... tenant=0e7c2f1a-tenant-acme user=8f2b-user-alice] sql success=true time=0ms statement=select team_name from teams where team_id = ? params=[{team_id=team-1}]
... the same SELECT 10 more times, one per team -- the N+1 the summary flagged
```

## Reading the SQL log in practice

The example above is filtered to a single request for clarity. The real `openaev-debug-sql.log` is
**not meant to be read top to bottom** -- it is a flat, chronological stream that you query, not browse.
Know this before you open it:

- **Most lines are background noise.** Every statement the datasource runs is logged, including
  scheduled jobs and pollers that have no request context (these show `[trace= tenant=]`). On a live
  instance the handful of lines for your request are a small fraction of the file.
- **Lines are long.** Hibernate selects every column, so a single statement can run past a few hundred
  characters. Use a pager that does not wrap (`less -S`) or filter first.
- **You filter by trace id.** Get the request's trace id from the application log, then
  pull just that request:

```bash
# every statement of one request, in order
grep "trace=<trace-id>" openaev-debug-sql.log | less -S

# only the masked values (sanity-check masking)
grep MASKED openaev-debug-sql.log | less -S

# drop the context-less background noise, keep correlated statements only
grep -E "trace=[0-9a-f]" openaev-debug-sql.log | less -S
```

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

### A production-safe, low-noise setup

When you need to debug on a real (production) instance and want signal over noise, this is a good
starting point. It focuses on slow queries and N+1s, keeps the output off centralised logging, and
disables itself so it cannot be left running by accident:

```properties
# On, allowed in production (no dev/test/ci profile), auto-off after 30 min as a safety net.
openaev.debug.enabled=true
openaev.debug.allow-in-production=true
openaev.debug.auto-disable-after=30m

# Write to a persistent volume so the files survive a container recycle.
openaev.debug.output-dir=/var/debug

# Signal over noise:
# only slow statements (keeps far more history for the same disk)
openaev.debug.sql.slow-query-threshold=50ms
# keep the per-request ORM/N+1 summary out of Loki/Grafana
openaev.debug.orm.summary-to-file=true
# skip CPU/allocation profiling when you are chasing queries
openaev.debug.jfr.enabled=false

# Masking stays on by default. For maximum safety on sensitive data, mask every value (keep column + type):
# openaev.debug.masking.mask-all-parameters=true
```

As environment variables (the recommended way in a container), each key is its uppercased form with
`.` and `-` replaced by `_`, for example `OPENAEV_DEBUG_SQL_SLOW_QUERY_THRESHOLD=50ms`.

Then read the SQL detail in `/var/debug/openaev-debug-sql.log` and the N+1 summaries in
`/var/debug/openaev-debug-orm.log`, filtering by `trace=` or `user=` (see "Reading the SQL log in
practice"). Turn it off with `OPENAEV_DEBUG_ENABLED=false`, or just let the 30-minute auto-disable
kick in.

## Configuration reference

All settings live under `openaev.debug.*` and use standard Spring configuration (properties file or
environment variables). Every setting only takes effect when `openaev.debug.enabled=true`.

| Property | Default | Description |
| --- | --- | --- |
| `openaev.debug.enabled` | `false` | Master switch for the whole feature. |
| `openaev.debug.allow-in-production` | `false` | Override required to start the mode in production (no `dev`/`test`/`ci` profile). |
| `openaev.debug.auto-disable-after` | `0` | Auto-disable the verbose tracing after this duration (`0` = never). |
| `openaev.debug.warning-interval` | `5m` | How often the "debug mode is active" warning repeats. |
| `openaev.debug.output-dir` | `./logs/debug` | Writable directory for all debug artifacts: the JFR recordings, the rotated SQL log file and, when `orm.summary-to-file` is on, the rotated ORM summary file. |
| `openaev.debug.sql.enabled` | `true` | Log SQL statements (to the rotated file) with timing and masked parameters. |
| `openaev.debug.sql.slow-query-threshold` | `0ms` | Only log statements slower than this (`0` logs all). |
| `openaev.debug.sql.max-parameter-length` | `200` | Truncate rendered parameter values longer than this (after masking). |
| `openaev.debug.sql.max-file-size` | `500MB` | Rotated SQL file: size a single file reaches before it rolls over. |
| `openaev.debug.sql.max-history` | `7` | Rotated SQL file: days of history to keep. |
| `openaev.debug.sql.total-size-cap` | `2GB` | Rotated SQL file: total size kept before the oldest are deleted. Raise it on high-traffic instances where the log fills fast, to keep more history. |
| `openaev.debug.orm.summary-to-file` | `false` | Write the per-request ORM summary to `openaev-debug-orm.log` instead of the console (keeps it out of centralised logging). |
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

## Data masking

Masking is mandatory and on by default. SQL parameters and log fields can contain secrets, tokens,
credentials and personal data, and masking makes sure they never reach the logs. Two layers apply:

- **Key based.** Any value bound to a field/column whose name matches a sensitive key is masked
  (default keys include `password`, `secret`, `token`, `api_key`, `encryption_key`,
  `encryption_salt`, `authorization`, `client_secret`, ...).
- **Value based.** Configured regular expressions are masked wherever they appear, even with no key
  context (defaults: JSON Web Tokens, `Bearer`/`Basic` authorization values, PEM (Privacy-Enhanced Mail) private key blocks,
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

## Running in a container

The debug output directory needs a writable, and ideally persistent, location. A hardened container
with a read-only root filesystem must mount a volume and point the debug output there; a named volume
also keeps the output across container recycles:

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
Look for log lines starting with `Debug mode: failed to start JFR recording` and `Debug mode: log
directory is not writable`.

## What's next?

- [Configuration](../reference/deployment/configuration.md) -- Set platform properties and environment variables
- [Installation](../deployment/platform/installation.md) -- Deploy OpenAEV with Docker
- [Parameters](parameters.md) -- Check the platform version and connected services
