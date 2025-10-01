const convertOperatorToIcon = (t: (text: string) => string, operator?: string) => {
  switch (operator) {
    case 'is':
      return (
        <>
&nbsp;
          {t('is')}
        </>
      );
    default:
      return null;
  }
};

export default convertOperatorToIcon;
