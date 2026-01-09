package com.sy.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分析任务实体类
 */
@Data
@TableName("analysis_tasks")
public class AnalysisTask {
    
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;
    
    private Long userId;
    
    private Long fileId;  // 普通任务的文件ID，MAG任务为null
    
    private String taskType;  // 任务类型：NORMAL-普通任务, MAG-MAG分析
    
    private String magDirPath;  // MAG文件夹路径
    
    private Integer magFileCount;  // MAG文件数量
    
    private String taskName;
    
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    
    private Integer progress;
    
    private String parameters; // JSON格式
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private String dockerContainerId;
    
    private String dockerImage;
    
    private String outputDir;
    
    private String errorMessage;
    
    private String logFile;
    
    private Long genomeLength;
    
    private Integer prophageCount;
}

