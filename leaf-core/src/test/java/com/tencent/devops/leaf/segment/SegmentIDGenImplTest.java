package com.tencent.devops.leaf.segment;

import com.tencent.devops.leaf.segment.dao.IDAllocDao;
import com.tencent.devops.leaf.segment.model.LeafAlloc;
import com.tencent.devops.leaf.segment.model.SegmentBuffer;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class SegmentIDGenImplTest {

    @Test
    public void removesCachedTagsWhenDatabaseHasNoTags() {
        SegmentIDGenImpl idGen = new SegmentIDGenImpl();
        idGen.setDao(new EmptyTagsDao());
        SegmentBuffer staleBuffer = new SegmentBuffer();
        staleBuffer.setKey("removed-tag");
        idGen.getCache().put("removed-tag", staleBuffer);

        idGen.updateCacheFromDb();

        assertTrue(idGen.getCache().isEmpty());
    }

    private static class EmptyTagsDao implements IDAllocDao {
        @Override
        public List<LeafAlloc> getAllLeafAllocs() {
            return Collections.emptyList();
        }

        @Override
        public LeafAlloc updateMaxIdAndGetLeafAlloc(String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LeafAlloc updateMaxIdByCustomStepAndGetLeafAlloc(LeafAlloc leafAlloc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getAllTags() {
            return Collections.emptyList();
        }
    }
}
