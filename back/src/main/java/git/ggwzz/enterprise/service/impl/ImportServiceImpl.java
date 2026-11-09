package git.ggwzz.enterprise.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.ImportRecordVO;
import git.ggwzz.enterprise.dto.ImportResultVO;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.entity.ImportRecord;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.enterprise.mapper.ImportRecordMapper;
import git.ggwzz.enterprise.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业导入服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final EnterpriseMapper enterpriseMapper;
    private final ImportRecordMapper importRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importEnterprises(MultipartFile file, String operator) {
        if (file == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件名不能为空");
        }

        List<ImportResultVO.FailureItem> failures = new ArrayList<>();
        int successCount = 0;
        int totalCount = 0;

        try {
            // 按文件扩展名路由到对应解析器，统一转换为内部行模型后再执行相同业务校验。
            List<EnterpriseRow> rows;
            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                rows = parseExcel(file);
            } else if (fileName.endsWith(".csv")) {
                rows = parseCsv(file);
            } else {
                throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 .xlsx/.xls/.csv 格式");
            }

            totalCount = rows.size();

            // 每行独立校验并累计失败明细，使格式正确的数据在同一批次中仍可成功导入。
            for (int i = 0; i < rows.size(); i++) {
                EnterpriseRow row = rows.get(i);
                int rowNum = i + 2; // Excel行号从2开始（第1行是表头）

                try {
                    // 校验必填字段
                    if (StringUtils.isBlank(row.enterpriseName)) {
                        ImportResultVO.FailureItem item = new ImportResultVO.FailureItem();
                        item.setRowNumber(rowNum);
                        item.setEnterpriseName("");
                        item.setReason("企业名称不能为空");
                        failures.add(item);
                        continue;
                    }

                    // 唯一性校验
                    if (StringUtils.isNotBlank(row.unifiedCode)) {
                        LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(Enterprise::getUnifiedCode, row.unifiedCode);
                        if (enterpriseMapper.selectCount(wrapper) > 0) {
                            ImportResultVO.FailureItem item = new ImportResultVO.FailureItem();
                            item.setRowNumber(rowNum);
                            item.setEnterpriseName(row.enterpriseName);
                            item.setReason("统一社会信用代码已存在");
                            failures.add(item);
                            continue;
                        }
                    }

                    // 创建企业
                    Enterprise enterprise = new Enterprise();
                    enterprise.setEnterpriseName(row.enterpriseName);
                    enterprise.setUnifiedCode(row.unifiedCode);
                    enterprise.setLegalPerson(row.legalPerson);
                    enterprise.setRegion(row.region);
                    enterprise.setIndustry(row.industry);
                    enterprise.setAddress(row.address);
                    enterprise.setRegisteredCapital(row.registeredCapital);
                    enterprise.setBusinessScope(row.businessScope);
                    enterprise.setMainProducts(row.mainProducts);
                    enterprise.setKeywords(row.keywords);
                    enterprise.setStatus("UNREGISTERED");
                    enterpriseMapper.insert(enterprise);
                    successCount++;

                } catch (Exception e) {
                    ImportResultVO.FailureItem item = new ImportResultVO.FailureItem();
                    item.setRowNumber(rowNum);
                    item.setEnterpriseName(row.enterpriseName);
                    item.setReason(e.getMessage());
                    failures.add(item);
                }
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析文件失败", e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件解析失败: " + e.getMessage());
        }

        // 无论全量成功还是部分失败，都保存汇总和失败 JSON，形成可追溯的导入记录。
        ImportRecord record = new ImportRecord();
        record.setFileName(fileName);
        record.setOperator(operator);
        record.setTotalCount(totalCount);
        record.setSuccessCount(successCount);
        record.setFailureCount(failures.size());
        record.setStatus(failures.isEmpty() ? "SUCCESS" : (successCount > 0 ? "PARTIAL_FAILED" : "FAILED"));
        record.setImportedAt(LocalDateTime.now());

        try {
            record.setFailureDetail(objectMapper.writeValueAsString(failures));
        } catch (JsonProcessingException e) {
            log.warn("序列化失败明细失败", e);
        }

        importRecordMapper.insert(record);

        // 返回结果
        ImportResultVO result = new ImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailureCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    @Override
    public PageResult<ImportRecordVO> listImportRecords(String keyword, String status, Integer pageNum, Integer pageSize) {
        Page<ImportRecord> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<ImportRecord> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(ImportRecord::getFileName, kw)
                    .or().like(ImportRecord::getOperator, kw)
            );
        }

        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(ImportRecord::getStatus, status);
        }

        wrapper.orderByDesc(ImportRecord::getImportedAt);

        IPage<ImportRecord> result = importRecordMapper.selectPage(page, wrapper);

        List<ImportRecordVO> voList = result.getRecords().stream().map(this::toVO).toList();

        return new PageResult<>(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ==================== 私有方法 ====================

    private ImportRecordVO toVO(ImportRecord record) {
        ImportRecordVO vo = new ImportRecordVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setImportedAt(record.getImportedAt());
        vo.setOperator(record.getOperator());
        vo.setTotalCount(record.getTotalCount());
        vo.setSuccessCount(record.getSuccessCount());
        vo.setFailureCount(record.getFailureCount());
        vo.setStatus(record.getStatus());

        // 解析失败明细
        if (StringUtils.isNotBlank(record.getFailureDetail())) {
            try {
                List<ImportResultVO.FailureItem> failures = objectMapper.readValue(
                        record.getFailureDetail(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ImportResultVO.FailureItem.class)
                );
                vo.setFailures(failures);
            } catch (JsonProcessingException e) {
                log.warn("解析失败明细JSON失败", e);
                vo.setFailures(List.of());
            }
        } else {
            vo.setFailures(List.of());
        }

        return vo;
    }

    /**
     * 解析 Excel 文件
     */
    private List<EnterpriseRow> parseExcel(MultipartFile file) throws Exception {
        List<EnterpriseRow> rows = new ArrayList<>();

        // 只读取首个工作表并跳过表头，使用 DataFormatter 统一处理文本、数字等单元格类型。
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // 跳过表头
                Row row = sheet.getRow(i);
                if (row == null) continue;

                EnterpriseRow er = new EnterpriseRow();
                er.enterpriseName = getCellStringValue(row.getCell(0));
                er.unifiedCode = getCellStringValue(row.getCell(1));
                er.legalPerson = getCellStringValue(row.getCell(2));
                er.region = getCellStringValue(row.getCell(3));
                er.industry = getCellStringValue(row.getCell(4));
                er.address = getCellStringValue(row.getCell(5));
                er.registeredCapital = getCellStringValue(row.getCell(6));
                er.businessScope = getCellStringValue(row.getCell(7));
                er.mainProducts = getCellStringValue(row.getCell(8));
                er.keywords = getCellStringValue(row.getCell(9));

                rows.add(er);
            }
        }

        return rows;
    }

    /**
     * 解析 CSV 文件
     */
    private List<EnterpriseRow> parseCsv(MultipartFile file) throws Exception {
        List<EnterpriseRow> rows = new ArrayList<>();

        // 使用 UTF-8 逐行读取，并用引号感知的分隔表达式避免拆开带逗号的字段。
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // 跳过表头
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                EnterpriseRow er = new EnterpriseRow();
                er.enterpriseName = cleanCsvField(parts.length > 0 ? parts[0] : "");
                er.unifiedCode = cleanCsvField(parts.length > 1 ? parts[1] : "");
                er.legalPerson = cleanCsvField(parts.length > 2 ? parts[2] : "");
                er.region = cleanCsvField(parts.length > 3 ? parts[3] : "");
                er.industry = cleanCsvField(parts.length > 4 ? parts[4] : "");
                er.address = cleanCsvField(parts.length > 5 ? parts[5] : "");
                er.registeredCapital = cleanCsvField(parts.length > 6 ? parts[6] : "");
                er.businessScope = cleanCsvField(parts.length > 7 ? parts[7] : "");
                er.mainProducts = cleanCsvField(parts.length > 8 ? parts[8] : "");
                er.keywords = cleanCsvField(parts.length > 9 ? parts[9] : "");

                rows.add(er);
            }
        }

        return rows;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;

        // POI 5.x 不再支持 setCellType，使用 DataFormatter 格式化
        DataFormatter dataFormatter = new DataFormatter();
        String value = dataFormatter.formatCellValue(cell);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanCsvField(String field) {
        if (field == null) return null;
        String cleaned = field.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    /**
     * 内部类：Excel/CSV 行数据
     */
    private static class EnterpriseRow {
        String enterpriseName;
        String unifiedCode;
        String legalPerson;
        String region;
        String industry;
        String address;
        String registeredCapital;
        String businessScope;
        String mainProducts;
        String keywords;
    }
}
