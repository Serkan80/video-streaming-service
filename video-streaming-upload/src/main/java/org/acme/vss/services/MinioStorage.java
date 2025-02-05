package org.acme.vss.services;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.vss.rest.dto.VideoUploadPOST;

import java.nio.file.Files;

@ApplicationScoped
@IfBuildProfile("prod")
public class MinioStorage implements FileStorage {

    @Inject
    MinioClient minioClient;

    @Override
    public Uni<String> saveFile(VideoUploadPOST request) {
        var uploadFolder = "%s/%s/original".formatted(getUploadFolder(), request.username());
        return Uni.createFrom().item(() -> {
                    try {
                        var file = request.fileUpload();
                        return this.minioClient.putObject(PutObjectArgs
                                .builder()
                                .object(file.fileName())
                                .bucket(uploadFolder)
                                .stream(Files.newInputStream(file.uploadedFile()), file.size(), -1)
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to upload file to Minio", e);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .map(v -> uploadFolder);
    }

    @Override
    public String getUploadFolder() {
        return "/videos";
    }
}
