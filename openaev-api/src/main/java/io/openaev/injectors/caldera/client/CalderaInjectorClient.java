package io.openaev.injectors.caldera.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.injectors.caldera.client.model.Ability;
import io.openaev.injectors.caldera.client.model.Agent;
import io.openaev.injectors.caldera.client.model.Result;
import io.openaev.injectors.caldera.config.CalderaInjectorConfig;
import io.openaev.injectors.caldera.model.Obfuscator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
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
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Service
@Slf4j
public class CalderaInjectorClient {

  private static final String KEY_HEADER = "KEY";

  private final CalderaInjectorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  // -- ABILITIES --

  private static final String ABILITIES_URI = "/abilities";

  public List<Ability> abilities() {
    try {
      String jsonResponse = this.get(this.config.getRestApiV2Url() + ABILITIES_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability findAbilityById(String abilityId) {
    try {
      String jsonResponse =
          this.get(this.config.getRestApiV2Url() + ABILITIES_URI + "/" + abilityId);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability createAbility(Map<String, Object> body) {
    try {
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

  // -- AGENTS --

  private static final String AGENT_URI = "/agents";

  public List<Agent> agents() {
    try {
      String jsonResponse = this.get(this.config.getRestApiV2Url() + AGENT_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      log.error("Cannot retrieve agent list", e);
      throw new RuntimeException(e);
    }
  }

  public Agent agent(@NotBlank final String paw, final String include) {
    try {
      String url = this.config.getRestApiV2Url() + AGENT_URI + "/" + paw;
      if (StringUtils.hasText(include)) {
        url = url + "?include=" + include;
      }
      String jsonResponse = this.get(url);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void killAgent(Agent agent) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("watchdog", 1);
      body.put("sleep_min", 3);
      body.put("sleep_max", 3);
      this.patch(this.config.getRestApiV2Url() + AGENT_URI + "/" + agent.getPaw(), body);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteAgent(Agent agent) {
    try {
      this.delete(this.config.getRestApiV2Url() + AGENT_URI + "/" + agent.getPaw());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- OBFUSCATORS --

  private static final String OBFUSCATOR_URI = "/obfuscators";

  public List<Obfuscator> obfuscators() {
    try {
      String jsonResponse = this.get(this.config.getRestApiV2Url() + OBFUSCATOR_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- RESULTS --

  private static final String RESULT_INDEX = "result";

  public Result results(@NotBlank final String linkId) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("index", RESULT_INDEX);
      body.put("link_id", linkId);
      String jsonResponse = this.post(this.config.getRestApiV1Url(), body);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PLUGIN ACCESS --

  private static final String EXPLOIT_URI = "/exploit";

  public String exploit(
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
      return this.post(this.config.getPluginAccessApiUrl() + EXPLOIT_URI, body);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PRIVATE --

  private String get(@NotBlank final String url) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(url);
      // Headers
      httpGet.addHeader(KEY_HEADER, this.config.getApiKey());
      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
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
