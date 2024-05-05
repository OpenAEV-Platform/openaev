# Atomic testing

!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).


When clicking on Atomic testing in the left menu, you access to the list of all atomic testings ever launched in the platform.

Atomic testing is a great way to simulate a singular attack technique you are particulary interested in, and test immediately your capability to prevent and detect it.

You can search for a specific one by name.

The presented list allows you to easily see global scores of all your recent atomic testings.

![Example of Atomic testing](assets/atomic_list.png)

## Create an Atomic testing

An atomic testing is essentially the simulation of a single inject, against a selection of targets (Players, Teams, Assets, Assets Group) with assorted expectations.

By clicking on the + button at the bottom right of the screen, you enter the atomic testing creation workflow.

On the left of the creation screen is the list of all available Inject you can play for atomic testing. Logos on the left of each line indicates which Injector is associated with each inject.

Depending on your integrations, this list can be long. You can filter the list by compatible platforms or by Mitre Att&ck tactics.By clicking on the "Att&CK" logo near the search bar, you can also filter by selecting a precise Mitre Att&ck techniques.

When selecting an inject on the left, the form on the right populates itself with a by-default title and propose you to define when the inject should be played after the launch of the atomic testing. You can keep it to 0.

By clicking on Inject content, you can define now or later the targeted assets or players, needed configurations, and the assorted expectations.

The "available variables" button helps you to use already defined variables into compatible fields.

## Atomic testing screens

Details of an Atomic testing is composed of three parts:
- a header allowing to launch the test, see its state and update/delete it.
- an Overview screen to eaily see the results of the test.
- an execution details screen to see expectations of the test and investigate on execution logs

### Overview

The first screen displayed when you click on a specific Atomic testing from the list is a breakdown of your security posture against this test. 

As for Simulation and Scenario, Results are broken down into:
- Prevention: the ability of your security posture to prevent the inject
- Detection: the ability of your security posture to detect the inject
- Human response: the ability of your security teams to react as intented facing the inject

Big metrics on top of the screen sum up the expectations' result of all targets. 

The list of targets on the left allows you to easily see the result per Target, and for example investigate further why a specific Asset have failed the test.

For a selected target, you can on the right the timeline of the test against the target and the assorted results. The result logs are also displayed.

![Atomic testing Overview with Results](assets/atomic_details_overview.png)

### Execution details

On this screen, you can retrieve details about the atomic testing and its expectations.

You can also see the raw execution logs of the [Injector](injectors.md) responsible for the test execution.

![Execution trace of a successfull atomic testing](assets/atomic_testing_execution_details.png)


