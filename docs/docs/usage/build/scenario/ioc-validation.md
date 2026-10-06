# IOC validation from OpenCTI dissemination assurance

## Overview

OpenCTI tracks where each Indicator of Compromise (IOC) is deployed: which security platforms received it, and
whether they acknowledged it. IOC validation answers the next question: does the deployed indicator actually
trigger a detection or a prevention on those platforms? This page uses *indicator* and *IOC* for the same thing.

When an analyst asks for a validation in OpenCTI, OpenAEV receives the request, waits for an operator to
approve it, then runs one **benign** test per indicator on the asset group you choose. The result of every
indicator and security platform pair goes back to OpenCTI.

IOC validation is available in every edition and uses no AI: the tests and the outcomes are deterministic.

## Safety first

IOC validation is designed so that nothing dangerous ever runs:

- **Nothing runs without an approval.** Every request waits in status *Awaiting approval* until an operator
  approves or rejects it.
- **Only the test kinds you allow run.** By default, no test kind is allowed: each one, *DNS resolution*
  included, must be enabled explicitly in the settings.
- **An approval never runs more than the request showed.** A test the settings no longer allow at approval time is
  dropped; a test that was skipped when the request arrived stays skipped even if you allow it meanwhile. Ask for a
  new validation from OpenCTI to run it.
- **The real indicator is never downloaded or executed.** File indicators are replaced by a benign text file that
  only carries the file name, and hashes are only written to a log line.
- **No contact with adversary infrastructure by default.** Nothing runs until you allow a test kind. DNS
  resolution never connects to the resolved address, but the resolver of the endpoint may query the name servers
  of the domain, which can tell its owner that the name was looked up: point the endpoints at a resolver that does
  not forward to the internet if that matters to you. Network tests can be redirected to a sinkhole you control,
  and HTTP tests can only be allowed once an egress proxy is configured.
- **Indicator values are checked before they reach a command.** Indicators come from threat-intelligence feeds:
  a value is used only when it matches the format of its test (see [Accepted indicator values](#accepted-indicator-values)),
  and no test is planned towards an internal address. Anything else is refused, never rewritten.
- **Skipped indicators are explained.** When a test kind is not allowed or does not apply to an indicator, the
  request shows why before anyone approves it.

## Benign tests

| Test kind        | Applies to                  | What it does                                                                                   |
|------------------|-----------------------------|------------------------------------------------------------------------------------------------|
| DNS resolution   | Domain names, host names    | Resolves the name. No connection is made to the resolved address; the resolver may query the name servers of the domain. |
| Network traffic  | IPv4 and IPv6 addresses     | Opens a TCP connection, to the sinkhole when one is set, and closes it at once without payload. |
| HTTP HEAD request| URLs                        | Sends an HTTP HEAD request through the egress proxy. No content is downloaded and a redirect is never followed. Any HTTP answer, from the proxy or the server, counts as a test that ran; a request that gets none (proxy, DNS, TLS or connection failure, timeout) ends with an execution error rather than a missed result. |
| Benign file drop | Files and artifacts         | Writes a benign text file named after the indicator file name, in a temporary directory created for the test (`openaev-ioc-validation-<run>`). On Windows, the cleanup removes the file only while it still holds the surrogate of its run, checked and deleted through one handle (never a directory that carries its name, nor a file that was there before or was put in its place), then the directory only when it is empty: anything else written in it is left in place. On Linux and macOS, where a file can only be removed by its path once checked, the cleanup leaves the surrogate in place and removes the directory only when it is empty, for instance once the security platform quarantined the file; the surrogate stays in the temporary directory until the system clears it. Neither the drop nor the cleanup ever follows a link: a run directory or a file path replaced by a symbolic link, a junction or another reparse point stops the drop with an error and is left alone by the cleanup, and so does a run directory that already exists and belongs to another account. The file is always created as a new file: a file already at its path is never written over, and the test then ends with an execution error rather than a missed result, as does any other write failure. Each inject gets its own run identifier, part of what the approval covers; an inject without one, or whose identifier was changed after the approval, is not executed. Before writing or removing anything, the endpoint checks that the run identifier is 32 lowercase hexadecimal characters and that the file name is a plain file name, so files of other applications are never touched. On Windows, a file name that Windows cannot create (a reserved device name such as `CON.txt` or `COM1`, or a name ending with a dot or a space) stops the test with an error before any file operation; the same name still runs on Linux and macOS. |
| Benign log line  | Any indicator, such as hashes | Writes a log line containing the indicator value to the system log (syslog through `logger` on Linux and macOS, the Application event log on Windows). An endpoint whose system log cannot be written ends the test with an execution error; the line is never written to a file instead. |

Each test carries a **Detection** and a **Prevention** expectation for every security platform of the request.

After an upgrade, the benign test payloads are brought to the current version the next time a validation is
approved. Until then, a file drop approved before the upgrade is refused when the agent asks for it, rather than run
with the earlier version of the test: approve a new validation to run it.

## Accepted indicator values

The value of an indicator ends up in the command of its benign test, so each test accepts only a strict format,
made of ASCII characters:

| Test kind         | Accepted value |
|-------------------|----------------|
| DNS resolution    | A host name: letters, digits, `-` and `_` in each label, at most 253 characters. An international name is converted to its `xn--` form; punctuation, quotes and spaces are refused. |
| Network traffic   | A single IPv4 or IPv6 address that is not internal, written with ASCII digits (an address written with fullwidth digits such as `U+FF18`, or with the digits of another script, is refused). A `/32` or `/128` range counts as its address; any wider range is refused. |
| HTTP HEAD request | An absolute `http` or `https` URL of at most 2048 characters, made only of the characters of RFC 3986: ASCII letters, digits and `-._~:/?#[]@!$&()*+,;=%`. An apostrophe must be percent-encoded as `%27`, and every `%` must start a percent-encoded byte. The URL carries no credentials, and its host is an IP address that is not internal or a DNS name of at least two labels. |
| Benign file drop  | The base name of the indicator file (a directory part is dropped): ASCII letters, digits, `.`, `_` and `-` only, at most 128 characters. |
| Benign log line   | The strongest hash of the indicator (SHA-256, then SHA-512, SHA-1, MD5), or its value when it has no hash: hexadecimal, with the 32, 40, 64 or 128 characters of an MD5, SHA-1, SHA-256 or SHA-512 digest (the length of its algorithm when the algorithm is known). It is written in lower case. |

**Internal addresses are refused.** Network and HTTP HEAD tests refuse unspecified (`0.0.0.0/8`, `::`),
loopback (`127.0.0.0/8`, `::1`), link-local (`169.254.0.0/16`, `fe80::/10`), private (`10.0.0.0/8`,
`172.16.0.0/12`, `192.168.0.0/16`), unique local (`fc00::/7`), multicast (`224.0.0.0/4`, `ff00::/8`) and broadcast
(`255.255.255.255`) addresses, as well as the other ranges that are not globally reachable: shared address space
(`100.64.0.0/10`), benchmarking (`198.18.0.0/15`), documentation (`192.0.2.0/24`, `198.51.100.0/24`,
`203.0.113.0/24`, `2001:db8::/32`, `3fff::/20`), IETF protocol assignments (`192.0.0.0/24`, `2001::/23`), reserved
(`240.0.0.0/4`), discard-only (`100::/64`) and local-use NAT64 (`64:ff9b:1::/48`). An IPv6 address that embeds an
IPv4 address (IPv4-mapped, 6to4, NAT64) is judged by the embedded address too. An HTTP HEAD test also refuses:

- a single-label host name, or a name under a top-level label that only internal resolvers answer (`localhost`,
  `local`, `localdomain`, `internal`, `intranet`, `lan`, `home`, `corp`, `private`, `arpa`);
- a host name that resolves, from the OpenAEV server, to at least one internal address. The name is resolved again
  at approval, and a test whose host resolves to an internal address by then is dropped with the reason. A name
  that does not exist for the DNS of the OpenAEV server (a NXDOMAIN answer) is accepted: the request still goes
  through the egress proxy. The host names of a request are resolved in parallel, before the request is recorded or
  approved, and the server waits at most 5 seconds for their answers. A lookup that gets no answer by then, or fails
  (the DNS server answers with an error or cannot be reached, or the name exists without resolving), says nothing
  about the addresses of the name: its HTTP HEAD test does not run, and says why. The same goes for the host names
  of the platform: while one of them gets no answer, no network or HTTP HEAD test runs.

When the test runs, the egress proxy resolves the host name itself and may get another answer than the OpenAEV
server did: a name that did not resolve from the server, or one whose records changed since the approval. The
checks above cannot see that answer, so **configure the egress proxy to refuse internal destinations** (the ranges
above) **and the hosts of the platform**: it is the control that applies at execution, and OpenAEV cannot apply it in
its place, since only the proxy sees the address it connects to. The test never follows a redirect, so the only URL contacted is
the one that was checked.

**The hosts of the platform are refused.** No HTTP HEAD or network test targets OpenAEV itself (the hosts of its base
URL and agent URL), the OpenCTI the tenant is connected to, or the egress proxy. Their host names are resolved from
the OpenAEV server when a request is received and when it is approved, and a URL whose host is one of these names or
addresses, a URL whose host name resolves to one of these addresses, or a network test towards one of these addresses
is refused. A DNS resolution test of one of these host names is refused too; a DNS resolution test of another name is
not, even when that name resolves to an address of the platform, since a lookup never connects to the addresses it
gets back. A host that only contains one of these names, such as `openaev.example.com.attacker.net`, is not a host of
the platform.

**Malformed requests are not recorded.** Every reference of a request must be the STIX identifier of its object type,
as OpenCTI generates it: `indicator--`, `identity--` (the security platform) or `relationship--` (the deployment),
followed by a lower-case version 4 or 5 UUID; the request itself is named by its OpenCTI id, a UUID of the same form.
A request with any other value is acknowledged in error to OpenCTI, with the field and the value, and nothing is
recorded: its results could not be written back to OpenCTI. So is a request larger than 64 MiB, which is not read
past its first bytes, and a request whose bundle exceeds 16 Mi characters, 2,211 objects, 200 IOCs or 2,000
indicator-platform pairs.

**A refused value is shown, not repaired.** The request shows the indicator as *Refused* in the **Test that runs**
column, with the reason. A character outside the accepted set is written with its code point, so a character that
looks like another is visible: a typographic apostrophe appears as `U+2019`. The status of the request counts the
refused indicators. Each refusal is written to the platform log and, when audit logs are enabled
(`openaev.audit-logs.transports`, see [Configuration](../../../deployment/configuration.md)), recorded as an
`IOC_VALUE_REFUSED` audit event.

## Configure IOC validation

Go to **Settings > Customization > IOC validation**. You need the *Manage tenant settings* capability.

- **Allowed test kinds**: the tests that may run. Anything else is skipped.
- **Egress proxy URL**: an absolute http or https URL, without credentials (a URL such as `https://user:password@proxy` is refused, because the URL is shown in the settings and copied into the simulation injects). Required to allow HTTP HEAD tests. The proxy should refuse internal destinations: it resolves the host names of the tests when they run (see [Accepted indicator values](#accepted-indicator-values)).
- **Sinkhole address**: an IPv4 or IPv6 address, written with ASCII digits. When set, network tests connect to it instead of the indicator. A sinkhole that is a host of the platform (see [Accepted indicator values](#accepted-indicator-values)) is never connected to: the network tests do not run, and say why.
- **Network test port**: the TCP port of network tests, 443 by default.
- **Asset group running the tests**: the endpoints of this group run the benign tests. Approval is refused until
  an asset group is set and at least one of its endpoints has an active agent: without one no test would run, and
  the indicators would be reported as missed.

IOC validation also needs an OpenCTI connection for the tenant and the IOC validation connector registered in
OpenCTI. The IOC validation settings show a warning when either is missing, with the next step:

- **No OpenCTI connection**: add the OpenCTI connection of the tenant to the platform configuration (see
  [Configure OpenAEV to connect to OpenCTI](../../evaluate/xtm-suite-connector.md#step-1-configure-openaev-to-connect-to-opencti)),
  then restart OpenAEV. Users who cannot change the platform configuration are asked to contact their administrator.
- **Connector not registered**: OpenAEV registers the IOC validation connector in OpenCTI with the OpenCTI account of
  the connection. OpenCTI lists it as *OpenAEV IOC Validation*, followed by the tenant name when several tenants
  connect to OpenCTI. In OpenCTI, give that account the *Connector* role, with the *Update knowledge* and *Connectors API
  usage* capabilities.

Each field of the settings gives an example value, and **Learn more** opens this page.

![IOC validation settings: allowed test kinds, network safety and the asset group running the tests](assets/ioc-validation-settings.png)

While the connector is not registered in OpenCTI, requests and results wait, and the settings say so:

![IOC validation settings while the IOC validation connector is not registered in OpenCTI yet](assets/ioc-validation-settings-write-back-waiting.png)

Without an OpenCTI connection for the tenant, the settings point to the platform configuration:

![IOC validation settings without an OpenCTI connection for the tenant](assets/ioc-validation-settings-opencti-missing.png)

## Approve or reject a request

Go to **Atomic testings** and open the **IOC validations** tab. Each request shows the indicators, the security platforms, the test
that will run for each indicator and the indicators that are skipped, with the reason.

![IOC validations tab of Atomic testings with requests awaiting approval, running, partial and rejected](assets/ioc-validation-list.png)

Until OpenCTI sends a first request, the tab explains where requests come from:

![IOC validations tab before the first request](assets/ioc-validation-list-empty.png)

![A request awaiting approval, with its indicators, the test that runs for each one and a skipped indicator with its reason](assets/ioc-validation-awaiting-approval.png)

Until the request is approved, its results stay empty and the decision fields are hidden. When an indicator is
skipped because the safety settings do not allow its test, its row reads *Not allowed by the safety settings*
(the full reason shows on hover or keyboard focus), and administrators get a link to those settings next to it.

- **Approve and start the simulation** builds a scenario with benign injects per indicator on the endpoints with an active agent of the configured asset group and starts
  a simulation at once. The confirmation lists the tests that run and the security platforms expected to see them,
  as the server plans the approval when the confirmation opens: with the current safety settings, DNS answers and
  security platforms, which may drop a test the request page still shows. When nothing can run, it says why and the
  approval stays disabled. The approval runs only if it still plans what the confirmation showed, on the same
  targets: the same asset group and the same endpoints with an active agent. Otherwise, for instance when the asset
  group of the settings changed or an agent started or stopped meanwhile, it is refused and asks you to review the
  tests again. The injects target the approved endpoints themselves, each the ones of an operating system its test
  supports, and not the asset group: an endpoint added to the group after the approval never runs them. The injects
  run exactly what was approved: an inject of the validation simulation whose payload, arguments or targets are
  changed afterwards is not executed and ends with an error, and so is an inject added to the validation simulation
  after the approval, whatever it runs, and an IOC validation payload used anywhere else (an atomic testing, another
  simulation).
- **Reject** closes the request without running anything. The optional reason is reported to OpenCTI.

![Approval confirmation listing the tests that run once approved and the security platforms](assets/ioc-validation-approve-dialog.png)

![Rejection confirmation with the optional reason reported to OpenCTI](assets/ioc-validation-reject-dialog.png)

## Results

When the simulation ends, every indicator and security platform pair gets an outcome:

| Outcome   | Meaning                                                        |
|-----------|----------------------------------------------------------------|
| Prevented | The security platform blocked the benign test.                 |
| Detected  | The security platform raised an alert for the benign test.     |
| Missed    | The security platform did neither.                             |
| Error     | The test could not run, so the pair could not be evaluated. An inject whose execution failed is an error even once its expectations expire: a miss needs the test executed, at least on part of its targets. |

The request ends in status *Completed* when every pair was evaluated, *Partial* when some pairs ended in error,
and *Failed* when no pair could be evaluated. A running validation is closed after seven days even if the
simulation never ends.

Validation simulations are kept out of the coverage statistics: their expectations never count in the
security coverage matrix of the home page, in the ATT&CK coverage, or in the custom dashboards, so a benign
test never changes how well your attack patterns look covered. A simulation is a validation simulation because
an IOC validation launched it, not because of its category: editing its category does not bring it into the coverage.

![A partial result: outcomes per indicator and security platform, with the reason of each outcome](assets/ioc-validation-result.png)

The results are sent back to OpenCTI, where they update the validation status of each deployment of the
indicator; **Open in OpenCTI** leads to the request there. See the
[OpenCTI documentation](https://docs.opencti.io/latest/usage/dissemination-assurance/).
