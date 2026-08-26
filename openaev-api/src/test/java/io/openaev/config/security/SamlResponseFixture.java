package io.openaev.config.security;

import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSStringBuilder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.w3c.dom.Element;

/**
 * Builds a SAML response signed with the test IdP key, so assertions really go through signature
 * and condition validation instead of being stubbed away.
 */
final class SamlResponseFixture {

  static final String IDP_ENTITY_ID = "https://idp.openaev.test/idp";
  static final String SP_ENTITY_ID = "https://openaev.test/saml2/service-provider-metadata";
  static final String ACS = "https://openaev.test/login/saml2/sso/openaev";

  static {
    OpenSamlInitializationService.initialize();
  }

  private SamlResponseFixture() {}

  static String signedResponse(String email, Map<String, String> attributes) throws Exception {
    Instant now = Instant.now();

    Assertion assertion = build(Assertion.DEFAULT_ELEMENT_NAME);
    assertion.setID("_assertion");
    assertion.setIssueInstant(now);
    assertion.setIssuer(issuer());
    assertion.setSubject(subject(email, now));
    assertion.setConditions(conditions(now));
    assertion.getAuthnStatements().add(authnStatement(now));
    assertion.getAttributeStatements().add(attributeStatement(attributes));
    sign(assertion);

    Response response = build(Response.DEFAULT_ELEMENT_NAME);
    response.setID("_response");
    response.setIssueInstant(now);
    response.setDestination(ACS);
    response.setIssuer(issuer());
    response.setStatus(success());
    response.getAssertions().add(assertion);

    return serialize(response);
  }

  private static Issuer issuer() {
    Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
    issuer.setValue(IDP_ENTITY_ID);
    return issuer;
  }

  private static Status success() {
    StatusCode code = build(StatusCode.DEFAULT_ELEMENT_NAME);
    code.setValue(StatusCode.SUCCESS);
    Status status = build(Status.DEFAULT_ELEMENT_NAME);
    status.setStatusCode(code);
    return status;
  }

  private static Subject subject(String email, Instant now) {
    NameID nameId = build(NameID.DEFAULT_ELEMENT_NAME);
    nameId.setFormat(NameID.EMAIL);
    nameId.setValue(email);

    SubjectConfirmationData data = build(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
    data.setRecipient(ACS);
    data.setNotOnOrAfter(now.plus(5, ChronoUnit.MINUTES));

    SubjectConfirmation confirmation = build(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
    confirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
    confirmation.setSubjectConfirmationData(data);

    Subject subject = build(Subject.DEFAULT_ELEMENT_NAME);
    subject.setNameID(nameId);
    subject.getSubjectConfirmations().add(confirmation);
    return subject;
  }

  private static Conditions conditions(Instant now) {
    Audience audience = build(Audience.DEFAULT_ELEMENT_NAME);
    audience.setURI(SP_ENTITY_ID);
    AudienceRestriction restriction = build(AudienceRestriction.DEFAULT_ELEMENT_NAME);
    restriction.getAudiences().add(audience);

    Conditions conditions = build(Conditions.DEFAULT_ELEMENT_NAME);
    conditions.setNotBefore(now.minus(1, ChronoUnit.MINUTES));
    conditions.setNotOnOrAfter(now.plus(5, ChronoUnit.MINUTES));
    conditions.getAudienceRestrictions().add(restriction);
    return conditions;
  }

  private static AuthnStatement authnStatement(Instant now) {
    AuthnContextClassRef classRef = build(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
    classRef.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
    AuthnContext context = build(AuthnContext.DEFAULT_ELEMENT_NAME);
    context.setAuthnContextClassRef(classRef);

    AuthnStatement statement = build(AuthnStatement.DEFAULT_ELEMENT_NAME);
    statement.setAuthnInstant(now);
    statement.setAuthnContext(context);
    return statement;
  }

  private static AttributeStatement attributeStatement(Map<String, String> attributes) {
    XSStringBuilder valueBuilder =
        (XSStringBuilder)
            XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(XSString.TYPE_NAME);

    AttributeStatement statement = build(AttributeStatement.DEFAULT_ELEMENT_NAME);
    attributes.forEach(
        (name, value) -> {
          XSString xsValue =
              valueBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
          xsValue.setValue(value);
          Attribute attribute = build(Attribute.DEFAULT_ELEMENT_NAME);
          attribute.setName(name);
          attribute.getAttributeValues().add(xsValue);
          statement.getAttributes().add(attribute);
        });
    return statement;
  }

  private static void sign(Assertion assertion) throws Exception {
    Signature signature = build(Signature.DEFAULT_ELEMENT_NAME);
    signature.setSigningCredential(new BasicX509Credential(certificate(), privateKey()));
    signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
    signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
    assertion.setSignature(signature);

    XMLObjectProviderRegistrySupport.getMarshallerFactory()
        .getMarshaller(assertion)
        .marshall(assertion);
    Signer.signObject(signature);
  }

  static X509Certificate certificate() throws Exception {
    try (var in = new ClassPathResource("tls/localhost/localhost.crt").getInputStream()) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    }
  }

  private static PrivateKey privateKey() throws Exception {
    try (var in = new ClassPathResource("tls/localhost/localhost.key").getInputStream()) {
      String pem =
          new String(in.readAllBytes())
              .replaceAll("-----BEGIN (.*)-----", "")
              .replaceAll("-----END (.*)-----", "")
              .replaceAll("\\s", "");
      return KeyFactory.getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
  }

  private static String serialize(Response response) throws Exception {
    Element element =
        XMLObjectProviderRegistrySupport.getMarshallerFactory()
            .getMarshaller(response)
            .marshall(response);
    StringWriter writer = new StringWriter();
    TransformerFactory.newInstance()
        .newTransformer()
        .transform(new DOMSource(element), new StreamResult(writer));
    return writer.toString();
  }

  @SuppressWarnings("unchecked")
  private static <T extends XMLObject> T build(QName name) {
    return (T)
        XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(name).buildObject(name);
  }
}
