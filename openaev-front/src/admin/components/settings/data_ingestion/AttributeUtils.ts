const alphabet = (size = 0) => {
  const fn = () => Array.from(Array(26))
    .map((_, i) => i + 65)
    .map(x => String.fromCharCode(x));
  const letters: string[] = fn();
  for (let step = 0; step < size; step += 1) {
    const additionalLetters = fn();
    const firstLetter = additionalLetters[step];
    letters.push(...additionalLetters.map(l => firstLetter.concat(l)));
  }
  return letters;
};

export default alphabet;
