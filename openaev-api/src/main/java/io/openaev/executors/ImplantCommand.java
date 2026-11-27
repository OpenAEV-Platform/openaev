package io.openaev.executors;

import static io.openaev.executors.ExecutorHelper.IMPLANT_BASE_NAME;
import static io.openaev.executors.ExecutorHelper.IMPLANT_LOCATION_UNIX;
import static io.openaev.executors.ExecutorHelper.IMPLANT_LOCATION_WINDOWS;
import static io.openaev.executors.ExecutorHelper.replaceArgs;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * The plain-text command that downloads the implant and runs the inject on an endpoint managed by
 * an EDR executor. Each executor only encodes it in its own format.
 *
 * @param archSource where the command takes the endpoint architecture from
 * @param windowsTemplatePrefix executor name prefixing the Windows template key, for executors
 *     whose Windows template differs (scheduled-task launch); {@code null} otherwise
 */
public record ImplantCommand(ArchSource archSource, String windowsTemplatePrefix) {

  public String build(
      OpenAEVConfig config,
      Injector injector,
      Inject inject,
      Agent agent,
      PLATFORM_TYPE platform,
      String token) {
    String template = injector.getExecutorCommands().get(templateKey(platform, agent));
    String command =
        replaceArgs(
            platform,
            archSource.adapt(template, platform),
            inject.getId(),
            agent.getId(),
            inject.getTenant().getId(),
            token,
            config.getBaseUrlForAgent(),
            Integer.toString(config.getLogsMaxSize()),
            Boolean.toString(config.isUnsecuredCertificate()),
            Boolean.toString(config.isWithProxy()));
    return withImplantLocation(command, platform);
  }

  private String templateKey(PLATFORM_TYPE platform, Agent agent) {
    String key = platform.name() + "." + archSource.templateArch(agent).name();
    return platform == PLATFORM_TYPE.Windows && windowsTemplatePrefix != null
        ? windowsTemplatePrefix + "." + key
        : key;
  }

  private static String withImplantLocation(String command, PLATFORM_TYPE platform) {
    String implantDirectory = IMPLANT_BASE_NAME + UUID.randomUUID();
    if (platform == PLATFORM_TYPE.Windows) {
      return command.replaceFirst(
          "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
          Matcher.quoteReplacement(
              "$location="
                  + IMPLANT_LOCATION_WINDOWS
                  + implantDirectory
                  + "\";md $location -ea 0;[Environment]::CurrentDirectory"));
    }
    return command.replaceFirst(
        "\\$?x=.+location=.+;filename=",
        Matcher.quoteReplacement(
            "location="
                + IMPLANT_LOCATION_UNIX
                + implantDirectory
                + ";mkdir -p $location;filename="));
  }
}
