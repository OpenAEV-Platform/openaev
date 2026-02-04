package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class InjectExecutionStepTest {
  @Mock private InjectorContractService injectorContractService;
  @Mock private UserService userService;
  @Mock private TeamService teamService;
  @Mock private AssetService assetService;
  @Mock private TagService tagService;
  @Mock private DocumentService documentService;
  @Mock private InjectService injectService;

  ObjectMapper mapper = new ObjectMapper();

  @Test
  public void createTest() throws JsonProcessingException {
    InjectExecutionStep injectExecutionStep =
        new InjectExecutionStep(
            injectorContractService,
            userService,
            assetService,
            teamService,
            tagService,
            documentService,
            injectService,
            null,
            null,
            null,
            null,
            null);

    doReturn(getInjectorContract()).when(injectorContractService).injectorContract(any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(any());
    doReturn(new HashSet<>()).when(tagService).tagSet(any());
    doReturn(null).when(documentService).document(any());
    doReturn(false).when(injectService).canApplyTargetType(any(), any());

    String jsonStepData =
        """
                {
                                                    "type": "inject",
                                                    "inject_title": "whoami",
                                                    "inject_description": "",
                                                    "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
                                                    "inject_content": {
                                                      "expectations": [
                                                        {
                                                          "expectation_type": "PREVENTION",
                                                          "expectation_name": "Prevention",
                                                          "expectation_description": null,
                                                          "expectation_score": 100,
                                                          "expectation_expectation_group": false,
                                                          "expectation_expiration_time": 21600
                                                        },
                                                        {
                                                          "expectation_type": "DETECTION",
                                                          "expectation_name": "Detection",
                                                          "expectation_description": null,
                                                          "expectation_score": 100,
                                                          "expectation_expectation_group": false,
                                                          "expectation_expiration_time": 21600
                                                        }
                                                      ],
                                                      "obfuscator": "plain-text",
                                                      "file": "c:\\\\programdata\\\\microsoft\\\\drm\\\\182.bat"
                                                    },
                                                    "inject_depends_on": [],
                                                    "inject_depends_duration": 100,
                                                    "inject_teams": [],
                                                    "inject_assets": [
                                                      "01962d36-c834-49aa-b475-b2cde0e1f40f"
                                                    ],
                                                    "inject_asset_groups": [],
                                                    "inject_documents": [],
                                                    "inject_all_teams": false,
                                                    "inject_country": null,
                                                    "inject_city": null,
                                                    "inject_tags": [],
                                                    "inject_enabled": true
                }
                """;
    InjectInput dataStep = mapper.readValue(jsonStepData, InjectInput.class);
    Exercise simulation = new Exercise();
    List<StepsCreateInput.StepCreateInput> steps = new ArrayList<>();
    steps.add(
        StepsCreateInput.StepCreateInput.builder()
            .dataStep(dataStep)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .limitExecution(0)
            .build());
    StepsCreateInput newStep =
        StepsCreateInput.builder()
            .steps(steps)
            .workflowId("cc01450b-b2f0-40da-bf14-3c30a3b24aeb")
            .build();
    Workflow workflowTemplate =
        Workflow.builder()
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();
    Step stepTemplate = injectExecutionStep.create(newStep.steps.get(0), workflowTemplate);
    assertEquals(StepActionClass.INJECT_EXECUTION, stepTemplate.getStepAction());
    assertEquals(StepStatus.TEMPLATE, stepTemplate.getStatus());
    assertFalse(stepTemplate.getData().isEmpty());
    assertFalse(stepTemplate.getData().isBlank());
    assertEquals(
        "73bfd988-b0bd-4740-bb7e-a6209a538835",
        StepService.getField(
            stepTemplate.getData(), "inject_injector_contract.injector_contract_id"));
  }

  @Test
  public void waitTest() throws JsonProcessingException {
    InjectExecutionStep injectExecutionStep =
        new InjectExecutionStep(
            injectorContractService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String stepData =
        """
                {
                        "type": "inject",
                        "inject_title": "whoami",
                        "inject_description": "",
                        "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
                        "inject_content": {
                          "expectations": [
                            {
                              "expectation_type": "PREVENTION",
                              "expectation_name": "Prevention",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            },
                            {
                              "expectation_type": "DETECTION",
                              "expectation_name": "Detection",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            }
                          ],
                          "obfuscator": "plain-text",
                          "file": "c:\\\\programdata\\\\microsoft\\\\drm\\\\182.bat"
                        },
                        "inject_depends_on": [],
                        "inject_depends_duration": 100,
                        "inject_teams": [],
                        "inject_assets": [
                          "01962d36-c834-49aa-b475-b2cde0e1f40f"
                        ],
                        "inject_asset_groups": [],
                        "inject_documents": [],
                        "inject_all_teams": false,
                        "inject_country": null,
                        "inject_city": null,
                        "inject_tags": [],
                        "inject_enabled": true
                      }
                """;
    mapper.readValue(stepData, InjectInput.class);
    Exercise simulation = new Exercise();

    Workflow workflowTemplate =
        Workflow.builder()
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepTemplate =
        Step.builder()
            .data(stepData)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .limitExecution(1)
            .workflow(workflowTemplate)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Workflow workflowRun =
        Workflow.builder()
            .status(WorkflowStatus.RUN)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();
    /// Really usefully ???

    Step stepWait =
        injectExecutionStep.wait(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
  }

  public void runTest() throws JsonProcessingException {
    InjectExecutionStep injectExecutionStep =
        new InjectExecutionStep(
            injectorContractService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String stepData =
        """
                {
                        "type": "inject",
                        "inject_title": "whoami",
                        "inject_description": "",
                        "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
                        "inject_content": {
                          "expectations": [
                            {
                              "expectation_type": "PREVENTION",
                              "expectation_name": "Prevention",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            },
                            {
                              "expectation_type": "DETECTION",
                              "expectation_name": "Detection",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            }
                          ],
                          "obfuscator": "plain-text",
                          "file": "c:\\\\programdata\\\\microsoft\\\\drm\\\\182.bat"
                        },
                        "inject_depends_on": [],
                        "inject_depends_duration": 100,
                        "inject_teams": [],
                        "inject_assets": [
                          "01962d36-c834-49aa-b475-b2cde0e1f40f"
                        ],
                        "inject_asset_groups": [],
                        "inject_documents": [],
                        "inject_all_teams": false,
                        "inject_country": null,
                        "inject_city": null,
                        "inject_tags": [],
                        "inject_enabled": true
                      }
                """;
    Exercise simulation = new Exercise();

    Workflow workflowTemplate =
        Workflow.builder()
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepTemplate =
        Step.builder()
            .data(stepData)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .limitExecution(1)
            .workflow(workflowTemplate)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Workflow workflowRun =
        Workflow.builder()
            .status(WorkflowStatus.RUN)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();
    /// Really usefully ???

    Step stepWait =
        injectExecutionStep.wait(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    mapper.readValue(
        """
                {
                        "type": "inject",
                        "inject_title": "whoami",
                        "inject_description": "",
                        "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
                        "inject_content": {
                          "expectations": [
                            {
                              "expectation_type": "PREVENTION",
                              "expectation_name": "Prevention",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            },
                            {
                              "expectation_type": "DETECTION",
                              "expectation_name": "Detection",
                              "expectation_description": null,
                              "expectation_score": 100,
                              "expectation_expectation_group": false,
                              "expectation_expiration_time": 21600
                            }
                          ],
                          "obfuscator": "plain-text",
                          "file": "c:\\\\programdata\\\\microsoft\\\\drm\\\\182.bat"
                        },
                        "inject_depends_on": [],
                        "inject_depends_duration": 100,
                        "inject_teams": [],
                        "inject_assets": [
                          "01962d36-c834-49aa-b475-b2cde0e1f40f"
                        ],
                        "inject_asset_groups": [],
                        "inject_documents": [],
                        "inject_all_teams": false,
                        "inject_country": null,
                        "inject_city": null,
                        "inject_tags": [],
                        "inject_enabled": true
                      }
                """,
        InjectInput.class);
  }

  public void stepInputFromConditionMapperTest() {
    List<ConditionCreateInput> conditionCreateInputList = new ArrayList<>();
    ConditionCreateInput conditionCreateInput =
        ConditionCreateInput.builder()
            .key("stdout")
            .value("outputs.message.stdout")
            .stepFrom("1")
            .type(ConditionType.MAPPER)
            .build();
  }

  private InjectorContract getInjectorContract() throws JsonProcessingException {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setContent(
        "{\"config\":{\"type\":\"openaev_implant\",\"expose\":true,\"label\":{\"en\":\"OpenAEV Implant\",\"fr\":\"OpenAEV Implant\"},\"color_dark\":\"#000000\",\"color_light\":\"#000000\"},\"label\":{\"en\":\"WHOAMI\",\"fr\":\"WHOAMI\"},\"manual\":false,\"fields\":[{\"key\":\"assets\",\"label\":\"Source assets\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset\"},{\"key\":\"asset_groups\",\"label\":\"Source asset groups\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset-group\"},{\"key\":\"obfuscator\",\"label\":\"Obfuscators\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"1\",\"defaultValue\":[\"plain-text\"],\"choices\":[{\"label\":\"plain-text\",\"value\":\"plain-text\",\"information\":\"\"},{\"label\":\"base64\",\"value\":\"base64\",\"information\":\"CMD does not support base64 obfuscation\"}],\"type\":\"choice\"},{\"key\":\"expectations\",\"label\":\"Expectations\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"predefinedExpectations\":[{\"expectation_type\":\"PREVENTION\",\"expectation_name\":\"Prevention\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600},{\"expectation_type\":\"DETECTION\",\"expectation_name\":\"Detection\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600}],\"type\":\"expectation\"}],\"variables\":[{\"key\":\"user\",\"label\":\"User that will receive the injection\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[{\"key\":\"user.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.email\",\"label\":\"Email of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.firstname\",\"label\":\"First name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lastname\",\"label\":\"Last name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lang\",\"label\":\"Language of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"exercise\",\"label\":\"Exercise of the current injection\",\"type\":\"Object\",\"cardinality\":\"1\",\"children\":[{\"key\":\"exercise.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.name\",\"label\":\"Name of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.description\",\"label\":\"Description of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"teams\",\"label\":\"List of team name for the injection\",\"type\":\"String\",\"cardinality\":\"n\",\"children\":[]},{\"key\":\"player_uri\",\"label\":\"Player interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"challenges_uri\",\"label\":\"Challenges interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"scoreboard_uri\",\"label\":\"Scoreboard interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"lessons_uri\",\"label\":\"Lessons learned interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}],\"context\":{},\"contract_id\":\"73bfd988-b0bd-4740-bb7e-a6209a538835\",\"contract_attack_patterns_external_ids\":[],\"is_atomic_testing\":true,\"needs_executor\":true,\"platforms\":[\"MacOS\"],\"domains\":[{\"listened\":true,\"domain_id\":\"948e3cdc-c345-45dd-80cb-943804c09a3a\",\"domain_name\":\"Endpoint\",\"domain_color\":\"#389CFF\",\"domain_created_at\":\"2026-02-03T12:15:01.323228Z\",\"domain_updated_at\":\"2026-02-03T12:15:01.323228Z\"}]}");
    injectorContract.setConvertedContent(
        (ObjectNode) mapper.readTree(injectorContract.getContent()));
    injectorContract.setId("73bfd988-b0bd-4740-bb7e-a6209a538835");
    Map<String, String> labels = new HashMap<>();
    labels.put("en", "WHOAMI");
    labels.put("fr", "WHOAMI");
    injectorContract.setLabels(labels);
    injectorContract.setManual(false);
    Injector injector = new Injector();
    injector.setId("injectorId");
    injectorContract.setInjector(injector);
    injectorContract.setAtomicTesting(false);
    injectorContract.setCustom(false);
    injectorContract.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    injectorContract.setNeedsExecutor(true);
    injectorContract.setImportAvailable(false);

    return injectorContract;
  }
}
