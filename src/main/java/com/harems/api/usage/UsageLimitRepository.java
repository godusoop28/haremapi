package com.harems.api.usage;

import com.harems.api.character.Character;
import com.harems.api.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageLimitRepository extends JpaRepository<UsageLimit, Long> {

    Optional<UsageLimit> findByUserAndCharacter(User user, Character character);
}
