package git.ggwzz.matching.embedding;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingProfileTextBuilderTest {

    private final EmbeddingProfileTextBuilder builder = new EmbeddingProfileTextBuilder();

    @Test
    void buildEnterpriseProfileShouldUseFullEnterpriseProfile() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setName("中际旭创股份有限公司");
        enterprise.setBusinessScope("光通信器件、光模块及相关设备研发、制造、销售");
        enterprise.setMainProducts("高速光模块,数据中心光模块,硅光模块,光电转换芯片");
        enterprise.setTags("光通信,算力基础设施");

        String profile = builder.buildEnterpriseProfile(enterprise);

        assertTrue(profile.contains("企业名称：中际旭创股份有限公司"));
        assertTrue(profile.contains("经营范围：光通信器件、光模块及相关设备研发、制造、销售"));
        assertTrue(profile.contains("主营产品与技术关键词：高速光模块，数据中心光模块，硅光模块，光电转换芯片"));
        assertTrue(profile.contains("企业标签：光通信，算力基础设施"));
    }

    @Test
    void buildEnterpriseProfileShouldAddRetrievalInstructionAndDomainExpansion() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setName("中际旭创股份有限公司");

        String profile = builder.buildEnterpriseProfile(enterprise);

        assertTrue(profile.contains("产业链挂链语义检索"));
        assertTrue(profile.contains("领域扩展词"));
        assertTrue(profile.contains("光模块"));
        assertTrue(profile.contains("光通信"));
    }

    @Test
    void buildEnterpriseProfileShouldExpandAppleToPhoneDomain() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setName("苹果公司");

        String profile = builder.buildEnterpriseProfile(enterprise);

        assertTrue(profile.contains("智能手机"));
        assertTrue(profile.contains("消费电子"));
    }

    @Test
    void buildNodeProfileShouldUseDtoContractFieldsOnly() {
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(1L);
        node.setChainId(2L);
        node.setNodeName("光模块");
        node.setNodePath("光通信产业链 / 中游 / 光模块");
        node.setKeywords("高速光模块,光通信模块,光器件,光电转换,数据中心互联");
        node.setLevel(3);

        String profile = builder.buildNodeProfile(node);

        assertTrue(profile.contains("节点路径：光通信产业链 / 中游 / 光模块"));
        assertTrue(profile.contains("节点名称：光模块"));
        assertTrue(profile.contains("节点关键词：高速光模块，光通信模块，光器件，光电转换，数据中心互联"));
        assertTrue(profile.contains("节点层级：3"));
    }

    @Test
    void buildEnterpriseProfileShouldNormalizeNoisyFieldsAndAddWeightHints() {
        EmbeddingProfileTextBuilder cleanBuilder = new EmbeddingProfileTextBuilder(text -> "光通信，光模块");
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setName("  中际旭创股份有限公司  ");
        enterprise.setBusinessScope(" 光通信器件研发\r\n\t制造；  光模块销售； ".repeat(20));
        enterprise.setMainProducts("高速光模块, 高速光模块；光器件、光电转换芯片");
        enterprise.setTags("光通信  算力基础设施");

        String profile = cleanBuilder.buildEnterpriseProfile(enterprise);

        assertTrue(profile.contains("字段权重提示：主营产品和核心技术优先，经营范围作为辅助语义。"));
        assertTrue(profile.contains("企业名称：中际旭创股份有限公司"));
        assertTrue(profile.contains("主营产品与技术关键词：高速光模块，光器件，光电转换芯片"));
        assertTrue(profile.contains("企业标签：光通信，算力基础设施"));
        assertTrue(profile.length() < 900);
    }
}
