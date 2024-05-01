# Atomic testing

!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).


When clicking on Atomic testing in the left menu, you access to the list of all atomic testings ever launched in the platform.

Atomic testing is a great way to simulate a singular attack technique you are particulary interested in, and test immediately your capability to prevent and detect it.

You can search for a specific one by name.

The presented list allows you to easily see global scores of all your recent atomic testings.

## Create an Atomic testing

An atomic testing is essentially the simulation of a single inject, against a selection of targets (Players, Teams, Assets) with assorted expectations.

By clicking on the + button on the nbottom right of the screen, you enter the atomic testing creation workflow.

<!-- to be completed after Samuel work on the new creation workflow -->

## Atomic testing screens

Details of an Atomic testing is composed of threee parts:
- a header allowing to launch the test, see its state and update/delete it.
- a targets response screen to eaily see the results of the test.
- an inject details screen to see expectations of the test and investigate on execution logs

### Targets response

The first screen displayed when you click on a specific Atomic testing from the list is a breakdown of your security posture against this test. 

As for Simulation and Scenario, Results are broken down into:
- Prevention: the ability of your security posture to prevent the inject
- Detection: the ability of your security posture to detect the inject
- Human response: the ability of your security teams to react as intented facing the inject

Big metrics on top of the screen sum up the expectations' result of all targets. 

The list of targets on the left allows you to easily see the result per Target, and for example investigate further why a specific Asset have failed the test.

<!-- screenshot of a targets response screen of an inject played against multiple targets -->

### Inject details

On this screen, you can retrieve details about the atomic testing and its expectations.

You can also see the raw execution logs of the [Injector](injectors.md) responsible for the test execution and why it may have failed.





