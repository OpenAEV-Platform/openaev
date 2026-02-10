package io.openaev.opencti.client.response;

import java.io.InputStream;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class File {

  private long size;

  private InputStream inputStream;
}
