import { Menu, MenuContent, MenuTrigger } from '@filigran/design-system';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';

interface GraphContextMenuProps {
  /** The pointer position, relative to the graph wrapper. */
  x: number;
  y: number;
  onClose: () => void;
  /** The sections of the menu, built by the explorer for what is under the pointer. */
  children: ReactNode;
}

/**
 * The right-click menu, as OpenCTI's GraphContextMenu: the explorer builds the actions that apply to
 * what is under the pointer (a node, a group, a relation, the empty canvas); this component places
 * them at the pointer.
 */
const GraphContextMenu = ({ x, y, onClose, children }: GraphContextMenuProps) => {
  const { t } = useFormatter();
  return (
    <Menu
      open
      modal={false}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      {/* The library Menu opens from a trigger only: a hidden one sits at the pointer, as in OpenCTI. */}
      <MenuTrigger asChild>
        <span
          aria-hidden
          tabIndex={-1}
          style={{
            position: 'absolute',
            left: x,
            top: y,
            width: 1,
            height: 1,
            pointerEvents: 'none',
          }}
        />
      </MenuTrigger>
      <MenuContent align="start" side="bottom" aria-label={t('Graph actions')} collisionPadding={8} data-testid="graph-context-menu">
        {children}
      </MenuContent>
    </Menu>
  );
};

export default GraphContextMenu;
