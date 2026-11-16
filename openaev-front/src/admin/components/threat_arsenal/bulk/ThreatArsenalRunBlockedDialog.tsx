import { Alert, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';

interface Props {
  /** Selected actions with no approved version. */
  blocked: ThreatArsenalAction[];
  /** How many selected actions can run. */
  runnableCount: number;
  onClose: () => void;
  onContinue: () => void;
}

/**
 * "Run a test" on a selection: the actions with no approved version are named before going on,
 * instead of a refused launch. The others can still run, each in its latest approved version.
 */
const ThreatArsenalRunBlockedDialog: FunctionComponent<Props> = ({ blocked, runnableCount, onClose, onContinue }) => {
  const { t, tPick } = useFormatter();
  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="sm">
        <DialogTitle>{t('Run a test')}</DialogTitle>
        <DialogBody style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          <Alert
            severity="warning"
            title={t('{count, plural, one {# selected action has no approved version and cannot run} other {# selected actions have no approved version and cannot run}}', { count: String(blocked.length) })}
            description={t('A person with "Approve content" must approve a version first.')}
          />
          <ul style={{
            margin: 0,
            paddingLeft: 20,
          }}
          >
            {blocked.map(action => (
              <li key={action.injector_contract_id}><Text variant="content-base">{tPick(action.action_labels)}</Text></li>
            ))}
          </ul>
          {runnableCount > 0 && (
            <Text variant="content-base">
              {t('{count, plural, one {Continue with the # other action, in its latest approved version.} other {Continue with the # other actions, each in its latest approved version.}}', { count: String(runnableCount) })}
            </Text>
          )}
        </DialogBody>
        <DialogFooter>
          {runnableCount > 0
            ? (
                <>
                  <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
                  <Button onClick={onContinue}>{t('Continue')}</Button>
                </>
              )
            : <Button priority="secondary" onClick={onClose}>{t('Close')}</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ThreatArsenalRunBlockedDialog;
