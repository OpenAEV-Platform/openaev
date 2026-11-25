import { useTheme } from '@mui/material/styles';
import { Children, type FunctionComponent, isValidElement, type ReactNode } from 'react';

interface Props { children: ReactNode }

const DIVIDER = '1px solid var(--border-elevation-subtle-soft)';

// Rows on one surface, split by a line: the target fields, and the injects they go to.
const TargetsGroup: FunctionComponent<Props> = ({ children }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      backgroundColor: 'var(--bg-elevation-default)',
      border: DIVIDER,
      borderRadius: theme.shape.borderRadius,
      overflow: 'hidden',
    }}
    >
      {Children.toArray(children).filter(isValidElement).map((row, index) => (
        <div
          key={row.key}
          style={{
            padding: theme.spacing(1.5, 2),
            borderTop: index > 0 ? DIVIDER : undefined,
          }}
        >
          {row}
        </div>
      ))}
    </div>
  );
};

export default TargetsGroup;
