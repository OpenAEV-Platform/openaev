import { Alert, Button, Checkbox, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogTitle, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { versionsTabLink } from '../hook/useOpenedAction';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';
import { usageCount } from '../versions/usages/usageCounts';
import useUsageImpact from '../versions/usages/useUsageImpact';
import useUsagesByVersion from '../versions/usages/useUsagesByVersion';
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

// Revoke or lift one version: what it does to what uses it, in one message, then the safe way to do it.
const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, lift, versions, currentPayloadId, actionId, onClose, onDone }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const {
    selected, setSelected, version, usages, reason, setReason, canReplace, replace, setReplace, replacement, setReplacement, replacements,
    canSubmit, submitting, submit, moving, moveCount,
  } = useRevocationForm({
    open,
    lift,
    versions,
    currentPayloadId,
    onDone,
  });
  const impact = useUsageImpact(usages);
  const count = usages ? usageCount(usages) : 0;
  const versionLabel = version?.payload_version ?? '';
  // What keeps running elsewhere, said even when revoking breaks nothing.
  const allUsages = useUsagesByVersion(open && !lift ? versions : []);
  const otherUsages = versions.filter(v => v.payload_id !== selected).reduce((sum, v) => sum + (allUsages[v.payload_id] ? usageCount(allUsages[v.payload_id]) : 0), 0);

  // The button says what happens: moving the injects first is part of the action.
  let submitLabel = lift ? t('Lift revocation') : t('Revoke');
  if (moving) submitLabel = counted(moveCount, 'Move 1 inject and revoke', 'Move {count} injects and revoke');

  // Where the version is used, one click away (new tab, the dialog stays).
  const seeWhere = (
    <Button asChild priority="secondary" size="sm">
      <Link to={versionsTabLink(actionId)} target="_blank" rel="noopener">
        {t('See where')}
        <OpenInNewOutlined fontSize="small" style={{ marginLeft: theme.spacing(0.5) }} />
      </Link>
    </Button>
  );

  // The impact in one message, its tone matching reality: a warning when something fails, a check when
  // nothing does. Lifting says what runs again.
  const renderImpact = () => {
    if (!version) return null;
    if (!usages) return <Spinner size="sm" />;
    if (lift) {
      return (
        <Alert
          elevation={1}
          severity="info"
          title={t('v{version} runs again in this tenant.', { version: versionLabel })}
          description={count > 0 ? impact : undefined}
          action={count > 0 ? seeWhere : undefined}
        />
      );
    }
    if (count === 0) {
      return (
        <Alert
          elevation={1}
          severity="success"
          title={t('Nothing fails: v{version} isn\'t used.', { version: versionLabel })}
          description={otherUsages > 0
            ? counted(otherUsages, '1 inject keeps running on another version.', '{count} injects keep running on other versions.')
            : undefined}
        />
      );
    }
    return (
      <Alert
        elevation={1}
        severity="warning"
        title={t('Revoked versions don\'t run. What uses v{version} will fail:', { version: versionLabel })}
        description={impact}
        action={seeWhere}
      />
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent>
        {/* A short title; the payload's name below it, on one line (in full on hover). */}
        <DialogTitle>{lift ? t('Lift a revocation') : t('Revoke a version')}</DialogTitle>
        <DialogDescription
          title={name}
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {name}
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
              <Text variant="content-base">{t('Version')}</Text>
              <PayloadVersionSelect value={selected} versions={versions} onChange={setSelected} />
            </div>
            {renderImpact()}
            {/* The safe way, right under the impact: move the injects first, so nothing fails. */}
            {canReplace && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1.5),
                flexWrap: 'wrap',
              }}
              >
                <Checkbox
                  checked={replace}
                  onCheckedChange={checked => setReplace(checked === true)}
                  label={counted(moveCount, 'Move it first to', 'Move them first to')}
                />
                <PayloadVersionSelect value={replacement} versions={replacements} onChange={setReplacement} />
                <Text variant="content-caption" className="text-default-secondary">{t('so nothing fails')}</Text>
              </div>
            )}
            {!lift && (
              <Textarea
                label={t('Reason')}
                required
                placeholder={t('Why this version is unsafe')}
                minRows={2}
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
