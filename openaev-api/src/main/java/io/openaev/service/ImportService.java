package io.openaev.service;

import static io.openaev.rest.payload.service.PayloadExportService.ZIP_PASSWORD;
import static java.io.File.createTempFile;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.importer.ImportException;
import io.openaev.importer.Importer;
import io.openaev.importer.V1_DataImporter;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@Service
public class ImportService {

  public static final String EXPORT_ENTRY_EXERCISE = "Exercise";
  public static final String EXPORT_ENTRY_SCENARIO = "Scenario";
  public static final String EXPORT_ENTRY_ATTACHMENT = "Attachment";
  public static final String EXPORT_ENTRY_ENCRYPTED_ATTACHMENT = "EncryptedAttachment";
  public static final String EXPORT_ENTRY_ENCRYPTED_ARGUMENT_ATTACHMENT =
      "EncryptedArgumentAttachment";
  public static final String EXPORT_ENTRY_PAYLOAD_ARCHIVE = "PayloadArchive";
  public static final String EXPORT_ENTRY_PAYLOAD = "Payload";

  private final Map<Integer, Importer> dataImporters = new HashMap<>();

  @Resource protected ObjectMapper mapper;

  @Autowired
  public void setV1_dataImporter(V1_DataImporter v1_dataImporter) {
    dataImporters.put(1, v1_dataImporter);
  }

  private void handleDataImport(
      InputStream inputStream,
      Map<String, ImportEntry> docReferences,
      Exercise exercise,
      Scenario scenario) {
    try {
      JsonNode importNode = mapper.readTree(inputStream);
      int importVersion = importNode.get("export_version").asInt();
      Importer importer = dataImporters.get(importVersion);
      if (importer != null) {
        importer.importData(importNode, docReferences, exercise, scenario);
      } else {
        throw new ImportException("Export with version " + importVersion + " is not supported");
      }
    } catch (Exception e) {
      throw new ImportException(e);
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public void handleFileImport(MultipartFile file, Exercise exercise, Scenario scenario)
      throws Exception {
    handleInputStreamImport(file.getInputStream(), exercise, scenario);
  }

  @Transactional(rollbackOn = Exception.class)
  public void handleInputStreamFileImport(InputStream is, Exercise exercise, Scenario scenario)
      throws Exception {
    handleInputStreamImport(is, exercise, scenario);
  }

  private void handleInputStreamImport(InputStream is, Exercise exercise, Scenario scenario)
      throws Exception {
    File tempFile = createTempFile("openaev-import-" + now().getEpochSecond(), ".zip");
    FileUtils.copyInputStreamToFile(is, tempFile);
    is.close();

    try (ZipFile parentZip = new ZipFile(tempFile)) { // java.util.zip.ZipFile!
      List<InputStream> dataImports = new ArrayList<>();
      Map<String, ImportEntry> docReferences = new HashMap<>();
      Enumeration<? extends ZipEntry> entries = parentZip.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String entryType = entry.getComment();
        String entryName = entry.getName();
        ByteArrayOutputStream encryptedAttachmentsBuffer = null;
        ByteArrayOutputStream encryptedArgumentsAttachmentsBuffer = null;
        if (entry.isDirectory()) {
          continue;
        }
        // Handle direct import of payloads
        if (entryName.contains("payload.json")) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            ByteArrayOutputStream jsonBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, jsonBuffer);
            dataImports.add(new ByteArrayInputStream(jsonBuffer.toByteArray()));
            entryType = "DIRECT_IMPORT";
          }
        } else if (entryName.contains("attachments.zip")) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            encryptedAttachmentsBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, encryptedAttachmentsBuffer);
            entryType = "DIRECT_IMPORT";
          }
        } else if (entryName.contains("arguments_attachments.zip")) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            encryptedArgumentsAttachmentsBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, encryptedArgumentsAttachmentsBuffer);
            entryType = "DIRECT_IMPORT";
          }
        }

        if (entryType == null) {
          throw new UnsupportedMediaTypeException(
              "Import file is using an incorrect format (" + entryName + ")");
        }

        if (EXPORT_ENTRY_PAYLOAD_ARCHIVE.equals(entryType)) {
          try (InputStream payloadZipStream = parentZip.getInputStream(entry)) {
            byte[] payloadZipBytes = IOUtils.toByteArray(payloadZipStream);
            try (ZipInputStream payloadZipInputStream =
                new ZipInputStream(new ByteArrayInputStream(payloadZipBytes))) {
              ZipEntry payloadEntry;
              while ((payloadEntry = payloadZipInputStream.getNextEntry()) != null) {
                String payloadEntryName = payloadEntry.getName();
                if ("payload.json".equals(payloadEntryName)) {
                  ByteArrayOutputStream jsonBuffer = new ByteArrayOutputStream();
                  IOUtils.copy(payloadZipInputStream, jsonBuffer);
                  dataImports.add(new ByteArrayInputStream(jsonBuffer.toByteArray()));
                } else if ("attachments.zip".equals(payloadEntryName)) {
                  encryptedAttachmentsBuffer = new ByteArrayOutputStream();
                  IOUtils.copy(payloadZipInputStream, encryptedAttachmentsBuffer);
                } else if ("arguments_attachments.zip".equals(payloadEntryName)) {
                  encryptedArgumentsAttachmentsBuffer = new ByteArrayOutputStream();
                  IOUtils.copy(payloadZipInputStream, encryptedArgumentsAttachmentsBuffer);
                }
                payloadZipInputStream.closeEntry();
              }
            }
          }
        } else if (EXPORT_ENTRY_ATTACHMENT.equals(entryType)) {
          try (InputStream attStream = parentZip.getInputStream(entry)) {
            ByteArrayOutputStream attachmentBuffer = new ByteArrayOutputStream();
            IOUtils.copy(attStream, attachmentBuffer);
            docReferences.put(
                entryName,
                new ImportEntry(
                    entry,
                    new ByteArrayInputStream(attachmentBuffer.toByteArray()),
                    attachmentBuffer.toByteArray().length));
          }
        } else if (EXPORT_ENTRY_EXERCISE.equals(entryType)
            || EXPORT_ENTRY_SCENARIO.equals(entryType)) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, dataBuffer);
            dataImports.add(new ByteArrayInputStream(dataBuffer.toByteArray()));
          }
        }

        // If encrypted attachments exist, extract with zip4j
        if (encryptedAttachmentsBuffer != null) {
          File tempEncryptedAttachmentFile = createTempFile("encrypted-attachment", ".zip");
          try {
            FileUtils.writeByteArrayToFile(
                tempEncryptedAttachmentFile, encryptedAttachmentsBuffer.toByteArray());
            try (net.lingala.zip4j.ZipFile encryptedZip =
                new net.lingala.zip4j.ZipFile(
                    tempEncryptedAttachmentFile, ZIP_PASSWORD.toCharArray())) {
              encryptedZip.setRunInThread(false);
              for (net.lingala.zip4j.model.FileHeader encHeader : encryptedZip.getFileHeaders()) {
                try (InputStream fileInZip = encryptedZip.getInputStream(encHeader)) {
                  String filename = encHeader.getFileName();
                  byte[] fileBytes = IOUtils.toByteArray(fileInZip);
                  ZipEntry fakeEntry = new ZipEntry(filename);
                  docReferences.put(
                      filename,
                      new ImportEntry(
                          fakeEntry, new ByteArrayInputStream(fileBytes), fileBytes.length));
                }
              }
            }
          } finally {
            tempEncryptedAttachmentFile.delete();
          }
        }
        // If encrypted arguments attachments exist, extract with zip4j
        if (encryptedArgumentsAttachmentsBuffer != null) {
          File tempEncryptedAttachmentFile = createTempFile("encrypted-attachment", ".zip");
          try {
            FileUtils.writeByteArrayToFile(
                tempEncryptedAttachmentFile, encryptedArgumentsAttachmentsBuffer.toByteArray());
            try (net.lingala.zip4j.ZipFile encryptedZip =
                new net.lingala.zip4j.ZipFile(
                    tempEncryptedAttachmentFile, ZIP_PASSWORD.toCharArray())) {
              encryptedZip.setRunInThread(false);
              for (net.lingala.zip4j.model.FileHeader encHeader : encryptedZip.getFileHeaders()) {
                try (InputStream fileInZip = encryptedZip.getInputStream(encHeader)) {
                  String filename = encHeader.getFileName();
                  byte[] fileBytes = IOUtils.toByteArray(fileInZip);
                  ZipEntry fakeEntry = new ZipEntry(filename);
                  docReferences.put(
                      filename,
                      new ImportEntry(
                          fakeEntry, new ByteArrayInputStream(fileBytes), fileBytes.length));
                }
              }
            }
          } finally {
            tempEncryptedAttachmentFile.delete();
          }
        }
      }

      // Process all loaded data
      for (InputStream dataStream : dataImports) {
        handleDataImport(dataStream, docReferences, exercise, scenario);
      }
    } finally {
      tempFile.delete();
    }
  }
}
