package br.com.nevvesdev.alcada.application.port.out;

import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface StalledCreditRequestPort {

    List<CreditRequestId> claim(Instant staleBefore, Instant now, Duration lease, int batchSize);
}