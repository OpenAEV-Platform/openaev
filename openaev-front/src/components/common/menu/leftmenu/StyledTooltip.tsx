import { Tooltip, tooltipClasses } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ComponentProps, type FunctionComponent } from 'react';

const StyledTooltip: FunctionComponent<ComponentProps<typeof Tooltip>> = (props) => {
  const theme = useTheme();

  return (
    <Tooltip
      {...props}
      arrow
      sx={{
        [`& .${tooltipClasses.arrow}`]: { color: theme.palette.common.black },
        [`& .${tooltipClasses.tooltip}`]: { backgroundColor: theme.palette.common.black },
      }}
    />
  );
};

export default StyledTooltip;
