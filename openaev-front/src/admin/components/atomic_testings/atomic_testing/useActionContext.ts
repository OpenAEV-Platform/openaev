// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. What the Remediation and Mitigation tabs of an atomic testing know about its
// action: the types of the findings it produced, and the hosts it was NOT prevented on (the hosts where a
// remediation must be applied). In the product both come from the execution; the prototype picks them from
// the injector and the title of the atomic testing, in line with the seeded demo results.
// ---------------------------------------------------------------------------------------------

interface ActionContext {
  findingTypes: string[];
  affectedHosts: string[];
}

const useActionContext = (injectType?: string, injectTitle?: string): ActionContext => {
  const title = (injectTitle ?? '').toLowerCase();
  if (injectType === 'openaev_nmap') {
    return {
      findingTypes: ['portscan'],
      affectedHosts: ['WIN-DC01', 'WIN-FIN-07', 'ubuntu-web-03'],
    };
  }
  if (injectType === 'openaev_netexec') {
    return {
      findingTypes: ['share'],
      affectedHosts: ['WIN-FIN-07', 'ubuntu-web-03', 'WIN-HR-12'],
    };
  }
  if (title.includes('zerologon')) {
    return {
      findingTypes: ['cve'],
      affectedHosts: ['WIN-DC01', 'WIN-FIN-07'],
    };
  }
  if (title.includes('log4j') || title.includes('log4shell') || title.includes('44228')) {
    return {
      findingTypes: ['cve'],
      affectedHosts: ['ubuntu-web-03'],
    };
  }
  return {
    findingTypes: ['credentials'],
    affectedHosts: ['WIN-DC01', 'WIN-FIN-07', 'ubuntu-web-03'],
  };
};

export default useActionContext;
