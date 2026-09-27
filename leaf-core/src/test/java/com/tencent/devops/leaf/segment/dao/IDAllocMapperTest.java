package com.tencent.devops.leaf.segment.dao;

import com.tencent.devops.leaf.segment.model.LeafAlloc;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IDAllocMapperTest {

    @Test
    public void customStepSqlUsesTheLeafAllocProperties() {
        Configuration configuration = new Configuration();
        configuration.addMapper(IDAllocMapper.class);
        LeafAlloc leafAlloc = new LeafAlloc();
        leafAlloc.setKey("orders");
        leafAlloc.setStep(2000);

        BoundSql boundSql = configuration.getMappedStatement(
                "com.tencent.devops.leaf.segment.dao.IDAllocMapper.updateMaxIdByCustomStep")
                .getBoundSql(leafAlloc);
        MetaObject parameterValues = configuration.newMetaObject(leafAlloc);

        assertEquals("orders", parameterValues.getValue(
                boundSql.getParameterMappings().get(1).getProperty()));
        assertEquals(2000, parameterValues.getValue(
                boundSql.getParameterMappings().get(0).getProperty()));
    }
}
