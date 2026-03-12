package com.danburn.core.api;

import com.danburn.common.response.ApiResponse;
import com.danburn.core.domain.user.User;
import com.danburn.core.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(userService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> create(@RequestBody CreateUserRequest request) {
        User user = userService.create(request.email(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(user));
    }

    public record CreateUserRequest(String email, String name) {}
}
