import { Alert, Button, Checkbox, Spinner, Text, Textarea } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';
import { usageCount } from '../versions/usages/usageCounts';
import useUsageImpact from '../versions/usages/useUsageImpact';
import useUsagesByVersion from '../versions/usages/useUsagesByVersion';
import PayloadVersionDialog from './PayloadVersionDialog';
import UsagesLink from './UsagesLink';
import useRevokeForm from './useRevokeForm';

interface Props {
  open: boolean;
  name: string;
  // The versions this dialog can revoke; the others are where its injects can go.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  // The action of the card: the list shows the latest version of each payload only.
  actionId: string;
  onClose: () => void;
  onDone: (version: PayloadVersionOutput) => void;
}

// Revoke one version: what it does to what uses it, in one message, then the safe way to do it.
const PayloadRevokeDialog: FunctionComponent<Props> = ({ open, name, versions, currentPayloadId, actionId, onClose, onDone }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const {
    selected, setSelected, version, usages, reason, setReason, canReplace, replace, setReplace, replacement, setReplacement, replacements,
    canSubmit, submitting, revoke, moving, moveCount,
  } = useRevokeForm({
    open,
    versions,
    currentPayloadId,
    onDone,
  });
  const impact = useUsageImpact(usages);
  const count = usages ? usageCount(usages) : 0;
  const versionLabel = version?.payload_version ?? '';
  // What keeps running elsewhere, said even when revoking breaks nothing.
  const allUsages = useUsagesByVersion(open ? versions : []);
  const otherUsages = versions.filter(v => v.payload_id !== selected).reduce((sum, v) => sum + (allUsages[v.payload_id] ? usageCount(allUsages[v.payload_id]) : 0), 0);

  // The button says what happens: moving the injects first is part of the action.
  const submitLabel = moving ? counted(moveCount, 'Move 1 inject and revoke', 'Move {count} injects and revoke') : t('Revoke');

  // The impact in one message, its tone matching reality: a warning when something fails, a check when nothing does.
  const renderImpact = () => {
    if (!version) return null;
    if (!usages) return <Spinner size="sm" />;
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
        action={<UsagesLink actionId={actionId} />}
      />
    );
  };

  return (
    <PayloadVersionDialog
      open={open}
      title={t('Revoke a version')}
      name={name}
      versions={versions}
      selected={selected}
      onSelect={setSelected}
      onClose={onClose}
      submit={(
        <Button type="button" variant="destructive" loading={submitting} disabled={!canSubmit} onClick={revoke}>
          {submitLabel}
        </Button>
      )}
    >
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
      <Textarea
        label={t('Reason')}
        required
        placeholder={t('Why this version is unsafe')}
        minRows={2}
        value={reason}
        onChange={event => setReason(event.target.value)}
      />
    </PayloadVersionDialog>
  );
};

export default PayloadRevokeDialog;
