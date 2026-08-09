import { CheckCircleOutlined, ErrorOutlineOutlined, VerifiedUserOutlined } from '@mui/icons-material';
import { Alert, Box, Button, CircularProgress, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  fetchCustomDomainInstructions,
  verifyCustomDomain,
} from '../../../../actions/custom_domains/customdomain-actions';
import CodeBlock from '../../../../components/common/overview/CodeBlock';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain, type CustomDomainInstructions } from '../../../../utils/api-types';
import CustomDomainStatusChip from './CustomDomainStatusChip';

interface Props {
  customDomain: CustomDomain;
  onUpdate?: (result: CustomDomain) => void;
}

interface DnsRecord {
  type: string;
  name?: string;
  value?: string;
}

const CustomDomainInstructionsPanel: FunctionComponent<Props> = ({ customDomain, onUpdate }) => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();

  const [instructions, setInstructions] = useState<CustomDomainInstructions | null>(null);
  const [domain, setDomain] = useState<CustomDomain>(customDomain);
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    setDomain(customDomain);
  }, [customDomain]);

  useEffect(() => {
    let active = true;
    fetchCustomDomainInstructions(customDomain.custom_domain_id).then(
      (result: { data: CustomDomainInstructions }) => {
        if (active && result?.data) {
          setInstructions(result.data);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [customDomain.custom_domain_id]);

  const onVerify = () => {
    setVerifying(true);
    verifyCustomDomain(customDomain.custom_domain_id)
      .then((result: { data: CustomDomain }) => {
        if (result?.data) {
          setDomain(result.data);
          if (onUpdate) {
            onUpdate(result.data);
          }
        }
        return result;
      })
      .finally(() => setVerifying(false));
  };

  const isVerified = domain.custom_domain_status === 'VERIFIED';

  const records: DnsRecord[] = [
    {
      type: 'CNAME',
      name: instructions?.cname_record_name,
      value: instructions?.cname_record_value,
    },
    {
      type: 'TXT',
      name: instructions?.txt_record_name,
      value: instructions?.txt_record_value,
    },
  ];

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 3,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
      }}
      >
        <Box>
          <Typography
            variant="h5"
            sx={{
              fontWeight: 600,
              marginBottom: 0.5,
            }}
          >
            {domain.custom_domain_hostname}
          </Typography>
          <CustomDomainStatusChip status={domain.custom_domain_status} />
        </Box>
        <Button
          variant="contained"
          color="primary"
          startIcon={verifying ? <CircularProgress size={16} color="inherit" /> : <VerifiedUserOutlined />}
          onClick={onVerify}
          disabled={verifying}
        >
          {isVerified ? t('Re-check DNS') : t('Verify domain')}
        </Button>
      </Box>

      {isVerified && (
        <Alert severity="success" icon={<CheckCircleOutlined />}>
          {t('This domain is verified and can be linked to phishing landing pages.')}
          {domain.custom_domain_verified_at
            ? ` ${t('Verified on')} ${fldt(domain.custom_domain_verified_at)}.`
            : ''}
        </Alert>
      )}

      {!isVerified && domain.custom_domain_last_error && (
        <Alert severity="warning" icon={<ErrorOutlineOutlined />}>
          {domain.custom_domain_last_error}
        </Alert>
      )}

      <Box>
        <Typography variant="body2" color="text.secondary" sx={{ marginBottom: 2 }}>
          {t('Publish the two DNS records below at your DNS provider, then run the verification. DNS changes can take a few minutes to propagate.')}
        </Typography>

        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
        }}
        >
          {records.map((record, index) => (
            <Box key={record.type}>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                marginBottom: 1,
              }}
              >
                <Box sx={{
                  width: 22,
                  height: 22,
                  borderRadius: '50%',
                  backgroundColor: theme.palette.primary.main,
                  color: theme.palette.primary.contrastText,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 12,
                  fontWeight: 700,
                  flexShrink: 0,
                }}
                >
                  {index + 1}
                </Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {record.type === 'CNAME'
                    ? t('Point your domain to the platform (CNAME record)')
                    : t('Prove ownership (TXT record)')}
                </Typography>
              </Box>
              <Box sx={{ paddingLeft: '30px' }}>
                <Typography variant="caption" color="text.secondary">
                  {t('Record type')}
                </Typography>
                <Box sx={{ marginBottom: 1 }}>
                  <CodeBlock content={record.type} />
                </Box>
                <Typography variant="caption" color="text.secondary">
                  {t('Name / host')}
                </Typography>
                <Box sx={{ marginBottom: 1 }}>
                  <CodeBlock content={record.name} />
                </Box>
                <Typography variant="caption" color="text.secondary">
                  {t('Value')}
                </Typography>
                <CodeBlock content={record.value} />
              </Box>
            </Box>
          ))}
        </Box>
      </Box>

      {domain.custom_domain_last_checked_at && (
        <Typography variant="caption" color="text.secondary">
          {t('Last checked')}
          {': '}
          {fldt(domain.custom_domain_last_checked_at)}
        </Typography>
      )}
    </Box>
  );
};

export default CustomDomainInstructionsPanel;
