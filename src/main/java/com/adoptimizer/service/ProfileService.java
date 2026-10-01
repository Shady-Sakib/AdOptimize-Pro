package com.adoptimizer.service;

import com.adoptimizer.dto.request.PasswordChangeRequest;
import com.adoptimizer.dto.request.ProfileUpdateRequest;
import com.adoptimizer.dto.response.ProfileResponse;
import com.adoptimizer.exception.FieldValidationException;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.User;
import com.adoptimizer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User getUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }

    public ProfileResponse profile(long userId) {
        return ProfileResponse.from(getUser(userId));
    }

    @Transactional
    public ProfileResponse update(long userId, ProfileUpdateRequest request) {
        getUser(userId);
        String email = UserRepository.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailForOtherUser(email, userId)) {
            throw new FieldValidationException(HttpStatus.CONFLICT, "email", "Another account already uses this email.");
        }
        String company = request.getCompany() == null || request.getCompany().isBlank() ? null : request.getCompany().trim();
        try {
            userRepository.updateProfile(userId, request.getName().trim().replaceAll("\\s+", " "), email, company);
        } catch (DuplicateKeyException e) {
            throw new FieldValidationException(HttpStatus.CONFLICT, "email", "Another account already uses this email.");
        }
        return profile(userId);
    }

    @Transactional
    public void changePassword(long userId, PasswordChangeRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("currentPassword", "Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("newPassword", "Choose a password different from your current one.");
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));
    }
}
