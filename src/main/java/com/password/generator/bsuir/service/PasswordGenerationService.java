package com.password.generator.bsuir.service;

import com.password.generator.bsuir.config.PasswordCache;
import com.password.generator.bsuir.dto.PasswordGenerationDto;
import com.password.generator.bsuir.model.difficultyenum.Difficulty;
import com.password.generator.bsuir.model.GeneratedPassword;
import com.password.generator.bsuir.repository.PasswordRepository;
import com.password.generator.bsuir.security.domain.model.User;
import com.password.generator.bsuir.security.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class PasswordGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordGenerationService.class);

    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+";

    private static final Random random = new SecureRandom();
    private final PasswordRepository passwordRepository;
    private final UserService userService;
    private final PasswordCache passwordCache;

    @Autowired
    public PasswordGenerationService(PasswordRepository passwordRepository, UserService userService, PasswordCache passwordCache) {
        this.passwordRepository = passwordRepository;
        this.userService = userService;
        this.passwordCache = passwordCache;
    }

    public String generatePassword(PasswordGenerationDto dto) {
        logger.info("Generating password...");
        String generatedPassword = generatePasswordString(dto);
        logger.info("Password generated successfully.");

        return generatedPassword;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userService.getByUsername(username);
    }

    private String generatePasswordString(PasswordGenerationDto dto) {
        User currentUser = getCurrentUser();
        Long userId = currentUser.getId();

        if (passwordCache.contains(userId)) {
            logger.info("Retrieving password for user '{}' from cache.", currentUser.getUsername());
            return passwordCache.get(userId);
        } else {
            logger.info("Retrieving password for user '{}' from database.", currentUser.getUsername());
            StringBuilder password = new StringBuilder();
            String charPool = getCharacterPool(dto.getDifficulty());

            for (int i = 0; i < dto.getLength(); i++) {
                int randomIndex = random.nextInt(charPool.length());
                password.append(charPool.charAt(randomIndex));
            }

            String generatedPassword = password.toString();
            Difficulty difficulty = dto.getDifficulty();

            passwordRepository.save(new GeneratedPassword(generatedPassword, difficulty, currentUser));
            passwordCache.put(userId, generatedPassword);

            return generatedPassword;
        }
    }

    private String getCharacterPool(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> LOWERCASE_CHARS;
            case NORMAL -> LOWERCASE_CHARS + UPPERCASE_CHARS + DIGITS;
            case HARD -> LOWERCASE_CHARS + UPPERCASE_CHARS + DIGITS + SYMBOLS;
        };
    }

    public Optional<GeneratedPassword> getPasswordById(Long id) {
        if (passwordCache.contains(id)) {
            logger.info("Retrieved password for id '{}' from cache.", id);
            String cachedPassword = passwordCache.get(id);
            return Optional.of(new GeneratedPassword(id, cachedPassword));
        } else {
            logger.info("Retrieved password for id '{}' from database.", id);
            Optional<GeneratedPassword> generatedPassword = passwordRepository.findById(id);
            generatedPassword.ifPresent(password -> passwordCache.put(id, password.getPassword()));
            return generatedPassword;
        }
    }

    public List<GeneratedPassword> getPasswordsByDifficulty(Difficulty difficulty) {
        List<GeneratedPassword> generatedPasswords = passwordRepository.findByDifficulty(difficulty);
        generatedPasswords.forEach(password -> {
            passwordCache.put(password.getId(), password.getPassword());
            logger.info("Retrieved password for id '{}' with difficulty '{}' from database.", password.getId(), difficulty);
        });
        return generatedPasswords;
    }

    public List<GeneratedPassword> getAllGeneratedPasswords() {
        List<GeneratedPassword> generatedPasswords = passwordRepository.findAll();
        generatedPasswords.forEach(password -> {
            passwordCache.put(password.getId(), password.getPassword());
            logger.info("Retrieved password for id '{}' from database.", password.getId());
        });
        return generatedPasswords;
    }

    public void deleteGeneratedPasswordById(Long generatedPasswordId) {
        passwordRepository.deleteById(generatedPasswordId);
        logger.info("Deleted password for id '{}' from database.", generatedPasswordId);
    }


    public List<GeneratedPassword> getAllGeneratedPasswordsForUser(String username) {
        return passwordRepository.findAllByUserUsername(username);
    }
}
