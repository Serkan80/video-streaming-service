package org.acme.vss.entities;

import io.quarkus.logging.Log;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

@MongoEntity(collection = "videos")
public class VideoMetaDataEntity extends ReactivePanacheMongoEntity {
    @NotBlank
    public String username;

    @NotBlank
    public String filename;

    @NotBlank
    public String filePath;

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

    @Size(min = 1, max = 10)
    public Set<String> tags = new HashSet<>();

    public VideoMetaDataEntity() {
    }

    public VideoMetaDataEntity(String username, String filename, String filePath, String codec, int bitrate, String description, long size, int width, int heigth) {
        this.username = username;
        this.filename = filename;
        this.filePath = filePath;
        this.codec = codec;
        this.bitrate = bitrate;
        this.description = description;
        this.size = size;
        this.width = width;
        this.heigth = heigth;
        this.uploadSuccess = true;
    }

    public void markPending(String bitrate) {
        this.status.add(new EncodingStatus(bitrate, "hls", false));
        this.status.add(new EncodingStatus(bitrate, "dash", false));
    }

    public static Uni<Void> markUploadFailure(String bitrate, String username, String fileName) {
        return update("uploadSuccess = false")
                .where("status.bitrate = ?1 AND username = ?2 AND inputFilePath = ?3", bitrate, username, fileName)
                .invoke(() -> Log.infof("Video(name=%s, bitrate=%s, uploadSuccess=false) updated", fileName, bitrate))
                .replaceWithVoid();
    }

    public static Multi<VideoMetaDataEntity> findFailedUploads() {
        return stream("uploadSuccess = false");
    }
}
