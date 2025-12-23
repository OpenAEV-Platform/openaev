import { CronParser } from 'cronstrue/dist/cronParser';
import cronstrue from 'cronstrue/i18n';
import { describe, expect, it } from 'vitest';

import { Cron, CronField, CronFieldParser, CronFieldPosition, CronParseError, CronParser as OwnCronParser, WellKnownMasks } from '../../utils/Cron';

describe('Cron utility class tests', () => {
  describe('With quartz-formatted expressions', () => {
    describe('When parsing a variety of cron expressions', () => {
      describe.each([
        {
          expr: '* 2/22 * * * *',
          valid: true,
        },
        {
          expr: '3 2 */4 * * *',
          valid: true,
        },
        {
          expr: '1-4 25,35,45 */4 * * *',
          valid: true,
        },
        {
          expr: '3 2 */4 * * *',
          valid: true,
        },
        {
          expr: '3 2 */4 * * *',
          valid: true,
        },
        {
          expr: '3 2 */4 * * *',
          valid: true,
        },
        {
          expr: '3 2 */4 * * 1L',
          valid: true,
        },
        {
          expr: '3 2 */4 * * 1#3',
          valid: true,
        },
        {
          expr: '3 2 */4 * * 1#6L',
          valid: false,
        },
      ])('With $expr', ({ expr, valid }) => {
        it(`parses correctly`, () => {
          expect(OwnCronParser.parse(expr).toCronExpression()).toBe(expr);
        });
        it(`is${valid ? '' : ' not'} valid`, () => {
          expect(OwnCronParser.parse(expr).isValid()).toBe(valid);
        });
        it.each([
          {
            locale: 'fr',
            output: cronstrue.toString(expr, {
              locale: 'fr',
              verbose: true,
            }),
          },
          {
            locale: 'en',
            output: cronstrue.toString(expr, {
              locale: 'en',
              verbose: true,
            }),
          },
        ])(`translates correctly in $locale`, ({ locale, output }) => {
          expect(OwnCronParser.parse(expr).toHumanReadableString(locale)).toBe(output);
        });
      });
    });
  });
  describe('With unix-formatted expressions', () => {
    const expr = '0 0 * * *';
    it('throws an exception', () => {
      expect(OwnCronParser.parse(expr)).toThrow(CronParseError);
    });
  });
  describe('Field parsing tests', () => {
    describe.each([
      {
        mask: WellKnownMasks.seconds,
        label: 'seconds',
      },
      {
        mask: WellKnownMasks.minutes,
        label: 'minutes',
      },
    ])(`With $label field`, ({ mask }) => {
      describe.each([
        {
          expr: '*',
          expected: true,
        },
        {
          expr: '*/30',
          expected: true,
        },
        {
          expr: '*/30-35',
          expected: false,
        },
        {
          expr: '2/20',
          expected: true,
        },
        {
          expr: '30/40-45',
          expected: false,
        },
        {
          expr: '20-25/45',
          expected: true,
        },
        {
          expr: '1/2/3',
          expected: false,
        },
        {
          expr: '1,2,3',
          expected: true,
        },
        {
          expr: '*,2,3',
          expected: true,
        },
        {
          expr: '*,*,*',
          expected: true,
        },
        {
          expr: '20-25/45,20-25/45,2,4',
          expected: true,
        },
        {
          expr: '*-45',
          expected: false,
        },
        {
          expr: '60',
          expected: false,
        },
        {
          expr: '*/30-60',
          expected: false,
        },
        {
          expr: '20-25/45-50',
          expected: false,
        },
        {
          expr: '4/*',
          expected: false,
        },
        {
          expr: '4-*',
          expected: false,
        },
        {
          expr: '20-25/45,20-25/45,60',
          expected: false,
        },
      ])('parsing $expr', ({ expr, expected }) => {
        it(`returns ${expected}`, () => {
          expect(new CronFieldParser(mask).validate(expr)).toBe(expected);
        });
      });
    });
    describe(`With hours field`, () => {
      const mask = WellKnownMasks.hours;
      describe.each([
        {
          expr: '*',
          expected: true,
        },
        {
          expr: '24',
          expected: false,
        },
        {
          expr: '*/30',
          expected: false,
        },
        {
          expr: '*/12-24',
          expected: false,
        },
        {
          expr: '1/2/3',
          expected: false,
        },
        {
          expr: '2/23',
          expected: true,
        },
        {
          expr: '2/24',
          expected: false,
        },
        {
          expr: '*/12',
          expected: true,
        },
        {
          expr: '1-12',
          expected: true,
        },
        {
          expr: '20-23',
          expected: true,
        },
        {
          expr: '20-24',
          expected: false,
        },
        {
          expr: '1,2,3',
          expected: true,
        },
        {
          expr: '1/12,2-3,*/4',
          expected: true,
        },
      ])('parsing $expr', ({ expr, expected }) => {
        it(`returns ${expected}`, () => {
          expect(new CronFieldParser(mask).validate(expr)).toBe(expected);
        });
      });
    });
    describe(`With months field`, () => {
      const mask = WellKnownMasks.months;
      describe.each([
        {
          expr: '*',
          expected: true,
        },
        {
          expr: '1-2',
          expected: true,
        },
        {
          expr: '*/2',
          expected: true,
        },
        {
          expr: '1/2/3',
          expected: false,
        },
        {
          expr: '0',
          expected: false,
        },
        {
          expr: '13',
          expected: false,
        },
        {
          expr: '*/12-24',
          expected: false,
        },
        {
          expr: '2/12',
          expected: true,
        },
        {
          expr: '2-4/10',
          expected: true,
        },
        {
          expr: '1,3,4',
          expected: true,
        },
        {
          expr: '1/12,2-3,*/4',
          expected: true,
        },
      ])('parsing $expr', ({ expr, expected }) => {
        it(`returns ${expected}`, () => {
          expect(new CronFieldParser(mask).validate(expr)).toBe(expected);
        });
      });
    });
    describe(`With monthdays field`, () => {
      const mask = WellKnownMasks.monthdays;
      describe.each([
        {
          expr: '*',
          expected: true,
        },
        {
          expr: '?',
          expected: true,
        },
        {
          expr: '0',
          expected: false,
        },
        {
          expr: '32',
          expected: false,
        },
        {
          expr: '1/2/3',
          expected: false,
        },
        {
          expr: '1',
          expected: true,
        },
        {
          expr: '31',
          expected: true,
        },
        {
          expr: '*/10',
          expected: true,
        },
        {
          expr: 'L',
          expected: true,
        },
        {
          expr: '*/L',
          expected: false,
        },
        {
          expr: '*/?',
          expected: false,
        },
        {
          expr: '*,L',
          expected: false,
        },
        {
          expr: '*,?',
          expected: false,
        },
        {
          expr: '3#10',
          expected: false,
        },
        {
          expr: '10L',
          expected: false,
        },
        {
          expr: '1?',
          expected: false,
        },
      ])('parsing $expr', ({ expr, expected }) => {
        it(`returns ${expected}`, () => {
          expect(new CronFieldParser(mask).validate(expr)).toBe(expected);
        });
      });
    });
    describe(`With weekdays field`, () => {
      const mask = WellKnownMasks.weekdays;
      describe.each([
        {
          expr: '*',
          expected: true,
        },
        {
          expr: '?',
          expected: true,
        },
        {
          expr: '0',
          expected: false,
        },
        {
          expr: '1/2/3',
          expected: false,
        },
        {
          expr: '8',
          expected: false,
        },
        {
          expr: '1',
          expected: true,
        },
        {
          expr: '7',
          expected: true,
        },
        {
          expr: '*/7',
          expected: true,
        },
        {
          expr: 'L',
          expected: true,
        },
        {
          expr: '*/L',
          expected: false,
        },
        {
          expr: '*/?',
          expected: false,
        },
        {
          expr: '*,L',
          expected: false,
        },
        {
          expr: '*,?',
          expected: false,
        },
        {
          expr: '3#2',
          expected: true,
        },
        {
          expr: '3#6',
          expected: false,
        },
        {
          expr: '8#2',
          expected: false,
        },
        {
          expr: '5L',
          expected: true,
        },
        {
          expr: '8L',
          expected: false,
        },
        {
          expr: '1?',
          expected: false,
        },
      ])('parsing $expr', ({ expr, expected }) => {
        it(`returns ${expected}`, () => {
          expect(new CronFieldParser(mask).validate(expr)).toBe(expected);
        });
      });
    });
  });
});
