package com.shinecraft.server.user;

import com.shinecraft.server.common.ApiException;
import com.shinecraft.server.common.LicensePlateNormalizer;
import com.shinecraft.server.common.PhoneNormalizer;
import com.shinecraft.server.loyalty.LoyaltyAccount;
import com.shinecraft.server.loyalty.LoyaltyAccountRepository;
import com.shinecraft.server.loyalty.MembershipTierRepository;
import com.shinecraft.server.security.JwtService;
import com.shinecraft.server.vehicle.Vehicle;
import com.shinecraft.server.vehicle.VehicleRepository;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final MembershipTierRepository membershipTierRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            MembershipTierRepository membershipTierRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.membershipTierRepository = membershipTierRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserDtos.AuthResponse register(UserDtos.RegisterRequest request) {
        String phone = PhoneNormalizer.normalize(request.phone());
        String licensePlate = LicensePlateNormalizer.normalize(request.licensePlate());
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already exists");
        }
        if (vehicleRepository.existsByLicensePlateAndIsActiveTrue(licensePlate)) {
            throw new ApiException(HttpStatus.CONFLICT, "License plate already exists");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ROLE_CUSTOMER);
        user = userRepository.save(user);

        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(user);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setOwnershipStartAt(LocalDateTime.now());
        vehicleRepository.save(vehicle);

        LoyaltyAccount account = new LoyaltyAccount();
        account.setCustomer(user);
        account.setMembershipTier(membershipTierRepository
                .findFirstByIsActiveTrueAndMinPointsLessThanEqualOrderByMinPointsDesc(0)
                .orElse(null));
        loyaltyAccountRepository.save(account);

        return authResponse(user);
    }

    public UserDtos.AuthResponse login(UserDtos.LoginRequest request) {
        User user = userRepository
                .findByPhone(PhoneNormalizer.normalize(request.phone()))
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid phone number or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid phone number or password");
        }

        return authResponse(user);
    }

    public User currentUser() {
        String phone = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository
                .findByPhone(phone)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }

    private UserDtos.AuthResponse authResponse(User user) {
        return new UserDtos.AuthResponse(jwtService.generateToken(user), UserDtos.UserResponse.from(user));
    }

}
