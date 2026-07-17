package io.coreplatform.billing.application.port;

import io.coreplatform.billing.application.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findById(Long id);

    List<Account> findAll(int page, int size);

    List<Account> findByTenant(String tenantId, int page, int size);

    int count();

    int countByTenant(String tenantId);

    Account save(Account account);
}
