package io.openaev.rest.notifier;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.NotifierType;
import io.openaev.database.repository.NotifierRepository;
import io.openaev.rest.notifier.form.NotifierInput;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
public class NotifierApiTest extends IntegrationTest {

  public static final String NOTIFIER_URI = "/api/notifiers";

  @Autowired private MockMvc mvc;
  @Autowired private NotifierRepository notifierRepository;

  @AfterEach
  void afterEach() {
    notifierRepository.deleteAll();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Listing notifiers seeds and returns the built-in UI and email notifiers")
  void listNotifiersSeedsBuiltIns() throws Exception {
    String response =
        mvc.perform(get(NOTIFIER_URI).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> types = JsonPath.read(response, "$[*].notifier_type");
    assertTrue(types.contains("UI"));
    assertTrue(types.contains("EMAIL"));
    List<Boolean> builtIns =
        JsonPath.read(response, "$[?(@.notifier_type == 'UI')].notifier_built_in");
    assertTrue(builtIns.stream().allMatch(Boolean::booleanValue));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A webhook notifier can be created, updated and deleted")
  void webhookNotifierLifecycle() throws Exception {
    NotifierInput input =
        NotifierInput.builder()
            .name("Teams webhook")
            .description("Sample webhook")
            .type(NotifierType.WEBHOOK)
            .configuration(
                Map.of(
                    "url", "https://example.org/webhook",
                    "verb", "POST"))
            .build();

    String created =
        mvc.perform(
                post(NOTIFIER_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String notifierId = JsonPath.read(created, "$.notifier_id");
    assertEquals("Teams webhook", JsonPath.read(created, "$.notifier_name"));
    assertEquals("WEBHOOK", JsonPath.read(created, "$.notifier_type"));
    assertFalse((Boolean) JsonPath.read(created, "$.notifier_built_in"));

    NotifierInput update =
        NotifierInput.builder()
            .name("Teams webhook updated")
            .type(NotifierType.WEBHOOK)
            .configuration(Map.of("url", "https://example.org/webhook2"))
            .build();
    String updated =
        mvc.perform(
                put(NOTIFIER_URI + "/" + notifierId)
                    .content(asJsonString(update))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals("Teams webhook updated", JsonPath.read(updated, "$.notifier_name"));

    mvc.perform(delete(NOTIFIER_URI + "/" + notifierId).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertFalse(notifierRepository.findById(notifierId).isPresent());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A webhook notifier without a valid http(s) url is rejected")
  void webhookNotifierRequiresValidUrl() throws Exception {
    NotifierInput input =
        NotifierInput.builder()
            .name("Broken webhook")
            .type(NotifierType.WEBHOOK)
            .configuration(Map.of("url", "ftp://example.org"))
            .build();

    mvc.perform(
            post(NOTIFIER_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Built-in notifiers cannot be modified or deleted")
  void builtInNotifiersAreReadOnly() throws Exception {
    // Seed built-ins through the list endpoint
    mvc.perform(get(NOTIFIER_URI).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    String builtInId = notifierRepository.findAll().iterator().next().getId();

    NotifierInput update = NotifierInput.builder().name("Hacked").type(NotifierType.UI).build();
    int updateStatus =
        mvc.perform(
                put(NOTIFIER_URI + "/" + builtInId)
                    .content(asJsonString(update))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus();
    assertTrue(updateStatus >= 400);

    int deleteStatus =
        mvc.perform(delete(NOTIFIER_URI + "/" + builtInId).with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus();
    assertTrue(deleteStatus >= 400);
  }
}
