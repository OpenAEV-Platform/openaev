import { type FunctionComponent, useContext } from 'react';

import ContextLink from '../../../components/ContextLink';
import { ATOMIC_BASE_URL, SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import { type RelatedFindingOutput } from '../../../utils/api-types';
import { AbilityContext } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

interface Props {
  finding: RelatedFindingOutput;
  type: string;
}

const FindingContextLink: FunctionComponent<Props> = ({ finding, type }) => {
  const ability = useContext(AbilityContext);

  switch (type) {
    case INJECT: {
      const title = finding.finding_inject?.inject_title;
      const injectId = finding.finding_inject?.inject_id;
      const simulationId = finding.finding_simulation?.exercise_id;

      if (!title || !injectId) return '-';

      const isAtomic = !simulationId;
      const url = isAtomic
        ? `${ATOMIC_BASE_URL}/${injectId}`
        : `${SIMULATION_BASE_URL}/${simulationId}/injects/${injectId}`;

      const userRight = isAtomic
        ? (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectId))
        : ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_simulation?.exercise_id);

      return userRight ? <ContextLink title={title} url={url} /> : title;
    }

    case SIMULATION: {
      const title = finding.finding_simulation?.exercise_name;
      const id = finding.finding_simulation?.exercise_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_simulation?.exercise_id) ? <ContextLink title={title} url={`${SIMULATION_BASE_URL}/${id}`} /> : title;
    }

    case SCENARIO: {
      const title = finding.finding_scenario?.scenario_name;
      const id = finding.finding_scenario?.scenario_id;

      if (!title || !id) return '-';

      return ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, finding.finding_scenario?.scenario_id) ? <ContextLink title={title} url={`${SCENARIO_BASE_URL}/${id}`} /> : title;
    }

    default:
      return '-';
  }
};

export default FindingContextLink;
