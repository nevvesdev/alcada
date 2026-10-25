package br.com.nevvesdev.alcada.application.support;

import br.com.nevvesdev.alcada.application.exception.BureauUnavailableException;
import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditScore;

public class FakeCreditBureau implements CreditBureauPort {

    private CreditScore score = CreditScore.of(700);
    private boolean unavailable;
    private int calls;

    public void returning(int value) {
        this.score = CreditScore.of(value);
        this.unavailable = false;
    }

    public void unavailable() {
        this.unavailable = true;
    }

    public int calls() {
        return calls;
    }

    @Override
    public CreditScore fetchScore(Cpf cpf) {
        calls++;
        if (unavailable) {
            throw new BureauUnavailableException("Bureau indisponível");
        }
        return score;
    }
}