import { type Page } from '@playwright/test';

class ContractApiMock {
  constructor(private page: Page) {
  }

  async mockContracts() {
    await this.page.route('*/**/api/contracts/search*', async (route) => {
      if (route.request().method() === 'POST') {
        const json = {
          content: [
            {
              config: {
                expose: true,
                label: {
                  en: 'Email by Filigran',
                  fr: 'Email par Filigran',
                },
                type: 'openex_email',
              },
              context: {},
              contract_id: '2790bd39-37d4-4e39-be7e-53f3ca783f86',
              fields: [
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'teams',
                  label: 'Teams',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: true,
                  mandatoryGroups: null,
                  type: 'team',
                },
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'attachments',
                  label: 'Attachments',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'attachment',
                },
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'expectations',
                  label: 'Expectations',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  predefinedExpectations: [
                  ],
                  type: 'expectation',
                },
              ],
              label: {
                en: 'Send multi-recipients mail',
                fr: 'Envoyer un mail multi-destinataires',
              },
              manual: false,
            },
            {
              config: {
                expose: true,
                icon: '/img/email.png',
                label: {
                  en: 'Email',
                  fr: 'Email',
                },
                type: 'openex_email',
              },
              context: {},
              contract_id: '138ad8f8-32f8-4a22-8114-aaa12322bd09',
              fields: [
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'teams',
                  label: 'Teams',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: true,
                  mandatoryGroups: null,
                  type: 'team',
                },
                {
                  defaultValue: false,
                  key: 'encrypted',
                  label: 'Encrypted',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'checkbox',
                },
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'expectations',
                  label: 'Expectations',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  predefinedExpectations: [
                  ],
                  type: 'expectation',
                },
              ],
              label: {
                en: 'Send individual mails ',
                fr: 'Envoyer des mails individuels',
              },
              manual: false,
            },
            {
              config: {
                expose: true,
                icon: '/img/channel.png',
                label: {
                  en: 'Media pressure by Filigran',
                  fr: 'Pression médiatique par Filigran',
                },
                type: 'openex_channel',
              },
              context: {},
              contract_id: 'fb5e49a2-6366-4492-b69a-f9b9f39a533e',
              fields: [
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'teams',
                  label: 'Teams',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'team',
                },
                {
                  defaultValue: true,
                  key: 'emailing',
                  label: 'Send email',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'checkbox',
                },
                {
                  defaultValue: false,
                  key: 'encrypted',
                  label: 'Encrypted',
                  linkedFields: [
                    {
                      key: 'emailing',
                      type: 'checkbox',
                    },
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'checkbox',
                },
              ],
              label: {
                en: 'Publish channel pressure',
                fr: 'Publier de la pression médiatique',
              },
              manual: false,
            },
            {
              config: {
                expose: false,
                icon: '/img/http.png',
                label: {
                  en: 'HTTP Request',
                  fr: 'Requête HTTP',
                },
                type: 'openex_http',
              },
              context: {},
              contract_id: '5948c96c-4064-4c0d-b079-51ec33f31b91',
              fields: [
                {
                  defaultValue: '',
                  key: 'basicUser',
                  label: 'Username',
                  linkedFields: [
                    {
                      key: 'basicAuth',
                      type: 'checkbox',
                    },
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'text',
                },
                {
                  defaultValue: '',
                  key: 'basicPassword',
                  label: 'Password',
                  linkedFields: [
                    {
                      key: 'basicAuth',
                      type: 'checkbox',
                    },
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  type: 'text',
                },
              ],
              label: {
                en: 'HTTP Request - POST (raw body)',
                fr: 'Requête HTTP - POST (body brut)',
              },
              manual: false,
            },
            {
              config: {
                color_dark: '#9c27b0',
                color_light: '#9c27b0',
                expose: false,
                icon: '/img/sms.png',
                label: { en: 'SMS (OVH)' },
                type: 'openex_ovh_sms',
              },
              context: {},
              contract_id: 'e9e902bc-b03d-4223-89e1-fca093ac79dd',
              fields: [
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'teams',
                  label: 'Teams',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: true,
                  mandatoryGroups: null,
                  type: 'team',
                },
                {
                  defaultValue: '',
                  key: 'message',
                  label: 'Message',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: true,
                  mandatoryGroups: null,
                  richText: false,
                  type: 'textarea',
                },
                {
                  cardinality: 'n',
                  defaultValue: [
                  ],
                  key: 'expectations',
                  label: 'Expectations',
                  linkedFields: [
                  ],
                  linkedValues: [
                  ],
                  mandatory: false,
                  mandatoryGroups: null,
                  predefinedExpectations: [
                  ],
                  type: 'expectation',
                },
              ],
              label: {
                en: 'Send a SMS',
                fr: 'Envoyer un SMS',
              },
              manual: false,
            },
          ],
          empty: false,
          first: true,
          last: true,
          number: 0,
          numberOfElements: 5,
          pageable: {
            offset: 0,
            paged: true,
            pageNumber: 0,
            pageSize: 10,
            sort: {
              empty: false,
              sorted: true,
              unsorted: false,
            },
            unpaged: false,
          },
          size: 10,
          sort: {
            empty: false,
            sorted: true,
            unsorted: false,
          },
          totalElements: 11,
          totalPages: 2,
        };
        await route.fulfill(
          {
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(json),
          },
        );
      } else {
        route.continue();
      }
    });
  }
}
export default ContractApiMock;
