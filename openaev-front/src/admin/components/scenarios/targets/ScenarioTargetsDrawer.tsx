import { Alert, Button, Chip, Drawer, DrawerBody, DrawerContent, DrawerFooter, DrawerHeader } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';

interface Props {
  // What the drawer does (Install, Launch), above the scenario name.
  overline: string;
  title: string;
  version?: string;
  submitLabel: string;
  // The submit starts a simulation.
  launches: boolean;
  blocked: boolean;
  onSubmit: () => Promise<unknown>;
  onClose: () => void;
  children: ReactNode;
}

// The drawer where the targets of a scenario are chosen, to install it or to launch it.
const ScenarioTargetsDrawer: FunctionComponent<Props> = ({ overline, title, version, submitLabel, launches, blocked, onSubmit, onClose, children }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [submitting, setSubmitting] = useState(false);
  // Why the submit failed, said in the drawer: a message behind its overlay is never seen.
  const [failure, setFailure] = useState<string | null>(null);

  const submit = () => {
    setSubmitting(true);
    setFailure(null);
    onSubmit()
      .catch((error: { message?: string }) => setFailure(error?.message || t('Something went wrong. Please refresh the page or try again later.')))
      .finally(() => setSubmitting(false));
  };

  return (
    <Drawer open onOpenChange={next => !next && onClose()}>
      <DrawerContent size="lg" busy={submitting}>
        <DrawerHeader
          overline={overline}
          title={title}
          badge={version ? <Chip label={`v${version}`} /> : undefined}
          closeLabel={t('Close')}
        />
        <DrawerBody>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(3),
          }}
          >
            {children}
            {failure && <Alert elevation={1} severity="error" title={failure} />}
          </div>
          <DrawerFooter>
            <Button type="button" priority="secondary" disabled={submitting} onClick={onClose}>{t('Cancel')}</Button>
            <Button
              type="button"
              loading={submitting}
              disabled={blocked}
              startIcon={launches ? <PlayArrowOutlined fontSize="small" /> : undefined}
              onClick={submit}
            >
              {submitLabel}
            </Button>
          </DrawerFooter>
        </DrawerBody>
      </DrawerContent>
    </Drawer>
  );
};

export default ScenarioTargetsDrawer;
