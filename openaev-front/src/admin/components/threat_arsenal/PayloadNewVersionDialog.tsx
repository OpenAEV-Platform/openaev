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

import { fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../components/i18n';
import { type PayloadVersionOutput } from '../../../utils/api-types';
import movePayloadVersionUsages from './payloadVersionMove';

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
    movePayloadVersionUsages(previousActionId, newPayloadId, newVersion, t)
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
