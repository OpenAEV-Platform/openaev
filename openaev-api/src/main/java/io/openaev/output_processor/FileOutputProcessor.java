package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * A {@code file} finding is a file discovered on a target: on an SMB share (NetExec spider_plus) or
 * on a local/remote filesystem (FTP/NFS listing). It is complex, not primitive, because the same
 * file name means different things depending on where it lives — so the finding keeps {@code
 * file_name} (basename), {@code path} (directory), {@code share} (SMB share, empty for local files)
 * and {@code host} as distinct fields.
 *
 * <p>The finding value is the full location (host + share + path + name), which is what makes it a
 * stable, unique deduplication key: a bare basename would collapse two same-named files on the same
 * host. The front renders only the basename from {@code file_name}; the full value carries the
 * context shown when the finding is opened.
 */
@Component
public class FileOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String FILE_NAME = "file_name";
  private static final String PATH = "path";
  private static final String SHARE = "share";
  private static final String HOST = "host";

  public FileOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.File,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(FILE_NAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PATH, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(SHARE, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService,
        NOT_SENSITIVE);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(FILE_NAME);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String fileName = buildString(jsonNode, FILE_NAME);
    String path = buildString(jsonNode, PATH);
    String share = buildString(jsonNode, SHARE);
    String host = buildString(jsonNode, HOST);

    // The share, directory and basename joined into one relative location, e.g.
    // "SYSVOL/north.sevenkingdoms.local/scripts/secret.ps1".
    String relative =
        Stream.of(share, path, fileName).filter(s -> !s.isEmpty()).collect(Collectors.joining("/"));

    if (host.isEmpty()) {
      return relative;
    }
    if (!share.isEmpty()) {
      // File on an SMB share: render as a UNC path (\\host\share\dir\file).
      return "\\\\" + host + "\\" + relative.replace('/', '\\');
    }
    // Local file (no share, e.g. FTP/NFS listing): keep the host prefix so files
    // with the same path on different hosts stay distinct.
    return host + ":" + (relative.startsWith("/") ? relative : "/" + relative);
  }

  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode == null) {
      return Collections.emptyList();
    }
    if (assetIdNode.isArray()) {
      List<String> result = new ArrayList<>();
      for (JsonNode idNode : assetIdNode) {
        result.add(idNode.asText());
      }
      return result;
    }
    return List.of(assetIdNode.asText());
  }
}
