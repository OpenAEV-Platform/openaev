package io.openaev.database.model;

import java.util.EnumMap;
import java.util.Map;

public record ChainingOutputType(
    ContractOutputType contractOutputType,
    ChainingTypeKind kind,
    PrimitiveType primitiveType,
    ComplexType complexType) {

  private static final Map<ContractOutputType, ChainingOutputType> INDEX =
      new EnumMap<>(ContractOutputType.class);

  static {
    registerPrimitive(ContractOutputType.Text, PrimitiveType.Text);
    registerPrimitive(ContractOutputType.Number, PrimitiveType.Number);
    registerPrimitive(ContractOutputType.Port, PrimitiveType.Port);
    registerPrimitive(ContractOutputType.IPv4, PrimitiveType.IPv4);
    registerPrimitive(ContractOutputType.IPv6, PrimitiveType.IPv6);
    registerPrimitive(ContractOutputType.CVE, PrimitiveType.CVE);
    registerPrimitive(ContractOutputType.Username, PrimitiveType.Username);

    registerComplex(ContractOutputType.Credentials, ComplexType.Credentials);
    registerComplex(ContractOutputType.PortsScan, ComplexType.PortsScan);
    registerComplex(ContractOutputType.Share, ComplexType.Share);
    registerComplex(ContractOutputType.Vulnerability, ComplexType.Vulnerability);
    registerComplex(ContractOutputType.AsreproastableAccount, ComplexType.AsreproastableAccount);
    registerComplex(ContractOutputType.KerberoastableAccount, ComplexType.KerberoastableAccount);

    registerNonChainable(ContractOutputType.AdminUsername);
    registerNonChainable(ContractOutputType.Group);
    registerNonChainable(ContractOutputType.Computer);
    registerNonChainable(ContractOutputType.PasswordPolicy);
    registerNonChainable(ContractOutputType.Delegation);
    registerNonChainable(ContractOutputType.Sid);
    registerNonChainable(ContractOutputType.AccountWithPasswordNotRequired);
    registerNonChainable(ContractOutputType.Asset);
    registerNonChainable(ContractOutputType.ExpectationSignature);
  }

  private static void registerPrimitive(
      ContractOutputType outputType, PrimitiveType primitiveType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(outputType, ChainingTypeKind.PRIMITIVE, primitiveType, null));
  }

  private static void registerComplex(ContractOutputType outputType, ComplexType complexType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(outputType, ChainingTypeKind.COMPLEX, null, complexType));
  }

  private static void registerNonChainable(ContractOutputType outputType) {
    INDEX.put(
        outputType, new ChainingOutputType(outputType, ChainingTypeKind.NON_CHAINABLE, null, null));
  }

  public static ChainingOutputType fromContractOutputType(ContractOutputType type) {
    ChainingOutputType outputType = INDEX.get(type);
    if (outputType == null) {
      throw new IllegalArgumentException("No chaining output classification found for " + type);
    }
    return outputType;
  }
}
