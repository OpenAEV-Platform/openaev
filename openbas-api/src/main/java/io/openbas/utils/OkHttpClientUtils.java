package io.openbas.utils;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.ConnectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RequiredArgsConstructor
public class OkHttpClientUtils {

  public <T> T call(
      OkHttpClient client, Request request, String error, Gson json, Class<? extends T> clazz) {
    try (Response response = client.newCall(request).execute()) {
      assert response.body() != null;
      if (!response.isSuccessful()) {
        log.error("{} {}", error, response.body());
        throw new RestClientException(error + response.body());
      }
      ResponseBody responseBody = response.body();

      return clazz.cast(json.fromJson(responseBody.string(), clazz));
    } catch (ConnectException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error, ex);

    } catch (IOException e) {
      throw new RestClientException(error + e);
    }
  }
}
