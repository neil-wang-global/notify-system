package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.config.DataSourceRole;
import com.example.notify.config.DataSourceRoleContext;
import com.example.notify.config.ReadWriteRoutingDataSource;
import org.junit.jupiter.api.Test;

class ReadWriteRoutingDataSourceTest {

    @Test
    void defaultsToWriteDatasource() {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();

        assertEquals(DataSourceRole.WRITE, routingDataSource.currentLookupKey());
    }

    @Test
    void usesReadDatasourceInsideReadContext() {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();

        DataSourceRoleContext.read(() -> assertEquals(DataSourceRole.READ, routingDataSource.currentLookupKey()));
    }

}
