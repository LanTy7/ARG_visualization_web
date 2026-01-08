package com.sy.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 分析结果实体类（原噬菌体区域）
 */
@Data
@TableName("analysis_results")
public class AnalysisResult {
    
    @TableId(value = "result_id", type = IdType.AUTO)
    private Long resultId;
    
    private Long taskId;
    
    private Integer regionIndex;
    
    private Integer startPos;
    
    private Integer endPos;
    
    private Integer length;
    
    private String strand;
    
    private Double score;
    
    private Double confidence;
    
    private String completeness; // complete, incomplete
    
    private Integer geneCount;
    
    private String genes; // JSON格式
    
    private String functionalCategory; // JSON格式
    
    private String annotations; // JSON格式
    
    private Double gcContent;
    
    @TableField(exist = false) // 序列可能很大，不一定存数据库
    private String sequence;
}

