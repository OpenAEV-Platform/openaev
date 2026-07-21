import { Box, Paper } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext } from 'react';

import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import KillChainTimeline from '../../../../components/common/detail/KillChainTimeline';
import ContextLink from '../../../../components/ContextLink';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import ItemTags from '../../../../components/ItemTags';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import TypeAffinityChip from '../../../../components/TypeAffinityChip';
import { SCENARIO_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Exercise, type KillChainPhase } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface Props {
  exercise: Exercise;
  /** When true, drop the outer Paper (the parent SectionBlock already frames it). */
  embedded?: boolean;
}

// Compact simulation information card, matching the scenario overview: a
// full-width description above an auto-fitting grid of fields, so it stays
// dense and its Paper can bottom-align with the results card beside it.
const SimulationMainInformation: FunctionComponent<Props> = ({ exercise, embedded = false }) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const sortByOrder = R.sortWith([R.ascend(R.prop('phase_order'))]);
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(exercise.exercise_scenario || '') }));
  const killChainPhases = sortByOrder(exercise.exercise_kill_chain_phases ?? []) as KillChainPhase[];

  const renderScenarioContent = () => {
    if (!scenario) {
      return '-';
    }
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, scenario.scenario_id)) {
      return (
        <ContextLink
          title={scenario.scenario_name}
          url={`${SCENARIO_BASE_URL}/${scenario.scenario_id}`}
        />
      );
    }
    return scenario.scenario_name;
  };

  const content = (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Field label={t('Description')}>
        {exercise.exercise_description
          ? <ExpandableMarkdown source={exercise.exercise_description} limit={500} />
          : '-'}
      </Field>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: 'repeat(2, minmax(0, 1fr))',
          md: 'repeat(3, minmax(0, 1fr))',
        },
        columnGap: 3,
        rowGap: 2,
      }}
      >
        <Field label={t('Parent scenario')}>{renderScenarioContent()}</Field>
        <Field label={t('Severity')}>
          <ItemSeverity severity={exercise.exercise_severity} label={t(exercise.exercise_severity ?? 'Unknown')} />
        </Field>
        <Field label={t('Category')}>
          <ItemCategory category={exercise?.exercise_category ?? ''} label={t(exercise.exercise_category ?? 'Unknown')} />
        </Field>
        <Field label={t('Main Focus')}>
          <ItemMainFocus mainFocus={exercise?.exercise_main_focus ?? ''} label={t(exercise.exercise_main_focus ?? 'Unknown')} />
        </Field>
        <Field label={t('Type Affinity')}>
          <TypeAffinityChip affinity_text={scenario?.scenario_type_affinity} />
        </Field>
        <Field label={t('Platforms')}>
          <PlatformIconGroup platforms={exercise.exercise_platforms} width={25} />
        </Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={exercise.exercise_tags} limit={10} />
        </Field>
        <Box sx={{ gridColumn: '1 / -1' }}>
          <Field label={t('Kill Chain Phases')}>
            <KillChainTimeline phases={killChainPhases} />
          </Field>
        </Box>
      </Box>
    </Box>
  );

  if (embedded) {
    return content;
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        padding: 2,
        borderRadius: 1,
        height: '100%',
      }}
    >
      {content}
    </Paper>
  );
};

export default SimulationMainInformation;
