package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.rest.log.form.LogDetailsInput;
import io.openaev.utils.fixtures.LogFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
public class LogApiTest extends IntegrationTest {

  private static final String LOG_URI = "/api/logs";
  @Autowired private MockMvc mvc;

  @Test
  @WithMockUser
  @DisplayName("Should return 200 when input is valid")
  void given_input_should_return_200() throws Exception {

    LogDetailsInput logDetailsInput = LogFixture.getDefaultLogDetailsInput("SEVERE");

    String response =
        mvc.perform(
                post(LOG_URI)
                    .content(asJsonString(logDetailsInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).isEqualTo("Log message processed successfully");
  }

  @Test
  @WithMockUser
  @DisplayName("Should return 400 when target type is unsupported")
  void given_unsupported_level_should_return_400() throws Exception {

    LogDetailsInput logDetailsInput = LogFixture.getDefaultLogDetailsInput("OTHER");

    mvc.perform(
            post(LOG_URI + "/")
                .content(asJsonString(logDetailsInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }
}
