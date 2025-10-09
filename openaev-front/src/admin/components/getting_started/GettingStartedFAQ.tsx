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
          summary: t('faq.howto.import_scenario.summary'),
          details: t('faq.howto.import_scenario.details'),
        },
        {
          summary: t('faq.howto.run_scenario.summary'),
          details: t('faq.howto.run_scenario.details'),
        },
        {
          summary: t('faq.howto.create_scenario.summary'),
          details: t('faq.howto.create_scenario.details'),
        },
        {
          summary: t('faq.question.simulations_production.summary'),
          details: t('faq.question.simulations_production.details'),
        },
        {
          summary: t('faq.question.inject_missing_content.summary'),
          details: t('faq.question.inject_missing_content.details'),
        },
        {
          summary: t('faq.question.share_scenarios.summary'),
          details: t('faq.question.share_scenarios.details'),
        },
      ],
    },
    {
      category: t('faq.results.title'),
      questions: [
        {
          summary: t('faq.howto.understand_results.summary'),
          details: t('faq.howto.understand_results.details'),
          subdetailsList: [
            t('faq.howto.understand_results.home_dashboard.details'),
            t('faq.howto.understand_results.scenario.details'),
            t('faq.howto.understand_results.simulation.details'),
            t('faq.howto.understand_results.inject_results.details'),
          ],
        },
        {
          summary: t('faq.question.expectations_expire.summary'),
          details: t('faq.question.expectations_expire.details'),
        },
      ],
    },
    {
      category: t('faq.components.title'),
      questions: [
        {
          summary: t('faq.question.executor_injectors_collectors.summary'),
          details: t('faq.question.executor_injectors_collectors.details'),
        },
      ],
    },
    {
      category: t('faq.support.title'),
      questions: [
        {
          summary: t('faq.question.get_help.summary'),
          details: t('faq.question.get_help.details'),
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
                  {faq.subdetailsList?.length
                    ? faq.details + '\n' + faq.subdetailsList.map(subdetail => '- ' + subdetail).join('\n')
                    : faq.details}
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
