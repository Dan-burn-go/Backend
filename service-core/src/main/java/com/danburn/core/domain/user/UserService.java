package com.danburn.core.domain.user;

import com.danburn.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new GlobalException(404, "User not found: " + id));
    }

    @Transactional
    public User create(String email, String name) {
        return userRepository.save(User.builder()
                .email(email)
                .name(name)
                .build());
    }
}
