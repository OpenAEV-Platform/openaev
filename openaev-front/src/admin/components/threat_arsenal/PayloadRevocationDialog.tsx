import { Button, Text, Textarea } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type PayloadVersionOutput } from '../../../utils/api-types';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  open: boolean;
  name: string;
  actionId: string;
  payloadId?: string;
  onClose: () => void;
  onSubmit: (payloadId: string, reason: string) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, actionId, payloadId, onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [reason, setReason] = useState('');
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [selected, setSelected] = useState(payloadId ?? '');

  useEffect(() => {
    if (!open) {
      return;
    }
    setSelected(payloadId ?? '');
    fetchThreatArsenalVersions(actionId)
      .then((response: { data: PayloadVersionOutput[] }) => setVersions(response.data
        .filter(v => v.payload_id && v.payload_installed && !v.payload_revoked)))
      .catch(() => setVersions([]));
  }, [open, actionId, payloadId]);

  const version = versions.find(v => v.payload_id === selected);

  const close = () => {
    setReason('');
    onClose();
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={close}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${t('Revoke')} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {t('A revoked payload no longer runs in this tenant: injects using it are skipped and their scenarios cannot launch until it is lifted.')}
        </DialogContentText>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 16,
        }}
        >
          <PayloadVersionSelect
            value={version?.payload_version ?? ''}
            options={versions.map(v => ({
              version: v.payload_version ?? '',
              latest: v.payload_is_latest,
              installed: true,
            }))}
            onChange={value => setSelected(versions.find(v => v.payload_version === value)?.payload_id ?? '')}
            loading={versions.length === 0}
          />
          {version && (
            <Text variant="content-caption">
              {t('Used by {count} injects', { count: String(version.payload_usage_count) })}
            </Text>
          )}
        </div>
        <Textarea
          aria-label={t('Reason')}
          placeholder={t('Reason')}
          minRows={3}
          value={reason}
          onChange={event => setReason(event.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant="destructive"
          disabled={reason.trim() === '' || !version}
          onClick={() => {
            onSubmit(selected, reason.trim());
            setReason('');
          }}
        >
          {t('Revoke')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
