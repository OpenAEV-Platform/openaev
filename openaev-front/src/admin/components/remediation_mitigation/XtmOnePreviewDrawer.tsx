// fds:keep-mui Drawer: the library has no drawer component yet
import { Chip, Switch, Text } from '@filigran/design-system';
import { LockOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Drawer } from '@mui/material';
import { type ReactNode, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type CollectorType, type ControlType } from './remediationMitigationMock';
import { type MockPayload } from './remediationMitigationStore';
import {
  buildMitigationRequest,
  buildRemediationRequest,
  MITIGATION_PROMPT,
  REMEDIATION_PROMPT,
  type RequestContext,
} from './xtmOneRequest';

// PROTOTYPE discussion tool: shows what WOULD be sent to XTM One. Nothing is sent.

interface Props {
  open: boolean;
  onClose: () => void;
  payload: MockPayload;
  context?: RequestContext;
  controlType?: Extract<ControlType, 'detection' | 'prevention'>;
  collector?: CollectorType;
}

const Json = ({ value }: { value: unknown }) => (
  <pre style={{
    margin: 0,
    padding: 10,
    borderRadius: 4,
    fontSize: 12,
    lineHeight: 1.45,
    overflowX: 'auto',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
    background: 'rgba(127,127,127,0.12)',
  }}
  >
    {JSON.stringify(value, null, 2)}
  </pre>
);

const Column = ({ title, subtitle, children }: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) => (
  <div style={{
    flex: 1,
    minWidth: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }}
  >
    <Text variant="content-base-bold">{title}</Text>
    <Text variant="content-caption" className="text-default-secondary">{subtitle}</Text>
    {children}
  </div>
);

const XtmOnePreviewDrawer = ({ open, onClose, payload, context, controlType, collector }: Props) => {
  const { t } = useFormatter();
  const [includeCommand, setIncludeCommand] = useState(true);
  const input = {
    payload,
    includeCommand,
    context,
    controlType,
    collector,
  };
  const thinContext = !includeCommand && payload.description.trim() === '';

  return (
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { sx: { width: 'min(1000px, 96vw)' } } }} sx={{ zIndex: theme => theme.zIndex.modal + 1 }}>
      <div style={{
        padding: 24,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
        }}
        >
          <Text variant="content-base-bold">{t('Content sent to XTM One')}</Text>
          <Chip label={t('Prototype - nothing is sent')} severity="medium" />
        </div>
        <Text variant="content-compact" className="text-default-secondary">
          {`${t('Payload')}: ${payload.name}`}
        </Text>

        <label style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <Switch checked={includeCommand} onCheckedChange={setIncludeCommand} />
          <Text variant="content-compact">{t('Include command')}</Text>
        </label>

        {thinContext && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
          >
            <WarningAmberOutlined fontSize="small" />
            <Text variant="content-compact">{t('Description is empty: remediation context would be thin.')}</Text>
          </div>
        )}

        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <LockOutlined fontSize="small" />
          <Text variant="content-compact">{`${t('finding value')}: ${t('never sent')}`}</Text>
        </div>

        <div style={{
          display: 'flex',
          gap: 16,
          alignItems: 'flex-start',
        }}
        >
          <Column title={t('Remediation request')} subtitle={t('No collector_type, no control_type.')}>
            <Json value={buildRemediationRequest(input)} />
          </Column>
          <Column title={t('Mitigation request')} subtitle={t('Adds control_type, and collector_type only when vendor-specific.')}>
            <Json value={buildMitigationRequest(input)} />
          </Column>
        </div>

        <details>
          <summary style={{ cursor: 'pointer' }}><Text variant="content-base-bold">{t('Remediation prompt (draft)')}</Text></summary>
          <Text variant="content-compact">{REMEDIATION_PROMPT}</Text>
        </details>
        <details>
          <summary style={{ cursor: 'pointer' }}><Text variant="content-base-bold">{t('Mitigation prompt (draft)')}</Text></summary>
          <Text variant="content-compact">{MITIGATION_PROMPT}</Text>
        </details>
      </div>
    </Drawer>
  );
};

export default XtmOnePreviewDrawer;
