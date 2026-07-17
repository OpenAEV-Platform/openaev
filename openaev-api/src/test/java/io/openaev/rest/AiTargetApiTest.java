package io.openaev.rest;

import static io.openaev.rest.asset.ai_targets.AiTargetApi.AI_TARGET_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Asset;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class AiTargetApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AiTargetRepository aiTargetRepository;
  @Autowired private EntityManager entityManager;

  private AiTargetInput input(String name) {
    AiTargetInput input = new AiTargetInput();
    input.setName(name);
    input.setAiTargetProvider(Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE);
    input.setAiTargetModality(Asset.AI_TARGET_MODALITY.TEXT);
    return input;
  }

  private String createAiTarget(String name) throws Exception {
    String body =
        mvc.perform(
                post(AI_TARGET_URI)
                    .content(asJsonString(input(name)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    entityManager.flush();
    entityManager.clear();
    return JsonPath.read(body, "$.asset_id");
  }

  @DisplayName("Create AI target")
  @Test
  @WithMockUser(isAdmin = true)
  void createAiTargetShouldSucceed() throws Exception {
    mvc.perform(
            post(AI_TARGET_URI)
                .content(asJsonString(input("Target-A")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_name").value("Target-A"))
        .andExpect(jsonPath("$.ai_target_provider").value("OPENAI_COMPATIBLE"));
  }

  @DisplayName("Get AI target by id")
  @Test
  @WithMockUser(isAdmin = true)
  void getAiTargetShouldSucceed() throws Exception {
    String id = createAiTarget("Target-B");

    mvc.perform(get(AI_TARGET_URI + "/" + id).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_id").value(id))
        .andExpect(jsonPath("$.asset_name").value("Target-B"));
  }

  @DisplayName("Update AI target")
  @Test
  @WithMockUser(isAdmin = true)
  void updateAiTargetShouldSucceed() throws Exception {
    String id = createAiTarget("Target-C");

    AiTargetInput update = input("Target-C-Updated");
    mvc.perform(
            put(AI_TARGET_URI + "/" + id)
                .content(asJsonString(update))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_id").value(id))
        .andExpect(jsonPath("$.asset_name").value("Target-C-Updated"));
  }

  @DisplayName("Delete AI target")
  @Test
  @WithMockUser(isAdmin = true)
  void deleteAiTargetShouldSucceed() throws Exception {
    String id = createAiTarget("Target-D");

    mvc.perform(delete(AI_TARGET_URI + "/" + id).with(csrf())).andExpect(status().isOk());
    entityManager.flush();
    entityManager.clear();

    assertThat(aiTargetRepository.findAiTargetById(id)).isEmpty();
  }

  @DisplayName("Search AI targets returns the created target")
  @Test
  @WithMockUser(isAdmin = true)
  void searchAiTargetsShouldSucceed() throws Exception {
    createAiTarget("Target-Searchable");

    mvc.perform(
            post(AI_TARGET_URI + "/search")
                .content(asJsonString(PaginationFixture.simpleTextSearch("Target-Searchable")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numberOfElements").value(1))
        .andExpect(jsonPath("$.content.[0].asset_name").value("Target-Searchable"));
  }

  @DisplayName("Options by name returns matching AI targets")
  @Test
  @WithMockUser(isAdmin = true)
  void optionsByNameShouldSucceed() throws Exception {
    createAiTarget("Optionable-1");
    createAiTarget("Optionable-2");

    String response =
        mvc.perform(
                get(AI_TARGET_URI + "/options")
                    .queryParam("searchText", "Optionable")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(2, new JSONArray(response).length());
  }

  @DisplayName("Options by id returns the requested AI target")
  @Test
  @WithMockUser(isAdmin = true)
  void optionsByIdShouldSucceed() throws Exception {
    String id = createAiTarget("OptionById-1");

    String response =
        mvc.perform(
                post(AI_TARGET_URI + "/options")
                    .content(asJsonString(java.util.List.of(id)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(1, new JSONArray(response).length());
  }
}
