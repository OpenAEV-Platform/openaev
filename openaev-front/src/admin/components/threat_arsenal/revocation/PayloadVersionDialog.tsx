import { Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';

interface Props {
  open: boolean;
  title: string;
  name: string;
  versions: PayloadVersionOutput[];
  selected: string;
  onSelect: (payloadId: string) => void;
  onClose: () => void;
  // The button that acts on the version, after Cancel.
  submit: ReactNode;
  children: ReactNode;
}

// The frame of the revoke and lift dialogs: the payload, the version to act on, then what it does.
const PayloadVersionDialog: FunctionComponent<Props> = ({ open, title, name, versions, selected, onSelect, onClose, submit, children }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent>
        <DialogTitle>{title}</DialogTitle>
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
              <PayloadVersionSelect value={selected} versions={versions} onChange={onSelect} />
            </div>
            {children}
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          {submit}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadVersionDialog;
