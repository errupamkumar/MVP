package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.common.error.ForbiddenOperationException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.DriverRepository;
import org.springframework.stereotype.Component;

/** Resolves the signed-in user to their driver record. */
@Component
public class DriverContext {

    private final DriverRepository driverRepository;

    public DriverContext(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public Driver require(AuthenticatedUser user) {
        return driverRepository.findByUserId(user.id())
                .orElseThrow(() -> new ForbiddenOperationException("This login has no driver profile."));
    }
}
