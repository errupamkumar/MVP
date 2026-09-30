package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.TrackingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "8. Public tracking")
public class PublicTrackingController {

    private final TrackingService trackingService;

    public PublicTrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/api/public/track/{token}")
    @ResponseBody
    @SecurityRequirements
    @Operation(summary = "Live progress for a shared tracking link (no login, no personal data)")
    public ResponseEntity<TrackingDto> track(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trackingService.track(token));
    }

    /** The shareable page, e.g. http://host:8080/t/8K2QFMXA. It polls the JSON endpoint above. */
    @GetMapping("/t/{token}")
    public String trackingPage(@PathVariable String token) {
        return "forward:/track.html";
    }
}
