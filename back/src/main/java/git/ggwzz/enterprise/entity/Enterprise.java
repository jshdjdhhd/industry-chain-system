package git.ggwzz.enterprise.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 企业实体，对应表 {@code enterprise}。
 */
@Data
@TableName("enterprise")
public class Enterprise {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业名称 */
    private String enterpriseName;

    /** 统一社会信用代码 */
    private String unifiedCode;

    /** 法定代表人 */
    private String legalPerson;

    /** 所在地区 */
    private String region;

    /** 所属行业 */
    private String industry;

    /** 经营地址 */
    private String address;

    /** 注册资本 */
    private String registeredCapital;

    /** 成立日期 */
    private LocalDate establishDate;

    /** 经营范围 */
    private String businessScope;

    /** 主营产品 */
    private String mainProducts;

    /** 核心技术 */
    private String coreTech;

    /** 专利信息 */
    private String patentInfo;

    /** 招投标信息 */
    private String bidInfo;

    /** 项目信息 */
    private String projectInfo;

    /** 联系人 */
    private String contactPerson;

    /** 联系方式 */
    private String contactPhone;

    /** 核心关键词（逗号分隔） */
    private String keywords;

    /** 产值（万元） */
    private BigDecimal outputValue;

    /** 税收（万元） */
    private BigDecimal taxAmount;

    /** 状态：UNREGISTERED（未入库）/ ACTIVE（已入库）/ OUTBOUND（已出库） */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
