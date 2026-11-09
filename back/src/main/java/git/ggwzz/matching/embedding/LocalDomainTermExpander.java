package git.ggwzz.matching.embedding;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地域词扩展。
 *
 * <p>小型 embedding 模型对企业简称和产业实体的知识不稳定，这里把高频产业实体
 * 扩展成产品/技术词，减少“公司名 vs 节点名”短文本误判。</p>
 */
final class LocalDomainTermExpander implements DomainTermExpander {

    private static final Map<String, String[]> TERM_MAP = new LinkedHashMap<>();

    static {
        TERM_MAP.put("中际旭创", terms("光模块", "高速光模块", "光通信", "光器件", "光收发模块", "数据中心互联"));
        TERM_MAP.put("旭创", terms("光模块", "高速光模块", "光通信", "光器件", "光收发模块"));
        TERM_MAP.put("苹果", terms("手机", "智能手机", "iPhone", "消费电子", "移动终端"));
        TERM_MAP.put("apple", terms("手机", "智能手机", "iPhone", "消费电子", "移动终端"));
        TERM_MAP.put("宁德时代", terms("动力电池", "锂电池", "电芯", "电池模组", "储能电池"));
        TERM_MAP.put("catl", terms("动力电池", "锂电池", "电芯", "电池模组", "储能电池"));
        TERM_MAP.put("比亚迪", terms("新能源汽车", "动力电池", "整车制造", "电驱系统"));
        TERM_MAP.put("阳光电源", terms("光伏逆变器", "储能系统", "新能源电力设备"));
        TERM_MAP.put("隆基", terms("光伏组件", "硅片", "太阳能电池", "光伏制造"));
        TERM_MAP.put("中芯国际", terms("晶圆制造", "集成电路", "半导体代工"));
        TERM_MAP.put("立讯精密", terms("消费电子", "连接器", "精密制造"));
        TERM_MAP.put("光模块", terms("高速光模块", "光通信模块", "光器件", "光电转换", "数据中心互联"));
        TERM_MAP.put("手机", terms("智能手机", "移动终端", "消费电子"));
        TERM_MAP.put("动力电池", terms("锂电池", "电芯", "模组", "储能电池"));
    }

    @Override
    public String expand(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> expandedTerms = new LinkedHashSet<>();
        for (Map.Entry<String, String[]> entry : TERM_MAP.entrySet()) {
            if (normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                for (String term : entry.getValue()) {
                    expandedTerms.add(term);
                }
            }
        }
        return String.join("，", expandedTerms);
    }

    private static String[] terms(String... values) {
        return values;
    }
}
