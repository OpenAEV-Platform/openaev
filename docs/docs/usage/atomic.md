# Atomic testing

When clicking on Atomic testing in the left menu, you access to the list of all atomic testings ever launched in the
platform.

Atomic testing is a great way to simulate a singular attack technique you are particulary interested in, and test
immediately your capability to prevent and detect it.

The presented list allows you to easily see global scores of all your recent atomic testings.

![Example of Atomic testing](assets/atomic_list.png)

## Search the list

You can search the list using the name or one the filters. Here are the available filters for this list.

![Atomic testing filters list](assets/atomic_list_filter_attributes.png)

Once you choose the attribute you want to apply a filter on, you can choose the operator.

![Atomic testing filters operators](assets/atomic_list_filter_operator.png)

Then you have the list of values for the attributes you choose.

![Atomic testing filters attributes values](assets/atomic_list_filter_elements.png)

Here is the list once you apply the filter.

![Atomic testing filters values](assets/atomic_list_filter_result.png)

## Create an Atomic testing

An atomic testing is essentially the simulation of a single inject, against a selection of targets (Players, Teams,
Assets, Assets Group) with assorted expectations.

By clicking on the + button at the bottom right of the screen, you enter the atomic testing creation workflow.

On the left of the creation screen is the list of all available Inject you can play for atomic testing. Logos on the
left of each line indicates which Injector is associated with each inject.

Depending on your integrations, this list can be long. You can filter the list by kill chain phase, injector, compatible
platforms or Mitre Att&ck tactics.By clicking on the "Att&CK" logo near the search bar, you can also filter by selecting
a precise Mitre Att&ck techniques.

When selecting an inject on the left, the form on the right populates itself with a by-default title and propose you to
define when the inject should be played after the launch of the atomic testing. You can keep it to 0.

By clicking on Inject content, you can define now or later the targeted assets or players, needed configurations, and
the assorted expectations.

The "available variables" button helps you to use already defined variables into compatible fields.

## Atomic testing screens

Details of an Atomic testing is composed of three parts:

- A header with the title, a tooltip showing details about the inject (status, tags, and description), pie charts
  summarizing the results, and actions like launch, update, delete, and export.
- An overview screen that gives a quick summary of test results across all targets.
- An execution details screen that shows test expectations and detailed execution traces.

![Atomic testing Overview with Results](assets/atomic_details_overview.png)
![Atomic testing Overview with Results](assets/atomic_details_tooltip.png)

### Overview

The first screen displayed when you click on a specific Atomic testing from the list is a breakdown of your security
posture against this test.

As for Simulation and Scenario, Results are broken down into:

- Prevention: the ability of your security posture to prevent the inject
- Detection: the ability of your security posture to detect the inject
- Human response: the ability of your security teams to react as intented facing the inject
- Vulnerability: the ability of your security posture to detect common vulnerabilities and exposures (CVEs)

At the top, big metrics summarize how all targets performed. On the left, a list of targets lets you quickly check
results for each one. When you select a target, the right side shows a timeline of the test and its results, including
execution logs.

![Atomic testing Overview with Results](assets/atomic_testing_overview.png)
![Atomic testing Overview with Results](assets/atomic_testing_overview_expectations.png)

### Findings

The Findings screen displays what was detected during the inject, based on the output parser in the payload. You can
filter findings by name, type, creation date, target, value, or tag.

![Atomic testing Overview with Results](assets/atomic_testing_findings.png)

### Execution details

This screen shows the full trace of the inject’s execution, including logs and status information.

![Execution trace of a successfull atomic testing](assets/atomic_testing_execution_details.png)

### Payload info

This screen is available for technical injects only. You can see the details of the payload related to the test.

![Payload info of atomic testing](assets/atomic_testing_payload_info.png)

### Remediations (EE)

This screen is available for technical injects only. It displays remediation content related to the executed payload,
specifically focused on detection logic. You will see one Remediation tab per collector available in the platform.

![Detection Remediations-no-present](assets/atomic_testing_detection_remediation_no_present.png)
![Detection Remediations](assets/atomic_testing_detection_remediation.png)
