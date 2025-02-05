package org.acme.vss.services;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.file.CopyOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.vss.rest.dto.VideoUploadPOST;

@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class LocalStorage implements FileStorage {

    @Inject
    Vertx vertx;

    @Override
    public Uni<String> saveFile(VideoUploadPOST request) {
        var uploadFolder = "%s/%s/%s/original".formatted(getUploadFolder(), request.username(), request.fileUpload().fileName().split(".")[0]);
        var fileUpload = request.fileUpload();

        return this.vertx.fileSystem().mkdirs(uploadFolder)
                .call(() -> this.vertx.fileSystem().move(
                                fileUpload.uploadedFile().toString(),
                                "%s/%s".formatted(uploadFolder, fileUpload.fileName()),
                                new CopyOptions().setReplaceExisting(true))
                        .invoke(() -> Log.infof("Video(name=%s, dir=%s) uploaded", fileUpload.fileName(), uploadFolder)))
                .map(v -> uploadFolder.replace("/original", ""));
    }

    @Override
    public String getUploadFolder() {
        return "./target/uploads";
    }
}
