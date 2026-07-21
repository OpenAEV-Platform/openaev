import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';

import { fetchScenarioChallenges } from '../../../../../actions/challenge-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchScenarioDocuments } from '../../../../../actions/documents/documents-actions';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { fetchScenarioTeams } from '../../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { fetchVariablesForScenario } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import {
  type Inject,
  type InjectInput,
  type InjectorContractFullOutput,
  type Scenario,
} from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForScenario from '../../../../../utils/context/endpoint/EndpointContextForScenario';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { ArticleContext, ChallengeContext, InjectContext, TeamContext } from '../../../common/Context';
import InjectContractPicker from '../../../common/injects/create/InjectContractPicker';
import InjectCreationConfig from '../../../common/injects/create/InjectCreationConfig';
import articleContextForScenario from '../articles/articleContextForScenario';
import teamContextForScenario from '../teams/teamContextForScenario';

// Full-page inject creation for scenarios: the Threat-Arsenal-style contract
// picker stays on screen; selecting a contract opens the configuration form in
// a drawer (deep links with a :contractId still open the drawer on load).
const ScenarioInjectCreation: FunctionComponent = () => {
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { scenarioId, contractId } = useParams() as {
    scenarioId: Scenario['scenario_id'];
    contractId?: string;
  };
  const [searchParams] = useSearchParams();
  const presetInjectDuration = Math.max(0, parseInt(searchParams.get('duration') ?? '0', 10) || 0);
  const durationSuffix = presetInjectDuration > 0 ? `?duration=${presetInjectDuration}` : '';

  const injectContext = useContext(InjectContext);
  const listUrl = `/admin/scenarios/${scenarioId}/injects`;
  const pickerUrl = `${listUrl}/create`;

  const [selectedContractId, setSelectedContractId] = useState<string | null>(contractId ?? null);
  const closeConfig = () => {
    setSelectedContractId(null);
    // Normalize deep-linked URLs back to the picker.
    if (contractId) {
      navigate(`${pickerUrl}${durationSuffix}`, { replace: true });
    }
  };

  const { scenario, articles, variables } = useHelper(
    (helper: ScenariosHelper & ArticlesHelper & ChallengeHelper & VariablesHelper) => ({
      scenario: helper.getScenario(scenarioId),
      articles: helper.getScenarioArticles(scenarioId),
      variables: helper.getScenarioVariables(scenarioId),
    }),
  );
  useDataLoader(() => {
    dispatch(fetchScenarioTeams(scenarioId));
    dispatch(fetchVariablesForScenario(scenarioId));
    dispatch(fetchScenarioDocuments(scenarioId));
  });

  const articleContext = articleContextForScenario(scenarioId);
  const teamContext = teamContextForScenario(scenarioId, scenario.scenario_teams_users, scenario.scenario_all_users_number, scenario.scenario_users_number);
  const endpointContext = endpointContextForScenario(scenarioId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchScenarioChallenges(scenarioId)) };

  const onCreateInject = async (data: InjectInput) => {
    await injectContext.onAddInject(data as Inject);
    navigate(listUrl);
  };

  const onQuickAdd = async (contracts: InjectorContractFullOutput[]) => {
    const quickInjects: InjectInput[] = contracts.map(contract => ({
      inject_title: tPick(contract.injector_contract_labels),
      inject_injector_contract: contract.injector_contract_id,
      inject_depends_duration: presetInjectDuration,
    }));
    await injectContext.onAddMultipleInjects(quickInjects);
    navigate(listUrl);
  };

  return (
    <ArticleContext.Provider value={articleContext}>
      <TeamContext.Provider value={teamContext}>
        <EndpointContext.Provider value={endpointContext}>
          <ChallengeContext.Provider value={challengeContext}>
            <InjectContractPicker
              title={t('Create a new inject')}
              onSelectContract={contract => setSelectedContractId(contract.injector_contract_id)}
              onQuickAdd={onQuickAdd}
            />
            <Drawer
              open={!!selectedContractId}
              handleClose={closeConfig}
              title={t('Create a new inject')}
              disableEnforceFocus
            >
              {selectedContractId
                ? (
                    <InjectCreationConfig
                      contractId={selectedContractId}
                      onCreateInject={onCreateInject}
                      onBack={closeConfig}
                      presetInjectDuration={presetInjectDuration}
                      articlesFromExerciseOrScenario={articles}
                      uriVariable={listUrl}
                      variablesFromExerciseOrScenario={variables}
                    />
                  )
                : null}
            </Drawer>
          </ChallengeContext.Provider>
        </EndpointContext.Provider>
      </TeamContext.Provider>
    </ArticleContext.Provider>
  );
};

export default ScenarioInjectCreation;
