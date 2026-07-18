package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.port.OperationLogRepository;
import io.coreplatform.billing.infrastructure.persistence.entity.BillingOperationLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcOperationLogRepository implements OperationLogRepository {

    private final JdbcTemplate jdbc;

    public JdbcOperationLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String accountId, String operation, String operator, String reason, String detail) {
        String sql = "INSERT INTO billing_operation_log (account_id, operation, operator, reason, detail, create_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, accountId, operation, operator, reason, detail,
                new Timestamp(System.currentTimeMillis()));
    }

    @Override
    public List<BillingOperationLogEntity> findByAccountId(Long accountId, int page, int size) {
        int offset = (page - 1) * size;
        String sql = "SELECT id, account_id, operation, operator, reason, detail, create_time " +
                     "FROM billing_operation_log WHERE account_id = ? " +
                     "ORDER BY create_time DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, OPERATION_LOG_ROW_MAPPER, accountId, size, offset);
    }

    private static final RowMapper<BillingOperationLogEntity> OPERATION_LOG_ROW_MAPPER = (rs, rowNum) -> {
        BillingOperationLogEntity entity = new BillingOperationLogEntity();
        entity.setId(rs.getLong("id"));
        entity.setAccountId(rs.getLong("account_id"));
        entity.setOperation(rs.getString("operation"));
        entity.setOperator(rs.getString("operator"));
        entity.setReason(rs.getString("reason"));
        entity.setDetail(rs.getString("detail"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) entity.setCreateTime(ct.toLocalDateTime().toString());
        return entity;
    };
}