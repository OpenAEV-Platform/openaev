# IOC validation from OpenCTI dissemination assurance

## Overview

OpenCTI tracks where each indicator is deployed: which security platforms received it, and whether they
acknowledged it. IOC validation answers the next question: does the deployed indicator actually trigger a
detection or a prevention on those platforms?

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
- **Skipped indicators are explained.** When a test kind is not allowed or does not apply to an indicator, the
  request shows why before anyone approves it.

## Benign tests

| Test kind        | Applies to                  | What it does                                                                                   |
|------------------|-----------------------------|------------------------------------------------------------------------------------------------|
| DNS resolution   | Domain names, host names    | Resolves the name. No connection is made to the resolved address; the resolver may query the name servers of the domain. |
| Network traffic  | IPv4 and IPv6 addresses     | Opens a TCP connection, to the sinkhole when one is set, and closes it at once without payload. |
| HTTP HEAD request| URLs                        | Sends an HTTP HEAD request through the egress proxy. No content is downloaded.                 |
| Benign file drop | Files and artifacts         | Writes a benign text file named after the indicator file name, in a temporary directory created for the test (`openaev-ioc-validation-<run>`). The cleanup removes the file only while it still holds the surrogate of its run (never a directory that carries its name, nor a file that was there before or was put in its place), then the directory only when it is empty: anything else written in it is left in place. Neither the drop nor the cleanup ever follows a link: a run directory or a file path replaced by a symbolic link, a junction or another reparse point stops the drop with an error and is left alone by the cleanup. The file is always created as a new file: a file already at its path is never written over, and the test then ends with an execution error rather than a missed result, as does any other write failure. Each inject gets its own run identifier; an inject without one is not executed. Before writing or removing anything, the endpoint checks that the run identifier is 32 lowercase hexadecimal characters and that the file name is a plain file name, so files of other applications are never touched. On Windows, a file name that Windows cannot create (a reserved device name such as `CON.txt` or `COM1`, or a name ending with a dot or a space) stops the test with an error before any file operation; the same name still runs on Linux and macOS. |
| Benign log line  | Any indicator, such as hashes | Writes a log line containing the indicator value to the system log (syslog through `logger` on Linux and macOS, the Application event log on Windows). An endpoint whose system log cannot be written ends the test with an execution error; the line is never written to a file instead. |

Each test carries a **Detection** and a **Prevention** expectation for every security platform of the request.

After an upgrade, the benign test payloads are brought to the current version the next time a validation is
approved. Until then, a file drop approved before the upgrade is refused when the agent asks for it, rather than run
with the earlier version of the test: approve a new validation to run it.

## Configure IOC validation

Go to **Settings > Customization > IOC validation**. You need the *Manage tenant settings* capability.

- **Allowed test kinds**: the tests that may run. Anything else is skipped.
- **Egress proxy URL**: an absolute http or https URL, without credentials (a URL such as `https://user:password@proxy` is refused, because the URL is shown in the settings and copied into the simulation injects). Required to allow HTTP HEAD tests.
- **Sinkhole address**: an IPv4 or IPv6 address. When set, network tests connect to it instead of the indicator.
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
  the connection. In OpenCTI, give that account the *Connector* role, with the *Update knowledge* and *Connectors API
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
skipped because the safety settings do not allow its test, administrators get a link to those settings next to the reason.

- **Approve and start the simulation** builds a scenario with one benign inject per indicator on the configured asset group and starts
  a simulation at once. The confirmation lists the tests that run and the security platforms expected to see them.
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
test never changes how well your attack patterns look covered.

![A partial result: outcomes per indicator and security platform, with the reason of each outcome](assets/ioc-validation-result.png)

The results are sent back to OpenCTI, where they update the validation status of each deployment of the
indicator; **Open in OpenCTI** leads to the request there. See the
[OpenCTI documentation](https://docs.opencti.io/latest/usage/dissemination-assurance/).
