# Scenario generation from OpenCTI

Creating a [scenario](../scenario.md) can be a complex task, especially when aiming to build one that is meaningful and
relevant to the specific threats facing your organization. To streamline this process and ensure that scenarios are
closely aligned with your threat landscape, you can leverage the integration between OpenCTI and OpenBAS.

This integration works across multiple entities:

- Reports
- Grouping
- Incident Response
- Request For Information
- Request For Takedown

![simulate button](assets/simulate-btn.png)

When you click on the simulate button, you’ll have two options:

- Generate a scenario based on technical injects
- Generate a scenario based on email injects, using AI to automatically generate email content

It’s essential to understand that a scenario creation for these entities relies on matching TTPs between OpenCTI and
OpenBAS. You’ll need to ensure that the TTPs in both platforms are aligned. For instance, if your report contains the
TTP T1059.001, a scenario can be created with an inject, provided OpenBAS also includes T1059.001.

When generating a scenario from OpenCTI, a scenario is created and can be accessed from the scenarios screen. The
scenario name will include a reference to OpenCTI, indicating its origin. This scenario will automatically contain
relevant sequences of injects based on the threat context identified in OpenCTI.

However, it's important to review and potentially customize the scenario to ensure it meets your organization's specific
requirements. Additionally, you'll need to select appropriate [targets](../targets.md) for the injects within the
scenario.

Once you've finalized the scenario, you can schedule your simulation as you would do for any other scenarios. The overall
results of the simulation will also be available directly within OpenCTI, providing insights into the threat context
upon which the scenario is based.

![Simulate results](assets/simulate-result.png)
