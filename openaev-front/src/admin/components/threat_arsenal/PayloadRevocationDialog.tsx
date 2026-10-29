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
  /** Lifting lists only the versions this tenant revoked: a list's revocation is lifted by a list. */
  lift?: boolean;
  /** A customized payload also lists the versions revoked through their Filigran base. */
  customized?: boolean;
  onClose: () => void;
  onSubmit: (payloadId: string, reason: string, inherited: boolean) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, actionId, payloadId, lift = false, customized = false, onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [reason, setReason] = useState('');
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [selected, setSelected] = useState(payloadId ?? '');

  useEffect(() => {
    if (!open) {
      return;
    }
    setVersions(null);
    fetchThreatArsenalVersions(actionId)
      .then((response: { data: PayloadVersionOutput[] }) => {
        const shown = response.data.filter(v => v.payload_id && v.payload_installed
          && (lift ? v.payload_revoked_in_tenant || (customized && v.payload_revoked) : !v.payload_revoked));
        setVersions(shown);
        setSelected(shown.some(v => v.payload_id === payloadId) ? payloadId ?? '' : shown[0]?.payload_id ?? '');
      })
      .catch(() => setVersions([]));
  }, [open, actionId, payloadId, lift, customized]);

  const version = versions?.find(v => v.payload_id === selected);
  const inherited = lift && customized && !!version && !version.payload_revoked_in_tenant;
  const needsReason = !lift || inherited;

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
      <DialogTitle>{`${t(lift ? 'Lift revocation' : 'Revoke')} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {lift
            ? t(inherited ? 'Revoked through its Filigran base. Lifting it lets this version run again in this tenant.' : 'The chosen version runs again in this tenant.')
            : t('A revoked payload no longer runs in this tenant: injects using it are skipped and their scenarios cannot launch until it is lifted.')}
        </DialogContentText>
        {versions?.length === 0 ? (
          <Text variant="content-base">{lift ? t('No version is revoked in this tenant.') : t('No version left to revoke.')}</Text>
        ) : (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 16,
          }}
          >
            <PayloadVersionSelect
              value={version?.payload_version ?? ''}
              options={(versions ?? []).map(v => ({
                version: v.payload_version ?? '',
                latest: v.payload_is_latest,
                installed: true,
                revoked: lift,
              }))}
              onChange={value => setSelected(versions?.find(v => v.payload_version === value)?.payload_id ?? '')}
              loading={versions === null}
            />
            {version && (
              <Text variant="content-caption">
                {t('Used by {count} injects', { count: String(version.payload_usage_count) })}
              </Text>
            )}
          </div>
        )}
        {needsReason && versions?.length !== 0 && (
          <Textarea
            aria-label={t(inherited ? 'Why this customized version is safe to run' : 'Reason')}
            placeholder={t(inherited ? 'Why this customized version is safe to run' : 'Reason')}
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={lift ? 'default' : 'destructive'}
          disabled={(needsReason && reason.trim() === '') || !version}
          onClick={() => {
            onSubmit(selected, reason.trim(), inherited);
            setReason('');
          }}
        >
          {t(lift ? 'Lift revocation' : 'Revoke')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
