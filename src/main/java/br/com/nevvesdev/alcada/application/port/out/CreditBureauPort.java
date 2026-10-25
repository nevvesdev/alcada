package br.com.nevvesdev.alcada.application.port.out;

import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditScore;

public interface CreditBureauPort {

    CreditScore fetchScore(Cpf cpf);
}