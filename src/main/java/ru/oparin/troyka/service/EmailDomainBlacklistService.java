package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Сервис для проверки заблокированных email доменов.
 * Используется для блокировки регистраций с временных email сервисов.
 */
@Service
@Slf4j
public class EmailDomainBlacklistService {

    /**
     * Список заблокированных email доменов.
     * Домены временных email сервисов, которые используются для спама.
     */
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "trashlify.com",
            "10minutemail.com",
            "guerrillamail.com",
            "tempmail.com",
            "mailinator.com",
            "temp-mail.org",
            "mohmal.com",
            "yopmail.com",
            "getnada.com",
            "maildrop.cc",
            "mintemail.com",
            "sharklasers.com",
            "grr.la",
            "guerrillamailblock.com",
            "pokemail.net",
            "spam4.me",
            "bccto.me",
            "chammy.info",
            "dispostable.com",
            "meltmail.com",
            "melt.li",
            "mintemail.com",
            "mohmal.com",
            "mytrashmail.com",
            "nada.email",
            "nada.ltd",
            "nada.pro",
            "nada1.ltd",
            "nadaemail.com",
            "nadaemail.net",
            "nadaemail.org",
            "nadaemail.pro",
            "nadaemail.xyz",
            "nadaemails.com",
            "nadaemails.net",
            "nadaemails.org",
            "nadaemails.pro",
            "nadaemails.xyz",
            "nadamail.com",
            "nadamail.net",
            "nadamail.org",
            "nadamail.pro",
            "nadamail.xyz",
            "nadamails.com",
            "nadamails.net",
            "nadamails.org",
            "nadamails.pro",
            "nadamails.xyz",
            "nowmymail.com",
            "nowmymail.net",
            "nowmymail.org",
            "nowmymail.pro",
            "nowmymail.xyz",
            "nowmymails.com",
            "nowmymails.net",
            "nowmymails.org",
            "nowmymails.pro",
            "nowmymails.xyz",
            "spamgourmet.com",
            "tempmailo.com",
            "throwaway.email",
            "trashmail.com",
            "trashmail.net",
            "trashmail.org",
            "trashmail.pro",
            "trashmail.xyz",
            "trashmails.com",
            "trashmails.net",
            "trashmails.org",
            "trashmails.pro",
            "trashmails.xyz",
            "trashymail.com",
            "trashymail.net",
            "trashymail.org",
            "trashymail.pro",
            "trashymail.xyz",
            "trashymails.com",
            "trashymails.net",
            "trashymails.org",
            "trashymails.pro",
            "trashymails.xyz"
    );

    /**
     * Проверить, заблокирован ли домен email адреса.
     *
     * @param email email адрес для проверки
     * @return true если домен заблокирован, false если разрешен
     */
    public boolean isDomainBlocked(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        int atIndex = normalizedEmail.indexOf('@');
        
        if (atIndex == -1 || atIndex == normalizedEmail.length() - 1) {
            return false;
        }

        String domain = normalizedEmail.substring(atIndex + 1);
        
        boolean blocked = BLOCKED_DOMAINS.contains(domain);
        
        if (blocked) {
            log.warn("Попытка регистрации с заблокированным email доменом: {}", domain);
        }
        
        return blocked;
    }
}
