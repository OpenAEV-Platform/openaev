package io.openaev.rest.payload.service;

import static io.openaev.service.ImportService.*;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Document;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.exports.PayloadFileExport;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PayloadExportService {
  @Resource protected ObjectMapper mapper;
  @Resource private DocumentRepository documentRepository;
  @Resource private FileService fileService;

  public static final String ZIP_PASSWORD = "infected";

  public String getZipFileName() {
    return ("payloads_" + now().toString()) + ".zip";
  }

  public byte[] exportPayloadsToZip(List<Payload> payloads) throws IOException {
    ByteArrayOutputStream parentOutputStream = new ByteArrayOutputStream();
    try (java.util.zip.ZipOutputStream parentZip =
        new java.util.zip.ZipOutputStream(parentOutputStream)) {
      for (Payload payload : payloads) {
        ByteArrayOutputStream payloadZipStream = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream payloadZip =
            new java.util.zip.ZipOutputStream(payloadZipStream)) {
          // 1. Add payload.json
          ZipEntry payloadJsonEntry = new ZipEntry("payload.json");
          payloadJsonEntry.setComment(EXPORT_ENTRY_PAYLOAD);
          payloadZip.putNextEntry(payloadJsonEntry);
          PayloadFileExport payloadExport =
              PayloadFileExport.fromPayload(payload, mapper.copy(), this.documentRepository);
          byte[] payloadJsonBytes =
              payloadExport
                  .getObjectMapper()
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsBytes(payloadExport);
          payloadZip.write(payloadJsonBytes);
          payloadZip.closeEntry();

          // 2. Add attachments.zip **if attachments exist**
          Optional<Document> optDoc = payload.getAttachedDocument();
          if (optDoc.isPresent()) {
            Document doc =
                documentRepository
                    .findById(optDoc.get().getId())
                    .orElseThrow(ElementNotFoundException::new);
            Optional<InputStream> docStream = fileService.getFile(doc);
            if (docStream.isPresent()) {
              ByteArrayOutputStream encryptedZipStream = new ByteArrayOutputStream();
              try (ZipOutputStream encryptedZip =
                  new ZipOutputStream(encryptedZipStream, ZIP_PASSWORD.toCharArray())) {
                ZipParameters params = new ZipParameters();
                params.setEncryptFiles(true);
                params.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD);
                params.setFileNameInZip(doc.getTarget());
                encryptedZip.putNextEntry(params);
                byte[] fileBytes = docStream.get().readAllBytes();
                encryptedZip.write(fileBytes);
                encryptedZip.closeEntry();
              }
              byte[] attachmentsZipBytes = encryptedZipStream.toByteArray();
              // Add attachments.zip
              ZipEntry attachmentsEntry = new ZipEntry("attachments.zip");
              attachmentsEntry.setComment(EXPORT_ENTRY_ENCRYPTED_ATTACHMENT);
              payloadZip.putNextEntry(attachmentsEntry);
              payloadZip.write(attachmentsZipBytes);
              payloadZip.closeEntry();
            }
          }

          // 3. Add arguments_attachments.zip
          List<String> argumentsAttachmentsIds = payload.getArgumentsDocumentsIds();
          List<Document> argumentsAttachments =
              argumentsAttachmentsIds.stream()
                  .map(
                      s ->
                          documentRepository.findById(s).orElseThrow(ElementNotFoundException::new))
                  .toList();

          if (!argumentsAttachments.isEmpty()) {
            ByteArrayOutputStream encryptedZipStream = new ByteArrayOutputStream();
            try (ZipOutputStream encryptedZip =
                new ZipOutputStream(encryptedZipStream, ZIP_PASSWORD.toCharArray())) {
              for (Document doc : argumentsAttachments) {
                Optional<InputStream> docStream = fileService.getFile(doc);
                if (docStream.isPresent()) {
                  ZipParameters params = new ZipParameters();
                  params.setEncryptFiles(true);
                  params.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD);
                  params.setFileNameInZip(doc.getTarget());
                  encryptedZip.putNextEntry(params);
                  byte[] fileBytes = docStream.get().readAllBytes();
                  encryptedZip.write(fileBytes);
                  encryptedZip.closeEntry();
                }
              }
            }
            byte[] argumentsAttachmentsZipBytes = encryptedZipStream.toByteArray();
            ZipEntry argumentsAttachmentsEntry = new ZipEntry("arguments_attachments.zip");
            argumentsAttachmentsEntry.setComment(EXPORT_ENTRY_ENCRYPTED_ARGUMENT_ATTACHMENT);
            payloadZip.putNextEntry(argumentsAttachmentsEntry);
            payloadZip.write(argumentsAttachmentsZipBytes);
            payloadZip.closeEntry();
          }
        }

        // Add payload zip to parent zip
        String entryName = payload.getName() + ".zip";
        ZipEntry payloadZipEntry = new ZipEntry(entryName);
        payloadZipEntry.setComment(EXPORT_ENTRY_PAYLOAD_ARCHIVE);
        parentZip.putNextEntry(payloadZipEntry);
        parentZip.write(payloadZipStream.toByteArray());
        parentZip.closeEntry();
      }
    }
    return parentOutputStream.toByteArray();
  }
}
