package com.srmecotech.ecoride.pricing;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class PromoService {

    private final PromoCodeRepository promos;
    private final Clock clock;

    public PromoService(PromoCodeRepository promos, Clock clock) {
        this.promos = promos;
        this.clock = clock;
    }

    /**
     * @return the promo if the rider may use it now, with a message either way. The
     * minimum-fare rule is checked per ride option when the fare is known.
     */
    public PromoCheck check(String rawCode, long riderId) {
        if (rawCode == null || rawCode.isBlank()) {
            return new PromoCheck(null, null, null);
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        PromoCode promo = promos.findById(code).orElse(null);
        if (promo == null || !promo.isActive()) {
            return new PromoCheck(code, null, "Promo code " + code + " is not valid.");
        }
        if (promo.getValidUntil() != null && LocalDate.now(clock).isAfter(promo.getValidUntil())) {
            return new PromoCheck(code, null, "Promo code " + code + " has expired.");
        }
        if (promos.usesBy(riderId, code) >= promo.getUsesPerRider()) {
            return new PromoCheck(code, null, "You have already used " + code + ".");
        }
        return new PromoCheck(code, promo, promo.getDescription());
    }

    /**
     * @param promo   null when the code was refused
     * @param message why it was refused, or what it gives
     */
    public record PromoCheck(String code, PromoCode promo, String message) {

        public boolean valid() {
            return promo != null;
        }
    }
}
