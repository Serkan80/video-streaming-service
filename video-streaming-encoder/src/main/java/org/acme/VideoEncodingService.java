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

import java.util.concurrent.CompletionStage;

import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.MANUAL;

@ApplicationScoped
public class VideoEncodingService {

    static final String DASH_COMMAND = """
                ffmpeg -i %s -preset fast -g 48 -keyint_min 48 -sc_threshold 0 
                -s:v %s -b:v %s -maxrate %s -bufsize %s 
                -map 0:v:0 -an -f dash -init_seg_name init_%s.mp4 -media_seg_name chunk_%s_%%05d.m4s
                -dash_segment_type mp4 -use_template 1 -use_timeline 1 -adaptation_sets "id=0,streams=v" 
                -y %s/playlist_%s.mpd
            """;

    static final String HLS_COMMAND = """
            ffmpeg -i %s -preset fast -g 48 -keyint_min 48 -sc_threshold 0 
            -s:v %s -b:v %s -maxrate %s -bufsize %s 
            -map 0:v:0 -an -f hls -hls_time 4 -hls_list_size 0  
            -hls_segment_filename "%s/segment_%s_%%05d.ts" 
            -hls_playlist_type vod -y %s/playlist_%s.m3u8
            """;

    @Inject
    MongoClient mongo;

    @Blocking
    @Acknowledgment(MANUAL)
    @Incoming("video-upload")
    public CompletionStage<Void> processEncoding(Message<VideoMetaData> message) {
        var request = message.getPayload();
        Log.infof("Received encoding request: %s", request);


        switch (request.encoding()) {
            case "hls" -> Log.infof("HLS content, skipping...");
            case "dash" -> {
                encodeVideo(dashCommand(request), request.inputFilePath());
                updateVideoMetaData(request);
                checkAndMergeManifest(request.videoId(), request.outputFolder(), request.encoding());
            }
            default -> throw new IllegalArgumentException("format %s not supported".formatted(request.encoding()));
        }
        return message.ack();
    }

    private void encodeVideo(String command, String filename) {
        executeCommand(command);
        Log.infof("Encoding completed: %s", filename);
    }

    private void updateVideoMetaData(VideoMetaData request) {
        var filter = Filters.and(
                Filters.eq("_id", new ObjectId(request.videoId())),
                Filters.eq("status.bitrate", request.bitrate()),
                Filters.eq("status.encoding", request.encoding())
        );
        var update = Updates.set("status.$.done", true);

        var collection = this.mongo.getDatabase("vss").getCollection("videos");
        if (collection.updateOne(filter, update).getModifiedCount() > 0) {
            Log.infof("VideoMetaData(name=%s, bitrate=%s, encoding=%s, status=done) updated", request.inputFilePath(), request.bitrate(), request.encoding());
        }
    }

    private void checkAndMergeManifest(String videoId, String outputDir, String encoding) {
        var filter = Filters.and(
                Filters.eq("_id", new ObjectId(videoId)),
                Filters.eq("status.encoding", encoding),
                Filters.elemMatch("status", Filters.eq("done", true))
        );
        var collection = this.mongo.getDatabase("vss").getCollection("videos");
        var encodingsFinished = collection.countDocuments(filter) > 0;

        if (encodingsFinished) {
            Log.info("All encodings completed, merging into master.mpd...");

            var command = """
                    MP4Box -dash 4000 -profile dashavc264:onDemand -out %1$s/master.mpd  
                    -dash-ctx %1$s/480p.mpd -dash-ctx %1$s/720p.mpd -dash-ctx %1$s/4k.mpd  
                    %1$s/init_480p.mp4 %1$s/chunk_480p_*.m4s  
                    %1$s/init_720p.mp4 %1$s/chunk_720p_*.m4s  
                    %1$s/init_4k.mp4 %1$s/chunk_4k_*.m4s
                    """.formatted(outputDir);

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

    private static String dashCommand(VideoMetaData request) {
        var resolution = request.getResolutionSize();
        var outputPath = request.outputFolder() + "/init_" + resolution + ".mp4";
        var segmentPath = request.outputFolder() + "/chunk_" + resolution + "_%05d.m4s";

        return DASH_COMMAND.formatted(
                request.inputFilePath(),
                resolution,
                request.getBitrate(),
                request.getMaxRate(),
                request.getBufSize(),
                outputPath,
                segmentPath,
                request.outputFolder(), request.bitrate()
        );
    }
}