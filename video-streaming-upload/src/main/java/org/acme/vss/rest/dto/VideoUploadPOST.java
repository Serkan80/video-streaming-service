package org.acme.vss.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class VideoUploadPOST {

    @NotNull
    @RestForm("video")
    public FileUpload fileUpload;

    @NotBlank
    @RestForm
    @Size(max = 20)
    public String username;

    @NotBlank
    @RestForm
    @Size(max = 100)
    public String description;

    @RestForm
    @Size(min = 1, max = 10)
    public String tags;
}

