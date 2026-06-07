package com.example.notify.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public final class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceRoleContext.current();
    }

    public DataSourceRole currentLookupKey() {
        return (DataSourceRole) determineCurrentLookupKey();
    }

}
