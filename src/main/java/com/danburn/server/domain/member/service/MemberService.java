package com.danburn.server.domain.member.service;

import com.danburn.server.domain.member.domain.Member;
import com.danburn.server.domain.member.repository.MemberRepository;
import com.danburn.server.global.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new GlobalException(404, "Member not found: " + id));
    }

    @Transactional
    public Member create(String email, String name) {
        return memberRepository.save(Member.builder()
                .email(email)
                .name(name)
                .build());
    }
}
