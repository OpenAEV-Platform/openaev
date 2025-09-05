package io.openbas.rest.detection_remediation.service;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HttpRequestMethodValues.GET;
import static io.opentelemetry.semconv.HttpAttributes.HttpRequestMethodValues.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.Gson;
import io.openbas.ee.Ee;
import io.openbas.rest.detection_remediation.form.DetectionRemediationHealthWebService;
import io.openbas.rest.detection_remediation.form.DetectionRemediationWebserviceInput;
import io.openbas.rest.detection_remediation.form.DetectionRemediationWebserviceOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationService {
  private final String X_OPEN_AEV_CERTIFICATE = "X-OpenAEV-Certificate";
  private final Ee ee;
  private final Environment env;
  private final Gson json = new Gson();

  public DetectionRemediationWebserviceOutput callRemediationDetectionAIWebservice(
      DetectionRemediationWebserviceInput payload) {
    // Check if account has EE licence
    String certificate = checkCertificate();

    String url =
        Objects.requireNonNull(env.getProperty("remediation.detection.webservice.crowdstrike"));

    // Configure Client
    OkHttpClient client =
        new OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES) // Max time connection
            .readTimeout(2, TimeUnit.MINUTES) // Max time waiting an answer
            .build();

    RequestBody body =
        RequestBody.create(json.toJson(payload), MediaType.parse(APPLICATION_JSON_VALUE));

    Request request =
        new Request.Builder()
            .url(url)
            .addHeader(
                X_OPEN_AEV_CERTIFICATE,
                Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8)))
            .addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .method(POST, body)
            .build();

    String errorMessage = "Request to Remediation Detection AI Webservice failed: ";

    return callAPI(client, request, errorMessage, DetectionRemediationWebserviceOutput.class);
  }

  public DetectionRemediationHealthWebService checkRemediationDetectionAIWebService() {
    // Check if account has EE licence
    checkCertificate();

    String url = Objects.requireNonNull(env.getProperty("remediation.detection.webservice.health"));
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder().url(url).method(GET, null).build();

    String errorMessage = "Connection to Remediation Detection AI Webservice failed: ";

    return callAPI(client, request, errorMessage, DetectionRemediationHealthWebService.class);
  }

  private String checkCertificate() {
    String certificate = ee.getEnterpriseEditionLicensePem();
    if (certificate == null || certificate.isBlank())
      throw new IllegalStateException("Enterprise Edition is not available");
    return certificate;
  }

  private <T> T callAPI(OkHttpClient client, Request request, String error, Class<? extends T> clazz) {
    try (Response response = client.newCall(request).execute()) {
      assert response.body() != null;
      if (!response.isSuccessful()) {
        log.error("{} {}", error, response.body());
        throw new RestClientException(error + response.body());
      }
      ResponseBody responseBody = response.body();

      return clazz.cast(json.fromJson(responseBody.string(), clazz));
    } catch (IOException e) {
      throw new RestClientException(error + e);
    }
  }
}
