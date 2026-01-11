import { Paper as PaperMui } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';
import { makeStyles } from 'tss-react/mui';

interface PaperProps {
  children: ReactNode;
  className?: string;
}

const useStyles = makeStyles()(theme => ({
  paper: {
    padding: theme.spacing(2),
    borderRadius: theme.borderRadius,
  },
}));

const Paper: FunctionComponent<PaperProps> = ({ children, className = '' }) => {
  const { classes } = useStyles();

  return (
    <PaperMui variant="outlined" className={`${classes.paper} ${className}`.trim()}>
      {children}
    </PaperMui>
  );
};

export default Paper;
