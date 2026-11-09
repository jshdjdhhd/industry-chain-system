package git.ggwzz.analysis.controller;

import git.ggwzz.analysis.service.ChainDiagnosisTreeService;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import git.ggwzz.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产业链诊断分析接口。
 *
 * <p>提供诊断树与健康度查询；报告生成相关接口见后续模块。</p>
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class ChainDiagnosisController {

    private final ChainDiagnosisTreeService chainDiagnosisTreeService;

    /**
     * 获取产业链诊断树（含健康度）。
     *
     * <p>返回虚拟根节点，前端 G6 以产业链名作为根展示。
     * 每个节点携带健康分、健康等级、中文标签及弱项原因。</p>
     *
     * @param chainId 产业链 ID
     * @return 诊断树虚拟根节点
     */
    @GetMapping("/chains/{chainId}/diagnosis-tree")
    @PreAuthorize("hasAuthority('analysis:diagnosis:query') "
            + "or hasAuthority('chain:node:query')")
    public Result<DiagnosisTreeNodeVO> getDiagnosisTree(
            @PathVariable Long chainId) {
        return Result.success(chainDiagnosisTreeService.getDiagnosisTree(chainId));
    }
}
