import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

interface Props {
  text: string;
  actionLabel: string;
  priority: 'secondary' | 'tertiary';
  onAction?: () => void;
}

/** A line of the Versions tab with its one action on the right. */
const VersionsTabNote: FunctionComponent<Props> = ({ text, actionLabel, priority, onAction }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-compact" className="text-default-secondary">{text}</Text>
      {onAction && (
        <Button priority={priority} size="sm" style={{ flexShrink: 0 }} onClick={onAction}>{actionLabel}</Button>
      )}
    </div>
  );
};

export default VersionsTabNote;
