package com.merge.merge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:latest"));
    }

    /**
     * Redis container. There is no dedicated Testcontainers module with
     * @ServiceConnection support for Redis in this Spring Boot version, so
     * GenericContainer is used and the connection details are contributed via
     * DynamicPropertyRegistrar below.
     */
    @Bean
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        container.start();
        return container;
    }

    /**
     * Wires the container's dynamic mapped port into Spring's Redis properties.
     * DynamicPropertyRegistrar runs after the container starts and before the
     * application context is created, so the correct port is always bound.
     *
     * <p>Uses database 1 so tests are isolated from the default database even
     * if a local Redis is running alongside the container.</p>
     */
    @Bean
    public DynamicPropertyRegistrar redisProperties(GenericContainer<?> redisContainer) {
        return registry -> {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
            registry.add("spring.data.redis.database", () -> 1);
        };
    }
}
