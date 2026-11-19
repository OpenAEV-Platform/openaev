import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { HubOutlined } from '@mui/icons-material';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { graphHref, type GraphNodeType } from './graph-model';

interface Props {
  type: GraphNodeType;
  entityId: string;
}

/**
 * "Explore in graph" (#9388): the hero icon button of every entity the graph knows. It sits with
 * the other hero icon buttons, before the kebab, and opens the Graph page focused on the entity
 * with its first ring of neighbours. Hidden for a user who cannot open the Graph page.
 */
const ExploreInGraphButton = ({ type, entityId }: Props) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  return (
    <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<HubOutlined fontSize="small" />}
            aria-label={t('Explore in graph')}
            onClick={() => navigate(graphHref(type, entityId))}
            priority="tertiary"
            size="md"
          />
        </TooltipTrigger>
        <TooltipContent>{t('Explore in graph')}</TooltipContent>
      </Tooltip>
    </Can>
  );
};

export default ExploreInGraphButton;
