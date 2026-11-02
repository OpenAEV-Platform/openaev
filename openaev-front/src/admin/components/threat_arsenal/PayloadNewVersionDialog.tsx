import {
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';

interface Props {
  // Contract of the version the save replaced, and the version it created.
  previousActionId: string;
  newPayloadId: string;
  newVersion: string;
  usages: number;
  onClose: () => void;
}

// After a save that added a version: move what runs any older version to it (F30), checked by
// default, so nothing is left behind on v1.0.0 or a revoked version and needs a migration later.
const PayloadNewVersionDialog: FunctionComponent<Props> = ({ previousActionId, newPayloadId, newVersion, usages, onClose }) => {
  const { t } = useFormatter();
  const [move, setMove] = useState(true);
  const [moving, setMoving] = useState(false);
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);

  useEffect(() => {
    fetchThreatArsenalVersions(previousActionId).then((response: { data: PayloadVersionOutput[] }) => setVersions(response.data));
  }, [previousActionId]);

  // Every older installed version something still runs, newest first.
  const sources = (versions ?? []).filter(v => v.payload_installed
    && v.payload_id !== newPayloadId
    && !!v.payload_action_id
    && (v.payload_usage_count ?? 0) > 0);
  const total = versions === null ? usages : sources.reduce((sum, v) => sum + (v.payload_usage_count ?? 0), 0);
  // A version waiting for approval cannot run yet: what runs stays where it is until it is approved.
  const pendingApproval = versions?.find(v => v.payload_id === newPayloadId)?.payload_approval_status === 'PENDING';
  const canMove = !pendingApproval && total > 0;

  const moveFrom = (source: PayloadVersionOutput) => fetchThreatArsenalUsages(source.payload_action_id as string)
    .then((response: { data: PayloadUsageOutput[] }) => changeThreatArsenalVersion(source.payload_action_id as string, {
      target_payload_id: newPayloadId,
      inject_ids: response.data.filter(u => u.usage_type !== 'step').map(u => u.usage_id),
      step_ids: response.data.filter(u => u.usage_type === 'step').map(u => u.usage_id),
    }))
    .then((response: { data: PayloadVersionChangeOutput }) => response.data);

  const confirm = () => {
    if (!move || !canMove) {
      onClose();
      return;
    }
    setMoving(true);
    Promise.all(sources.map(moveFrom))
      .then((results) => {
        const refused = results.reduce((sum, r) => sum + (r.refused?.length ?? 0), 0);
        const moved = results.reduce((sum, r) => sum + (r.moved_ids?.length ?? 0), 0);
        if (refused > 0) {
          MESSAGING$.notifyError(t('{count} usages could not move and stay on the previous version', { count: String(refused) }));
        } else {
          MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
            count: String(moved),
            version: newVersion,
          }));
        }
      })
      .finally(() => {
        setMoving(false);
        onClose();
      });
  };

  const renderBody = () => {
    if (versions === null) {
      return <Spinner />;
    }
    if (total === 0) {
      return <Text variant="content-base">{t('No scenario inject, atomic test or chained step uses an older version.')}</Text>;
    }
    return (
      <>
        <Text variant="content-base">
          {t('{count} scenario injects, atomic tests or chained steps use older versions:', { count: String(total) })}
        </Text>
        <ul style={{
          margin: 0,
          paddingLeft: 0,
          listStyle: 'none',
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
        }}
        >
          {sources.map(source => (
            <li
              key={source.payload_id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <Text variant="content-base-bold">{`v${source.payload_version}`}</Text>
              {source.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
              <Text variant="content-compact" className="text-default-secondary">
                {t('{count} injects', { count: String(source.payload_usage_count ?? 0) })}
              </Text>
            </li>
          ))}
        </ul>
        {pendingApproval
          ? (
              <Text variant="content-compact" className="text-default-secondary">
                {t('v{version} is waiting for approval: they stay on their versions until it is approved, then you can move them from the Versions tab.', { version: newVersion })}
              </Text>
            )
          : (
              <Checkbox
                checked={move}
                onCheckedChange={checked => setMove(checked === true)}
                label={t('Move all {count} to v{version}', {
                  count: String(total),
                  version: newVersion,
                })}
              />
            )}
      </>
    );
  };

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="sm">
        <DialogTitle>{t('New version v{version}', { version: newVersion })}</DialogTitle>
        <DialogBody>{renderBody()}</DialogBody>
        <DialogFooter>
          <Button onClick={confirm} disabled={moving}>{t('Confirm')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadNewVersionDialog;
