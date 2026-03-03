package io.openaev.integration.impl.injectors.openaev;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.injectors.openaev.OpenAEVImplantExecutor;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.QualifiedComponent;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenaevInjectorIntegration extends IntegrationInMemory {
  public static final String OPENAEV_INJECTOR_NAME = "OpenAEV Implant";
  public static final String OPENAEV_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0144";

  /**
   * Record to group all command variables
   *
   * @param tokenVar the token variable
   * @param serverVar the server variable
   * @param maxSizeVar the max size variable
   * @param unsecuredCertificateVar unsecured certificate variable
   * @param withProxyVar with proxy variable
   */
  public record CommandVars(
      String tokenVar,
      String serverVar,
      String maxSizeVar,
      String unsecuredCertificateVar,
      String withProxyVar) {
    public CommandVars(OpenAEVConfig cfg) {
      this(
          "token=\"" + cfg.getAdminToken() + "\"",
          "server=\"" + cfg.getBaseUrlForAgent() + "\"",
          "max_size=\"" + cfg.getLogsMaxSize() + "\"",
          "unsecured_certificate=\"" + cfg.isUnsecuredCertificate() + "\"",
          "with_proxy=\"" + cfg.isWithProxy() + "\"");
    }
  }

  private String dlUri(OpenAEVConfig openAEVConfig, String platform, String arch) {
    return "\""
        + openAEVConfig.getBaseUrlForAgent()
        + "/api/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}\"";
  }

  @SuppressWarnings("SameParameterValue")
  private String dlVar(OpenAEVConfig openAEVConfig, String platform, String arch) {
    return "$url=\""
        + openAEVConfig.getBaseUrl()
        + "/api/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}"
        + "\"";
  }

  private final InjectorService injectorService;
  private final OpenAEVImplantContract openAEVImplantContract;
  private final OpenAEVConfig openAEVConfig;
  private final InjectorContext injectorContext;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  @QualifiedComponent(identifier = {OpenAEVImplantContract.TYPE, OPENAEV_INJECTOR_ID})
  private OpenAEVImplantExecutor openAEVImplantExecutor;

  public OpenaevInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenAEVImplantContract openAEVImplantContract,
      OpenAEVConfig openAEVConfig,
      InjectorContext injectorContext,
      AssetGroupService assetGroupService,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.openAEVImplantContract = openAEVImplantContract;
    this.openAEVConfig = openAEVConfig;
    this.injectorContext = injectorContext;
    this.assetGroupService = assetGroupService;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  protected void innerStart() throws Exception {
    Map<String, String> executorCommands = buildExecutorCommands(openAEVConfig);
    Map<String, String> executorClearCommands = buildExecutorClearCommands();

    injectorService.registerBuiltinInjector(
        OPENAEV_INJECTOR_ID,
        OPENAEV_INJECTOR_NAME,
        openAEVImplantContract,
        false,
        "simulation-implant",
        executorCommands,
        executorClearCommands,
        true,
        List.of());
    this.openAEVImplantExecutor =
        new OpenAEVImplantExecutor(
            injectorContext, assetGroupService, injectExpectationService, injectService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }

  private Map<String, String> buildExecutorCommands(OpenAEVConfig cfg) {
    Map<String, String> commands = new HashMap<>();
    CommandVars vars = new CommandVars(cfg);
    // --- PALO ALTO WINDOWS SPECIFIC ---
    this.buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    this.buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- WINDOWS ---
    this.buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    this.buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- LINUX ---
    this.buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    this.buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- MACOS ---
    this.buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    this.buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);

    return commands;
  }

  private Map<String, String> buildExecutorClearCommands() {
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

  /**
   * Build a Palo Alto Windows command
   *
   * @param arch targeted architecture
   * @param cfg OpenAEV configuration
   * @param commands list of commands to append the new command to
   * @param vars command variables
   */
  private void buildPaloAltoWindowsCommand(
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
            + "$implantArgs = '--uri ' + $server + ' --token ' + $token + ' --unsecured-certificate ' + $unsecured_certificate + ' --with-proxy ' + $with_proxy + ' --agent-id #{agent} --inject-id #{inject}';"
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

  /**
   * Build a generic (working with most executors) Windows command
   *
   * @param arch targeted architecture
   * @param cfg OpenAEV configuration
   * @param commands list of commands to append the new command to
   * @param vars command variables
   */
  private void buildGenericWindowsCommand(
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
            + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\" -WindowStyle hidden;");
  }

  /**
   * Build a generic (working with most executors) Linux command
   *
   * @param arch targeted architecture
   * @param cfg OpenAEV configuration
   * @param commands list of commands to append the new command to
   * @param vars command variables
   */
  private void buildGenericLinuxCommand(
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
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
  }

  /**
   * Build a generic (working with most executors) MacOS command
   *
   * @param arch targeted architecture
   * @param cfg OpenAEV configuration
   * @param commands list of commands to append the new command to
   * @param vars command variables
   */
  private void buildGenericMacOSCommand(
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
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
  }
}
