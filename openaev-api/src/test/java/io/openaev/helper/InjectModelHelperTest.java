package io.openaev.helper;

import static io.openaev.helper.InjectModelHelper.isReady;
import static io.openaev.helper.ObjectMapperHelper.openAEVJsonMapper;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.utils.fixtures.InjectorContractFixture.*;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultPayloadInjector;
import static io.openaev.utils.fixtures.PayloadFixture.createCommand;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Command;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.fixtures.composers.DomainComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InjectModelHelperTest extends IntegrationTest {

  private final ObjectMapper mapper = openAEVJsonMapper();

  @Autowired private DomainComposer domainComposer;

  private InjectorContract prepareInjectorContract() throws JsonProcessingException {

    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();
    Injector injector = createDefaultPayloadInjector();
    Command payloadCommand = createCommand("cmd", "whoami", List.of(), "whoami", domains);
    return createPayloadInjectorContract(injector, payloadCommand);
  }

  @Nested
  class MandatoryAssetTests {

    @Test
    void given_an_injector_contract_with_asset_mandatory_and_an_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_mandatory_and_no_asset_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_optional_and_an_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_optional_and_not_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(false));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }
  }

  @Nested
  class MandatoryGroupTests {

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_an_element_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_full_elements_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_no_element_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Nested
    class MandatoryOnConditionTests {

      @Test
      void
          given_an_injector_contract_with_mandatory_on_condition_and_no_element_should_not_be_ready()
              throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of();
        List<String> assetGroups = List.of();

        // -- EXECUTE --
        boolean isReady =
            isReady(
                injectorContract,
                injectorContract.getConvertedContent(),
                allTeams,
                teams,
                assets,
                assetGroups);

        // -- ASSERT --
        assertFalse(isReady);
      }

      @Test
      void given_an_injector_contract_with_mandatory_on_condition_and_element_should_be_ready()
          throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of("assetId");
        List<String> assetGroups = List.of();

        // -- EXECUTE --
        boolean isReady =
            isReady(
                injectorContract,
                injectorContract.getConvertedContent(),
                allTeams,
                teams,
                assets,
                assetGroups);

        // -- ASSERT --
        assertTrue(isReady);
      }

      @Test
      void
          given_an_injector_contract_with_mandatory_on_condition_and_condition_element_should_not_be_ready()
              throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of();
        List<String> assetGroups = List.of("assetGroupId");

        // -- EXECUTE --
        boolean isReady =
            isReady(
                injectorContract,
                injectorContract.getConvertedContent(),
                allTeams,
                teams,
                assets,
                assetGroups);

        // -- ASSERT --
        assertFalse(isReady);
      }

      @Test
      void given_an_injector_contract_with_mandatory_on_condition_and_all_elements_should_be_ready()
          throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of("assetId");
        List<String> assetGroups = List.of("assetGroupId");

        // -- EXECUTE --
        boolean isReady =
            isReady(
                injectorContract,
                injectorContract.getConvertedContent(),
                allTeams,
                teams,
                assets,
                assetGroups);

        // -- ASSERT --
        assertTrue(isReady);
      }
    }
  }

  @Nested
  class MandatoryOnConditionValueTests {

    @Test
    void given_mandatory_on_condition_with_specific_value_when_condition_matches_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_mandatory_on_condition_with_specific_values_when_condition_matches_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(
          injectorContract,
          mapper,
          buildMandatoryOnConditionValue(List.of("assetGroupId", "assetGroupId2")));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_specific_value_when_condition_not_matches_should_not_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_not_specific_value_when_condition_not_matches_should_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_not_specific_values_when_condition_not_matches_should_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(
          injectorContract,
          mapper,
          buildMandatoryOnConditionValue(List.of("assetGroupId", "assetGroupId3")));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }
  }

  @Nested
  class DefaultValueTests {

    @Test
    void given_text_field_with_default_value_and_no_content_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, List.of(textField("title", "title", "Default title")));

      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_text_field_without_default_value_and_no_content_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, List.of(textField("title", "title")));

      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          isReady(
              injectorContract,
              injectorContract.getConvertedContent(),
              allTeams,
              teams,
              assets,
              assetGroups);

      // -- ASSERT --
      assertFalse(isReady);
    }
  }
}
