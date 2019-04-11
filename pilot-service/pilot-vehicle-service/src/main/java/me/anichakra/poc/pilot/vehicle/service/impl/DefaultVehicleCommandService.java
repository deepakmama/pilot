package me.anichakra.poc.pilot.vehicle.service.impl;

import java.util.List;

import javax.inject.Inject;

import me.anichakra.poc.pilot.framework.annotation.CommandService;
import me.anichakra.poc.pilot.framework.annotation.Event;
import me.anichakra.poc.pilot.vehicle.domain.Vehicle;
import me.anichakra.poc.pilot.vehicle.repo.VehicleRepository;
import me.anichakra.poc.pilot.vehicle.service.VehicleCommandService;

@CommandService(config="transunion")
public class DefaultVehicleCommandService implements VehicleCommandService {
	
	@Property
	private String prop1;
	
	@Property
	private String prop2;

	@Inject
	private VehicleRepository vehicleRepository;

	@Override
	@Event(name="notification")
	public Vehicle saveVehicle(Vehicle vehicle) {
		Vehicle v = vehicleRepository.save(vehicle);
		return v;
	}

	
	@Override
	public List<Vehicle> saveVehicles(List<Vehicle> vehicles) {
		List<Vehicle> v = vehicleRepository.saveAll(vehicles);
		return v;

	}

	@Override
	public void deleteVehicle(Long id) {
		vehicleRepository.deleteById(id);
	}

	
}
