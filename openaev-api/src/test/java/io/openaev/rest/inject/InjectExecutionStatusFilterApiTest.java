package io.openaev.rest.inject;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@code inject_status} and {@code inject_enabled} filters of the inject
 * results search. An inject never launched has no status row and is serialized as DRAFT, so those
 * injects must answer to a DRAFT filter and must not vanish from a negative one.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Inject execution status filter")
@WithMockUser(isAdmin = true)
class InjectExecutionStatusFilterApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;

  private String injectWithoutStatusId;
  private String draftInjectId;
  private String executedInjectId;
  private String disabledInjectId;

  @BeforeEach
  void setup() {
    injectComposer.reset();
    injectStatusComposer.reset();

    injectWithoutStatusId = persistInject(InjectFixture.getDefaultInject(), null);
    draftInjectId =
        persistInject(
            InjectFixture.getDefaultInject(), InjectStatusFixture.createDraftInjectStatus());
    executedInjectId =
        persistInject(InjectFixture.getDefaultInject(), InjectStatusFixture.createSuccessStatus());

    Inject disabledInject = InjectFixture.getDefaultInject();
    disabledInject.setEnabled(false);
    disabledInjectId = persistInject(disabledInject, null);

    entityManager.flush();
  }

  private String persistInject(Inject inject, InjectStatus status) {
    InjectComposer.Composer composer = injectComposer.forInject(inject);
    if (status != null) {
      composer = composer.withInjectStatus(injectStatusComposer.forInjectStatus(status));
    }
    return composer.persist().get().getId();
  }

  private SearchPaginationInput searchOn(
      String key, Filters.FilterOperator operator, String... values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(List.of(values));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    return PaginationFixture.getDefault().size(100).filterGroup(filterGroup).build();
  }

  /** Asserts the search returns the expected injects and none of the unexpected ones. */
  private void searchAndExpect(
      SearchPaginationInput input, List<String> expectedIds, List<String> unexpectedIds)
      throws Exception {
    ResultActions result =
        mvc.perform(
                post(ATOMIC_TESTING_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful());
    for (String expectedId : expectedIds) {
      result.andExpect(
          jsonPath("$.content[?(@.inject_id == '%s')]".formatted(expectedId)).exists());
    }
    for (String unexpectedId : unexpectedIds) {
      result.andExpect(
          jsonPath("$.content[?(@.inject_id == '%s')]".formatted(unexpectedId)).doesNotExist());
    }
  }

  @Test
  @DisplayName("DRAFT matches injects with a draft status and injects with no status row")
  void given_draftFilter_should_returnInjectsWithoutStatus() throws Exception {
    searchAndExpect(
        searchOn("inject_status", Filters.FilterOperator.eq, ExecutionStatus.DRAFT.name()),
        List.of(injectWithoutStatusId, draftInjectId, disabledInjectId),
        List.of(executedInjectId));
  }

  @Test
  @DisplayName("A real status only matches the injects holding it")
  void given_executedFilter_should_returnOnlyExecutedInject() throws Exception {
    searchAndExpect(
        searchOn("inject_status", Filters.FilterOperator.eq, ExecutionStatus.EXECUTED.name()),
        List.of(executedInjectId),
        List.of(injectWithoutStatusId, draftInjectId, disabledInjectId));
  }

  @Test
  @DisplayName("Several statuses are OR-combined")
  void given_multipleStatuses_should_returnAllOfThem() throws Exception {
    searchAndExpect(
        searchOn(
            "inject_status",
            Filters.FilterOperator.eq,
            ExecutionStatus.EXECUTED.name(),
            ExecutionStatus.DRAFT.name()),
        List.of(executedInjectId, injectWithoutStatusId, draftInjectId),
        List.of());
  }

  @Test
  @DisplayName("A negative filter keeps the injects with no status row")
  void given_negativeFilter_should_keepInjectsWithoutStatus() throws Exception {
    searchAndExpect(
        searchOn("inject_status", Filters.FilterOperator.not_eq, ExecutionStatus.EXECUTED.name()),
        List.of(injectWithoutStatusId, draftInjectId, disabledInjectId),
        List.of(executedInjectId));
  }

  @Test
  @DisplayName("Negating DRAFT excludes the injects with no status row")
  void given_negativeDraftFilter_should_excludeInjectsWithoutStatus() throws Exception {
    searchAndExpect(
        searchOn("inject_status", Filters.FilterOperator.not_eq, ExecutionStatus.DRAFT.name()),
        List.of(executedInjectId),
        List.of(injectWithoutStatusId, draftInjectId, disabledInjectId));
  }

  @Test
  @DisplayName("Disabled injects are found on the enabled filter")
  void given_enabledFilter_should_returnDisabledInject() throws Exception {
    searchAndExpect(
        searchOn("inject_enabled", Filters.FilterOperator.eq, "false"),
        List.of(disabledInjectId),
        List.of(injectWithoutStatusId, draftInjectId, executedInjectId));
  }
}
