package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.port.TransactionRepository;
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
public class JdbcTransactionRepository implements TransactionRepository {

    private final JdbcTemplate jdbc;

    public JdbcTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        String sql = "SELECT id, account_id, transaction_no, transaction_type, amount, direction, " +
                     "reference_type, reference_id, description, create_time, create_user " +
                     "FROM billing_transaction WHERE id = ?";
        List<Transaction> list = jdbc.query(sql, TransactionRowMapper.INSTANCE, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<Transaction> findByTransactionNo(String transactionNo) {
        String sql = "SELECT id, account_id, transaction_no, transaction_type, amount, direction, " +
                     "reference_type, reference_id, description, create_time, create_user " +
                     "FROM billing_transaction WHERE transaction_no = ?";
        List<Transaction> list = jdbc.query(sql, TransactionRowMapper.INSTANCE, transactionNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<Transaction> findByReference(String referenceType, String referenceId) {
        String sql = "SELECT id, account_id, transaction_no, transaction_type, amount, direction, " +
                     "reference_type, reference_id, description, create_time, create_user " +
                     "FROM billing_transaction WHERE reference_type = ? AND reference_id = ?";
        List<Transaction> list = jdbc.query(sql, TransactionRowMapper.INSTANCE, referenceType, referenceId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Transaction> findByAccountId(Long accountId, int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT id, account_id, transaction_no, transaction_type, amount, direction, " +
                     "reference_type, reference_id, description, create_time, create_user " +
                     "FROM billing_transaction WHERE account_id = ? " +
                     "ORDER BY create_time DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, TransactionRowMapper.INSTANCE, accountId, size, offset);
    }

    @Override
    public int countByAccountId(Long accountId) {
        String sql = "SELECT COUNT(*) FROM billing_transaction WHERE account_id = ?";
        Integer result = jdbc.queryForObject(sql, Integer.class, accountId);
        return result != null ? result : 0;
    }

    @Override
    public Transaction save(Transaction tx) {
        String sql = "INSERT INTO billing_transaction (account_id, transaction_no, transaction_type, " +
                     "amount, direction, reference_type, reference_id, description, create_time, create_user) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tx.getAccountId());
            ps.setString(2, tx.getTransactionNo());
            ps.setString(3, tx.getTransactionType().name());
            ps.setBigDecimal(4, tx.getAmount());
            ps.setString(5, tx.getDirection().name());
            ps.setString(6, tx.getReferenceType() != null ? tx.getReferenceType() : "");
            ps.setString(7, tx.getReferenceId() != null ? tx.getReferenceId() : "");
            ps.setString(8, tx.getDescription() != null ? tx.getDescription() : "");
            ps.setTimestamp(9, now);
            ps.setString(10, tx.getCreateUser() != null ? tx.getCreateUser() : "");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            tx.setId(key.longValue());
        }
        return tx;
    }
}