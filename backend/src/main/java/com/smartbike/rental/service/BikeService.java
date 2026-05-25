package com.smartbike.rental.service;

import com.smartbike.rental.dto.BikeDto;
import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.BikeState;
import com.smartbike.rental.repository.BikeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BikeService {

    @Autowired
    private BikeRepository bikeRepository;

    public List<BikeDto> getAllBikes() {
        return bikeRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<BikeDto> getAvailableBikes() {
        return bikeRepository.findByState(BikeState.AVAILABLE).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public BikeDto getBikeById(UUID id) {
        Bike bike = bikeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found"));
        return mapToDto(bike);
    }

    public Bike getBikeEntityById(UUID id) {
        return bikeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found"));
    }

    public Bike getBikeByQrCode(String qrCode) {
        return bikeRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found with QR code: " + qrCode));
    }

    @Transactional
    public BikeDto addBike(BikeDto bikeDto) {
        if (bikeRepository.findByQrCode(bikeDto.getQrCode()).isPresent()) {
            throw new BadRequestException("Bike with this QR code already exists");
        }

        Bike bike = Bike.builder()
                .qrCode(bikeDto.getQrCode())
                .latitude(bikeDto.getLatitude() != null ? bikeDto.getLatitude() : 9.035) // Default campus coordinate
                .longitude(bikeDto.getLongitude() != null ? bikeDto.getLongitude() : 38.752)
                .batteryLevel(100)
                .locked(true)
                .state(BikeState.AVAILABLE)
                .build();

        bike = bikeRepository.save(bike);
        return mapToDto(bike);
    }

    @Transactional
    public BikeDto updateBike(UUID id, BikeDto bikeDto) {
        Bike bike = bikeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found"));

        bike.setLatitude(bikeDto.getLatitude());
        bike.setLongitude(bikeDto.getLongitude());
        bike.setLocked(bikeDto.isLocked());
        bike.setBatteryLevel(bikeDto.getBatteryLevel());
        bike.setState(bikeDto.getState());

        bike = bikeRepository.save(bike);
        return mapToDto(bike);
    }

    @Transactional
    public void deleteBike(UUID id) {
        Bike bike = bikeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found"));
        bikeRepository.delete(bike);
    }

    public BikeDto mapToDto(Bike bike) {
        return BikeDto.builder()
                .id(bike.getId())
                .qrCode(bike.getQrCode())
                .latitude(bike.getLatitude())
                .longitude(bike.getLongitude())
                .locked(bike.isLocked())
                .batteryLevel(bike.getBatteryLevel())
                .state(bike.getState())
                .build();
    }
}
