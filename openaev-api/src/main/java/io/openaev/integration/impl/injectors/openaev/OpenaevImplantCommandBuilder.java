package io.openaev.integration.impl.injectors.openaev;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Endpoint;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds executor commands for the OpenAEV implant injector. These commands are tenant-independent
 * (they only depend on {@link OpenAEVConfig}) and are used both at integration startup and during
 * tenant provisioning.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenaevImplantCommandBuilder {

  /**
   * Record to group all command variables.
   *
   * @param tokenVar the token variable
   * @param serverVar the server variable
   * @param maxSizeVar the max size variable
   * @param unsecuredCertificateVar unsecured certificate variable
   * @param withProxyVar with proxy variable
   */
  record CommandVars(
      String tokenVar,
      String serverVar,
      String maxSizeVar,
      String unsecuredCertificateVar,
      String withProxyVar) {
    CommandVars(OpenAEVConfig cfg) {
      this(
          "token=\"" + cfg.getAdminToken() + "\"",
          "server=\"" + cfg.getBaseUrlForAgent() + "\"",
          "max_size=\"" + cfg.getLogsMaxSize() + "\"",
          "unsecured_certificate=\"" + cfg.isUnsecuredCertificate() + "\"",
          "with_proxy=\"" + cfg.isWithProxy() + "\"");
    }
  }

  static Map<String, String> buildExecutorCommands(OpenAEVConfig cfg) {
    Map<String, String> commands = new HashMap<>();
    CommandVars vars = new CommandVars(cfg);
    // --- PALO ALTO WINDOWS SPECIFIC ---
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- WINDOWS ---
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- LINUX ---
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- MACOS ---
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    return commands;
  }

  static Map<String, String> buildExecutorClearCommands() {
    Map<String, String> clear = new HashMap<>();
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    return clear;
  }

  // --- Private helpers ---

  private static String dlUri(OpenAEVConfig cfg, String platform, String arch) {
    return "\""
        + cfg.getBaseUrlForAgent()
        + "/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}\"";
  }

  @SuppressWarnings("SameParameterValue")
  private static String dlVar(OpenAEVConfig cfg, String platform, String arch) {
    return "$url=\""
        + cfg.getBaseUrl()
        + "/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}"
        + "\"";
  }

  private static void buildPaloAltoWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + vars.tokenVar()
            + ";$"
            + vars.serverVar()
            + ";$"
            + vars.unsecuredCertificateVar()
            + ";$"
            + vars.withProxyVar()
            + ";$"
            + vars.maxSizeVar()
            + ";"
            + dlVar(cfg, "windows", arch.name())
            + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;"
            + "$taskName = 'OpenAEV-Inject-#{inject}-Agent-#{agent}';"
            + "$taskDescription = 'OpenAEV EDR validation task - inject #{inject} - agent #{agent} - safe to ignore - will self-delete after execution';"
            + "$implantArgs = '--uri ' + $server + ' --token ' + $token + ' --unsecured-certificate ' + $unsecured_certificate + ' --with-proxy ' + $with_proxy + ' --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}';"
            + "$action = New-ScheduledTaskAction -Execute \"$location\\$filename\" -Argument $implantArgs;"
            + "$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest;"
            + "$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 0);"
            + "Register-ScheduledTask -TaskName $taskName -Description $taskDescription -Action $action -Principal $principal -Settings $settings -Force | Out-Null;"
            + "Start-ScheduledTask -TaskName $taskName;"
            + "$timeout = 300; $elapsed = 0;"
            + "while($elapsed -lt $timeout) {"
            + "  $state = (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue).State;"
            + "  if($state -eq 'Ready') { break }"
            + "  Start-Sleep -Seconds 1; $elapsed++;"
            + "}"
            + "$info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue;"
            + "$exitCode = $info.LastTaskResult;"
            + "Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue;"
            + "exit $exitCode;");
  }

  private static void buildGenericWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + vars.tokenVar()
            + ";$"
            + vars.serverVar()
            + ";$"
            + vars.unsecuredCertificateVar()
            + ";$"
            + vars.withProxyVar()
            + ";$"
            + vars.maxSizeVar()
            + ";"
            + dlVar(cfg, "windows", arch.name())
            + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}\" -WindowStyle hidden;");
  }

  private static void buildGenericLinuxCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar()
            + ";"
            + vars.tokenVar()
            + ";"
            + vars.unsecuredCertificateVar()
            + ";"
            + vars.withProxyVar()
            + ";"
            + vars.maxSizeVar()
            + ";curl -s -X GET "
            + dlUri(cfg, "linux", arch.name())
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &");
  }

  private static void buildGenericMacOSCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar()
            + ";"
            + vars.tokenVar()
            + ";"
            + vars.unsecuredCertificateVar()
            + ";"
            + vars.withProxyVar()
            + (Endpoint.PLATFORM_ARCH.x86_64.equals(arch)
                ? ";"
                : ";$") // TODO: Should find a way to test on an x86 mac if the diff is necessary
            + vars.maxSizeVar()
            + ";curl -s -X GET "
            + dlUri(cfg, "macos", arch.name())
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &");
  }
}
