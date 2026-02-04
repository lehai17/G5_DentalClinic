package com.dentalclinic.service.common;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthUserService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public OAuthUserService(UserRepository userRepository,
                            CustomerProfileRepository customerProfileRepository) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    @Transactional
    public void upsertGoogleUser(OAuth2User oauthUser) {
        String email = oauthUser.getAttribute("email");
        String fullName = oauthUser.getAttribute("name");

        // 1) Upsert USER
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPassword("GOOGLE");          // pass ngẫu nhiên vì kh cần pass
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);

            // GG kh có DOB/Gender
            u.setGender(Gender.OTHER);        // trả other hoặc null
            u.setDateOfBirth(null);
            return userRepository.save(u);
        });

        // 2) Tạo luôn profile mới
        CustomerProfile profile = customerProfileRepository
                .findByUser_Id(user.getId())
                .orElseGet(() -> {
                    CustomerProfile p = new CustomerProfile();
                    p.setUser(user);
                    return p;
                });

        // lấy fullName
        profile.setFullName(fullName);
        customerProfileRepository.save(profile);
    }
}
