import { Typography, useTheme } from '@mui/material';
import { type CSSProperties, Fragment, type FunctionComponent } from 'react';
import { Link } from 'react-router';

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

const Breadcrumbs: FunctionComponent<BreadcrumbsProps> = ({ elements, variant, style = {} }) => {
  const theme = useTheme();

  return (
    <div
      style={{
        marginBottom: variant === 'standard' ? undefined : theme.spacing(1),
        display: 'flex',
        alignItems: 'center',
        ...style,
      }}
    >
      {elements.map((element, index) => {
        const isLast = index === elements.length - 1;
        const separator = !isLast
          ? (
              <span style={{
                marginLeft: theme.spacing(1),
                marginRight: theme.spacing(1),
                fontSize: 12,
                color: theme.palette.text.disabled,
              }}
              >
                /
              </span>
            )
          : null;

        if (element.current) {
          return (
            <Fragment key={element.label}>
              <Typography
                sx={{
                  fontSize: 12,
                  fontWeight: 700,
                }}
                color="text.primary"
              >
                {truncate(element.label, 50)}
              </Typography>
              {separator}
            </Fragment>
          );
        }
        if (!element.link) {
          return (
            <Fragment key={element.label}>
              <Typography
                sx={{ fontSize: 12 }}
                color="common.lightGrey"
              >
                {truncate(element.label, 30)}
              </Typography>
              {separator}
            </Fragment>
          );
        }
        return (
          <Fragment key={element.label}>
            <Link
              style={{ fontSize: 12 }}
              to={element.link}
            >
              {truncate(element.label, 30)}
            </Link>
            {separator}
          </Fragment>
        );
      })}
    </div>
  );
};

export default Breadcrumbs;
