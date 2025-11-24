package com.rookies4.finalProject.exception;

import lombok.Getter;

@Getter
public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityName, Object identifier) {
        super(ErrorCode.valueOf(entityName.toUpperCase() + "_NOT_FOUND"),
                String.format("%s를 찾을 수 없습니다. ID: %s", entityName, identifier));
    }
}