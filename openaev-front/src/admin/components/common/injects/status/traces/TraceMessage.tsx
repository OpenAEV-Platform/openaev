import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import type { LoggedHelper } from '../../../../../../actions/helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type ExecutionTraceOutput, type PlatformSettings } from '../../../../../../utils/api-types';
import EEChip from '../../../entreprise_edition/EEChip';

interface Props { traces: ExecutionTraceOutput[] }

const TraceMessage = ({ traces }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [expandedMessages, setExpandedMessages] = useState<Set<number>>(new Set());
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const sorted = [...traces].sort((a, b) => new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime());
  const truncateLength = 1000;

  const toggleMessage = (index: number) => {
    const updatedSet = new Set(expandedMessages);
    if (updatedSet.has(index)) {
      updatedSet.delete(index);
    } else {
      updatedSet.add(index);
    }
    setExpandedMessages(updatedSet);
  };

  return (
    <pre
      style={{
        marginTop: theme.spacing(1),
        padding: theme.spacing(0.1),
      }}
    >
      <ul style={{ padding: theme.spacing(0, 2) }}>
        {sorted.map((tr, index) => {
          const isExpanded = expandedMessages.has(index);
          const isTruncated = tr.execution_message.length > truncateLength;
          const displayMessage
            = isExpanded || !isTruncated
              ? tr.execution_message
              : `${tr.execution_message.slice(0, truncateLength)}`;
          return (
            <li
              key={index}
              style={{
                listStyle: 'none',
                marginBottom: theme.spacing(1),
              }}
            >
              {!settings.platform_license?.license_is_validated && tr.execution_message.startsWith('LICENSE RESTRICTION') && <EEChip clickable featureDetectedInfo={tr.execution_message.replace('LICENSE RESTRICTION - ', '')} />}
              <strong>{tr.execution_status}</strong>
              {' '}
              {displayMessage}
              {isTruncated && (
                <div
                  style={{
                    width: '100%',
                    display: 'flex',
                    justifyContent: 'center',
                  }}
                >
                  <Button
                    size="small"
                    onClick={() => toggleMessage(index)}
                    style={{
                      padding: theme.spacing(1, 2),
                      display: 'flex',
                      alignItems: 'center',
                      gap: theme.spacing(1),
                    }}
                  >
                    {isExpanded ? (
                      <>
                        <ArrowDropUpOutlined fontSize="small" />
                        {t('See Less')}
                      </>
                    ) : (
                      <>
                        <ArrowDropDownOutlined fontSize="small" />
                        {t('See More')}
                      </>
                    )}
                  </Button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </pre>
  );
};
export default TraceMessage;
