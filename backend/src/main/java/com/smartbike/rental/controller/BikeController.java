package com.smartbike.rental.controller;

import com.smartbike.rental.dto.BikeDto;
import com.smartbike.rental.service.BikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bikes")
public class BikeController {

    @Autowired
    private BikeService bikeService;

    @GetMapping
    public ResponseEntity<List<BikeDto>> getBikes(@RequestParam(value = "availableOnly", defaultValue = "false") boolean availableOnly) {
        if (availableOnly) {
            return ResponseEntity.ok(bikeService.getAvailableBikes());
        }
        return ResponseEntity.ok(bikeService.getAllBikes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BikeDto> getBikeById(@PathVariable UUID id) {
        return ResponseEntity.ok(bikeService.getBikeById(id));
    }
}
