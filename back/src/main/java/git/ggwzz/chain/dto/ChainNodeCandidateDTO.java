package git.ggwzz.chain.dto;

import lombok.Data;

@Data
public class ChainNodeCandidateDTO {

    private Long nodeId;

    private Long chainId;

    private String nodeName;

    private String nodePath;

    private String keywords;

    private Integer level;
}
