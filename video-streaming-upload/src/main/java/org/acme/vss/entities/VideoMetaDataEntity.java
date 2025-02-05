package org.acme.vss.entities;

import io.quarkus.logging.Log;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

@MongoEntity(collection = "videos")
public class VideoMetaDataEntity extends ReactivePanacheMongoEntity {
    @NotBlank
    public String username;

    @NotBlank
    public String filename;

    @NotBlank
    public String codec;

    @NotBlank
    public String description;

    @Min(1)
    public long size;

    @Min(1)
    public int bitrate;

    @Min(1)
    public int width;

    @Min(1)
    public int heigth;
    public boolean uploadSuccess = true;
    public Set<EncodingStatus> status = new HashSet<>();

    public VideoMetaDataEntity() {
    }

    public VideoMetaDataEntity(String username, String filename, String codec, int bitrate, String description, long size, int width, int heigth) {
        this.username = username;
        this.filename = filename;
        this.codec = codec;
        this.bitrate = bitrate;
        this.description = description;
        this.size = size;
        this.width = width;
        this.heigth = heigth;
        this.uploadSuccess = true;
    }

    public void markPending(String encoding) {
        this.status.add(new EncodingStatus(encoding, false));
    }

    public static Uni<Void> markUploadFailure(String encoding, String username, String fileName) {
        return update("uploadSuccess = false")
                .where("status.encoding = ?1 AND username = ?2 AND filePath = ?3", encoding, username, fileName)
                .invoke(() -> Log.infof("Video(name=%s, encoding=%s, uploadSuccess=false) updated", fileName, encoding))
                .replaceWithVoid();
    }

    public static Multi<VideoMetaDataEntity> findFailedUploads() {
        return stream("uploadSuccess = false");
    }
}
