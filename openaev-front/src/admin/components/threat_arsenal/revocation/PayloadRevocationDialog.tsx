import { Button, Checkbox, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogTitle, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';
import { usageCount } from '../versions/usages/usageCounts';
import useUsageImpact from '../versions/usages/useUsageImpact';
import useRevocationForm from './useRevocationForm';

interface Props {
  open: boolean;
  name: string;
  lift: boolean;
  // The versions this dialog can revoke, or lift. Revoking, the others are where its injects can go.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  // The action of the card: the list shows the latest version of each payload only.
  actionId: string;
  onClose: () => void;
  onDone: (version: PayloadVersionOutput) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, lift, versions, currentPayloadId, actionId, onClose, onDone }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const theme = useTheme();
  const {
    selected, setSelected, version, usages, reason, setReason, canReplace, replace, setReplace, replacement, setReplacement, replacements,
    canSubmit, submitting, submit,
  } = useRevocationForm({
    open,
    lift,
    versions,
    currentPayloadId,
    onDone,
  });
  const impact = useUsageImpact(usages);
  const count = usages ? usageCount(usages) : 0;

  const submitLabel = lift ? t('Lift revocation') : t('Revoke');

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent>
        <DialogTitle>{`${submitLabel} ${name}`}</DialogTitle>
        <DialogDescription>
          {lift
            ? t('The chosen version runs again in this tenant.')
            : t('Revoked versions don\'t run. Anything using this version will fail until you lift the revocation.')}
        </DialogDescription>
        <DialogBody>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(2),
          }}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1.5),
            }}
            >
              <PayloadVersionSelect value={selected} versions={versions} onChange={setSelected} />
              {usages && <Text variant="content-caption">{counted(count, 'Used by 1 inject', 'Used by {count} injects')}</Text>}
            </div>
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(1),
            }}
            >
              <Text variant="content-base-bold">{lift ? t('Runs again in:') : t('What will fail:')}</Text>
              {!usages && <Spinner size="sm" />}
              {usages && count === 0 && (
                <Text variant="content-base">{t('Nothing fails: v{version} isn\'t used.', { version: version?.payload_version ?? '' })}</Text>
              )}
              {usages && count > 0 && (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: theme.spacing(1.5),
                  flexWrap: 'wrap',
                }}
                >
                  <Text variant="content-base">{impact}</Text>
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
                </div>
              )}
            </div>
            {canReplace && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1.5),
                flexWrap: 'wrap',
              }}
              >
                <Checkbox checked={replace} onCheckedChange={checked => setReplace(checked === true)} label={t('Move them first to')} />
                <PayloadVersionSelect value={replacement} versions={replacements} onChange={setReplacement} />
              </div>
            )}
            {!lift && (
              <Textarea
                aria-label={t('Reason')}
                placeholder={t('Reason')}
                minRows={3}
                value={reason}
                onChange={event => setReason(event.target.value)}
              />
            )}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button
            type="button"
            variant={lift ? undefined : 'destructive'}
            disabled={!canSubmit}
            onClick={submit}
          >
            {submitting ? <Spinner size="sm" /> : submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
