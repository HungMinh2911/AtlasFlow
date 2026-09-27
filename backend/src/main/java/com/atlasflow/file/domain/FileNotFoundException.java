package com.atlasflow.file.domain;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {

    private final UUID fileID;

    public FileNotFoundException(UUID fileID) {
        super("File does not exist with id: " + fileID);
        this.fileID = fileID;
    }
}
