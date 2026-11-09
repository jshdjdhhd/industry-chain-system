package git.ggwzz.matching.embedding;

import git.ggwzz.matching.entity.IndustryTermAlias;
import git.ggwzz.matching.mapper.IndustryTermAliasMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainTermExpansionServiceTest {

    @Test
    void expand_shouldUseEnabledDatabaseAliasesBeforeFallbackTerms() {
        IndustryTermAliasMapper mapper = mock(IndustryTermAliasMapper.class);
        IndustryTermAlias alias = new IndustryTermAlias();
        alias.setSourceTerm("小米");
        alias.setExpandedTerms("智能手机,消费电子,IoT终端");
        when(mapper.selectEnabledAliases()).thenReturn(List.of(alias));

        DomainTermExpansionService service = new DomainTermExpansionService(mapper, new LocalDomainTermExpander());

        String expanded = service.expand("小米集团智能制造业务");

        assertTrue(expanded.contains("智能手机"));
        assertTrue(expanded.contains("消费电子"));
        assertTrue(expanded.contains("IoT终端"));
    }

    @Test
    void expand_shouldKeepLocalFallbackWhenDatabaseHasNoMatchingAlias() {
        IndustryTermAliasMapper mapper = mock(IndustryTermAliasMapper.class);
        when(mapper.selectEnabledAliases()).thenReturn(List.of());

        DomainTermExpansionService service = new DomainTermExpansionService(mapper, new LocalDomainTermExpander());

        String expanded = service.expand("中际旭创股份有限公司");

        assertTrue(expanded.contains("光模块"));
        assertTrue(expanded.contains("光通信"));
    }
}
