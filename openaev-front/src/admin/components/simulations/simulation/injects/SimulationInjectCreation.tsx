import { type FunctionComponent, useContext, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import {
  type Exercise,
  type Inject,
  type InjectInput,
  type InjectorContractFullOutput,
} from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForExercise from '../../../../../utils/context/endpoint/EndpointContextForExercise';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { ArticleContext, ChallengeContext, InjectContext, TeamContext } from '../../../common/Context';
import InjectContractPicker from '../../../common/injects/create/InjectContractPicker';
import InjectCreationConfig from '../../../common/injects/create/InjectCreationConfig';
import articleContextForExercise from '../articles/articleContextForExercise';
import teamContextForExercise from '../teams/teamContextForExercise';

// Full-page inject creation for simulations: the Threat-Arsenal-style contract
// picker stays on screen; selecting a contract opens the configuration form in
// a drawer (deep links with a :contractId still open the drawer on load).
const SimulationInjectCreation: FunctionComponent = () => {
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { exerciseId, contractId } = useParams() as {
    exerciseId: Exercise['exercise_id'];
    contractId?: string;
  };
  const [searchParams] = useSearchParams();
  const presetInjectDuration = Math.max(0, parseInt(searchParams.get('duration') ?? '0', 10) || 0);
  const durationSuffix = presetInjectDuration > 0 ? `?duration=${presetInjectDuration}` : '';

  const injectContext = useContext(InjectContext);
  const listUrl = `/admin/simulations/${exerciseId}/injects`;
  const pickerUrl = `${listUrl}/create`;

  const [selectedContractId, setSelectedContractId] = useState<string | null>(contractId ?? null);
  const closeConfig = () => {
    setSelectedContractId(null);
    // Normalize deep-linked URLs back to the picker.
    if (contractId) {
      navigate(`${pickerUrl}${durationSuffix}`, { replace: true });
    }
  };

  const { exercise, articles, variables } = useHelper(
    (helper: ExercisesHelper & ArticlesHelper & ChallengeHelper & VariablesHelper) => ({
      exercise: helper.getExercise(exerciseId),
      articles: helper.getExerciseArticles(exerciseId),
      variables: helper.getExerciseVariables(exerciseId),
    }),
  );
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchVariablesForExercise(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
  });

  const articleContext = articleContextForExercise(exerciseId);
  const teamContext = teamContextForExercise(exerciseId, exercise.exercise_teams_users, exercise.exercise_all_users_number, exercise.exercise_users_number);
  const endpointContext = endpointContextForExercise(exerciseId);
  const challengeContext = useMemo(() => ({ fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) }), [dispatch, exerciseId]);

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

export default SimulationInjectCreation;
