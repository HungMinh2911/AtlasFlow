package com.atlasflow.file.application.port;

import java.io.IOException;
import java.io.InputStream;

public interface FileStoragePort {

    void save(String objectKey,
              InputStream inputStream)
        throws IOException;
}
