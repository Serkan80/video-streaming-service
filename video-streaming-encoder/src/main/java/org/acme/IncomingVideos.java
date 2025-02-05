package org.acme;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.MANUAL;

@ApplicationScoped
public class IncomingVideos {

    static final String ENCODING_COMMAND_HLS = """
            ffmpeg -i %s  
                    -filter_complex "[0:v]split=3[v1][v2][v3];[v1]scale=w=854:h=480[v1out];[v2]scale=w=1280:h=720[v2out];[v3]scale=w=3840:h=2160[v3out]" 
                    -map [v1out] -map 0:a -c:v h264 -b:v 800k -c:a aac -b:a 128k -f hls -hls_time 6 -hls_playlist_type vod -hls_segment_filename %s/stream_480p_%%03d.ts %s/stream_480p.m3u8  
                    -map [v2out] -map 0:a -c:v h264 -b:v 2500k -c:a aac -b:a 128k -f hls -hls_time 6 -hls_playlist_type vod -hls_segment_filename %s/stream_720p_%%03d.ts %s/stream_720p.m3u8  
                    -map [v3out] -map 0:a -c:v h264 -b:v 15000k -c:a aac -b:a 128k -f hls -hls_time 6 -hls_playlist_type vod -hls_segment_filename %s/stream_4k_%%03d.ts %s/stream_4k.m3u8 
                    """;

    static final String ENCODING_COMMAND_DASH = """
            ffmpeg -i %s 
            -filter_complex "[0:v]split=3[v1][v2][v3];[v1]scale=w=854:h=480[v1out];[v2]scale=w=1280:h=720[v2out];[v3]scale=w=3840:h=2160[v3out]" 
            -map [v1out] -map 0:a -c:v:0 libx264 -b:v:0 800k -c:a:0 aac -b:a:0 128k 
            -map [v2out] -map 0:a -c:v:1 libx264 -b:v:1 2500k -c:a:1 aac -b:a:1 128k 
            -map [v3out] -map 0:a -c:v:2 libx264 -b:v:2 15000k -c:a:2 aac -b:a:2 128k 
            -seg_duration 6 -adaptation_sets "id=0,streams=v id=1,streams=a" 
            -use_timeline 1 -use_template 1 
            -f dash %s/manifest.mpd
            """;

    @Incoming("video-upload")
    @Acknowledgment(MANUAL)
    public CompletionStage<Void> encodeVideo(Message<VideoMetaData> message) {
        var payload = message.getPayload();
        switch (payload.encoding()) {
            case "hls" -> Log.infof("received %s", payload);
            case "dash" -> encode(payload.filePath(), ENCODING_COMMAND_DASH);
        }
        return message.ack();
    }


    private void encode(String videoFile, String command) {
        try {
            var outputFile = Path.of(videoFile).getParent();
            Files.createDirectories(outputFile);

            var process = Runtime.getRuntime().exec(command.formatted(videoFile, outputFile.toString()));
            process.waitFor();

            Log.infof("VideoFile(name=%s) encoding done", videoFile);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
