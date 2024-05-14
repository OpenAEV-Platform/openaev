# Scenario generation from OpenCTI

Creating a [scenario](scenario.md) can be a complex task, especially when aiming to build one that is meaningful and relevant to the specific threats facing your organization. To streamline this process and ensure that scenarios are closely aligned with your threat landscape, you can leverage the integration between OpenCTI and OpenBAS.

<!-- screenshot of the OpenBAS integration widget in OpenCTI without results -->

When generating a scenario from OpenCTI, a scenario is created and can be accessed from the scenarios screen. The scenario name will include a reference to OpenCTI, indicating its origin. This scenario will automatically contain relevant sequences of injects based on the threat context identified in OpenCTI.

However, it's important to review and potentially customize the scenario to ensure it meets your organization's specific requirements. Additionally, you'll need to select appropriate [targets](targets.md) for the injects within the scenario.

Once you've finalized the scenario, you can schedule your simulation as you would for any other scenario. The overall results of the simulation will also be available directly within OpenCTI, providing insights into the threat context upon which the scenario is based.

<!-- screenshot of the OpenBAS integration widget in OpenCTI with results -->