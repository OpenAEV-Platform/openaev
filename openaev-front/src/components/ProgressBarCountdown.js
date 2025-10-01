import { LinearProgress } from '@mui/material';
import { useEffect, useState } from 'react';

const now = Math.round(Date.now() / 1000);

const Countdown = ({ date, paused }) => {
  let dateInSeconds = date;
  if (date === null) {
    dateInSeconds = null;
  } else if (typeof date === 'number') {
    dateInSeconds = Math.round(date / 1000);
  } else if (date instanceof Date) {
    dateInSeconds = Math.round(date.getTime() / 1000);
  } else if (typeof date === 'string') {
    dateInSeconds = Math.round(Date.parse(date) / 1000);
  }
  const remainingTime = now - dateInSeconds;
  const [percentRemaining, setPercentRemaining] = useState(
    // eslint-disable-next-line no-nested-ternary
    dateInSeconds === null
      ? 100
      : dateInSeconds > Math.round(Date.now() / 1000)
        ? ((Math.round(Date.now() / 1000) - dateInSeconds) * 100) / remainingTime
        : 0,
  );
  const tick = () => {
    if (paused) {
      return;
    }
    const currentDate = Math.round(Date.now() / 1000);
    setPercentRemaining(
      // eslint-disable-next-line no-nested-ternary
      dateInSeconds === null
        ? 100
        : dateInSeconds > currentDate
          ? ((currentDate - dateInSeconds) * 100) / remainingTime
          : 0,
    );
  };
  useEffect(() => {
    const timerID = setInterval(() => tick(), 1000);
    return () => clearInterval(timerID);
  });
  return (
    <LinearProgress
      value={100 - percentRemaining}
      variant="determinate"
      style={{ width: '90%' }}
    />
  );
};

export default Countdown;
