package org.acme.vss.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.acme.vss.entities.VideoMetaDataEntity;
import org.acme.vss.rest.dto.VideoUploadPOST;
import org.acme.vss.services.VideoUploadService;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Path("/uploads")
public class VideoUploadController {

    @Inject
    VideoUploadService service;

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    public Uni<Void> upload(@BeanParam @Valid VideoUploadPOST request) {
        return this.service.uploadVideo(request);
    }

    @GET
    public Uni<List<VideoMetaDataEntity>> findAll() {
        return VideoMetaDataEntity.listAll();
    }

    @POST
    @Path("/retry")
    public Uni<Void> retryFailed() {
        return this.service.retryFailedUploads();
    }
}
