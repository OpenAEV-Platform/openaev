package io.openaev.database.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ChainingOutputType(
    ContractOutputType contractOutputType,
    ChainingTypeKind kind,
    PrimitiveType primitiveType,
    ComplexType complexType,
    List<PrimitiveType> primitiveRecipe) {

  public ChainingOutputType {
    primitiveRecipe = primitiveRecipe == null ? List.of() : List.copyOf(primitiveRecipe);
  }

  private static final Map<ContractOutputType, ChainingOutputType> INDEX =
      new EnumMap<>(ContractOutputType.class);

  static {
    registerPrimitive(ContractOutputType.Text, PrimitiveType.Text);
    registerPrimitive(ContractOutputType.Number, PrimitiveType.Number);
    registerPrimitive(ContractOutputType.Port, PrimitiveType.Port);
    registerPrimitive(ContractOutputType.IPv4, PrimitiveType.IPv4);
    registerPrimitive(ContractOutputType.IPv6, PrimitiveType.IPv6);

    registerComplex(
        ContractOutputType.Credentials,
        ComplexType.Credentials,
        List.of(PrimitiveType.Username, PrimitiveType.Password));
    registerComplex(ContractOutputType.PortsScan, ComplexType.PortsScan); // TODO recipe
    registerComplex(ContractOutputType.Share, ComplexType.Share); // TODO recipe
    registerComplex(ContractOutputType.Vulnerability, ComplexType.Vulnerability); // TODO recipe
    registerComplex(
        ContractOutputType.AsreproastableAccount, ComplexType.AsreproastableAccount); // TODO recipe
    registerComplex(
        ContractOutputType.KerberoastableAccount, ComplexType.KerberoastableAccount); // TODO recipe
    registerComplex(ContractOutputType.CVE, ComplexType.CVE); // TODO recipe
    registerComplex(ContractOutputType.Username, ComplexType.Username); // TODO recipe
    registerComplex(ContractOutputType.AdminUsername, ComplexType.AdminUsername); // TODO recipe
    registerComplex(ContractOutputType.Group, ComplexType.Group); // TODO recipe
    registerComplex(ContractOutputType.Computer, ComplexType.Computer); // TODO recipe
    registerComplex(ContractOutputType.PasswordPolicy, ComplexType.PasswordPolicy); // TODO recipe
    registerComplex(ContractOutputType.Delegation, ComplexType.Delegation); // TODO recipe
    registerComplex(ContractOutputType.Sid, ComplexType.Sid); // TODO recipe
    registerComplex(
        ContractOutputType.AccountWithPasswordNotRequired,
        ComplexType.AccountWithPasswordNotRequired); // TODO recipe
    registerComplex(ContractOutputType.Asset, ComplexType.Asset); // TODO recipe

    registerNonChainable(ContractOutputType.ExpectationSignature);
  }

  private static void registerPrimitive(
      ContractOutputType outputType, PrimitiveType primitiveType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(
            outputType, ChainingTypeKind.PRIMITIVE, primitiveType, null, List.of()));
  }

  private static void registerComplex(ContractOutputType outputType, ComplexType complexType) {
    registerComplex(outputType, complexType, List.of());
  }

  private static void registerComplex(
      ContractOutputType outputType, ComplexType complexType, List<PrimitiveType> primitiveRecipe) {
    INDEX.put(
        outputType,
        new ChainingOutputType(
            outputType, ChainingTypeKind.COMPLEX, null, complexType, primitiveRecipe));
  }

  private static void registerNonChainable(ContractOutputType outputType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(outputType, ChainingTypeKind.NOT_CHAINABLE, null, null, List.of()));
  }

  public static ChainingOutputType fromContractOutputType(ContractOutputType type) {
    ChainingOutputType outputType = INDEX.get(type);
    if (outputType == null) {
      throw new IllegalArgumentException("No chaining output classification found for " + type);
    }
    return outputType;
  }
}
