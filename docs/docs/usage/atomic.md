# Atomic testing

When clicking on Atomic testing in the left menu, you access to the list of all atomic testings ever launched in the
platform.

Atomic testing is a great way to simulate a singular attack technique you are particularly interested in, and test
immediately your capability to prevent and detect it.

The presented list allows you to easily see global scores of all your recent atomic testings.

![Example of Atomic testing](assets/atomic_list.png)

## Search the list
- [Injects: Search and Filters](inject-result-list.md/#executed-injects-search-and-filters)

## Create an Atomic testing

An atomic testing is essentially the simulation of a single inject, against a selection of targets (Players, Teams,
Assets, Assets Group) with assorted expectations.

By clicking on the + button at the bottom right of the screen, you enter the atomic testing creation workflow.

On the left of the creation screen is the list of all available injects you can play for atomic testing. Logos on the
left of each line indicates which Injector is associated with each inject.

Depending on your integrations, this list can be long. You can filter the list by kill chain phase, injector, compatible
platforms or MITRE ATT&CK tactics. By clicking on the "Att&CK" logo near the search bar, you can also filter by selecting
a precise MITRE ATT&CK techniques.

When selecting an inject on the left, the form on the right populates itself with a by-default title and propose you to
define when the inject should be played after the launch of the atomic testing. You can keep it to 0.

By clicking on Inject content, you can define now or later the targeted assets or players, needed configurations, and
the assorted expectations.

The "available variables" button helps you to use already defined variables into compatible fields.

## Schedule a recurring atomic testing

Atomic testings can be launched once, or scheduled to run on a recurring basis. Recurring
executions are the easiest way to continuously validate that a prevention or detection capability
keeps working over time: the same technique is replayed automatically and every run produces fresh
results and expectations.

To schedule an atomic testing:

1. Open the atomic testing and click the scheduling action in the header.
2. Choose a frequency: **once**, **hourly**, **daily**, **weekly** or **monthly**, with the
   execution time and, for weekly and monthly frequencies, the day it should run.
3. Define the start date and, optionally, an end date after which the recurrence stops.
4. Save. The next planned execution is displayed on the atomic testing.

The platform checks for due recurring atomic testings every minute and launches them
automatically. Each execution behaves exactly like a manual relaunch: previous results are
archived and new expectations are created for all targets.

!!! note

    Scheduling an atomic testing requires the permission to launch it. The recurrence is stored
    as a cron expression together with the start and end dates, and can be updated or removed at
    any time from the same dialog.

## Atomic testing screens

Details of an Atomic testing is composed of three parts:

- A header with the title, a tooltip showing details about the inject (status, tags, and description), pie charts
  summarizing the results, and actions like launch, update, delete, and export.
- An overview screen that gives a quick summary of test results across all targets.
- An execution details screen that shows test expectations and detailed execution traces.

![Atomic testing Overview with Results](assets/atomic_details_overview.png)
![Atomic testing Overview with Results](assets/atomic_details_tooltip.png)

- [Overview](inject-result.md/#overview)
- [Findings](inject-result.md/#findings)
- [Inject execution details](inject-result.md/#execution-details)
- [Threat Arsenal Action info](inject-result.md/#threat-arsenal-action-info)
- [Remediation](inject-result.md/#remediations-ee)