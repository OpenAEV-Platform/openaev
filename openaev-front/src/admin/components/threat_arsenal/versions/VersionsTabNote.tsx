import { Button, Text } from '@filigran/design-system';
import { CheckCircleOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

interface Props {
  text: string;
  // Right after a save: a check first, the text in the primary colour, so the tab reads as its continuation.
  saved?: boolean;
  actionLabel: string;
  priority: 'secondary' | 'tertiary';
  onAction?: () => void;
}

/** A line of the Versions tab with its one action on the right. */
const VersionsTabNote: FunctionComponent<Props> = ({ text, saved = false, actionLabel, priority, onAction }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: theme.spacing(1),
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        minWidth: 0,
      }}
      >
        {saved && <CheckCircleOutlined fontSize="small" color="success" />}
        <Text variant="content-compact" className={saved ? undefined : 'text-default-secondary'}>{text}</Text>
      </div>
      {onAction && (
        <Button priority={priority} size="sm" style={{ flexShrink: 0 }} onClick={onAction}>{actionLabel}</Button>
      )}
    </div>
  );
};

export default VersionsTabNote;
