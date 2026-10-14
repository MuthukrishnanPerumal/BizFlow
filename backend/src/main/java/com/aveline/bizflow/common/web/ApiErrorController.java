package com.aveline.bizflow.common.web;

import com.aveline.bizflow.common.api.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusAttribute instanceof Integer value) {
            status = value;
        }
        String message = status >= 500
                ? "An unexpected error occurred"
                : status == HttpStatus.NOT_FOUND.value()
                ? "The requested resource was not found"
                : "Request could not be completed";
        return ResponseEntity.status(status).body(ApiResponse.fail(message, List.of()));
    }
}
