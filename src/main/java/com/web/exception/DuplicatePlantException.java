package com.web.exception;

import com.web.entity.Plant;
import lombok.Getter;

@Getter
public class DuplicatePlantException extends MessageException {
    
    private Plant duplicatePlant;
    
    public DuplicatePlantException(Plant duplicatePlant) {
        super("Cây dược liệu này đã tồn tại trong hệ thống");
        this.duplicatePlant = duplicatePlant;
        this.setDefaultMessage(
            String.format(
                "Cây dược liệu '%s' đã tồn tại trong hệ thống của chúng tôi. " +
                "Bạn có chắc chắn muốn gửi lên không?",
                duplicatePlant.getName()
            )
        );
    }
}

