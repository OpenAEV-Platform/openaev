import { Paper as PaperMui } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';
import { makeStyles } from 'tss-react/mui';

interface PaperProps {
  children: ReactNode;
  className?: string;
  variant?: 'elevation' | 'outlined';
}

const useStyles = makeStyles()(theme => ({
  paper: {
    padding: theme.spacing(2),
    borderRadius: 4,
    background: theme.palette.background.secondary,
  },
}));

const Paper: FunctionComponent<PaperProps> = ({ children, className = '', variant }) => {
  const { classes } = useStyles();

  return (
    <PaperMui elevation={0} variant={variant} className={`${classes.paper} ${className}`.trim()}>
      {children}
    </PaperMui>
  );
};

export default Paper;
