package br.com.nevvesdev.alcada.infrastructure.bureau;

import br.com.nevvesdev.alcada.application.exception.BureauUnavailableException;
import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.infrastructure.http.PermanentRemoteException;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteCallException;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteCalls;
import br.com.nevvesdev.alcada.infrastructure.http.RemoteServiceProperties;
import br.com.nevvesdev.alcada.infrastructure.http.ResilientCaller;
import br.com.nevvesdev.alcada.infrastructure.http.RestClients;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class HttpCreditBureauAdapter implements CreditBureauPort {

    private static final String SCORES_PATH = "/bureau/scores";

    private final RestClient restClient;
    private final ResilientCaller caller;

    public HttpCreditBureauAdapter(RemoteServiceProperties properties) {
        this.restClient = RestClients.create(properties);
        this.caller = new ResilientCaller("bureau", properties.retry(), properties.circuitBreaker());
    }

    @Override
    public CreditScore fetchScore(Cpf cpf) {
        try {
            int score = caller.execute(() -> requestScore(cpf));
            return CreditScore.of(score);
        } catch (RemoteCallException | CallNotPermittedException exception) {
            throw new BureauUnavailableException("Bureau de crédito indisponível", exception);
        }
    }

    private int requestScore(Cpf cpf) {
        BureauScoreResponse response = RemoteCalls.translate(() -> restClient.post()
                .uri(SCORES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BureauScoreRequest(cpf.value()))
                .retrieve()
                .body(BureauScoreResponse.class));
        if (response == null || response.score() == null || !isWithinRange(response.score())) {
            throw new PermanentRemoteException("Resposta inválida do bureau de crédito");
        }
        return response.score();
    }

    private static boolean isWithinRange(int score) {
        return score >= CreditScore.MIN && score <= CreditScore.MAX;
    }
}