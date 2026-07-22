import { KeyboardArrowDownOutlined, KeyboardArrowUpOutlined } from '@mui/icons-material';
import { Box, Button, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, useState } from 'react';

import type { LoggedHelper } from '../../../../../../actions/helper';
import { useFormatter } from '../../../../../../components/i18n';
import { FONT_FAMILY_CODE } from '../../../../../../components/Theme';
import { useHelper } from '../../../../../../store';
import { type ExecutionTraceOutput, type PlatformSettings } from '../../../../../../utils/api-types';
import { getStatusIconComponent } from '../../../../../../utils/statusIcons';
import { getAgentStatusTooltip, getTraceStatusLabel } from '../../../../../../utils/statusLabels';
import EEChip from '../../../entreprise_edition/EEChip';
import { type Severity, severityColor, severityForStatus } from './severity';

// `boxed` = each trace in its own tinted, left-accented card (default, used by
// the flat team/player log). `plain` = borderless rows, used inside the agent
// timeline where the timeline rail already provides the structure.
type TraceVariant = 'boxed' | 'plain';

interface Props {
  traces: ExecutionTraceOutput[];
  variant?: TraceVariant;
}

const TRUNCATE_LENGTH = 1000;

const TraceMessage = ({ traces, variant = 'boxed' }: Props) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const isPlain = variant === 'plain';

  const [expandedMessages, setExpandedMessages] = useState<Set<number>>(new Set());
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const sorted = [...traces].sort((a, b) => new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime());

  const toggleMessage = (index: number) => {
    const updatedSet = new Set(expandedMessages);
    if (updatedSet.has(index)) {
      updatedSet.delete(index);
    } else {
      updatedSet.add(index);
    }
    setExpandedMessages(updatedSet);
  };

  const messageStyle: CSSProperties = {
    margin: 0,
    fontFamily: FONT_FAMILY_CODE,
    fontSize: 12.5,
    lineHeight: 1.5,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
    color: theme.palette.text.primary,
  };

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: isPlain ? 0.5 : 1,
      marginTop: isPlain ? 0.5 : 1,
    }}
    >
      {sorted.map((tr, index) => {
        const severity: Severity = severityForStatus(tr.execution_status);
        const color = severityColor(theme, severity);
        const Icon = getStatusIconComponent(tr.execution_status);
        const label = getTraceStatusLabel(tr.execution_status);
        const tooltip = getAgentStatusTooltip(tr.execution_status);

        const isExpanded = expandedMessages.has(index);
        const isTruncated = tr.execution_message.length > TRUNCATE_LENGTH;
        const displayMessage = isExpanded || !isTruncated
          ? tr.execution_message
          : tr.execution_message.slice(0, TRUNCATE_LENGTH);
        const isLicenseRestricted = tr.execution_message.startsWith('LICENSE RESTRICTION');

        return (
          <Box
            key={index}
            sx={{
              display: 'flex',
              gap: 1.25,
              padding: isPlain ? 0 : 1.25,
              borderRadius: 1,
              ...(isPlain
                ? {}
                : {
                    borderLeft: `3px solid ${color}`,
                    backgroundColor: alpha(color, 0.06),
                  }),
            }}
          >
            <Icon sx={{
              fontSize: 18,
              color,
              flexShrink: 0,
              marginTop: '1px',
            }}
            />
            <div style={{
              flex: 1,
              minWidth: 0,
            }}
            >
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
                flexWrap: 'wrap',
                marginBottom: theme.spacing(0.5),
              }}
              >
                <Tooltip title={tooltip ? t(tooltip) : ''} disableHoverListener={!tooltip} arrow>
                  <Box
                    component="span"
                    sx={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      paddingInline: 0.75,
                      paddingBlock: 0.125,
                      borderRadius: 0.5,
                      backgroundColor: alpha(color, 0.12),
                      color,
                      fontSize: 10.5,
                      fontWeight: 700,
                      letterSpacing: '0.06em',
                      textTransform: 'uppercase',
                      cursor: tooltip ? 'help' : 'default',
                    }}
                  >
                    {label}
                  </Box>
                </Tooltip>
                <Typography
                  component="span"
                  sx={{
                    fontSize: 11,
                    color: 'text.secondary',
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {nsdt(tr.execution_time)}
                </Typography>
                {!settings.platform_license?.license_is_validated && isLicenseRestricted && (
                  <EEChip clickable featureDetectedInfo={tr.execution_message.replace('LICENSE RESTRICTION - ', '')} />
                )}
              </div>
              <pre style={messageStyle}>{displayMessage}</pre>
              {isTruncated && (
                <Button
                  size="small"
                  onClick={() => toggleMessage(index)}
                  startIcon={isExpanded ? <KeyboardArrowUpOutlined fontSize="small" /> : <KeyboardArrowDownOutlined fontSize="small" />}
                  sx={{ marginTop: 0.5 }}
                >
                  {isExpanded ? t('See Less') : t('See More')}
                </Button>
              )}
            </div>
          </Box>
        );
      })}
    </Box>
  );
};
export default TraceMessage;
