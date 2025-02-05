package org.acme.vss.services.dto;

import org.acme.vss.entities.VideoMetaDataEntity;

public record VideoMetaData(String videoId, String inputFilePath, String outputFolder, String bitrate, String encoding) {

    public static VideoMetaData fromEntity(VideoMetaDataEntity entity, String bitrate, String encoding) {
        return new VideoMetaData(
                entity.id.toHexString(),
                entity.filename,
                entity.filePath,
                bitrate,
                encoding
        );
    }
}
