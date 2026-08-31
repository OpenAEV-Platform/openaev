import { DarkModeOutlined, ImageOutlined, LightModeOutlined } from '@mui/icons-material';
import { Box, Paper, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useCallback, useState } from 'react';
import { useParams } from 'react-router';

import { fetchDocumentsChannels, updateChannel, updateChannelLogos } from '../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { type DocumentHelper } from '../../../../actions/helper';
import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import { DetailSections, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Channel as ChannelType, type ChannelUpdateInput, type Document } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can, useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import ChannelAddLogo from './ChannelAddLogo';
import ChannelParametersForm from './ChannelParametersForm';
import ChannelPreview from './ChannelPreview';

// One themed logo slot of the Branding section: the logo is displayed on a
// surface matching its target theme (always-dark / always-white), so both
// variants are readable whatever the current app theme is.
const LogoTile = ({ label, mode, src, action }: {
  label: string;
  mode: 'dark' | 'light';
  src: string | null;
  action: ReactNode;
}) => {
  const { t } = useFormatter();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
    }}
    >
      <Typography
        variant="h3"
        sx={{
          fontSize: 12,
          margin: 0,
        }}
      >
        {label}
      </Typography>
      <Box sx={{
        height: 120,
        borderRadius: 1,
        border: theme => `1px solid ${theme.palette.divider}`,
        // Fixed surfaces on purpose: each tile previews the logo in its own
        // target theme, independently from the current app theme.
        backgroundColor: mode === 'dark' ? '#070d19' : '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 1,
        overflow: 'hidden',
      }}
      >
        {src
          ? (
              <img
                src={src}
                alt={label}
                style={{
                  maxHeight: 90,
                  maxWidth: '85%',
                  objectFit: 'contain',
                }}
              />
            )
          : (
              <>
                <ImageOutlined sx={{
                  fontSize: 28,
                  color: mode === 'dark' ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.3)',
                }}
                />
                <Typography sx={{
                  fontSize: 12,
                  color: mode === 'dark' ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)',
                }}
                >
                  {t('No logo yet')}
                </Typography>
              </>
            )}
      </Box>
      {action}
    </div>
  );
};

const Channel = () => {
  const { channelId } = useParams() as { channelId: ChannelType['channel_id'] };
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();

  const { channel, documentsMap } = useHelper((helper: ChannelsHelper & DocumentHelper) => ({
    channel: helper.getChannel(channelId) as ChannelType,
    documentsMap: helper.getDocumentsMap() as Record<string, Document>,
  }));
  useDataLoader(() => {
    dispatch(fetchDocumentsChannels(channelId));
  });

  // The preview follows the app theme by default but can be flipped to check
  // the other variant without switching the whole platform theme.
  const [previewMode, setPreviewMode] = useState<'dark' | 'light'>(theme.palette.mode === 'dark' ? 'dark' : 'light');
  // Unsaved form edits, streamed by the parameters form for a live preview.
  const [liveValues, setLiveValues] = useState<Partial<ChannelUpdateInput> | null>(null);
  const handleLiveChange = useCallback((values: Partial<ChannelUpdateInput>) => setLiveValues(values), []);

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.CHANNELS);

  const submitUpdate = (data: ChannelUpdateInput) => dispatch(updateChannel(channelId, data));
  const submitLogo = (documentId: string, logoTheme: 'dark' | 'light') => {
    const data = {
      channel_logo_dark: logoTheme === 'dark' ? documentId : channel.channel_logo_dark,
      channel_logo_light: logoTheme === 'light' ? documentId : channel.channel_logo_light,
    };
    return dispatch(updateChannelLogos(channelId, data));
  };

  const initialValues: ChannelUpdateInput = {
    channel_type: channel.channel_type ?? 'newspaper',
    channel_name: channel.channel_name ?? '',
    channel_description: channel.channel_description ?? '',
    channel_mode: channel.channel_mode ?? 'title',
    channel_primary_color_dark: channel.channel_primary_color_dark ?? '',
    channel_primary_color_light: channel.channel_primary_color_light ?? '',
    channel_secondary_color_dark: channel.channel_secondary_color_dark ?? '',
    channel_secondary_color_light: channel.channel_secondary_color_light ?? '',
  };

  const logoDark = channel.channel_logo_dark ? documentsMap[channel.channel_logo_dark] : null;
  const logoLight = channel.channel_logo_light ? documentsMap[channel.channel_logo_light] : null;

  const previewChannel = {
    ...initialValues,
    ...(liveValues ?? {}),
    logoDark,
    logoLight,
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
      paddingBottom: 40,
    }}
    >
      <DetailSections>
        {/* Left column: configuration */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {/* action={null} adopts the 32px header row so the Paper top-aligns
              with the Live preview column (whose header holds the theme
              toggle). */}
          <SectionBlock title={t('Parameters')} action={null}>
            <ChannelParametersForm
              initialValues={initialValues}
              onSubmit={submitUpdate}
              disabled={!canManage}
              onLiveChange={handleLiveChange}
            />
          </SectionBlock>
          <SectionBlock title={t('Branding')}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: 16,
            }}
            >
              <LogoTile
                label={t('Dark theme')}
                mode="dark"
                src={logoDark ? buildTenantApiPath(`/api/images/channels/id/${channelId}/dark`) : null}
                action={(
                  <Can I={ACTIONS.MANAGE} a={SUBJECTS.CHANNELS}>
                    <ChannelAddLogo handleAddLogo={documentId => submitLogo(documentId, 'dark')} />
                  </Can>
                )}
              />
              <LogoTile
                label={t('Light theme')}
                mode="light"
                src={logoLight ? buildTenantApiPath(`/api/images/channels/id/${channelId}/light`) : null}
                action={(
                  <Can I={ACTIONS.MANAGE} a={SUBJECTS.CHANNELS}>
                    <ChannelAddLogo handleAddLogo={documentId => submitLogo(documentId, 'light')} />
                  </Can>
                )}
              />
            </div>
          </SectionBlock>
        </div>
        {/* Right column: live front-page preview */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            minHeight: 32,
            marginBottom: 1.5,
          }}
          >
            <Typography sx={{
              ...SECTION_LABEL_SX,
              marginBottom: 0,
            }}
            >
              {t('Live preview')}
            </Typography>
            <Typography sx={{
              fontSize: 11,
              color: 'text.secondary',
            }}
            >
              {t('Unsaved edits are reflected instantly')}
            </Typography>
            <div style={{ flex: 1 }} />
            <ToggleButtonGroup
              size="small"
              exclusive
              value={previewMode}
              onChange={(_, value: 'dark' | 'light' | null) => value && setPreviewMode(value)}
              // The global MuiToggleButtonGroup override pins the group to
              // 36px; cap it (and its buttons) at the 32px header height so
              // this column's Paper top-aligns with the Parameters column
              // (same normalization as DetailHero).
              sx={{
                'height': 32,
                '& .MuiToggleButton-root': {
                  width: 32,
                  height: 32,
                },
              }}
            >
              <ToggleButton value="dark" aria-label={t('Dark theme')}>
                <Tooltip title={t('Dark theme')}>
                  <DarkModeOutlined fontSize="small" />
                </Tooltip>
              </ToggleButton>
              <ToggleButton value="light" aria-label={t('Light theme')}>
                <Tooltip title={t('Light theme')}>
                  <LightModeOutlined fontSize="small" />
                </Tooltip>
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>
          <Paper
            variant="outlined"
            sx={{
              padding: 1,
              borderRadius: 1,
              flex: 1,
            }}
          >
            <ChannelPreview channel={previewChannel} mode={previewMode} />
          </Paper>
        </div>
      </DetailSections>
    </div>
  );
};

export default Channel;
