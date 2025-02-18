package org.acme.vss.services;

import io.smallrye.mutiny.Uni;
import org.acme.vss.rest.dto.VideoUploadPOST;

public interface FileStorage {

    /**
     * @return returns the path were the video is saved.
     */
    Uni<String> saveFile(VideoUploadPOST request);

    String getUploadFolder();
}
