package git.ggwzz.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建企业请求。
 */
@Data
public class CreateEnterpriseRequest {

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 200, message = "企业名称不能超过 200 个字符")
    private String name;

    /** 统一社会信用代码 */
    @Size(max = 32, message = "统一社会信用代码不能超过 32 个字符")
    private String creditCode;

    /** 法定代表人 */
    @Size(max = 64, message = "法定代表人不能超过 64 个字符")
    private String legalPerson;

    /** 所在地区 */
    @Size(max = 64, message = "所在地区不能超过 64 个字符")
    private String region;

    /** 所属行业 */
    @Size(max = 64, message = "所属行业不能超过 64 个字符")
    private String industry;

    /** 经营地址 */
    @Size(max = 255, message = "经营地址不能超过 255 个字符")
    private String address;

    /** 注册资本 */
    @Size(max = 64, message = "注册资本不能超过 64 个字符")
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
    @Size(max = 64, message = "联系人不能超过 64 个字符")
    private String contactPerson;

    /** 联系方式 */
    @Size(max = 32, message = "联系方式不能超过 32 个字符")
    private String contactPhone;

    /** 核心关键词 */
    @Size(max = 500, message = "核心关键词不能超过 500 个字符")
    private String keywords;

    /** 产值（万元） */
    private BigDecimal outputValue;

    /** 税收（万元） */
    private BigDecimal taxAmount;
}
