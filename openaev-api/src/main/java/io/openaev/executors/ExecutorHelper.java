package io.openaev.executors;

import io.openaev.database.model.Endpoint.PLATFORM_TYPE;

public class ExecutorHelper {

  public static final String WINDOWS_LOCATION_PATH = "$PWD.Path";
  public static final String UNIX_LOCATION_PATH = "$(pwd)";
  public static final String IMPLANT_BASE_NAME = "implant-";
  // Only used in Tanium / CS / Caldera executors, the native OpenAEV agent will determine a
  // relative path at its level
  public static final String IMPLANT_LOCATION_WINDOWS =
      "\"C:\\Program Files (x86)\\Filigran\\OAEV Agent\\runtimes\\";
  public static final String IMPLANT_LOCATION_UNIX = "/opt/openaev-agent/runtimes/";
  // Clean payloads older than 24 hours
  public static final String WINDOWS_CLEAN_PAYLOADS_COMMAND =
      "Get-ChildItem -Path \"C:\\Program Files (x86)\\Filigran\\OAEV Agent\\payloads\",\"C:\\Program Files (x86)\\Filigran\\OAEV Agent\\runtimes\" -Directory -Recurse | Where-Object {$_.CreationTime -lt (Get-Date).AddHours(-24)} | Remove-Item -Recurse -Force";
  public static final String UNIX_CLEAN_PAYLOADS_COMMAND =
      "find /opt/openaev-agent/payloads /opt/openaev-agent/runtimes -type d -mmin +1440 -exec rm -rf {} + 2>/dev/null";

  private ExecutorHelper() {}

  public static String replaceArgs(
      PLATFORM_TYPE platformType, String command, String injectId, String agentId) {
    if (platformType == null || command == null || injectId == null || agentId == null) {
      throw new IllegalArgumentException(
          "Platform type, command, injectId, and agentId must not be null.");
    }

    String location =
        switch (platformType) {
          case Windows -> WINDOWS_LOCATION_PATH;
          case Linux, MacOS -> UNIX_LOCATION_PATH;
          default ->
              throw new IllegalArgumentException("Unsupported platform type: " + platformType);
        };

    return command
        .replace("\"#{location}\"", location)
        .replace("#{inject}", injectId)
        .replace("#{agent}", agentId);
  }
}
