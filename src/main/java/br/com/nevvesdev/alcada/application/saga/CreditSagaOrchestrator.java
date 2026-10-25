package br.com.nevvesdev.alcada.application.saga;

import br.com.nevvesdev.alcada.application.exception.BureauUnavailableException;
import br.com.nevvesdev.alcada.application.exception.LimitConfirmationException;
import br.com.nevvesdev.alcada.application.exception.LimitReservationException;
import br.com.nevvesdev.alcada.application.port.out.CreditBureauPort;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.domain.exception.InvalidStateTransitionException;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestStatus;
import br.com.nevvesdev.alcada.domain.model.CreditScore;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import br.com.nevvesdev.alcada.domain.model.RiskDecision;
import br.com.nevvesdev.alcada.domain.service.RiskPolicy;

import java.time.Clock;
import java.util.Objects;

public final class CreditSagaOrchestrator {

    private final CreditRequestRepository repository;
    private final CreditBureauPort bureau;
    private final LimitReservationPort limits;
    private final RiskPolicy riskPolicy;
    private final Clock clock;

    public CreditSagaOrchestrator(CreditRequestRepository repository,
                                  CreditBureauPort bureau,
                                  LimitReservationPort limits,
                                  RiskPolicy riskPolicy,
                                  Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bureau = Objects.requireNonNull(bureau, "bureau");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreditRequest run(CreditRequest request) {
        while (!request.isTerminal() && request.status() != CreditRequestStatus.UNDER_REVIEW) {
            advance(request);
        }
        return request;
    }

    public CreditRequest approveReview(CreditRequest request) {
        requireUnderReview(request);
        reserveLimit(request);
        return run(request);
    }

    public CreditRequest denyReview(CreditRequest request) {
        requireUnderReview(request);
        reject(request, RejectionReason.REVIEW_DENIED);
        return request;
    }

    private void advance(CreditRequest request) {
        switch (request.status()) {
            case PENDING -> checkBureau(request);
            case BUREAU_CHECKED -> analyzeRisk(request);
            case RISK_ANALYZED -> decide(request);
            case LIMIT_RESERVED -> confirmLimit(request);
            default -> throw new IllegalStateException("Status sem passo na saga: " + request.status());
        }
    }

    private void checkBureau(CreditRequest request) {
        CreditScore score;
        try {
            score = bureau.fetchScore(request.cpf());
        } catch (BureauUnavailableException exception) {
            reject(request, RejectionReason.BUREAU_UNAVAILABLE);
            return;
        }
        request.registerBureauScore(score, clock.instant());
        repository.save(request);
    }

    private void analyzeRisk(CreditRequest request) {
        RiskDecision decision = riskPolicy.evaluate(
                request.creditScore(), request.requestedAmount(), request.declaredIncome());
        request.registerRiskDecision(decision, clock.instant());
        repository.save(request);
    }

    private void decide(CreditRequest request) {
        RiskDecision decision = request.riskDecision();
        if (decision.isDenied()) {
            reject(request, decision.rejectionReason());
            return;
        }
        if (decision.requiresManualReview()) {
            request.sendToReview(clock.instant());
            repository.save(request);
            return;
        }
        reserveLimit(request);
    }

    private void reserveLimit(CreditRequest request) {
        try {
            limits.reserve(request.id(), request.cpf(), request.requestedAmount());
        } catch (LimitReservationException exception) {
            reject(request, RejectionReason.LIMIT_RESERVATION_FAILED);
            return;
        }
        request.reserveLimit(clock.instant());
        repository.save(request);
    }

    private void confirmLimit(CreditRequest request) {
        try {
            limits.confirm(request.id());
        } catch (LimitConfirmationException exception) {
            limits.release(request.id());
            reject(request, RejectionReason.APPROVAL_FAILED);
            return;
        }
        request.approve(clock.instant());
        repository.save(request);
    }

    private void reject(CreditRequest request, RejectionReason reason) {
        request.reject(reason, clock.instant());
        repository.save(request);
    }

    private void requireUnderReview(CreditRequest request) {
        if (request.status() != CreditRequestStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("Solicitação não está em revisão manual");
        }
    }
}