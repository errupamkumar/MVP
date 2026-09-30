package com.srmecotech.plantride.matching;

public record DispatchResult(boolean assigned, Candidate candidate, String note) {

    public static DispatchResult assigned(Candidate candidate) {
        return new DispatchResult(true, candidate, candidate.reason());
    }

    public static DispatchResult unassigned(String note) {
        return new DispatchResult(false, null, note);
    }
}
