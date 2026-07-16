package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.AccountStatus;
import io.coreplatform.billing.application.domain.AccountType;
import io.coreplatform.billing.application.port.AccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findById(Long id) {
        String sql = "SELECT id, tenant_id, account_name, account_type, status, " +
                     "create_time, update_time, create_user, update_user " +
                     "FROM billing_account WHERE id = ?";
        List<Account> list = jdbc.query(sql, AccountRowMapper.INSTANCE, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Account> findAll(int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT id, tenant_id, account_name, account_type, status, " +
                     "create_time, update_time, create_user, update_user " +
                     "FROM billing_account ORDER BY id DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, AccountRowMapper.INSTANCE, size, offset);
    }

    @Override
    public List<Account> findByTenant(String tenantId, int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT id, tenant_id, account_name, account_type, status, " +
                     "create_time, update_time, create_user, update_user " +
                     "FROM billing_account WHERE tenant_id = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, AccountRowMapper.INSTANCE, tenantId, size, offset);
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM billing_account";
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result != null ? result : 0;
    }

    @Override
    public Account save(Account account) {
        if (account.getId() == null) {
            return insert(account);
        } else {
            return update(account);
        }
    }

    private Account insert(Account account) {
        String sql = "INSERT INTO billing_account (tenant_id, account_name, account_type, status, " +
                     "create_time, update_time, create_user, update_user) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, account.getTenantId() != null ? account.getTenantId() : "");
            ps.setString(2, account.getAccountName());
            ps.setString(3, account.getAccountType().name());
            ps.setString(4, account.getStatus().name());
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            ps.setString(7, account.getCreateUser() != null ? account.getCreateUser() : "");
            ps.setString(8, account.getUpdateUser() != null ? account.getUpdateUser() : "");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            account.setId(key.longValue());
        }
        return account;
    }

    private Account update(Account account) {
        String sql = "UPDATE billing_account SET tenant_id=?, account_name=?, account_type=?, " +
                     "status=?, update_time=?, update_user=? WHERE id=?";
        jdbc.update(sql,
                account.getTenantId(),
                account.getAccountName(),
                account.getAccountType().name(),
                account.getStatus().name(),
                new Timestamp(System.currentTimeMillis()),
                account.getUpdateUser(),
                account.getId());
        return account;
    }
}