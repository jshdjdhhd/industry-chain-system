package git.ggwzz.matching.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * AI 聊天上下文校验器。
 */
public class AiChatContextValidator implements ConstraintValidator<ValidAiChatContext, AiChatRequest> {

    @Override
    public boolean isValid(AiChatRequest request, ConstraintValidatorContext context) {
        return true;
    }
}
