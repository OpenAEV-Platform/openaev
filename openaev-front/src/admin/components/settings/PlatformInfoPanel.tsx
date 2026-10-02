import { Box, ButtonBase, List, ListItem, ListItemText, Paper, Tooltip } from '@mui/material';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import ItemCopy from '../../../components/ItemCopy';
import type { PlatformSettings } from '../../../utils/api-types';
import useAI from '../../../utils/hooks/useAI';
import { copyToClipboard } from '../../../utils/utils';

interface PlatformInfoPanelProps {
  settings: PlatformSettings;
  topContent?: ReactNode;
  bottomContent?: ReactNode;
}

const PlatformInfoPanel = ({ settings, topContent, bottomContent }: PlatformInfoPanelProps) => {
  const { t } = useFormatter();
  const { enabled: aiEnabled, xtmOneConfigured } = useAI();
  const isEnterpriseEditionValid = settings.platform_license?.license_is_validated;

  const editionLabel = isEnterpriseEditionValid ? t('Enterprise') : t('Community');
  const version = settings.platform_version?.replace('-SNAPSHOT', '');
  const commit = settings.platform_commit;

  const resolveAiLabel = () => {
    if (!aiEnabled) {
      return t('Disabled');
    }
    if (settings.platform_ai_has_token) {
      return settings.platform_ai_type;
    }
    return `${settings.platform_ai_type} - ${t('Missing token')}`;
  };
  const aiLabel = resolveAiLabel();

  const aiTooltip = settings.platform_ai_has_token
    ? `${settings.platform_ai_type} - ${settings.platform_ai_model}`
    : t('The token is missing in your platform configuration, please ask your Filigran representative to provide you with it or with on-premise deployment instructions. Your can open a support ticket to do so.');

  return (
    <Paper
      variant="outlined"
      sx={{
        padding: theme => `${theme.spacing(1)}`,
        flex: 1,
      }}
    >
      <List sx={{ padding: 0 }}>
        {topContent}
        <ListItem divider>
          <ListItemText primary={t('Platform identifier')} />
          <pre
            style={{
              padding: 0,
              margin: 0,
            }}
            key={settings.platform_id}
          >
            <ItemCopy content={settings.platform_id ?? ''} variant="inLine" />
          </pre>
        </ListItem>
        <ListItem divider>
          <ListItemText primary={t('Version')} />
          {commit ? (
            <Tooltip
              slotProps={{ tooltip: { sx: { maxWidth: 'none' } } }}
              title={(
                // The theme lowercases tooltip text, which would mangle the labels
                <Box sx={{ textTransform: 'none' }}>
                  <div>{`${t('Version')}: ${version}`}</div>
                  <div>{`${t('Commit hash')}: ${commit}`}</div>
                </Box>
              )}
            >
              <ButtonBase
                onClick={() => copyToClipboard(t, `${version}#${commit}`)}
                sx={{ '& .MuiChip-root': { cursor: 'pointer' } }}
              >
                <ItemBoolean variant="large" status={null} neutralLabel={version} />
              </ButtonBase>
            </Tooltip>
          ) : (
            <ItemBoolean variant="large" status={null} neutralLabel={version} />
          )}
        </ListItem>
        <ListItem divider>
          <ListItemText primary={t('Edition')} />
          <ItemBoolean variant="large" neutralLabel={editionLabel} status={null} />
        </ListItem>
        {!xtmOneConfigured && (
          <ListItem divider={!!bottomContent}>
            <ListItemText primary={t('AI Powered')} />
            <ItemBoolean
              variant="large"
              label={aiLabel}
              status={aiEnabled && settings.platform_ai_has_token}
              tooltip={aiTooltip}
            />
          </ListItem>
        )}
        {bottomContent}
      </List>
    </Paper>
  );
};

export default PlatformInfoPanel;
