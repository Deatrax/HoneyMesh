package com.honeymesh.incident.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    @GetMapping("/ping")
    public String ping() {
        return "incident-service is up";
    }
}
