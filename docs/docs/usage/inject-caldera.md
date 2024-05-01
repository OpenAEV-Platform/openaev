# Caldera injects

The [Caldera framework](https://caldera.mitre.org/), developed by MITRE, is a powerful framework designed to simulate cyberattacks. It enables security teams to conduct realistic and controlled simulations of adversary behavior, reducing the amount of time and resources needed for routine cybersecurity testing.

## Injects

In OpenBAS, the Caldera framework has been fully integrated, offering users access to a comprehensive library of injects for conducting simulation exercises. With this integration, users can leverage the extensive capabilities of Caldera within OpenBAS.

Caldera offers 1600+ [abilities](https://caldera.readthedocs.io/en/latest/Learning-the-terminology.html#abilities-and-adversaries), covering the full range of ATT&CK tactics and techniques. These capabilities equip security teams with an extensive toolkit to simulate various threats and assess defense mechanisms effectively.

<!-- screenshot of the window "Integrations > Injectors > Caldera" with a nice list of injects -->


## Behavior

Injects within the Caldera framework can be played on both individual [Endpoints and Asset groups](assets). Prior to playing injects, [Caldera agents](injectors.md#agent-section) need to be installed on the target machines to enable interaction with the platform.

Once the agents are deployed, simulations with Caldera injects can be executed. The platform will contact the agent to start the ability. Subsequently, the agents will report the results to OpenBAS. Below is the workflow illustrating the behavior of injects.

![Async workflow](assets/inject-caldera.png)


## Configuration variables

<!-- cette partie n'est plus d'actualité maintenant que Caldera est built-in, non ? à supprimer ? Si ce n'est pas à supprimer, c'est à migrer dans une page de configuration, pas dans une page "usage" -->

Below are the properties you'll need to set for OpenBAS:

| Property                 | application.properties         | Docker environment variable      | Mandatory | Description                                              |
|--------------------------|--------------------------------|----------------------------------|-----------|----------------------------------------------------------|
| Enable Caldera collector | injector.caldera.enable        | `INJECTOR_CALDERA_ENABLE`        | Yes       | Enable the Caldera injector.                             |
| Injector ID              | injector.caldera.id            | `INJECTOR_CALDERA_ID`            | Yes       | The ID of the injector.                                  |
| Collector IDs            | injector.caldera.collector-ids | `INJECTOR_CALDERA_COLLECTOR_IDS` | Yes       | The collector IDs compatible with the injection process. |
| Caldera URL              | injector.caldera.url           | `INJECTOR_CALDERA_URL`           | Yes       | The URL of the Caldera instance.                         |
| Caldera API Key          | injector.caldera.api-key       | `INJECTOR_CALDERA_API-KEY`       | Yes       | The API Key for the rest API of the Caldera instance.    |

