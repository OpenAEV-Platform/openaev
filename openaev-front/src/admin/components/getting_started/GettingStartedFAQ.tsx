import { ExpandMore } from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Typography,
} from '@mui/material';
import ReactMarkdown from 'react-markdown';

import { useFormatter } from '../../../components/i18n';

const GettingStartedFAQ = () => {
  const { t } = useFormatter();

  const faqCategories = [
    {
      category: t('faq.usage.title'),
      questions: [
        {
          summary: t('faq.usage.missing_content.summary'),
          details: t('faq.usage.missing_content.details'),
        },
        {
          summary: t('faq.usage.simulation_not_working.summary'),
          details: t('faq.usage.simulation_not_working.details'),
        },
        {
          summary: t('faq.usage.production_safe.summary'),
          details: t('faq.usage.production_safe.details'),
        },
      ],
    },
    {
      category: t('faq.results.title'),
      questions: [
        {
          summary: t('faq.results.expectations_expire.summary'),
          details: t('faq.results.expectations_expire.details'),
        },
        {
          summary: t('faq.results.share_scenarios.summary'),
          details: t('faq.results.share_scenarios.details'),
        },
      ],
    },
    {
      category: t('faq.components.title'),
      questions: [
        {
          summary: t('faq.components.executors_injectors_collectors.summary'),
          details: t('faq.components.executors_injectors_collectors.details'),
        },
      ],
    },
    {
      category: t('faq.support.title'),
      questions: [
        {
          summary: t('faq.support.help.summary'),
          details: t('faq.support.help.details'),
        },
      ],
    },
  ];

  return (
    <Box>
      <Typography variant="h1">
        {t('getting_started_faq')}
      </Typography>
      <Typography variant="h3">
        {t('getting_started_faq_explanation')}
      </Typography>
      {faqCategories.map(cat => (
        <Box key={cat.category} sx={{ mt: 3 }}>
          <Typography variant="h4" sx={{ mb: 1 }}>
            {cat.category}
          </Typography>
          {cat.questions.map(faq => (
            <Accordion
              key={faq.summary}
              variant="outlined"
              sx={{ '&:before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMore />}>
                <Typography>{faq.summary}</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <ReactMarkdown
                  components={{
                    a: ({ ...props }) => (
                      <a {...props} target="_blank" rel="noopener noreferrer">
                        {props.children}
                      </a>
                    ),
                  }}
                >
                  {faq.details}
                </ReactMarkdown>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      ))}
    </Box>
  );
};

export default GettingStartedFAQ;
