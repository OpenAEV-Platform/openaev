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
- **Only the test kinds you allow run.** By default, only *DNS resolution* is allowed. Every other kind must be
  enabled explicitly in the settings.
- **The real indicator is never downloaded or executed.** File indicators are replaced by a benign text file that
  only carries the file name, and hashes are only written to a log line.
- **No contact with adversary infrastructure by default.** DNS resolution never connects to the resolved
  address. Network tests can be redirected to a sinkhole you control, and HTTP tests can only be allowed once an
  egress proxy is configured.
- **Skipped indicators are explained.** When a test kind is not allowed or does not apply to an indicator, the
  request shows why before anyone approves it.

## Benign tests

| Test kind        | Applies to                  | What it does                                                                                   |
|------------------|-----------------------------|------------------------------------------------------------------------------------------------|
| DNS resolution   | Domain names, host names    | Resolves the name. No connection is made to the resolved address.                              |
| Network traffic  | IPv4 and IPv6 addresses     | Opens a TCP connection, to the sinkhole when one is set, and closes it at once without payload. |
| HTTP HEAD request| URLs                        | Sends an HTTP HEAD request through the egress proxy. No content is downloaded.                 |
| Benign file drop | Files and artifacts         | Writes a benign text file named after the indicator file name, in a temporary directory created for the test (`openaev-ioc-validation-<run>`). The cleanup removes the file (never a directory that carries its name), then the directory only when it is empty: anything else written in it is left in place. Each inject gets its own run identifier; an inject without one is not executed. Before writing or removing anything, the endpoint checks that the run identifier is 32 lowercase hexadecimal characters and that the file name is a plain file name, so files of other applications are never touched. On Windows, a file name that Windows cannot create (a reserved device name such as `CON.txt` or `COM1`, or a name ending with a dot or a space) stops the test with an error before any file operation; the same name still runs on Linux and macOS. |
| Benign log line  | Any indicator, such as hashes | Writes a log line containing the indicator value.                                            |

Each test carries a **Detection** and a **Prevention** expectation for every security platform of the request.

After an upgrade, the benign test payloads are brought to the current version the next time a validation is
approved. Until then, a file drop approved before the upgrade is refused when the agent asks for it, rather than run
with the earlier version of the test: approve a new validation to run it.

## Configure IOC validation

Go to **Settings > Customization > IOC validation**. You need the *Manage tenant settings* capability.

- **Allowed test kinds**: the tests that may run. Anything else is skipped.
- **Egress proxy URL**: an absolute http or https URL. Required to allow HTTP HEAD tests.
- **Sinkhole address**: an IPv4 or IPv6 address. When set, network tests connect to it instead of the indicator.
- **Network test port**: the TCP port of network tests, 443 by default.
- **Asset group running the tests**: the endpoints of this group run the benign tests. Approval is refused until
  an asset group is set.

IOC validation also needs an OpenCTI connection for the tenant and the IOC validation connector registered in
OpenCTI. The IOC validations screen shows a warning when either is missing.

## Approve or reject a request

Go to **Atomic testings** and open the **IOC validations** tab. Each request shows the indicators, the security platforms, the test
that will run for each indicator and the indicators that are skipped, with the reason.

- **Approve and start the simulation** builds a scenario with one benign inject per indicator on the configured asset group and starts
  a simulation at once.
- **Reject** closes the request without running anything. The optional reason is reported to OpenCTI.

## Results

When the simulation ends, every indicator and security platform pair gets an outcome:

| Outcome   | Meaning                                                        |
|-----------|----------------------------------------------------------------|
| Prevented | The security platform blocked the benign test.                 |
| Detected  | The security platform raised an alert for the benign test.     |
| Missed    | The security platform did neither.                             |
| Error     | The test could not run, so the pair could not be evaluated.    |

The request ends in status *Completed* when every pair was evaluated, *Partial* when some pairs ended in error,
and *Failed* when no pair could be evaluated. A running validation is closed after seven days even if the
simulation never ends.

The results are sent back to OpenCTI, where they update the validation status of each deployment of the
indicator. See the [OpenCTI documentation](https://docs.opencti.io/latest/usage/dissemination-assurance/).
