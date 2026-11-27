import { Button, Drawer, DrawerBody, DrawerContent, DrawerFooter, DrawerHeader } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../i18n';

interface Props {
  title: string;
  onClose: () => void;
  // What the item allows after Cancel: install, open or update.
  actions: ReactNode;
  children: ReactNode;
}

// The drawer of an item of an Available tab, payload or scenario: what it is, then what to do with it.
const MarketplaceItemDrawer = ({ title, onClose, actions, children }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <Drawer open onOpenChange={next => !next && onClose()}>
      <DrawerContent size="md">
        <DrawerHeader overline={t('Catalog')} title={title} closeLabel={t('Close')} />
        <DrawerBody>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(3),
          }}
          >
            {children}
          </div>
          <DrawerFooter>
            <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
            {actions}
          </DrawerFooter>
        </DrawerBody>
      </DrawerContent>
    </Drawer>
  );
};

export default MarketplaceItemDrawer;
