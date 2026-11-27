import { PlayCircleOutlineOutlined, RouteOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import ContextLink from '../../../components/ContextLink';
import { useFormatter } from '../../../components/i18n';
import { ATOMIC_BASE_URL, SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import { type FindingOccurrenceOutput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const chipIconStyle = { fontSize: '1rem' };

interface Props {
  occurrence: FindingOccurrenceOutput;
  type: string;
}

const FindingContextLink: FunctionComponent<Props> = ({ occurrence, type }) => {
  const ability = useAbility();
  const { t } = useFormatter();

  switch (type) {
    case INJECT: {
      const title = occurrence.finding_occurrence_inject?.inject_title;
      const injectId = occurrence.finding_occurrence_inject?.inject_id;
      const simulationId = occurrence.finding_occurrence_simulation?.context_id;

      // The inject was deleted, or its simulation reset: the occurrence keeps its date and field
      if (!title || !injectId) return t('No longer linked to an inject');

      const isAtomic = !simulationId;
      const url = isAtomic
        ? `${ATOMIC_BASE_URL}/${injectId}`
        : `${SIMULATION_BASE_URL}/${simulationId}/injects/${injectId}`;

      const userRight = isAtomic
        ? (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectId))
        : ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, simulationId);

      // TrackChangesOutlined is the platform-wide inject icon (menus, hero
      // stats, inject results): keep this pivot aligned with it.
      return userRight ? <ContextLink title={title} url={url} icon={<TrackChangesOutlined style={chipIconStyle} />} /> : title;
    }

    case SIMULATION: {
      const title = occurrence.finding_occurrence_simulation?.context_name;
      const id = occurrence.finding_occurrence_simulation?.context_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, id) ? <ContextLink title={title} url={`${SIMULATION_BASE_URL}/${id}`} icon={<PlayCircleOutlineOutlined style={chipIconStyle} />} /> : title;
    }

    case SCENARIO: {
      const title = occurrence.finding_occurrence_scenario?.context_name;
      const id = occurrence.finding_occurrence_scenario?.context_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, id) ? <ContextLink title={title} url={`${SCENARIO_BASE_URL}/${id}`} icon={<RouteOutlined style={chipIconStyle} />} /> : title;
    }

    default:
      return '-';
  }
};

export default FindingContextLink;
