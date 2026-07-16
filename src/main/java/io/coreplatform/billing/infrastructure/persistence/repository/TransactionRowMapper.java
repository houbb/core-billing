package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.domain.Transaction;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class TransactionRowMapper implements RowMapper<Transaction> {

    public static final TransactionRowMapper INSTANCE = new TransactionRowMapper();

    @Override
    public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getLong("id"));
        tx.setAccountId(rs.getLong("account_id"));
        tx.setTransactionNo(rs.getString("transaction_no"));
        tx.setTransactionType(
                io.coreplatform.billing.application.domain.TransactionType.valueOf(rs.getString("transaction_type")));
        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setDirection(
                io.coreplatform.billing.application.domain.Direction.valueOf(rs.getString("direction")));
        tx.setReferenceType(rs.getString("reference_type"));
        tx.setReferenceId(rs.getString("reference_id"));
        tx.setDescription(rs.getString("description"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) tx.setCreateTime(ct.toLocalDateTime());
        tx.setCreateUser(rs.getString("create_user"));
        return tx;
    }
}