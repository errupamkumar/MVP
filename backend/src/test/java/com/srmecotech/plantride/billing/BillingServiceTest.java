package com.srmecotech.plantride.billing;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.settings.SettingsService.CostSharingRule;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.DriverRepository;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.trip.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static com.srmecotech.plantride.support.Fixtures.BF;
import static com.srmecotech.plantride.support.Fixtures.CLOCK;
import static com.srmecotech.plantride.support.Fixtures.COKE;
import static com.srmecotech.plantride.support.Fixtures.GATE2;
import static com.srmecotech.plantride.support.Fixtures.NOW;
import static com.srmecotech.plantride.support.Fixtures.cab;
import static com.srmecotech.plantride.support.Fixtures.costCentre;
import static com.srmecotech.plantride.support.Fixtures.driver;
import static com.srmecotech.plantride.support.Fixtures.rider;
import static com.srmecotech.plantride.support.Fixtures.route1;
import static com.srmecotech.plantride.support.Fixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock
    CostAllocationRepository allocationRepository;
    @Mock
    SettingsService settings;
    @Mock
    NotificationService notificationService;
    @Mock
    NamedParameterJdbcTemplate jdbc;
    @Mock
    VehicleRepository vehicleRepository;
    @Mock
    DriverRepository driverRepository;
    @Mock
    ComplianceService complianceService;

    private BillingService billing;
    private final RoutePlan plan = route1(3);
    private Trip trip;
    private List<Booking> rides;

    @BeforeEach
    void setUp() {
        billing = new BillingService(allocationRepository, settings, notificationService, jdbc, vehicleRepository,
                driverRepository, complianceService, CLOCK);
        trip = trip(10, plan.route(), cab(1, "C-12", GATE2), driver(1, "S. Kumar"), 1, 4);
        trip.setEndedAt(NOW);
        trip.setDistanceKm(new BigDecimal("3.40"));
        trip.setCostAmount(new BigDecimal("61.20"));
        // The golden-path trip: M. Iyer to Coke Plant (2.3 km), two riders to Blast Furnace (3.4 km).
        rides = List.of(ride(1, 1, 3), ride(2, 1, 4), ride(3, 1, 4));
        when(allocationRepository.existsByBookingId(anyLong())).thenReturn(false);
    }

    private Booking ride(long id, int from, int to) {
        Booking b = rider(id, trip, from, to, BookingStatus.COMPLETED);
        b.setCostCentre(costCentre("CC-" + id));
        b.setFromStop(GATE2);
        b.setToStop(to == 3 ? COKE : BF);
        b.setDistanceKm(plan.kmBetween(from, to));
        return b;
    }

    @Test
    @DisplayName("Distance rule: shares follow kilometres ridden and add up to the trip cost to the paisa")
    void distanceSplit() {
        when(settings.costSharingRule()).thenReturn(CostSharingRule.DISTANCE);

        billing.allocate(trip, plan, rides);

        assertThat(rides).extracting(Booking::getCostAmount)
                .containsExactly(new BigDecimal("15.47"), new BigDecimal("22.87"), new BigDecimal("22.86"));
        BigDecimal total = rides.stream().map(Booking::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(trip.getCostAmount());

        ArgumentCaptor<CostAllocation> saved = ArgumentCaptor.forClass(CostAllocation.class);
        verify(allocationRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(a -> {
            assertThat(a.getPeriod()).isEqualTo("2026-09");
            assertThat(a.getSharingRule()).isEqualTo("DISTANCE");
            assertThat(a.getRidersOnTrip()).isEqualTo(3);
        });
        verify(notificationService, times(3)).notifyBooker(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Equal rule: everyone pays the same share")
    void equalSplit() {
        when(settings.costSharingRule()).thenReturn(CostSharingRule.EQUAL);

        billing.allocate(trip, plan, rides);

        assertThat(rides).extracting(Booking::getCostAmount).allSatisfy(a -> assertThat(a).isEqualByComparingTo("20.40"));
    }

    @Test
    @DisplayName("A ride already billed is never billed twice")
    void idempotentPerBooking() {
        when(settings.costSharingRule()).thenReturn(CostSharingRule.DISTANCE);
        when(allocationRepository.existsByBookingId(2L)).thenReturn(true);

        billing.allocate(trip, plan, rides);

        verify(allocationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Nothing to bill when no ride completed")
    void noRides() {
        billing.allocate(trip, plan, List.of());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Billing periods are validated: yyyy-MM, never in the future")
    void periodValidation() {
        assertThat(billing.parsePeriod(null).toString()).isEqualTo("2026-09");
        assertThat(billing.parsePeriod("2026-08").toString()).isEqualTo("2026-08");
        assertThatThrownBy(() -> billing.parsePeriod("Sept")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> billing.parsePeriod("2027-01")).isInstanceOf(InvalidRequestException.class);
    }
}
