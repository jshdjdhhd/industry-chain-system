package git.ggwzz.enterprise.service;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.enterprise.dto.ImportRecordVO;
import git.ggwzz.enterprise.dto.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业导入服务
 */
public interface ImportService {

    /**
     * 批量导入企业（Excel/CSV）
     *
     * @param file     上传文件
     * @param operator 操作人
     * @return 导入结果
     */
    ImportResultVO importEnterprises(MultipartFile file, String operator);

    /**
     * 查询导入记录（分页）
     *
     * @param keyword 关键词（文件名/操作人）
     * @param status  状态筛选
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<ImportRecordVO> listImportRecords(String keyword, String status, Integer pageNum, Integer pageSize);
}
