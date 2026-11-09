package git.ggwzz.analysis.service;

import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;

/**
 * 产业链诊断树服务。
 *
 * <p>由后端规则实时计算节点健康度，组装虚拟根节点返回，
 * 便于前端 G6 以产业链名作为根展示。</p>
 */
public interface ChainDiagnosisTreeService {

    /**
     * 获取产业链诊断树。
     *
     * @param chainId 产业链 ID
     * @return 诊断树虚拟根节点
     */
    DiagnosisTreeNodeVO getDiagnosisTree(Long chainId);
}
