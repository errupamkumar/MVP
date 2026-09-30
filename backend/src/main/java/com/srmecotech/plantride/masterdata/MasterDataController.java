package com.srmecotech.plantride.masterdata;

import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteDto;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.StopDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "2. Master data")
public class MasterDataController {

    private final StopRepository stopRepository;
    private final RouteResolver routeResolver;

    public MasterDataController(StopRepository stopRepository, RouteResolver routeResolver) {
        this.stopRepository = stopRepository;
        this.routeResolver = routeResolver;
    }

    @GetMapping("/stops")
    @Operation(summary = "All stops in service, alphabetically")
    @Transactional(readOnly = true)
    public List<StopDto> stops() {
        return stopRepository.findByActiveTrueOrderByNameAsc().stream().map(StopDto::of).toList();
    }

    @GetMapping("/routes")
    @Operation(summary = "Active routes with their stop sequence and rules")
    public List<RouteDto> routes() {
        return routeResolver.activePlans().stream().map(RouteDto::of).toList();
    }

    @GetMapping("/routes/{id}")
    @Operation(summary = "One route with its stop sequence and rules")
    public RouteDto route(@PathVariable Long id) {
        return RouteDto.of(routeResolver.planFor(id));
    }
}
