import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { HubOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import useExplorationGraph from '../../../utils/hooks/useExplorationGraph';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { graphHref, type GraphNodeType } from './graph-model';

interface Props {
  type: GraphNodeType;
  entityId: string;
}

/**
 * "Explore in graph": the hero icon button of every entity the graph knows. It sits with
 * the other hero icon buttons, before the kebab, and opens the Graph page focused on the entity
 * with its first ring of neighbours. Hidden for a user who cannot open the Graph page, and while
 * the EXPLORATION_GRAPH preview feature is off. A real link, so ctrl-click and "open in new tab" work.
 */
const ExploreInGraphButton = ({ type, entityId }: Props) => {
  const { t } = useFormatter();
  const graphEnabled = useExplorationGraph();
  if (!graphEnabled) {
    return null;
  }
  return (
    <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            asChild
            icon={<HubOutlined fontSize="small" />}
            aria-label={t('Explore in graph')}
            priority="tertiary"
            size="md"
          >
            <Link to={graphHref(type, entityId)} />
          </IconButton>
        </TooltipTrigger>
        <TooltipContent>{t('Explore in graph')}</TooltipContent>
      </Tooltip>
    </Can>
  );
};

export default ExploreInGraphButton;
