package io.openaev.service;

import static java.io.File.createTempFile;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.importer.ImportException;
import io.openaev.importer.ImportResult;
import io.openaev.importer.Importer;
import io.openaev.importer.MissingImportedAction;
import io.openaev.importer.V1_DataImporter;
import io.openaev.utils.constants.Constants;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@Service
public class ImportService {
  public static final String ZIP_PASSWORD = "infected";

  public static final String EXPORT_ENTRY_EXERCISE = "Exercise";
  public static final String EXPORT_ENTRY_SCENARIO = "Scenario";
  public static final String EXPORT_ENTRY_ATTACHMENT = "Attachment";

  private final Map<Integer, Importer> dataImporters = new HashMap<>();

  @Resource protected ObjectMapper mapper;

  @Autowired
  public void setV1_dataImporter(V1_DataImporter v1_dataImporter) {
    dataImporters.put(1, v1_dataImporter);
  }

  private ImportResult handleDataImport(
      TxCtx ctx,
      InputStream inputStream,
      Map<String, ImportEntry> docReferences,
      Exercise exercise,
      Scenario scenario,
      Asset asset,
      AssetGroup assetGroup,
      String suffix) {
    try {
      JsonNode importNode = mapper.readTree(inputStream);
      int importVersion = importNode.get("export_version").asInt();
      Importer importer = dataImporters.get(importVersion);
      if (importer != null) {
        return importer.importData(
            ctx, importNode, docReferences, exercise, scenario, asset, assetGroup, suffix);
      } else {
        throw new ImportException("Export with version " + importVersion + " is not supported");
      }
    } catch (Exception e) {
      throw new ImportException(e);
    }
  }

  // Kinds of archive entry the importer handles, told from the entry itself (#3686).
  enum EntryKind {
    // The exported exercise or scenario (the importer reads which one from the JSON).
    EXPORT,
    // A payload export archive: a nested zip carrying a payload.json, as payload exports wrote
    // before the JSON payload export.
    PAYLOAD_ARCHIVE,
    // Anything else: an attachment, looked up by its entry name.
    ATTACHMENT
  }

  // Tells the kind of an archive entry from its name and content, never from the zip entry
  // comment: standard zip tools drop entry comments, so an export unzipped and zipped again would
  // otherwise not import. A JSON object holding an export_version is the export, a nested zip
  // holding a payload.json is a payload archive, anything else is an attachment.
  static EntryKind inferEntryKind(String entryName, byte[] content, ObjectMapper objectMapper) {
    if (entryName.startsWith("__MACOSX/")) {
      return EntryKind.ATTACHMENT;
    }
    String lowerName = entryName.toLowerCase(Locale.ROOT);
    if (lowerName.endsWith(".json")) {
      try {
        JsonNode node = objectMapper.readTree(content);
        if (node != null && node.isObject() && node.has("export_version")) {
          return EntryKind.EXPORT;
        }
      } catch (IOException e) {
        // Not a JSON export file: keep it as an attachment.
      }
    } else if (lowerName.endsWith(".zip") && holdsPayloadJson(content)) {
      return EntryKind.PAYLOAD_ARCHIVE;
    }
    return EntryKind.ATTACHMENT;
  }

  private static boolean holdsPayloadJson(byte[] content) {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
      ZipEntry nested;
      while ((nested = zip.getNextEntry()) != null) {
        if ("payload.json".equals(nested.getName())) {
          return true;
        }
      }
    } catch (IOException e) {
      // Not a readable zip: an attachment.
    }
    return false;
  }

  @Transactional(rollbackFor = Exception.class)
  public ImportResult handleFileImport(
      TxCtx ctx, MultipartFile file, Exercise exercise, Scenario scenario) throws Exception {
    return handleInputStreamImport(
        ctx,
        file.getInputStream(),
        exercise,
        scenario,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);
  }

  @Transactional(rollbackFor = Exception.class)
  public ImportResult handleInputStreamFileImport(
      TxCtx ctx,
      InputStream is,
      Exercise exercise,
      Scenario scenario,
      Asset asset,
      AssetGroup assetGroup,
      String suffix)
      throws Exception {
    return handleInputStreamImport(ctx, is, exercise, scenario, asset, assetGroup, suffix);
  }

  private ImportResult handleInputStreamImport(
      TxCtx ctx,
      InputStream is,
      Exercise exercise,
      Scenario scenario,
      Asset asset,
      AssetGroup assetGroup,
      String suffix)
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
        boolean directImport = false;
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
            directImport = true;
          }
        } else if (entryName.contains("attachments.zip")) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            encryptedAttachmentsBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, encryptedAttachmentsBuffer);
            directImport = true;
          }
        } else if (entryName.contains("arguments_attachments.zip")) {
          try (InputStream dataStream = parentZip.getInputStream(entry)) {
            encryptedArgumentsAttachmentsBuffer = new ByteArrayOutputStream();
            IOUtils.copy(dataStream, encryptedArgumentsAttachmentsBuffer);
            directImport = true;
          }
        }

        if (!directImport) {
          // Every other entry is typed from its name and content (#3686).
          byte[] content;
          try (InputStream entryStream = parentZip.getInputStream(entry)) {
            content = IOUtils.toByteArray(entryStream);
          }
          EntryKind kind = inferEntryKind(entryName, content, mapper);
          if (kind == EntryKind.PAYLOAD_ARCHIVE) {
            try (ZipInputStream payloadZipInputStream =
                new ZipInputStream(new ByteArrayInputStream(content))) {
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
          } else if (kind == EntryKind.EXPORT) {
            dataImports.add(new ByteArrayInputStream(content));
          } else {
            docReferences.put(
                entryName,
                new ImportEntry(entry, new ByteArrayInputStream(content), content.length));
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

      if (dataImports.isEmpty()) {
        throw new UnsupportedMediaTypeException(
            "Import file is using an incorrect format (no exercise, scenario or payload found)");
      }

      // Process all loaded data. A single ZIP can carry the main Exercise/Scenario file plus one or
      // more payload.json (direct-import or nested in a PayloadArchive): aggregate the missing
      // actions reported by every importData call into one consolidated result.
      List<MissingImportedAction> aggregatedMissingActions = new ArrayList<>();
      for (InputStream dataStream : dataImports) {
        ImportResult result =
            handleDataImport(
                ctx, dataStream, docReferences, exercise, scenario, asset, assetGroup, suffix);
        aggregatedMissingActions.addAll(result.missingActions());
      }
      return new ImportResult(aggregatedMissingActions);
    } finally {
      tempFile.delete();
    }
  }
}
