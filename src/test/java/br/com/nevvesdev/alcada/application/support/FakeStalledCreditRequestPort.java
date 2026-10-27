package br.com.nevvesdev.alcada.application.support;

import br.com.nevvesdev.alcada.application.port.out.StalledCreditRequestPort;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class FakeStalledCreditRequestPort implements StalledCreditRequestPort {

    private List<CreditRequestId> toReturn = List.of();
    private Instant lastStaleBefore;
    private Instant lastNow;
    private Duration lastLease;
    private int lastBatchSize;

    public void willReturn(CreditRequestId... ids) {
        this.toReturn = List.of(ids);
    }

    public Instant lastStaleBefore() {
        return lastStaleBefore;
    }

    public Instant lastNow() {
        return lastNow;
    }

    public Duration lastLease() {
        return lastLease;
    }

    public int lastBatchSize() {
        return lastBatchSize;
    }

    @Override
    public List<CreditRequestId> claim(Instant staleBefore, Instant now, Duration lease, int batchSize) {
        this.lastStaleBefore = staleBefore;
        this.lastNow = now;
        this.lastLease = lease;
        this.lastBatchSize = batchSize;
        return toReturn;
    }
}