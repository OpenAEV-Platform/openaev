import { describe, expect, it } from 'vitest';

import { CronField, CronFieldParser, CronFieldPosition, CronParser, WellKnownMasks } from '../../utils/period/Cron';

describe('When parsing cron expressions', () => {
  describe.each([
    {
      expr: '* 2/22 * * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '3 30 16 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '* 30 16 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '* 30 16 * * 1L',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '* 30 16 * * 1#3',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '0 30 16 * * *',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '0 30 16 * * 1L',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '0 30 16 * * 1#3',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '* * 16 * * 1#3',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '0 40 16 * * 1-5',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '3 2 */4 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '1-4 25,35,45 */4 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '3 2 */4 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '3 2 */4 * * 1L',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '3 2 */4 * * 1#3',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '3 2 */4 * * 1#6L',
      valid: false,
      uiSupported: false,
    },
    {
      expr: '2/22 * * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '30 16 * * *',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '30 16 * * 1L',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '30 16 * * 1#3',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '* 16 * * 1#3',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '2 */4 * * *',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '25,35,45 */4 * * *',
      valid: true,
      uiSupported: false,
    },
    {
      expr: '2 */4 * * *',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '2 */4 * * 1L',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '2 */4 * * 1#3',
      valid: true,
      uiSupported: true,
    },
    {
      expr: '2 */4 * * 1#6L',
      valid: false,
      uiSupported: false,
    },
  ])('With $expr', ({ expr, valid, uiSupported }) => {
    it(`parses correctly`, () => {
      expect(CronParser.parse(expr).toCronExpression()).toBe(expr);
    });
    it(`is${valid ? '' : ' not'} valid`, () => {
      expect(CronParser.parse(expr).isValid()).toBe(valid);
    });
    it(`is${uiSupported ? '' : ' not'} supported in the UI`, () => {
      expect(CronParser.parse(expr).isUiSupported()).toBe(uiSupported);
    });
  });
});
describe('Cron field features', () => {
  describe.each([
    {
      fieldExpr: '1L',
      parserMask: WellKnownMasks.weekdays,
      recurrence: 'L',
    },
    {
      fieldExpr: 'L',
      parserMask: WellKnownMasks.weekdays,
      recurrence: 'L',
    },
    {
      fieldExpr: '1#3',
      parserMask: WellKnownMasks.weekdays,
      recurrence: '3',
    },
    {
      fieldExpr: '*/22',
      parserMask: WellKnownMasks.weekdays,
      recurrence: '22',
    },
    {
      fieldExpr: '2/12',
      parserMask: WellKnownMasks.weekdays,
      recurrence: '12',
    },
  ])('When expression is $fieldExpr', ({ fieldExpr, parserMask, recurrence }) => {
    it(`returns recurrence ${recurrence}`, () => {
      const field = new CronField(fieldExpr, new CronFieldParser(parserMask), /* any will do here */ CronFieldPosition.Minutes);
      expect(field.getRecurrence()).toBe(recurrence);
    });
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
