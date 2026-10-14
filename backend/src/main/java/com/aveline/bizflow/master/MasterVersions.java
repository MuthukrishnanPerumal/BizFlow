package com.aveline.bizflow.master;

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Objects;

public final class MasterVersions {

    private MasterVersions() {
    }

    public static void check(Long current, Long requested) {
        if (!Objects.equals(current, requested)) {
            throw new OptimisticLockingFailureException("The record was updated by someone else");
        }
    }
}
