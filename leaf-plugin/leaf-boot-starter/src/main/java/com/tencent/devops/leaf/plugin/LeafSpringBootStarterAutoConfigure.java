package com.tencent.devops.leaf.plugin;

import com.alibaba.druid.pool.DruidDataSource;
import com.google.common.base.Preconditions;
import com.tencent.devops.leaf.exception.InitException;
import com.tencent.devops.leaf.plugin.util.LeafSpringContextUtil;
import com.tencent.devops.leaf.segment.dao.IDAllocDao;
import com.tencent.devops.leaf.segment.dao.impl.IDAllocDaoImpl;
import com.tencent.devops.leaf.service.SegmentService;
import com.tencent.devops.leaf.service.SnowflakeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.util.StringUtils;


@Configuration
@EnableConfigurationProperties(LeafSpringBootProperties.class)
public class LeafSpringBootStarterAutoConfigure {
    @Bean
    public LeafSpringContextUtil leafSpringContextUtil() {
        return new LeafSpringContextUtil();
    }

    @Bean
    @DependsOn(value = {"leafSpringContextUtil"})
    @ConditionalOnProperty(prefix = "leaf.segment", name = "enable", havingValue = "true")
    public SegmentService initLeafSegmentStarter(LeafSpringBootProperties properties) throws Exception {
        LeafSpringBootProperties.Segment segment = properties.getSegment();
        if (segment != null) {
            String allocStrategyDaoBeanName = segment.getAllocStrategyDaoBeanName();
            IDAllocDao allocDao = null;
            if (!StringUtils.isEmpty(allocStrategyDaoBeanName)) {
                allocDao = LeafSpringContextUtil.getBean(allocStrategyDaoBeanName, IDAllocDao.class);
            } else {
                String url = segment.getUrl();
                String username = segment.getUsername();
                String pwd = segment.getPassword();
                Preconditions.checkNotNull(url, "database url can not be null");
                Preconditions.checkNotNull(username, "username can not be null");
                Preconditions.checkNotNull(pwd, "password can not be null");
                // Config dataSource
                DruidDataSource dataSource = new DruidDataSource();
                dataSource.setUrl(url);
                dataSource.setUsername(username);
                dataSource.setPassword(pwd);
                dataSource.init();
                // Config Dao
                allocDao = new IDAllocDaoImpl(dataSource);
            }
            return new SegmentService(allocDao);
        }
        throw new IllegalStateException("leaf.segment configuration is required when segment mode is enabled");
    }

    @Bean
    @ConditionalOnProperty(prefix = "leaf.snowflake", name = "enable", havingValue = "true")
    public SnowflakeService initLeafSnowflakeStarter(LeafSpringBootProperties properties) throws InitException {
        if (properties.getSnowflake() != null) {
            return new SnowflakeService(properties.getSnowflake().getAddress(), properties.getSnowflake().getPort());
        }
        throw new IllegalStateException("leaf.snowflake configuration is required when snowflake mode is enabled");
    }
}
