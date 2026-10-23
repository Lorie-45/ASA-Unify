package com.asa.asaunify.repos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the new lock queries against the real database; mocks cannot tell
 * whether the native advisory-lock SQL or the PESSIMISTIC_WRITE query is valid.
 * Transactional, so everything is rolled back and the locks are released.
 */
@SpringBootTest
@Transactional
class CaseIdLockQueriesTest {

    @Autowired
    private RequestRepo requestRepo;

    @Autowired
    private MemoRepo memoRepo;

    @Test
    void advisoryLockQueriesExecute() {
        assertEquals(1, requestRepo.lockCaseIdGeneration());
        assertEquals(1, memoRepo.lockCaseIdGeneration());
    }

    @Test
    void rowLockQueryExecutesAndMissesCleanly() {
        assertTrue(requestRepo.findByIdForUpdate(UUID.randomUUID()).isEmpty());
    }
}
