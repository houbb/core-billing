package io.coreplatform.billing.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 在 Spring 初始化任何 Bean 之前确保 data 目录存在。
 * 这比 Flyway 自动配置更早执行。
 */
@Component
public class DataDirectoryInitializer implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryInitializer.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("Created data directory: {}", dataDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to create data directory", e);
            throw new RuntimeException("Cannot create data directory for SQLite", e);
        }
    }
}