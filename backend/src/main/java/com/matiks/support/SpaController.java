package com.matiks.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the bundled React app for client-side routes.
 *
 * Returns index.html as a body rather than forwarding to it. A
 * "forward:/index.html" here recurses infinitely - the forwarded path matches
 * this same controller mapping and re-enters it, giving a StackOverflowError
 * on every page load.
 *
 * The path pattern excludes api, assets and static file names so that real API
 * calls still return JSON 404s (not the HTML page, which is a genuinely
 * confusing thing to debug) and hashed bundle files are served by Spring's
 * static resource handler instead.
 */
@RestController
public class SpaController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping({"/", "/{path:^(?!api$|assets$)[^.]*$}", "/{path:^(?!api$|assets$)[^.]*}/**"})
    public ResponseEntity<Resource> spa() {
        if (!INDEX.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // The HTML shell must never be cached: it references hashed
                // asset filenames that change on every deploy.
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(INDEX);
    }
}
