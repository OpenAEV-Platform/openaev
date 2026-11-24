import { type CounterpartAnswer } from './counterpartStore';

// Contract v3, rule 5 (Sam, 22 Nov 2026): a product an object was imported from holds it, whatever
// the hostname lookup answers. The source link (the object's external reference, a URL into that
// product) is read first; the lookup by identifier stays the fallback.

const originOf = (url: string | null | undefined): string | null => {
  if (!url) {
    return null;
  }
  try {
    return new URL(url).origin.toLowerCase();
  } catch {
    return null;
  }
};

/**
 * The answer with the source link applied: when the product is connected and the object's source
 * link points into it (same origin as the connection URL), the product holds the object and the
 * link opens it, even when the lookup found nothing or is still loading.
 */
export const withSourceLink = (
  answer: CounterpartAnswer & { connectionUrl?: string },
  sourceUrl: string | null | undefined,
): CounterpartAnswer => {
  if (answer.state === 'match' || answer.state === 'not_connected') {
    return answer;
  }
  const source = originOf(sourceUrl);
  const connection = originOf(answer.connectionUrl);
  if (!source || !connection || source !== connection) {
    return answer;
  }
  return {
    state: 'match',
    product: answer.product,
    counterpart: {
      counterpart_product: answer.product,
      counterpart_status: 'match',
      counterpart_url: sourceUrl ?? undefined,
    },
  };
};
