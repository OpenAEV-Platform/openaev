import {
  Button,
  Checkbox,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
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

// After a save that added a version: move what runs the previous one (F30), checked by default.
const PayloadNewVersionDialog: FunctionComponent<Props> = ({ previousActionId, newPayloadId, newVersion, usages, onClose }) => {
  const { t } = useFormatter();
  const [move, setMove] = useState(true);
  const [moving, setMoving] = useState(false);
  const [previousVersion, setPreviousVersion] = useState('');

  useEffect(() => {
    fetchThreatArsenalVersions(previousActionId).then((response: { data: PayloadVersionOutput[] }) => {
      setPreviousVersion(response.data.find(v => v.payload_current)?.payload_version ?? '');
    });
  }, [previousActionId]);

  const confirm = () => {
    if (!move) {
      onClose();
      return;
    }
    setMoving(true);
    fetchThreatArsenalUsages(previousActionId)
      .then((response: { data: PayloadUsageOutput[] }) => changeThreatArsenalVersion(previousActionId, {
        target_payload_id: newPayloadId,
        inject_ids: response.data.filter(u => u.usage_type !== 'step').map(u => u.usage_id),
        step_ids: response.data.filter(u => u.usage_type === 'step').map(u => u.usage_id),
      }))
      .then((response: { data: PayloadVersionChangeOutput }) => {
        const refused = response.data.refused?.length ?? 0;
        if (refused > 0) {
          MESSAGING$.notifyError(t('{count} usages could not move and stay on the previous version', { count: String(refused) }));
        } else {
          MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
            count: String(response.data.moved_ids?.length ?? 0),
            version: newVersion,
          }));
        }
      })
      .finally(() => {
        setMoving(false);
        onClose();
      });
  };

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="sm">
        <DialogTitle>{t('New version v{version}', { version: newVersion })}</DialogTitle>
        <DialogBody>
          <Text variant="content-base">
            {t('{count} scenario injects, atomic tests or chained steps use v{version}.', {
              count: String(usages),
              version: previousVersion,
            })}
          </Text>
          <Checkbox
            checked={move}
            onCheckedChange={checked => setMove(checked === true)}
            label={t('Move them to v{version}', { version: newVersion })}
          />
        </DialogBody>
        <DialogFooter>
          <Button onClick={confirm} disabled={moving}>{t('Confirm')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadNewVersionDialog;
