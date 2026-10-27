package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.application.port.out.StalledCreditRequestPort;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaStalledCreditRequestAdapter implements StalledCreditRequestPort {

    private final SpringDataCreditRequestRepository repository;
    private final TransactionTemplate transactionTemplate;

    public JpaStalledCreditRequestAdapter(SpringDataCreditRequestRepository repository,
                                          PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<CreditRequestId> claim(Instant staleBefore, Instant now, Duration lease, int batchSize) {
        List<CreditRequestId> claimed = transactionTemplate.execute(status -> {
            List<UUID> ids = repository.lockStalled(staleBefore, now, batchSize);
            if (ids.isEmpty()) {
                return List.<CreditRequestId>of();
            }
            repository.claim(ids, now.plus(lease));
            return ids.stream().map(CreditRequestId::new).toList();
        });
        return claimed == null ? List.of() : claimed;
    }
}