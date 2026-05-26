package com.smartbike.rental.controller;

import com.smartbike.rental.model.GeofenceViolation;
import com.smartbike.rental.model.UnsafeDrivingAlert;
import com.smartbike.rental.repository.GeofenceViolationRepository;
import com.smartbike.rental.repository.UnsafeDrivingRepository;
import com.smartbike.rental.service.GpsSimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulation")
public class GpsSimulationController {

    @Autowired
    private GpsSimulationService gpsSimulationService;

    @Autowired
    private UnsafeDrivingRepository unsafeDrivingRepository;

    @Autowired
    private GeofenceViolationRepository geofenceViolationRepository;

    @GetMapping("/playback/{rideId}")
    public ResponseEntity<List<Map<String, Object>>> getRidePlayback(@PathVariable UUID rideId) {
        return ResponseEntity.ok(gpsSimulationService.getPlaybackCoordinates(rideId));
    }

    @GetMapping("/alerts/{rideId}")
    public ResponseEntity<List<UnsafeDrivingAlert>> getUnsafeDrivingAlerts(@PathVariable UUID rideId) {
        return ResponseEntity.ok(unsafeDrivingRepository.findByRideIdOrderByCreatedAtDesc(rideId));
    }

    @GetMapping("/violations/{rideId}")
    public ResponseEntity<List<GeofenceViolation>> getGeofenceViolations(@PathVariable UUID rideId) {
        return ResponseEntity.ok(geofenceViolationRepository.findByRideIdOrderByViolatedAtDesc(rideId));
    }
}
