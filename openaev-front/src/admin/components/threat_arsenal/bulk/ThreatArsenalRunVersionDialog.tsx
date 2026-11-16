import { Alert, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import PayloadVersionSelect from '../PayloadVersionSelect';
import { actionOfVersion, installedVersions, isRunnableVersion } from './runTestVersions';

interface Props {
  /** The action to run a test of; the dialog is open while it is set. */
  action: ThreatArsenalAction | null;
  /** The version to offer first when it can run, e.g. the one shown in the drawer. */
  preferredActionId?: string;
  onClose: () => void;
  /** The action of the version to run. */
  onChoose: (action: ThreatArsenalAction) => void;
}

/**
 * Before "Run a test": which version runs. Only an approved version that is installed and not
 * revoked can run, the latest one by default; the others are listed, greyed out with their status,
 * so the user sees why. A single approved version runs without asking, and an action with no
 * approved version is stopped here with a warning instead of a refused launch.
 */
const ThreatArsenalRunVersionDialog: FunctionComponent<Props> = ({ action, preferredActionId, onClose, onChoose }) => {
  const { t, tPick } = useFormatter();
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [selected, setSelected] = useState('');

  useEffect(() => {
    setVersions(null);
    setFailed(false);
    if (!action) return undefined;
    // An action without a payload has no version to approve: it runs as it is.
    if (!action.action_payload) {
      onChoose(action);
      return undefined;
    }
    let cancelled = false;
    fetchThreatArsenalVersions(action.injector_contract_id)
      .then((response: { data: PayloadVersionOutput[] }) => {
        if (cancelled) return;
        const installed = installedVersions(response.data);
        const runnable = installed.filter(isRunnableVersion);
        if (installed.length === 1 && runnable.length === 1) {
          onChoose(actionOfVersion(action, runnable[0]));
          return;
        }
        const preferred = runnable.find(v => v.payload_action_id === preferredActionId) ?? runnable[0];
        setSelected(preferred?.payload_action_id ?? '');
        setVersions(installed);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [action?.injector_contract_id]);

  if (!action || (versions === null && !failed)) return null;

  const name = tPick(action.action_labels);
  const runnable = (versions ?? []).filter(isRunnableVersion);
  const chosen = runnable.find(v => v.payload_action_id === selected);

  let body;
  if (failed) {
    body = <Alert severity="error" title={t('The versions of this action could not be loaded.')} />;
  } else if (runnable.length === 0) {
    body = (
      <Alert
        severity="warning"
        title={t('This action has no approved version, so it cannot run a test.')}
        description={t('A person with "Approve content" must approve a version first.')}
      />
    );
  } else {
    body = (
      <>
        <Text variant="content-base">
          {t('Choose the version to run. Only approved versions can run; the others are listed with their status.')}
        </Text>
        <PayloadVersionSelect
          value={chosen?.payload_version ?? ''}
          options={(versions ?? []).map(v => ({
            version: v.payload_version ?? '',
            latest: v.payload_is_latest,
            installed: true,
            revoked: v.payload_revoked,
            approvalStatus: v.payload_approval_status,
            disabled: !isRunnableVersion(v),
          }))}
          onChange={value => setSelected(runnable.find(v => v.payload_version === value)?.payload_action_id ?? '')}
        />
      </>
    );
  }

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="sm">
        <DialogTitle>{t('Run a test: {name}', { name })}</DialogTitle>
        <DialogBody style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {body}
        </DialogBody>
        <DialogFooter>
          {chosen
            ? (
                <>
                  <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
                  <Button onClick={() => onChoose(actionOfVersion(action, chosen))}>{t('Continue')}</Button>
                </>
              )
            : <Button priority="secondary" onClick={onClose}>{t('Close')}</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ThreatArsenalRunVersionDialog;
