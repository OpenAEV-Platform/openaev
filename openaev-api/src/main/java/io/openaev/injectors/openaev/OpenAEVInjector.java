package io.openaev.injectors.openaev;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Endpoint;
import io.openaev.integrations.InjectorService;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenAEVInjector {

  public static final String OPENAEV_INJECTOR_NAME = "OpenAEV Implant";
  public static final String OPENAEV_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0144";

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

  @Autowired
  public OpenAEVInjector(
      InjectorService injectorService,
      OpenAEVImplantContract contract,
      OpenAEVConfig openAEVConfig) {
    String tokenVar = "token=\"" + openAEVConfig.getAdminToken() + "\"";
    String serverVar = "server=\"" + openAEVConfig.getBaseUrlForAgent() + "\"";
    String unsecuredCertificateVar =
        "unsecured_certificate=\"" + openAEVConfig.isUnsecuredCertificate() + "\"";
    String withProxyVar = "with_proxy=\"" + openAEVConfig.isWithProxy() + "\"";
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + tokenVar
            + ";$"
            + serverVar
            + ";$"
            + unsecuredCertificateVar
            + ";$"
            + withProxyVar
            + ";"
            + dlVar(openAEVConfig, "windows", "x86_64")
            + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\" -WindowStyle hidden;");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + tokenVar
            + ";$"
            + serverVar
            + ";$"
            + unsecuredCertificateVar
            + ";$"
            + withProxyVar
            + ";"
            + dlVar(openAEVConfig, "windows", "arm64")
            + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\" -WindowStyle hidden;");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";curl -s -X GET "
            + dlUri(openAEVConfig, "linux", "x86_64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";curl -s -X GET "
            + dlUri(openAEVConfig, "linux", "arm64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";curl -s -X GET "
            + dlUri(openAEVConfig, "macos", "x86_64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";curl -s -X GET "
            + dlUri(openAEVConfig, "macos", "arm64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    Map<String, String> executorClearCommands = new HashMap<>();
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    try {
      injectorService.register(
          OPENAEV_INJECTOR_ID,
          OPENAEV_INJECTOR_NAME,
          contract,
          false,
          "simulation-implant",
          executorCommands,
          executorClearCommands,
          true);
    } catch (Exception e) {
      log.error(String.format("Error creating OpenAEV implant injector (%s)", e.getMessage()), e);
    }
  }
}
