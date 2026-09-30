package com.srmecotech.plantride.matching;

import java.util.List;
import java.util.Optional;

/** Feasible candidates best-first, plus every rejection with its reason. */
public record MatchOutcome(List<Candidate> feasible, List<Rejection> rejected) {

    public Optional<Candidate> best() {
        return feasible.isEmpty() ? Optional.empty() : Optional.of(feasible.get(0));
    }
}
