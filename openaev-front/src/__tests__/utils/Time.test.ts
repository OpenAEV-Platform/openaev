import { afterAll, describe, expect, it } from 'vitest';

import { toUtcMidnightIso, utcMidnightToLocalDay } from '../../utils/Time';

const ORIGINAL_TZ = process.env.TZ;

describe('toUtcMidnightIso', () => {
  afterAll(() => {
    process.env.TZ = ORIGINAL_TZ;
  });

  describe.each(['Europe/Paris', 'America/New_York', 'UTC'])('in %s', (timeZone) => {
    it('stores UTC midnight of the day the picker shows', () => {
      // Arrange: the date picker returns local midnight of the picked day
      process.env.TZ = timeZone;
      const pickedOct3 = new Date(2026, 9, 3);

      // Act
      const stored = toUtcMidnightIso(pickedOct3);

      // Assert
      expect(stored).toBe('2026-10-03T00:00:00.000Z');
    });

    it('keeps a one-off schedule window on the picked day', () => {
      // Arrange: SchedulingDialog bounds a one-off schedule to [start, start + 24h UTC]
      process.env.TZ = timeZone;
      const start = toUtcMidnightIso(new Date(2026, 9, 3));
      const end = new Date(new Date(start).setUTCHours(24, 0, 0, 0)).toISOString();

      // Act
      const cronFire = new Date('2026-10-03T08:00:00.000Z').getTime();

      // Assert
      expect(cronFire).toBeGreaterThanOrEqual(new Date(start).getTime());
      expect(cronFire).toBeLessThan(new Date(end).getTime());
    });
  });
});

describe('utcMidnightToLocalDay', () => {
  afterAll(() => {
    process.env.TZ = ORIGINAL_TZ;
  });

  describe.each(['Europe/Paris', 'America/New_York', 'UTC'])('in %s', (timeZone) => {
    it('gives back the local day a UTC midnight stands for, and round-trips with toUtcMidnightIso', () => {
      // Arrange
      process.env.TZ = timeZone;

      // Act
      const day = utcMidnightToLocalDay('2026-10-03T00:00:00.000Z');
      const nextDay = utcMidnightToLocalDay('2026-10-31T00:00:00.000Z', 1);

      // Assert
      expect([day.getFullYear(), day.getMonth(), day.getDate(), day.getHours()]).toEqual([2026, 9, 3, 0]);
      expect([nextDay.getMonth(), nextDay.getDate()]).toEqual([10, 1]);
      expect(toUtcMidnightIso(day)).toBe('2026-10-03T00:00:00.000Z');
    });
  });
});
