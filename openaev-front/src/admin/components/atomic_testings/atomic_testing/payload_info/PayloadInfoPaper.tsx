import { InfoOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';

import AttackPatternChip from '../../../../../components/AttackPatternChip';
import Field from '../../../../../components/common/overview/Field';
import Section from '../../../../../components/common/overview/Section';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import PlatformIconGroup from '../../../../../components/PlatformIconGroup';
import type { AttackPatternSimple, StatusPayloadOutput } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';

interface Props { payloadOutput?: StatusPayloadOutput }

const PayloadInfoPaper = ({ payloadOutput }: Props) => {
  const { t } = useFormatter();

  if (!payloadOutput) {
    return (
      <Section title={t('Payload')} icon={<InfoOutlined fontSize="small" />}>
        <Typography variant="body1">{t('No data available')}</Typography>
      </Section>
    );
  }

  return (
    <Section title={t('Payload')} icon={<InfoOutlined fontSize="small" />}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
      >
        <Typography
          sx={{
            fontSize: 18,
            fontWeight: 600,
            lineHeight: 1.3,
          }}
        >
          {payloadOutput.payload_name}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {emptyFilled(payloadOutput.payload_description)}
        </Typography>
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            md: 'repeat(2, minmax(0, 1fr))',
          },
          gap: 2,
          marginTop: 1,
        }}
        >
          <Field label="Platforms">
            <PlatformIconGroup platforms={payloadOutput.payload_platforms} width={25} />
          </Field>
          <Field label="Attack patterns">
            {payloadOutput.payload_attack_patterns && payloadOutput.payload_attack_patterns.length === 0 ? '-' : payloadOutput.payload_attack_patterns?.map((attackPattern: AttackPatternSimple) => (
              <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
            ))}
          </Field>
          <Field label="Tags">
            <ItemTags
              variant="reduced-view"
              tags={payloadOutput.payload_tags}
            />
          </Field>
          <Field label="External ID">
            {emptyFilled(payloadOutput.payload_external_id)}
          </Field>
        </Box>
      </div>
    </Section>
  );
};

export default PayloadInfoPaper;
