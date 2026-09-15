import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Breadcrumbs as MUIBreadcrumbs, Typography } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { truncate } from '../utils/String';

export const BACK_LABEL = 'backlabel';
export const BACK_URI = 'backuri';

export interface BreadcrumbsElement {
  label: string;
  link?: string;
  current?: boolean;
}

interface BreadcrumbsProps {
  variant: 'standard' | 'list' | 'object';
  elements: BreadcrumbsElement[];
  style?: CSSProperties;
}

const useStyles = makeStyles()(() => ({
  breadcrumbsList: {
    marginTop: -5,
    marginBottom: 16,
  },
  breadcrumbsObject: {
    marginTop: -5,
    marginBottom: 16,
  },
  breadcrumbsStandard: { marginTop: -5 },
}));

const Breadcrumbs: FunctionComponent<BreadcrumbsProps> = ({ elements, variant, style = {} }) => {
  const { classes } = useStyles();
  let className = classes.breadcrumbsStandard;
  if (variant === 'list') {
    className = classes.breadcrumbsList;
  } else if (variant === 'object') {
    className = classes.breadcrumbsObject;
  }

  return (
    <MUIBreadcrumbs style={style} classes={{ root: className }}>
      {elements.map((element) => {
        const text = truncate(element.label, 26);
        if (element.current) {
          return (
            <Tooltip key={element.label}>
              <TooltipTrigger asChild>
                <Typography color="text.primary">{text}</Typography>
              </TooltipTrigger>
              <TooltipContent>{element.label}</TooltipContent>
            </Tooltip>
          );
        }
        if (!element.link) {
          return (
            <Tooltip key={element.label}>
              <TooltipTrigger asChild>
                <Typography color="inherit">{text}</Typography>
              </TooltipTrigger>
              <TooltipContent>{element.label}</TooltipContent>
            </Tooltip>
          );
        }
        return (
          <Tooltip key={element.label}>
            <TooltipTrigger asChild>
              <Link to={element.link}>{text}</Link>
            </TooltipTrigger>
            <TooltipContent>{element.label}</TooltipContent>
          </Tooltip>
        );
      })}
    </MUIBreadcrumbs>
  );
};

export default Breadcrumbs;
