package com.shorty.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ClickWriterDuplicateTest {

    @Test
    void streamIdUniqueIsDuplicate() {
        var sql = new SQLException("duplicate key value violates unique constraint \"clicks_stream_id_key\"", "23505");
        var cve = new ConstraintViolationException("dup", sql, "clicks_stream_id_key");
        var div = new DataIntegrityViolationException("dup", cve);
        assertThat(ClickWriter.isStreamIdDuplicate(div)).isTrue();
    }

    @Test
    void foreignKeyIsNotTreatedAsDuplicate() {
        var sql = new SQLException("violates foreign key constraint \"clicks_url_id_fkey\"", "23503");
        var cve = new ConstraintViolationException("fk", sql, "clicks_url_id_fkey");
        var div = new DataIntegrityViolationException("fk", cve);
        assertThat(ClickWriter.isStreamIdDuplicate(div)).isFalse();
    }
}
