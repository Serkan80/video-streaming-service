package org.acme.vss.services;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.acme.vss.entities.VideoMetaDataEntity;
import org.acme.vss.rest.dto.VideoUploadPOST;
import org.acme.vss.services.dto.VideoMetaData;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;

@ApplicationScoped
public class VideoUploadService {

    @ConfigProperty(name = "file.encoding.types")
    List<String> encodings;

    @Inject
    FileStorage fileStorage;

    @Inject
    @Channel("video-upload")
    MutinyEmitter<VideoMetaData> emitter;

    public Uni<Void> uploadVideo(VideoUploadPOST request) {
        return this.fileStorage.saveFile(request)
                .chain(savedFilePath -> createMetaData(request, savedFilePath))
                .onItem().transformToMulti(this::sendToQueue)
                .toUni().replaceWithVoid();
    }

    public Uni<Void> retryFailedUploads() {
        return VideoMetaDataEntity.findFailedUploads()
                .flatMap(entity -> {
                    Log.infof("Retrying Video(name=%s, bitrate=%d)...", entity.filename, entity.bitrate);
                    return sendToQueue(entity);
                })
                .toUni().replaceWithVoid();
    }

    private Uni<VideoMetaDataEntity> createMetaData(VideoUploadPOST request, String savedFilePath) {
        // extract video info command
        var command = "ffprobe -v error -show_entries stream=codec_name,width,height,bit_rate -of json %s"
                .formatted(request.fileUpload().uploadedFile().toAbsolutePath());

        return Uni.createFrom().item(() -> {
                    try {
                        var process = Runtime.getRuntime().exec(command);
                        process.waitFor();

                        var output = new String(process.getInputStream().readAllBytes());
                        Log.debugf("Output: \n%s", output);
                        return new JsonObject(output);
                    } catch (Exception e) {
                        throw new WebApplicationException("Processing video failed", e);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .map(json -> {
                    var stream = json.getJsonArray("streams").getJsonObject(0);
                    var entity = new VideoMetaDataEntity(
                            request.username(),
                            request.fileUpload().fileName(),
                            savedFilePath,
                            stream.getString("codec_name"),
                            Integer.parseInt(stream.getString("bit_rate", "1")),
                            request.description(),
                            request.fileUpload().size(),
                            stream.getInteger("width"),
                            stream.getInteger("height")
                    );
                    this.encodings.forEach(entity::markPending);
                    return entity;
                })
                .call(video -> video.persist().invoke(() -> Log.infof("Video(name=%s, bitrate=%d) persisted".formatted(video.filename, video.bitrate))));
    }

    private Multi<Void> sendToQueue(VideoMetaDataEntity entity) {
        return Multi.createFrom().items(this.encodings.stream())
                .onItem().transformToUniAndMerge(bitrate -> {
                    List.of("hls", "dash").forEach(encoding ->
                            this.emitter.send(VideoMetaData.fromEntity(entity, bitrate, encoding))
                                    .invoke(() -> Log.infof("Video(name=%s, bitrate=%s, bitrate=%s) sent to queue".formatted(entity.filename, bitrate, encoding)))
                                    .onFailure().call(() -> VideoMetaDataEntity
                                            .markUploadFailure(bitrate, entity.username, entity.filename)
                                            .invoke(() -> Log.errorf("Video(name=%s, bitrate=%s) failed sending to queue", entity.filename, encoding))));
                    return Uni.createFrom().voidItem();
                });
    }
}
