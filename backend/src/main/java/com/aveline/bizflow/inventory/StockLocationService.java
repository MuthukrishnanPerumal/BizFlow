package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.RecordStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StockLocationService {

    private final StockLocationRepository locationRepository;

    public StockLocationService(StockLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<StockLocation> active() {
        return locationRepository.findByStatusOrderByNameAsc(RecordStatus.ACTIVE);
    }

    public StockLocation create(String code, String name) {
        InventoryAccess.require("INVENTORY_TRANSFER");
        String locationCode = MasterText.code(code, "code");
        String locationName = MasterText.required(name, 120, "name", "Name");
        if (locationRepository.existsByLocationCode(locationCode)) {
            throw new BusinessRuleException("DUPLICATE_CODE", "code", "A location with this code already exists");
        }
        return locationRepository.save(StockLocation.create(locationCode, locationName, false));
    }

    @Transactional(readOnly = true)
    public StockLocation requireActive(Long locationId) {
        StockLocation location = locationId == null
                ? locationRepository.findByDefaultLocationTrue()
                        .orElseThrow(() -> new IllegalStateException("Default stock location is not configured"))
                : locationRepository.findById(locationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Stock location was not found"));
        if (location.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("LOCATION_INACTIVE", "locationId", location.getName() + " is inactive");
        }
        return location;
    }
}
