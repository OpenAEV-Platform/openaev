package io.openbas.helper;

import static io.openbas.helper.InjectModelHelper.isReady;
import static io.openbas.helper.ObjectMapperHelper.openBASJsonMapper;
import static io.openbas.utils.fixtures.InjectorContractFixture.*;
import static io.openbas.utils.fixtures.InjectorFixture.createDefaultPayloadInjector;
import static io.openbas.utils.fixtures.PayloadFixture.createCommand;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.Command;
import io.openbas.database.model.Injector;
import io.openbas.database.model.InjectorContract;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InjectModelHelperTest {

  private final ObjectMapper mapper = openBASJsonMapper();

  private InjectorContract prepareInjectorContract() throws JsonProcessingException {
    Injector injector = createDefaultPayloadInjector();
    Command payloadCommand = createCommand("cmd", "whoami", List.of(), "whoami");
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
  }
}
