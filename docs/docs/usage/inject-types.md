# Inject types

There are different types of injector in OpenBAS.

<a id="manual-section"></a>

## Manual action reminders

Manual action reminders are injects designed to prompt animation team to perform specific actions manually. It allows to
place in the timeline a stimulus to be produced manually, outside the platform (e.g. simulated a call from a journalist
on the switchboard telephone). These reminders ensure that critical tasks are completed as part of the simulation,
enhancing the accuracy and realism of the exercise.

The inject associated with this type is referred to as `Manual`. To be able to log events not directly related to an
email or a sms, you can attach manual expectation to this events (
see [Manual Expectations](https://docs.openbas.io/latest/usage/expectations/?h=manual#manual-expectations)).

### Example of a manual inject:

- "A crisis cell has been put together"

### Manual expectations:

- "The team responded to the journalist's inquiry."
- "Analyze the situation and identify the key issues."
- "Anticipate future developments."
- "Determine the necessary actions to take."
- "Coordinate and communicate with both internal and external stakeholders."
- "Provide regular updates on the ongoing situation."

## Direct contact

Injects for direct contact allow sending emails or SMS messages to players. These injects assess the organization's
response to communication-based threats, such as phishing attempts, social engineering attacks, or emergency
notifications. They can also assess crisis management, including responses to internal information requests or
management pressure.

Here's the list of injects linked to this category:

- `Send a SMS`: enables sending SMS messages.
- `Send individual mails`: enables sending emails to individuals separately.
- `Send multi-recipients mail`: enables sending emails to a group of people (each recipient can see the other
  recipients).

<a id="media-pressure-section"></a>

## Media pressure

Injects simulating public communications involve the publication of articles, social media posts, or other fake
announcements. These injects replicate scenarios where public disclosure of information or events affects an
organization's reputation or operational continuity.

The inject associated with this type is referred to as `Publish channel pressure`.

<a id="challenge-section"></a>

## Challenges

Challenge injects are set to test participants' skills and response capabilities by publishing challenges. These injects
present scenarios or tasks that require active participation and problem-solving, allowing administrators to evaluate
players.

The inject associated with this type is referred to as `Publish challenges`.

<a id="http-section"></a>

## HTTP requests

HTTP request injects are used to forge HTTP requests to a third party services in order to perform actions outside the
platform (e.g. API call to an EDR). These injects enable the platform to communicate with external services, gather
information, or trigger specific actions via HTTP protocols.

HTTP requests GET, POST, and PUT, can be sent. The corresponding injects are named `HTTP Request - \<request type>`.

<a id="integration-section"></a>

## Integrations with Agents and CyberRanges

Injects executed on remote systems are facilitated by Injectors like [Caldera](inject-caldera.md) or Airbus CyberRange.
These actions simulate real-world attack techniques, allowing administrators to gauge the effectiveness of their
security posture in response to various technical actions attackers may take.

There are over 1,700 such injects covering all the TTPs in the MITRE ATT&CK matrix.

## Nmap

### Behavior

The injector enables new inject contracts, supporting the following Nmap scan types:

#### Nmap - FIN Scan

Command executed:

```shell
nmap -Pn -sF
```

#### Nmap - SYN Scan

Command executed:

```shell
nmap -Pn -sS
```

#### Nmap - TCP Connect Scan

Command executed:

```shell
nmap -Pn -sT
```

#### Target Selection

The targets vary based on the provided options:

If type of targets is Assets:

| Targeted property | Asset property       | 
|-------------------|----------------------|
| Seen IP           | Seen IP address      |
| Local IP (first)  | IP Addresses (first) |
| Hostname          | Hostname             |

If type of targets is Manual:

- Hostnames or IP addresses are provided directly as comma-separated values.

### Resources

- Official Nmap Documentation: https://nmap.org/docs.html
- Options Explanation:
  - -Pn: Host Discovery
  - -sS: SYN Scan
  - -sT: TCP Connect Scan
  - -sF: FIN Scan

## Nuclei

### Behavior

The Nuclei injector supports contract-based scans by dynamically constructing and executing Nuclei commands based on provided tags or templates.

#### Supported Contracts

The following scan types are supported via `-tags`:

- Cloud
- Misconfiguration
- Exposure
- Panel
- XSS
- WordPress
- HTTP

The CVE scan uses the `-tags cve` argument and enforces JSON output.

The Template scan accepts a manual template via `-t <template>` or `template_path`.

#### Target Selection

Targets are selected based on the `target_selector` field.

##### If target type is **Assets**:

| Selected Property | Uses Asset Field         |
|-------------------|-------------------------|
| Seen IP           | endpoint_seen_ip         |
| Local IP          | First IP from endpoint_ips |
| Hostname          | endpoint_hostname       |

##### If target type is **Manual**:

Hostnames or IP addresses are provided directly as comma-separated values.

#### Results

Scan results are categorized into:

- **CVEs** (based on template classifications)
- **Other vulnerabilities** (general issues found)

If no vulnerabilities are detected, the injector will clearly indicate this with a **"Nothing Found"** message.

### Resources

* [Nuclei Documentation](https://github.com/projectdiscovery/nuclei)
* [Official Nuclei Templates](https://github.com/projectdiscovery/nuclei-templates)
* http://testphp.vulnweb.com/ is a safe, intentionally vulnerable target provided by Acunetix for security testing purposes
