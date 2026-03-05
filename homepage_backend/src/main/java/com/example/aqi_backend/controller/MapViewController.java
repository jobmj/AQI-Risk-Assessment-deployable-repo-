package com.example.aqi_backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class MapViewController {

    @GetMapping(value = "/map", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mapPage() {
        try {
            ClassPathResource resource = new ClassPathResource("static/map.html");
            String html = StreamUtils.copyToString(
                    resource.getInputStream(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(html);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("<h1>Error loading map: " + e.getMessage() + "</h1>");
        }
    }
}
