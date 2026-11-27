package io.openaev.executors;

import static io.openaev.executors.ExecutorHelper.ARCH_VARIABLE;
import static io.openaev.executors.ExecutorHelper.UNIX_ARCH;
import static io.openaev.executors.ExecutorHelper.WINDOWS_ARCH;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Endpoint.PLATFORM_ARCH;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;

/** Where the implant command takes the endpoint architecture from. */
public enum ArchSource {
  /** The architecture the EDR reported at sync. */
  AGENT {
    @Override
    String templateArch(Agent agent) {
      return ((Endpoint) agent.getAsset()).getArch().name();
    }

    @Override
    String adapt(String template, PLATFORM_TYPE platform) {
      return template;
    }
  },
  /** The EDR does not report it: the command detects it on the endpoint before downloading. */
  ENDPOINT {
    @Override
    String templateArch(Agent agent) {
      return PLATFORM_ARCH.x86_64.name();
    }

    @Override
    String adapt(String template, PLATFORM_TYPE platform) {
      String x86 = PLATFORM_ARCH.x86_64.name();
      // The backtick escapes the "?" that follows the architecture in the Windows download URL
      return platform == PLATFORM_TYPE.Windows
          ? WINDOWS_ARCH + template.replace(x86, ARCH_VARIABLE + "`")
          : UNIX_ARCH + template.replace(x86, ARCH_VARIABLE);
    }
  };

  abstract String templateArch(Agent agent);

  abstract String adapt(String template, PLATFORM_TYPE platform);
}
