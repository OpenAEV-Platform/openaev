import { ArrowDropDownSharp, ArrowRightSharp } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useCallback, useState } from 'react';

interface Props {
  header: ReactNode;
  children: ReactNode;
  forceExpanded?: boolean;
}

const ExpandableSection: FunctionComponent<Props> = ({ header, children, forceExpanded = false }) => {
  const theme = useTheme();
  const [isExpanded, setIsExpanded] = useState(false);

  const expanded = forceExpanded || isExpanded;

  const handleToggle = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  return (
    <>
      {!forceExpanded
        && (
          <button
            onClick={handleToggle}
            style={{
              cursor: 'pointer',
              display: 'flex',
              background: 'none',
              border: 'none',
              padding: 0,
              color: theme.palette.text.primary,
            }}
          >
            {expanded ? <ArrowDropDownSharp /> : <ArrowRightSharp />}
            {header}
          </button>
        )}
      {expanded && children}
    </>
  );
};

export default ExpandableSection;
