package com.aveline.bizflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class DataResetDtos {

    private DataResetDtos() {
    }

    public record ResetTableView(
            String key,
            String label,
            String group,
            String table,
            long rowCount,
            List<String> requires) {
    }

    public record DataResetRequest(
            @NotEmpty @Size(max = 64) List<@NotBlank @Size(max = 64) String> tables) {
    }

    public record ResetDeletion(String key, String label, long deletedRows) {
    }

    public record DataResetResult(long deletedRows, List<ResetDeletion> tables) {
    }
}
