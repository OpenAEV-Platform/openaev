import { ExpandMore } from '@mui/icons-material';
import { Accordion, AccordionDetails, AccordionSummary, Box, Typography } from '@mui/material';

import { useFormatter } from '../../../components/i18n';

const GettingStartedFAQ = () => {
  const { t } = useFormatter();

  const faqContent = [
    {
      summary: 'Dolor sit amet',
      details: 'Lorem ipsum dolor sit amet. Eum maiores quod At repellendus vitae ut aliquid enim nam illo quidem est ullam amet ex dicta molestias quo neque voluptas. Aut quas quia et libero voluptas non eligendi dolores et temporibus rerum?',
    },
    {
      summary: 'Cum internos praesentium',
      details: 'Est amet quia 33 quas minima sit sunt blanditiis vel omnis tempora ab perspiciatis quaerat. Eos laboriosam ipsa vel iure dolor et voluptatem dignissimos.',
    },
    {
      summary: 'Non sunt aliquam',
      details: 'Eos repellendus aliquid est numquam nisi qui quia quisquam est quia velit. Et rerum voluptatibus ea amet distinctio eum error error ea blanditiis eaque ea rerum culpa. Qui omnis ipsum sit officiis sequi et perspiciatis recusandae et quae enim ut animi culpa quo porro explicabo qui quos rerum. Sed consequuntur sapiente qui reprehenderit aspernatur sed rerum officiis ut sequi quis.',
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
      {faqContent.map(faq => (
        <Accordion
          key={faq.summary}
          variant="outlined"
          sx={{ '&:before': { display: 'none' } }}
        >
          <AccordionSummary expandIcon={<ExpandMore />}>
            <Typography>{faq.summary}</Typography>
          </AccordionSummary>
          <AccordionDetails>
          </AccordionDetails>
        </Accordion>
      ))}
    </Box>
  );
};

export default GettingStartedFAQ;
