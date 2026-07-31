# Injects and Expectations

Evaluating security posture in OpenAEV means confronting events (aka [Injects](../evaluate/injects/inject-overview.md)) with [Expectations](../evaluate/expectations/expectations.md).

## Injects

Threats are the results of actions by threat actors, and a combination of intent, capability and opportunity. In OpenAEV, simulating threats and their attack capabilities involves executing Injects targeting [Players](../build/people.md) and [Assets](../build/assets.md).

Injects can be technical, emulating actions attackers might take on an endpoint, and non-technical, representing interactions with Players or impactful contextual events during a crisis (such as media inquiries by phone following a data breach). They are always triggered at a specific point in time but it is possible to execute them only if one or multiple conditions are met.

## Expectations

Each Inject is associated with Expectations. Expectations outline the anticipated outcomes from security systems and Teams in response to attacker actions or contextual events.

Expectations can be about:

- Prevention: ensuring that the security posture can prevent the attacker's actions.
- Detection: ensuring that the security posture can detect the attacker's actions.
- Vulnerability: ensuring that the security posture can detect common vulnerabilities and exposures (CVEs).
- Human response: ensuring that Teams react appropriately according to defined security processes.

The collection and concatenation of Expectations results, broken down per type, allows to assess the security posture against a threat context. This provides insights to identify areas for improvement. Expectations can also be used as conditions for the execution of other Injects.

## Expectations drift

Injects inherit the predefined Expectations of their Injector contract at creation time and keep them as-is afterwards, even when the contract later evolves (for example when an integration updates the validation requirements of its contracts). This divergence is called expectations drift.

When at least one Inject of a Scenario, a Simulation, or an Atomic Test carries Expectations that no longer match its contract, a warning indicator appears in the header with the number of drifted Injects. A drifted Inject is not an error: Expectations may have been customized on purpose. The indicator only surfaces that the underlying contracts evolved, so you can decide whether to realign.

The **Realign expectations** quick action overwrites the stored Expectations of every drifted Inject with the current predefined Expectations of its contract. For Scenarios and Simulations, the realignment runs as a background operation whose progress is tracked in the header. Customizations made to the drifted Injects' Expectations are replaced by the contract defaults.
