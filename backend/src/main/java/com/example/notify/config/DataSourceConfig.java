package com.example.notify.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "notify.datasource.routing-enabled", havingValue = "true")
public class DataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(
        @Value("${notify.datasource.write-url}") String writeUrl,
        @Value("${notify.datasource.read-url}") String readUrl,
        @Value("${notify.datasource.username}") String username,
        @Value("${notify.datasource.password}") String password
    ) {
        HikariDataSource write = hikari(writeUrl, username, password);
        HikariDataSource read = hikari(readUrl, username, password);
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setDefaultTargetDataSource(write);
        routing.setTargetDataSources(Map.of(DataSourceRole.WRITE, write, DataSourceRole.READ, read));
        routing.afterPropertiesSet();
        return routing;
    }

    private static HikariDataSource hikari(String url, String username, String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

}
