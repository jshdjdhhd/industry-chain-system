package git.ggwzz.agent.tools;

import git.ggwzz.chain.dto.CreateNodeRequest;
import git.ggwzz.chain.service.ChainService;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 产业链与节点相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class ChainTools {

    private final ChainService chainService;
    private final EnterpriseNodeRelService enterpriseNodeRelService;

    @Tool(description = "列出所有产业链选项（ID和名称）。")
    public String listChains() {
        try {
            List<ChainOptionVO> options = chainService.listChainOptions();
            if (options.isEmpty()) {
                return "当前没有产业链";
            }
            return options.stream()
                    .map(c -> String.format("ID=%d, 名称=%s", c.getId(), c.getName()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "获取产业链列表失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取指定产业链的完整节点树结构，包含每个节点的企业数量。")
    public String getChainNodeTree(
            @ToolParam(description = "产业链ID") Long chainId) {
        try {
            TreeNodeVO tree = chainService.getChainTree(chainId);
            return formatTreeNode(tree, 0);
        } catch (Exception e) {
            return "获取节点树失败: " + e.getMessage();
        }
    }

    @Tool(description = "在产业链中搜索节点，按关键词匹配节点名称或关键词字段。返回匹配的节点列表。")
    public String searchNodes(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "产业链ID，限定在某条产业链内搜索") Long chainId) {
        try {
            PageResult<NodeVO> result = chainService.searchByKeywords(keyword, chainId, 1, 20);
            if (result.getRecords().isEmpty()) {
                return "未找到匹配的节点";
            }
            return result.getRecords().stream()
                    .map(n -> String.format("ID=%d, 名称=%s, 层级=%d, 关键词=%s",
                            n.getId(), n.getNodeName(), n.getNodeLevel(),
                            n.getKeywords() != null ? n.getKeywords() : "无"))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "搜索节点失败: " + e.getMessage();
        }
    }

    @Tool(description = "在产业链中创建新节点。可指定父节点ID来创建子节点，不指定则创建一级节点。")
    public String createNode(
            @ToolParam(description = "产业链ID") Long chainId,
            @ToolParam(description = "父节点ID，为空则创建一级节点", required = false) Long parentId,
            @ToolParam(description = "节点名称") String nodeName,
            @ToolParam(description = "节点关键词，多个用逗号分隔", required = false) String keywords,
            @ToolParam(description = "节点描述/定义", required = false) String definition) {
        try {
            CreateNodeRequest request = new CreateNodeRequest();
            request.setChainId(chainId);
            request.setParentId(parentId);
            request.setNodeName(nodeName);
            request.setKeywords(keywords);
            request.setDescription(definition);
            NodeVO node = chainService.createNode(request);
            return String.format("节点创建成功: ID=%d, 名称=%s, 层级=%d, 产业链ID=%d",
                    node.getId(), node.getNodeName(), node.getNodeLevel(), node.getChainId());
        } catch (Exception e) {
            return "创建节点失败: " + e.getMessage();
        }
    }

    @Tool(description = "将企业挂链到指定产业链节点。执行人工确认挂链操作。")
    public String linkEnterpriseToNode(
            @ToolParam(description = "企业ID") Long enterpriseId,
            @ToolParam(description = "节点ID") Long nodeId) {
        try {
            Long operator = SecurityUtils.currentUserId();
            enterpriseNodeRelService.linkManually(enterpriseId, nodeId, "AI Agent 挂链", operator);
            return String.format("挂链成功: 企业ID=%d -> 节点ID=%d", enterpriseId, nodeId);
        } catch (Exception e) {
            return "挂链失败: " + e.getMessage();
        }
    }

    @Tool(description = "将企业从指定产业链节点解绑。")
    public String unlinkEnterpriseFromNode(
            @ToolParam(description = "企业ID") Long enterpriseId,
            @ToolParam(description = "节点ID") Long nodeId) {
        try {
            Long operator = SecurityUtils.currentUserId();
            enterpriseNodeRelService.unlink(nodeId, enterpriseId, operator);
            return String.format("解绑成功: 企业ID=%d 从节点ID=%d", enterpriseId, nodeId);
        } catch (Exception e) {
            return "解绑失败: " + e.getMessage();
        }
    }

    /**
     * 递归格式化节点树为缩进文本。
     */
    private String formatTreeNode(TreeNodeVO node, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        sb.append(String.format("%s- ID=%d, 名称=%s, 直接企业数=%d, 子树企业数=%d",
                indent, node.getId(), node.getNodeName(),
                node.getDirectEnterpriseCount() != null ? node.getDirectEnterpriseCount() : 0,
                node.getSubtreeEnterpriseCount() != null ? node.getSubtreeEnterpriseCount() : 0));
        if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
            sb.append(", 关键词=").append(node.getKeywords());
        }
        if (node.getChildren() != null) {
            for (TreeNodeVO child : node.getChildren()) {
                sb.append("\n").append(formatTreeNode(child, depth + 1));
            }
        }
        return sb.toString();
    }
}
