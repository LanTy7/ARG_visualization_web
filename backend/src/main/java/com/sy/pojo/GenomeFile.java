package com.sy.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 基因文件实体类
 */
@Data
@TableName("genome_files")
public class GenomeFile {
    
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;
    
    private Long userId;
    
    private String originalFilename;
    
    private String storedFilename;
    
    private String filePath;
    
    private Long fileSize;
    
    private String fileType;
    
    private String fileFormat;
    
    private String md5Hash;
    
    private String description;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;
    
    private String status; // UPLOADED, DELETED
    
    private Boolean isPublic;
}

