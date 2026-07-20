import { ExpandMore } from '@mui/icons-material';
import { Accordion, AccordionDetails, AccordionSummary, Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  title: string;
  color: string;
  children: ReactNode;
}

const AlertBanner: FunctionComponent<Props> = ({ title, color, children }) => (
  <div style={{
    display: 'flex',
    width: '100%',
  }}
  >
    <div
      style={{
        backgroundColor: color,
        borderBottomLeftRadius: 5,
        borderTopLeftRadius: 5,
        height: 'auto',
        width: '2px',
      }}
    />
    <Accordion
      defaultExpanded
      style={{
        margin: 0,
        width: '100%',
      }}
    >
      <AccordionSummary expandIcon={<ExpandMore />}>
        <Typography sx={{ color }} variant="h6">
          {title}
        </Typography>
      </AccordionSummary>
      <AccordionDetails style={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        {children}
      </AccordionDetails>
    </Accordion>
  </div>
);

export default AlertBanner;
