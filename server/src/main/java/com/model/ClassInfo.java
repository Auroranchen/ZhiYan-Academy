package com.model;

import lombok.Data;

/**
 * 班级实体类
 */
@Data
public class ClassInfo {

    /**
     * 班级ID
     */
    private Integer classid;

    /**
     * 班级名称
     */
    private String classname;

    /**
     * 所属专业ID
     */
    private Integer majorid;

    /**
     * 专业名称（关联查询）
     */
    private String majorname;

    public ClassInfo() {
    }

    public ClassInfo(Integer classid, String classname, Integer majorid) {
        this.classid = classid;
        this.classname = classname;
        this.majorid = majorid;
    }

}
