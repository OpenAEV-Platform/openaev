package io.openbas.rest.scenario;

import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Base;
import io.openbas.database.model.Scenario;
import io.openbas.database.model.Tag;
import io.openbas.export.Mixins;
import io.openbas.utils.ZipUtils;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class ScenarioExportTest extends IntegrationTest {
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager manager;

  @BeforeEach
  public void before() {
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    tagComposer.reset();
  }

  private String getJsonExportFromZip(byte[] zipBytes, String entryName) throws IOException {
    return ZipUtils.getZipEntry(zipBytes, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("When payloads have tags, scenario export has these tags")
  public void WhenPayloadsHaveTags_ScenarioExportHasTheseTags() throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Scenario scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultCrisisScenario())
            .withTag(tagComposer.forTag(TagFixture.getTagWithText("scenario tag")))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject tag")))
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(injectorFixture.getWellKnownObasImplantInjector())
                            .withPayload(
                                payloadComposer
                                    .forPayload(PayloadFixture.createDefaultCommand())
                                    .withTag(
                                        tagComposer.forTag(
                                            TagFixture.getTagWithText("this is a payload tag"))))))
            .persist()
            .get();

    manager.flush();
    manager.clear();

    byte[] response =
        mvc.perform(
                get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, scenario.getName());

    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
    String tagsJson = objectMapper.writeValueAsString(tagComposer.generatedItems.stream().toList());

    assertThatJson(actualJson)
        .when(Option.IGNORING_ARRAY_ORDER)
        .node("scenario_tags")
        .isArray()
        .isEqualTo(tagsJson);
  }
}
