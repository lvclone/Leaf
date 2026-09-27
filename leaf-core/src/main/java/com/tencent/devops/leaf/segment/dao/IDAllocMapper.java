package com.tencent.devops.leaf.segment.dao;

import com.tencent.devops.leaf.segment.model.LeafAlloc;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface IDAllocMapper {

    @Select("SELECT BIZ_TAG, MAX_ID, STEP, UPDATE_TIME FROM t_leaf_alloc")
    @Results(value = {
            @Result(column = "BIZ_TAG", property = "key"),
            @Result(column = "MAX_ID", property = "maxId"),
            @Result(column = "STEP", property = "step"),
            @Result(column = "UPDATE_TIME", property = "updateTime")
    })
    List<LeafAlloc> getAllLeafAllocs();

    @Select("SELECT BIZ_TAG, MAX_ID, STEP FROM T_LEAF_ALLOC WHERE BIZ_TAG = #{tag}")
    @Results(value = {
            @Result(column = "BIZ_TAG", property = "key"),
            @Result(column = "MAX_ID", property = "maxId"),
            @Result(column = "STEP", property = "step")
    })
    LeafAlloc getLeafAlloc(@Param("tag") String tag);

    @Update("UPDATE T_LEAF_ALLOC SET MAX_ID = MAX_ID + STEP WHERE BIZ_TAG = #{tag}")
    void updateMaxId(@Param("tag") String tag);

    @Update("UPDATE T_LEAF_ALLOC SET MAX_ID = MAX_ID + #{leafAlloc.step} WHERE BIZ_TAG = #{leafAlloc.key}")
    void updateMaxIdByCustomStep(@Param("leafAlloc") LeafAlloc leafAlloc);

    @Select("SELECT BIZ_TAG FROM T_LEAF_ALLOC")
    List<String> getAllTags();

}
