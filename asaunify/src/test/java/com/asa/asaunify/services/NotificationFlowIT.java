package com.asa.asaunify.services;

import com.asa.asaunify.dtos.ApprovalActionDto;
import com.asa.asaunify.dtos.CreateMemoRequest;
import com.asa.asaunify.dtos.CreateRequestDto;
import com.asa.asaunify.dtos.RequestResponseDto;
import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.DepartmentRepo;
import com.asa.asaunify.repos.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that notifications really get stored for the right people.
 * Not transactional on purpose: the bugs this guards against only show up when
 * the calling transaction commits and the async sender runs on its own thread.
 * Runs against whatever database the app points at, using throw-away data with
 * unique names.
 */
@SpringBootTest
class NotificationFlowIT {

    @Autowired RequestService requestService;
    @Autowired MemoService memoService;
    @Autowired NotificationService notificationService;
    @Autowired UserRepo users;
    @Autowired DepartmentRepo departments;
    @Autowired JdbcTemplate jdbc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private Department dept(String name) {
        return departments.save(Department.builder().name(name + "-" + tag).build());
    }

    private User user(String name, Role role, Department d) {
        return users.save(User.builder().fullName(name).email(name + "-" + tag + "@t.io")
                .passwordHash("x").role(role).department(d).isActive(true).build());
    }

    private long count(User u, String like) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND title LIKE ?",
                Long.class, u.getId(), like);
    }

    /** Async delivery: poll rather than assume a timing. */
    private boolean eventually(Supplier<Boolean> cond) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (cond.get()) return true;
            Thread.sleep(100);
        }
        return false;
    }

    private ApprovalActionDto approve() {
        ApprovalActionDto dto = new ApprovalActionDto();
        dto.setAction(StageStatus.APPROVED);
        return dto;
    }

    @Test
    void requestWorkflowNotifiesTheRightPeople() throws Exception {
        Department sales = dept("sales"), hr = dept("hr");
        User emp = user("emp", Role.EMPLOYEE, sales);
        User head = user("head", Role.DEPARTMENT_HEAD, sales);
        User otherHead = user("otherhead", Role.DEPARTMENT_HEAD, hr);
        User logistics = user("logi", Role.LOGISTICS, hr);
        User admin = user("adm", Role.ADMIN, null);

        CreateRequestDto dto = new CreateRequestDto();
        dto.setType(RequestType.EQUIPMENT);
        dto.setTitle("Laptop " + tag);
        dto.setDetails("d");
        dto.setExtraFields(Map.of("item_name", "Laptop", "quantity", 1));
        RequestResponseDto draft = requestService.createDraft(dto, emp, null);
        requestService.submitRequest(draft.getId(), emp, null);

        assertTrue(eventually(() -> count(head, "New Request Assigned") == 1),
                "own department head must be told a request needs them");
        Thread.sleep(500);
        assertEquals(0, count(otherHead, "New Request Assigned"),
                "another department's head must not be told");

        // The bell / notifications page reads these back; the links to the
        // request need a session, which used to be missing.
        assertEquals(1, notificationService.getAllForUser(head).size());
        assertEquals(1, notificationService.getSummary(head).getUnreadCount());
        assertEquals(draft.getCaseId(), notificationService.getAllForUser(head)
                .get(0).getRequestReferenceNumber());

        requestService.processAction(draft.getId(), approve(), head, null);
        assertTrue(eventually(() -> count(logistics, "Action Required") == 1),
                "logistics must be told once the request reaches them");

        requestService.cancelRequest(draft.getId(), "no longer needed", admin, null);
        assertTrue(eventually(() -> count(emp, "Request Cancelled") == 1),
                "initiator must be told about the cancellation");
        assertTrue(eventually(() -> count(head, "Request Cancelled") == 1),
                "people who already acted must be told about the cancellation");
    }

    @Test
    void memoNotifiesOnlyTheNamedApprover() throws Exception {
        Department d = dept("ops");
        User author = user("author", Role.EMPLOYEE, d);
        User alice = user("alice", Role.RM, d);
        User bob = user("bob", Role.RM, d);

        CreateMemoRequest req = new CreateMemoRequest();
        req.setTitle("Memo " + tag);
        req.setContent("c");
        req.setApproverIds(List.of(alice.getId()));
        memoService.createMemo(req, author, null);

        assertTrue(eventually(() -> count(alice, "Memo Requires Your Approval") == 1),
                "the named approver must be notified");
        Thread.sleep(500);
        assertEquals(0, count(bob, "Memo Requires Your Approval"),
                "an unnamed user with the same role must not be notified");
    }

    @Test
    void loanNotifiesAllThreeApproversAndRejectionReachesTheInitiator() throws Exception {
        Department d = dept("loans");
        User officer = user("officer", Role.LOAN_OFFICER, d);
        User msme = user("msme", Role.MSME_OFFICER, d);
        User rm = user("rm", Role.RM, d);
        User credit = user("credit", Role.CREDIT_OFFICER, d);

        CreateRequestDto dto = new CreateRequestDto();
        dto.setType(RequestType.LOAN);
        dto.setTitle("Loan " + tag);
        dto.setDetails("d");
        dto.setExtraFields(Map.of("amount", 3000000));
        RequestResponseDto draft = requestService.createDraft(dto, officer, null);
        requestService.submitRequest(draft.getId(), officer, null);

        for (User approver : List.of(msme, rm, credit)) {
            assertTrue(eventually(() -> count(approver, "New Request Assigned") == 1),
                    approver.getRole() + " must be told about the loan");
        }

        ApprovalActionDto reject = new ApprovalActionDto();
        reject.setAction(StageStatus.REJECTED);
        reject.setComment("not viable");
        requestService.processAction(draft.getId(), reject, rm, null);
        requestService.processAction(draft.getId(), approve(), msme, null);
        requestService.processAction(draft.getId(), approve(), credit, null);

        assertTrue(eventually(() -> count(officer, "Request Rejected") == 1),
                "the initiator must be told the loan was rejected");
    }
}
