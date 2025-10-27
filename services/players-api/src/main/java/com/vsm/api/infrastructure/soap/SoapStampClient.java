package com.vsm.api.infrastructure.soap;

import java.io.StringReader;
import java.time.Duration;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Minimal SOAP client that calls a public EchoString endpoint to retrieve a stamp persisted with
 * report metadata. Uses Spring Web Services to construct and parse the SOAP envelope.
 */
@Component
public class SoapStampClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SoapStampClient.class);

  private static final String SOAP_ENVELOPE_TEMPLATE =
      """
      <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"
          xmlns:tem=\"http://tempuri.org\">
        <soapenv:Header/>
        <soapenv:Body>
          <tem:EchoString>
            <tem:input>%s</tem:input>
          </tem:EchoString>
        </soapenv:Body>
      </soapenv:Envelope>
      """;

  private final WebServiceTemplate webServiceTemplate;
  private final String endpointUrl;
  private final String soapAction;

  public SoapStampClient(
      WebServiceTemplateBuilder builder,
      @Value("${app.soap.endpoint-url:}") String endpointUrl,
      @Value("${app.soap.soap-action:}") String soapAction,
      @Value("${app.soap.connect-timeout:2s}") Duration connectTimeout,
      @Value("${app.soap.read-timeout:5s}") Duration readTimeout) {
    HttpUrlConnectionMessageSender messageSender = new HttpUrlConnectionMessageSender();
    messageSender.setConnectionTimeout(Math.toIntExact(connectTimeout.toMillis()));
    messageSender.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.webServiceTemplate = builder.messageSenders(messageSender).build();
    this.endpointUrl = endpointUrl;
    this.soapAction = soapAction;
  }

  /**
   * Calls the configured SOAP endpoint with the provided payload and returns the echo response as a
   * soapStamp. Returns {@link Optional#empty()} when the endpoint is not configured or the call
   * fails.
   */
  public Optional<String> fetchStamp(String payload) {
    if (!StringUtils.hasText(endpointUrl)) {
      return Optional.empty();
    }

    String requestXml = SOAP_ENVELOPE_TEMPLATE.formatted(escapeXml(payload));
    try {
      String responseXml =
          webServiceTemplate.sendAndReceive(
              endpointUrl, requestCallback(requestXml), responseExtractor());

      if (!StringUtils.hasText(responseXml)) {
        return Optional.empty();
      }

      return extractEchoResult(responseXml);
    } catch (Exception ex) {
      LOGGER.warn("Failed to fetch soapStamp via SOAP endpoint: {}", ex.getMessage());
      LOGGER.debug("SOAP invocation failure", ex);
      return Optional.empty();
    }
  }

  private WebServiceMessageCallback requestCallback(String requestXml) {
    return message -> {
      Transformer transformer = newTransformer();
      transformer.transform(new StringSource(requestXml), message.getPayloadResult());
      if (StringUtils.hasText(soapAction) && message instanceof SoapMessage soapMessage) {
        soapMessage.setSoapAction(soapAction);
      }
    };
  }

  private WebServiceMessageExtractor<String> responseExtractor() {
    return message -> {
      Transformer transformer = newTransformer();
      StringResult result = new StringResult();
      transformer.transform(message.getPayloadSource(), result);
      return result.toString();
    };
  }

  private Optional<String> extractEchoResult(String xml)
      throws ParserConfigurationException, SAXException, java.io.IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    NodeList nodes = document.getElementsByTagNameNS("http://tempuri.org", "EchoStringResult");
    if (nodes.getLength() == 0) {
      nodes = document.getElementsByTagName("EchoStringResult");
    }
    if (nodes.getLength() == 0) {
      return Optional.empty();
    }
    String value = nodes.item(0).getTextContent();
    return StringUtils.hasText(value) ? Optional.of(value.trim()) : Optional.empty();
  }

  private Transformer newTransformer() throws TransformerException {
    return TransformerFactory.newInstance().newTransformer();
  }

  private String escapeXml(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
