package com.srmecotech.ecoride.rider;

import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rider")
@Tag(name = "Rider app · account")
public class RiderAccountController {

    private final RiderAccountService accounts;

    public RiderAccountController(RiderAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    public RiderDtos.Profile me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.profile(user.id());
    }

    @PutMapping("/me")
    public RiderDtos.Profile update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody RiderDtos.UpdateProfileRequest request) {
        return accounts.update(user.id(), request);
    }

    @GetMapping("/places")
    @Operation(summary = "Saved places (Home, Work, others)")
    public List<RiderDtos.SavedPlaceDto> places(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.places(user.id());
    }

    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)
    public RiderDtos.SavedPlaceDto savePlace(@AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody RiderDtos.SavePlaceRequest request) {
        return accounts.savePlace(user.id(), request);
    }

    @DeleteMapping("/places/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlace(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        accounts.deletePlace(user.id(), id);
    }

    @GetMapping("/wallet")
    public RiderDtos.Wallet wallet(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.wallet(user.id());
    }

    @PostMapping("/wallet/topup")
    @Operation(summary = "Add money to the wallet (simulated UPI)")
    public RiderDtos.Wallet topUp(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody RiderDtos.TopUpRequest request) {
        return accounts.topUp(user.id(), request.amount());
    }
}
