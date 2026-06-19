package com.asa.asaunify.controllers;



import com.asa.asaunify.dtos.CreateMemoRequest;
import com.asa.asaunify.dtos.MemoApprovalActionDto;
import com.asa.asaunify.dtos.MemoDto;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.services.MemoService;
import com.asa.asaunify.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/memos")
@RequiredArgsConstructor
@Tag(name = "Memo")
public class MemoController {

    private final MemoService memoService;
    private final UserService userService;

    // ─── Create memo ──────────────────────────────────────────

    // POST /api/memos
    @PostMapping
    public ResponseEntity<MemoDto> createMemo(
            @Valid @RequestBody CreateMemoRequest dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(memoService.createMemo(
                        dto, currentUser, httpRequest
                ));
    }

    // ─── Approve or Reject memo ───────────────────────────────

    // POST /api/memos/{id}/action
    @PostMapping("/{id}/action")
    public ResponseEntity<MemoDto> processAction(
            @PathVariable UUID id,
            @Valid @RequestBody MemoApprovalActionDto dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                memoService.processAction(
                        id, dto, currentUser, httpRequest
                )
        );
    }

    // ─── Get my memos ─────────────────────────────────────────

    // GET /api/memos/my
    @GetMapping("/my")
    public ResponseEntity<List<MemoDto>> getMyMemos(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                memoService.getMyMemos(currentUser)
        );
    }

    // ─── Get pending memos for current user's role ────────────

    // GET /api/memos/pending
    @GetMapping("/pending")
    public ResponseEntity<List<MemoDto>> getPendingForRole(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                memoService.getPendingMemosForRole(currentUser)
        );
    }

    // ─── Get all memos — Admin and Auditor ────────────────────

    // GET /api/memos
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<MemoDto>> getAllMemos() {
        return ResponseEntity.ok(memoService.getAllMemos());
    }

    // ─── Get single memo ──────────────────────────────────────

    // GET /api/memos/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MemoDto> getMemoById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(memoService.getMemoById(id));
    }
}