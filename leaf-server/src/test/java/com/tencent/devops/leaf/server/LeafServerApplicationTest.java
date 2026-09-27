package com.tencent.devops.leaf.server;

import com.tencent.devops.leaf.plugin.LeafSpringBootProperties;
import com.tencent.devops.leaf.service.SegmentService;
import com.tencent.devops.leaf.service.SnowflakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class LeafServerApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void contextLoads() {
        assertTrue(applicationContext.getBeansOfType(SegmentService.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(SnowflakeService.class).isEmpty());
    }

    @Test
    public void bindsEnvironmentVariableNames() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("LEAF_NAME", "env-leaf");
        variables.put("LEAF_SEGMENT_ENABLE", "true");
        variables.put("LEAF_SEGMENT_URL", "jdbc:mysql://db.example/leaf");
        variables.put("LEAF_SEGMENT_USERNAME", "leaf-user");
        variables.put("LEAF_SEGMENT_PASSWORD", "test-password");
        variables.put("LEAF_SNOWFLAKE_ENABLE", "true");
        variables.put("LEAF_SNOWFLAKE_ADDRESS", "zk.example:2181");
        variables.put("LEAF_SNOWFLAKE_PORT", "8081");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("testEnvironment", variables));
        assertEquals("env-leaf", environment.getProperty("leaf.name"));
        assertEquals("true", environment.getProperty("leaf.segment.enable"));
        assertEquals("jdbc:mysql://db.example/leaf", environment.getProperty("leaf.segment.url"));
        assertEquals("leaf-user", environment.getProperty("leaf.segment.username"));
        assertEquals("test-password", environment.getProperty("leaf.segment.password"));
        assertEquals("true", environment.getProperty("leaf.snowflake.enable"));
        assertEquals("zk.example:2181", environment.getProperty("leaf.snowflake.address"));
        assertEquals(Integer.valueOf(8081), Binder.get(environment).bind("leaf.snowflake.port", Integer.class).get());

        new ApplicationContextRunner()
                .withPropertyValues(
                        "leaf.name=env-leaf",
                        "leaf.segment.enable=true",
                        "leaf.segment.url=jdbc:mysql://db.example/leaf",
                        "leaf.segment.username=leaf-user",
                        "leaf.segment.password=test-password",
                        "leaf.snowflake.enable=true",
                        "leaf.snowflake.address=zk.example:2181",
                        "leaf.snowflake.port=8081")
                .withUserConfiguration(LeafPropertiesTestConfiguration.class)
                .run(context -> {
                    LeafSpringBootProperties properties = context.getBean(LeafSpringBootProperties.class);
                    assertEquals("env-leaf", properties.getName());
                    assertTrue(properties.getSegment().isEnable());
                    assertEquals("jdbc:mysql://db.example/leaf", properties.getSegment().getUrl());
                    assertEquals("leaf-user", properties.getSegment().getUsername());
                    assertEquals("test-password", properties.getSegment().getPassword());
                    assertTrue(properties.getSnowflake().isEnable());
                    assertEquals("zk.example:2181", properties.getSnowflake().getAddress());
                    assertEquals(8081, properties.getSnowflake().getPort());
                });
    }

    @Configuration
    @EnableConfigurationProperties(LeafSpringBootProperties.class)
    static class LeafPropertiesTestConfiguration {
    }
}
