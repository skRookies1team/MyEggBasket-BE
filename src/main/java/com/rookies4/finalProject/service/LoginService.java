package com.rookies4.finalProject.service;

import com.rookies4.finalProject.dto.UserDTO;

public interface LoginService {
    UserDTO.LoginResponse login(UserDTO.LoginRequest request);
}

