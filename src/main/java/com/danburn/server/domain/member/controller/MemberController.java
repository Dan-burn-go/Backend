package com.danburn.server.domain.member.controller;

import com.danburn.server.domain.member.domain.Member;
import com.danburn.server.domain.member.dto.request.CreateMemberRequest;
import com.danburn.server.domain.member.service.MemberService;
import com.danburn.server.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Member>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(memberService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Member>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Member>> create(@RequestBody CreateMemberRequest request) {
        Member member = memberService.create(request.email(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(member));
    }
}
