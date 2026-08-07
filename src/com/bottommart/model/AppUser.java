package com.bottommart.model;

import java.time.LocalDateTime;

public class AppUser {

    private Long userId;
    private String username;
    private String password;
    private String name;
    private String role; // ADMIN / STAFF
    private LocalDateTime createdAt;

    public AppUser() {
    }

    public AppUser(Long userId, String username, String password, String name, String role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AppUser{userId=" + userId + ", username='" + username + "', name='" + name
                + "', role='" + role + "', createdAt=" + createdAt + "}";
    }
}
