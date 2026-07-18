package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.domain.Account;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AccountRowMapper implements RowMapper<Account> {

    public static final AccountRowMapper INSTANCE = new AccountRowMapper();

    @Override
    public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        Account account = new Account();
        account.setId(rs.getLong("id"));
        account.setTenantId(rs.getString("tenant_id"));
        account.setAccountName(rs.getString("account_name"));
        account.setAccountType(
                io.coreplatform.billing.application.domain.AccountType.valueOf(rs.getString("account_type")));
        account.setStatus(
                io.coreplatform.billing.application.domain.AccountStatus.valueOf(rs.getString("status")));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) account.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) account.setUpdateTime(ut.toLocalDateTime());
        account.setCreateUser(rs.getString("create_user"));
        account.setUpdateUser(rs.getString("update_user"));
        return account;
    }
}