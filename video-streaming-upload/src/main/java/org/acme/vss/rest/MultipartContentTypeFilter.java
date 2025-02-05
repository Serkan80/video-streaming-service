package org.acme.vss.rest;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jboss.resteasy.reactive.RestResponse.Status.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.Status.UNSUPPORTED_MEDIA_TYPE;

@ApplicationScoped
public class MultipartContentTypeFilter {

    @ConfigProperty(name = "quarkus.http.body.multipart.file-content-types")
    List<String> allowedContentTypes;

    @RouteFilter(Priorities.USER + 1)
    public void filter(RoutingContext ctx) {
        if (!isMultipartRequest(ctx)) {
            ctx.next();
            return;
        }

        var hasError = new AtomicBoolean(false);
        ctx.request().setExpectMultipart(true);

        ctx.request().uploadHandler(fileUpload -> {
            var filePath = Path.of(fileUpload.filename());
            try {
                var contentType = Files.probeContentType(filePath);
                if (contentType == null || !this.allowedContentTypes.contains(contentType)) {
                    sendJsonError(ctx, UNSUPPORTED_MEDIA_TYPE, contentType);
                    hasError.set(true);
                }
            } catch (IOException e) {
                sendJsonError(ctx, INTERNAL_SERVER_ERROR, e.getMessage());
                hasError.set(true);
            }
        });

        if (!hasError.get()) {
            ctx.next();
        }
    }

    private boolean isMultipartRequest(RoutingContext ctx) {
        var contentType = ctx.request().getHeader(CONTENT_TYPE);
        return contentType != null && contentType.startsWith("multipart/");
    }

    private void sendJsonError(RoutingContext ctx, Status status, String details) {
        ctx.response()
                .setStatusCode(status.getStatusCode())
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(JsonObject.of(
                        "error", status.toString(),
                        "details", details
                ).encodePrettily());
    }
}
