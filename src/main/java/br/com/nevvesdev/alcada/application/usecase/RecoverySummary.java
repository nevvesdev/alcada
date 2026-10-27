package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.util.List;

public record RecoverySummary(int claimed, List<CreditRequestId> failed) {

    public RecoverySummary {
        failed = List.copyOf(failed);
    }

    public int recovered() {
        return claimed - failed.size();
    }
}