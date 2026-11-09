package git.ggwzz.agent.tools;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.enterprise.dto.CreateEnterpriseRequest;
import git.ggwzz.enterprise.dto.EnterpriseQueryRequest;
import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.enterprise.vo.EnterpriseDetailVO;
import git.ggwzz.enterprise.vo.EnterpriseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 企业相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseTools {

    private final EnterpriseService enterpriseService;

    @Tool(description = "搜索企业列表，按关键词模糊匹配企业名称。返回匹配的企业列表（ID、名称、信用代码、状态等）。")
    public String searchEnterprises(
            @ToolParam(description = "搜索关键词，模糊匹配企业名称") String keyword) {
        try {
            EnterpriseQueryRequest query = new EnterpriseQueryRequest();
            query.setKeyword(keyword);
            query.setPageNum(1);
            query.setPageSize(20);
            PageResult<EnterpriseVO> result = enterpriseService.list(query);
            if (result.getRecords().isEmpty()) {
                return "未找到匹配的企业";
            }
            return result.getRecords().stream()
                    .map(e -> String.format("ID=%d, 名称=%s, 信用代码=%s, 状态=%s",
                            e.getId(), e.getName(), e.getCreditCode(), e.getStatus()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "搜索企业失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取企业详情，包含标签、挂链节点等完整信息。")
    public String getEnterpriseDetail(
            @ToolParam(description = "企业ID") Long enterpriseId) {
        try {
            EnterpriseDetailVO detail = enterpriseService.getById(enterpriseId);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ID=%d, 名称=%s, 信用代码=%s, 法人=%s, 行业=%s, 地址=%s, 状态=%s",
                    detail.getId(), detail.getName(), detail.getCreditCode(),
                    detail.getLegalPerson(), detail.getIndustry(), detail.getAddress(),
                    detail.getStatus()));
            if (detail.getMainProducts() != null) {
                sb.append(", 主营产品=").append(detail.getMainProducts());
            }
            if (detail.getTags() != null && !detail.getTags().isEmpty()) {
                sb.append(", 标签=").append(detail.getTags().stream()
                        .map(t -> t.getName()).collect(Collectors.joining(",")));
            }
            if (detail.getLinkedNodes() != null && !detail.getLinkedNodes().isEmpty()) {
                sb.append(", 已挂链节点数=").append(detail.getLinkedNodes().size());
            }
            return sb.toString();
        } catch (Exception e) {
            return "获取企业详情失败: " + e.getMessage();
        }
    }

    @Tool(description = "创建新企业（默认未入库状态）。至少需要提供企业名称。")
    public String createEnterprise(
            @ToolParam(description = "企业名称") String name,
            @ToolParam(description = "统一社会信用代码", required = false) String creditCode,
            @ToolParam(description = "法定代表人", required = false) String legalPerson,
            @ToolParam(description = "主营产品", required = false) String mainProducts,
            @ToolParam(description = "经营地址", required = false) String address) {
        try {
            CreateEnterpriseRequest request = new CreateEnterpriseRequest();
            request.setName(name);
            request.setCreditCode(creditCode);
            request.setLegalPerson(legalPerson);
            request.setMainProducts(mainProducts);
            request.setAddress(address);
            EnterpriseDetailVO created = enterpriseService.create(request);
            return String.format("企业创建成功: ID=%d, 名称=%s, 状态=%s",
                    created.getId(), created.getName(), created.getStatus());
        } catch (Exception e) {
            return "创建企业失败: " + e.getMessage();
        }
    }

    @Tool(description = "企业入库操作，将企业状态从 UNREGISTERED/OUTBOUND 变更为 ACTIVE。")
    public String inboundEnterprise(
            @ToolParam(description = "企业ID") Long enterpriseId) {
        try {
            enterpriseService.inbound(enterpriseId);
            return "企业入库成功, ID=" + enterpriseId;
        } catch (Exception e) {
            return "企业入库失败: " + e.getMessage();
        }
    }

    @Tool(description = "企业出库操作，将企业状态从 ACTIVE 变更为 OUTBOUND。")
    public String outboundEnterprise(
            @ToolParam(description = "企业ID") Long enterpriseId) {
        try {
            enterpriseService.outbound(enterpriseId);
            return "企业出库成功, ID=" + enterpriseId;
        } catch (Exception e) {
            return "企业出库失败: " + e.getMessage();
        }
    }

    @Tool(description = "删除企业（仅未入库状态可删除）。")
    public String deleteEnterprise(
            @ToolParam(description = "企业ID") Long enterpriseId) {
        try {
            enterpriseService.delete(enterpriseId);
            return "企业删除成功, ID=" + enterpriseId;
        } catch (Exception e) {
            return "删除企业失败: " + e.getMessage();
        }
    }
}
