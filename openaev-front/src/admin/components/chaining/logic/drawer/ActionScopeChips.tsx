import { GroupsOutlined, InfoOutlined } from '@mui/icons-material';
import { Box, Chip, Tooltip, Typography } from '@mui/material';

import { useFormatter } from '../../../../../components/i18n';
import type { ScopeAssetOutput, ScopeTeamOutput } from '../../../../../utils/api-types';
import InjectFormSection from '../../../common/injects/form/InjectFormSection';

interface ActionScopeChipsProps {
  isPayload: boolean;
  /** Assets this action targets (empty when the contract is not asset-centric). */
  assets: ScopeAssetOutput[];
  /** Teams this action targets (empty when the contract is not team-centric). */
  teams?: ScopeTeamOutput[];
  /** When true, the action targets every team; a single "All teams" chip is shown instead. */
  allTeams?: boolean;
}

const ActionScopeChips = ({ isPayload, assets, teams = [], allTeams = false }: ActionScopeChipsProps) => {
  const { t } = useFormatter();

  const title = isPayload ? t('Initial Source Assets') : t('Initial Target');
  const tooltip = isPayload
    ? t('Additional endpoints may be included during simulation based on real decision logic.')
    : t('Additional targets may be included during simulation based on real decision logic.');
  const emptyLabel = isPayload ? t('No assets in the allow list.') : t('No targets configured.');

  const hasTargets = assets.length > 0 || allTeams || teams.length > 0;

  return (
    <InjectFormSection
      title={title}
      titleAdornment={(
        <Tooltip title={tooltip}>
          <InfoOutlined fontSize="small" color="info" />
        </Tooltip>
      )}
    >
      {hasTargets ? (
        <Box sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 0.5,
        }}
        >
          {assets.map(asset => (
            <Chip
              key={`asset-${asset.asset_id ?? ''}`}
              label={asset.asset_name ?? ''}
              size="small"
              variant="filled"
            />
          ))}
          {allTeams ? (
            <Chip
              key="team-all"
              icon={<GroupsOutlined />}
              label={t('All teams')}
              size="small"
              variant="filled"
            />
          ) : (
            teams.map(team => (
              <Chip
                key={`team-${team.team_id ?? ''}`}
                icon={<GroupsOutlined />}
                label={team.team_name ?? ''}
                size="small"
                variant="filled"
              />
            ))
          )}
        </Box>
      ) : (
        <Typography variant="body2" color="text.secondary">{emptyLabel}</Typography>
      )}
    </InjectFormSection>
  );
};

export default ActionScopeChips;
