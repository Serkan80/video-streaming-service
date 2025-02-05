package org.acme.vss.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public record VideoUploadPOST(

        @NotNull
        @RestForm("video")
        FileUpload fileUpload,

        @NotBlank
        @RestForm
        @Size(max = 20)
        String username,

        @NotBlank
        @RestForm
        @Size(max = 100)
        String description
) {
}
