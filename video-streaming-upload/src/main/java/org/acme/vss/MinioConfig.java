package org.acme.vss;

import io.minio.MinioClient;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class MinioConfig {

    @ConfigProperty(name = "minio.endpoint")
    String endpoint;

    @ConfigProperty(name = "minio.accessKey")
    String accessKey;

    @ConfigProperty(name = "minio.secretKey")
    String secretKey;

    @ConfigProperty(name = "minio.bucket")
    String bucket;

    @Produces
    @Singleton
    public MinioClient build() {
        return MinioClient.builder()
                .endpoint(this.endpoint)
                .credentials(this.accessKey, this.secretKey)
                .build();
    }
}
