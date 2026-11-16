package com.creator.catalog.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("video_category")
public class CategoryRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private byte[] nameKey;
    private Integer sortOrder;
    private Boolean enabled;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public byte[] getNameKey() { return nameKey; }
    public void setNameKey(byte[] nameKey) { this.nameKey = nameKey; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
