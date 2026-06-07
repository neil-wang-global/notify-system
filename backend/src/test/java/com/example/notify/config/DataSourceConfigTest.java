package com.example.notify.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DataSourceConfigTest {

    @Test
    void createsReadWriteRoutingDatasource() {
        DataSourceConfig config = new DataSourceConfig();

        DataSource dataSource = config.dataSource(
            "jdbc:h2:mem:write-routing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jdbc:h2:mem:read-routing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );

        ReadWriteRoutingDataSource routing = assertInstanceOf(ReadWriteRoutingDataSource.class, dataSource);
        assertEquals(DataSourceRole.WRITE, routing.currentLookupKey());
        DataSourceRoleContext.read(() -> assertEquals(DataSourceRole.READ, routing.currentLookupKey()));
    }

}
