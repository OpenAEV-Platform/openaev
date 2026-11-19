import { Button, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';
import useRevocationForm from './useRevocationForm';

interface Props {
  open: boolean;
  name: string;
  lift: boolean;
  // The versions this dialog can revoke, or lift.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  // The action of the card: the list shows the latest version of each payload only.
  actionId: string;
  onClose: () => void;
  onDone: (version: PayloadVersionOutput) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, lift, versions, currentPayloadId, actionId, onClose, onDone }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { selected, setSelected, version, usages, reason, setReason, canSubmit, submitting, submit } = useRevocationForm({
    open,
    lift,
    versions,
    currentPayloadId,
    onDone,
  });
  const { loaded, usageCount, impact } = usages;

  const submitLabel = lift ? t('Lift revocation') : t('Revoke');

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={onClose}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${submitLabel} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: theme.spacing(1.5) }}>
          {lift
            ? t('The chosen version runs again in this tenant.')
            : t('Revoked versions don\'t run. Anything using this version will fail until you lift the revocation.')}
        </DialogContentText>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1.5),
          marginBottom: theme.spacing(2),
        }}
        >
          <PayloadVersionSelect value={selected} versions={versions} onChange={setSelected} />
          {loaded && (
            <Text variant="content-caption">
              {usageCount === 1 ? t('Used by 1 inject') : t('Used by {count} injects', { count: String(usageCount) })}
            </Text>
          )}
        </div>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(1),
          marginBottom: theme.spacing(2),
        }}
        >
          <Text variant="content-base-bold">{lift ? t('Runs again in:') : t('What will fail:')}</Text>
          {!loaded && <Spinner size="sm" />}
          {loaded && usageCount === 0 && (
            <Text variant="content-base">{t('Nothing fails: v{version} isn\'t used.', { version: version?.payload_version ?? '' })}</Text>
          )}
          {loaded && usageCount > 0 && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1.5),
              flexWrap: 'wrap',
            }}
            >
              <Text variant="content-base">{impact}</Text>
              {version && (
                <Link
                  to={`/admin/threat-arsenal?action=${encodeURIComponent(actionId)}&tab=versions`}
                  target="_blank"
                  rel="noopener"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: theme.spacing(0.5),
                  }}
                >
                  {t('See where it is used')}
                  <OpenInNewOutlined fontSize="inherit" />
                </Link>
              )}
            </div>
          )}
        </div>
        {!lift && (
          <Textarea
            aria-label={t('Reason')}
            placeholder={t('Reason')}
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={lift ? undefined : 'destructive'}
          disabled={!canSubmit}
          onClick={submit}
        >
          {submitting ? <Spinner size="sm" /> : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
