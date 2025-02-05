package org.acme;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.MANUAL;

@ApplicationScoped
public class VideoEncodingService {

    private static final String DASH_COMMAND = """
                ffmpeg -i %s -preset fast -g 48 -keyint_min 48 -sc_threshold 0 
                -s:v %s -b:v %s -maxrate %s -bufsize %s 
                -map 0:v:0 -an -f dash -init_seg_name init_%s.mp4 -media_seg_name chunk_%s_%%05d.m4s
                -dash_segment_type mp4 -use_template 1 -use_timeline 1 -adaptation_sets "id=0,streams=v" 
                -y %s/%s.mpd
            """;

    @Inject
    MongoClient mongo;

    @Blocking
    @Acknowledgment(MANUAL)
    @Incoming("video-encoding")
    public CompletionStage<Void> processEncoding(Message<VideoMetaData> message) {
        var request = message.getPayload();
        Log.infof("Received encoding request: %s", request);

        encodeVideo(request);
        updateVideoMetaData(request);
        checkAndMergeManifest(request.videoId(), request.outputFolder());
        return message.ack();
    }

    private String encodeVideo(VideoMetaData request) {
        var resolution = request.getResolutionSize();
        var outputPath = request.outputFolder() + "/init_" + resolution + ".mp4";
        var segmentPath = request.outputFolder() + "/chunk_" + resolution + "_%05d.m4s";

        var command = DASH_COMMAND.formatted(
                request.inputFilePath(),
                resolution,
                request.getBitrate(),
                request.getMaxRate(),
                request.getBufSize(),
                resolution, resolution, request.outputFolder(), resolution
        );

        executeCommand(command);
        Log.infof("Encoding completed: %s", outputPath);
        return outputPath;
    }

    private void updateVideoMetaData(VideoMetaData request) {
        var filter = Filters.and(
                Filters.eq("_id", new ObjectId(request.videoId())),
                Filters.elemMatch("statuses", Filters.eq("bitrate", request.bitrate()))
        );
        var update = Updates.set("statuses.$.done", true);

        var collection = this.mongo.getDatabase("vss").getCollection("videos");
        collection.updateOne(filter, update);
        Log.infof("VideoMetaData(name=%s, bitrate=%s, status=done) updated", request.inputFilePath(), request.bitrate());
    }

    private void checkAndMergeManifest(String videoId, String outputDir) {
        var mpd480p = Paths.get(outputDir, "480p.mpd");
        var mpd720p = Paths.get(outputDir, "720p.mpd");
        var mpd4k = Paths.get(outputDir, "4k.mpd");

        if (Files.exists(mpd480p) && Files.exists(mpd720p) && Files.exists(mpd4k)) {
            Log.info("All encodings completed, merging into master.mpd...");

            var command = String.format(
                    "MP4Box -dash 4000 -profile dashavc264:onDemand -out %s/master.mpd " +
                            "-dash-ctx %s/480p.mpd -dash-ctx %s/720p.mpd -dash-ctx %s/4k.mpd " +
                            "%s/init_480p.mp4 %s/chunk_480p_*.m4s " +
                            "%s/init_720p.mp4 %s/chunk_720p_*.m4s " +
                            "%s/init_4k.mp4 %s/chunk_4k_*.m4s",
                    outputDir, outputDir, outputDir, outputDir,
                    outputDir, outputDir, outputDir, outputDir, outputDir, outputDir
            );

            executeCommand(command);
            Log.infof("DASH manifest created: %s/master.mpd", outputDir);
        }
    }

    private void executeCommand(String command) {
        try {
            var process = Runtime.getRuntime().exec(command);
            process.waitFor();
        } catch (Exception e) {
            Log.error("Error running FFmpeg command", e);
        }
    }
}