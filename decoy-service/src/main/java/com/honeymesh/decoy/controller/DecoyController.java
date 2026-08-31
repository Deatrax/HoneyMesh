package com.honeymesh.decoy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decoy")
public class DecoyController {

    @GetMapping("/ping")
    public String ping() {
        return "decoy-service is up";
    }
}
