package git.ggwzz.matching.embedding;

import git.ggwzz.matching.entity.IndustryTermAlias;
import git.ggwzz.matching.mapper.IndustryTermAliasMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据库优先的领域词扩展服务。
 */
@Service
public class DomainTermExpansionService implements DomainTermExpander {

    private final IndustryTermAliasMapper aliasMapper;
    private final LocalDomainTermExpander fallbackExpander;

    @Autowired
    public DomainTermExpansionService(IndustryTermAliasMapper aliasMapper) {
        this(aliasMapper, new LocalDomainTermExpander());
    }

    public DomainTermExpansionService(IndustryTermAliasMapper aliasMapper,
                                      LocalDomainTermExpander fallbackExpander) {
        this.aliasMapper = aliasMapper;
        this.fallbackExpander = fallbackExpander;
    }

    @Override
    public String expand(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Set<String> expandedTerms = new LinkedHashSet<>();
        addSplitTerms(expandedTerms, fallbackExpander.expand(text));
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (IndustryTermAlias alias : aliasMapper.selectEnabledAliases()) {
            if (alias.getSourceTerm() == null || alias.getSourceTerm().isBlank()) {
                continue;
            }
            String sourceTerm = alias.getSourceTerm().toLowerCase(Locale.ROOT);
            if (normalizedText.contains(sourceTerm)) {
                addSplitTerms(expandedTerms, alias.getExpandedTerms());
            }
        }
        return String.join("，", expandedTerms);
    }

    private void addSplitTerms(Set<String> terms, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        List.of(value.split("[,，;；、\\s]+")).stream()
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .forEach(terms::add);
    }
}
