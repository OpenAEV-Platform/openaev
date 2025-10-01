package io.openaev.executors.caldera.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.executors.caldera.client.model.Ability;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.executors.caldera.model.Agent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class CalderaExecutorClient {

  private static final String KEY_HEADER = "KEY";

  private final CalderaExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  // -- AGENTS --

  private static final String AGENT_URI = "/agents";

  public List<Agent> agents() {
    try {
      String jsonResponse = this.get(AGENT_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      log.error("Cannot retrieve agent list", e);
      throw new RuntimeException(e);
    }
  }

  public void deleteAgent(final String externalReference) {
    try {
      this.delete(this.config.getRestApiV2Url() + AGENT_URI + "/" + externalReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- ABILITIES --

  private static final String ABILITIES_URI = "/abilities";

  public List<Ability> abilities() {
    try {
      String jsonResponse = this.get(ABILITIES_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability createSubprocessorAbility(Injector injector) {
    try {
      List<Map<String, String>> executors = new ArrayList<>();
      Map<String, String> injectorExecutorCommands = injector.getExecutorCommands();
      if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorWindows);
      } else if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorWindows);
      }
      if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorLinux);
      } else if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorLinux);
      }
      if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorMac);
      } else if (injectorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            injectorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorMac);
      }
      Map<String, Object> body = new HashMap<>();
      body.put("name", "caldera-subprocessor-" + injector.getName());
      body.put("tactic", "openaev");
      body.put("technique_id", "openaev");
      body.put("technique_name", "openaev");
      body.put("executors", executors);
      String jsonResponse = this.post(this.config.getRestApiV2Url() + ABILITIES_URI, body);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability createClearAbility(Injector injector) {
    try {
      List<Map<String, String>> executors = new ArrayList<>();
      Map<String, String> injectorExecutorClearCommands = injector.getExecutorClearCommands();
      if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorWindows);
      } else if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorWindows);
      }
      if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorLinux);
      } else if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorLinux);
      }
      if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorMac);
      } else if (injectorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            injectorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorMac);
      }
      Map<String, Object> body = new HashMap<>();
      body.put("name", "caldera-clear-" + injector.getName());
      body.put("tactic", "openaev");
      body.put("technique_id", "openaev");
      body.put("technique_name", "openaev");
      body.put("executors", executors);
      String jsonResponse = this.post(this.config.getRestApiV2Url() + ABILITIES_URI, body);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteAbility(Ability ability) {
    try {
      this.delete(this.config.getRestApiV2Url() + ABILITIES_URI + "/" + ability.getAbility_id());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- EXPLOITS --

  private static final String EXPLOIT_URI = "/exploit";

  public void exploit(
      @NotBlank final String obfuscator,
      @NotBlank final String paw,
      @NotBlank final String abilityId,
      final List<Map<String, String>> additionalFields) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("obfuscator", obfuscator);
      body.put("paw", paw);
      body.put("ability_id", abilityId);
      body.put("facts", additionalFields);
      String result = this.post(this.config.getPluginAccessApiUrl() + EXPLOIT_URI, body);
      assert result.contains("complete"); // the exploit is well taken into account
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PRIVATE --

  private String get(@NotBlank final String uri) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(this.config.getRestApiV2Url() + uri);
      // Headers
      httpGet.addHeader(KEY_HEADER, this.config.getApiKey());

      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + uri, e);
    }
  }

  private String post(@NotBlank final String url, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      // Headers
      httpPost.addHeader(KEY_HEADER, this.config.getApiKey());
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);

      return httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }

  private void patch(@NotBlank final String url, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPatch httpPatch = new HttpPatch(url);
      // Headers
      httpPatch.addHeader(KEY_HEADER, this.config.getApiKey());
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPatch.setEntity(entity);
      httpClient.execute(httpPatch);
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }

  private void delete(@NotBlank final String url) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpDelete httpdelete = new HttpDelete(url);
      // Headers
      httpdelete.addHeader(KEY_HEADER, this.config.getApiKey());
      httpClient.execute(httpdelete);
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }
}
