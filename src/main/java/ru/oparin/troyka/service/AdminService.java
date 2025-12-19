package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple8;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.admin.AdminPaymentDTO;
import ru.oparin.troyka.model.dto.admin.AdminStatsDTO;
import ru.oparin.troyka.model.dto.admin.AdminUserDTO;
import ru.oparin.troyka.model.dto.admin.UserStatisticsDTO;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.entity.UserPoints;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.repository.UserPointsRepository;
import ru.oparin.troyka.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.oparin.troyka.config.DatabaseConfig.withRetry;

/**
 * Сервис для работы с админ-панелью.
 * Предоставляет методы для получения статистики, управления пользователями и платежами.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String UNKNOWN_USER = "Неизвестный";
    private static final String DEFAULT_RESOLUTION = "1K";
    private static final String RESOLUTION_1K = "1K";
    private static final String RESOLUTION_2K = "2K";
    private static final String RESOLUTION_4K = "4K";
    private static final int DEFAULT_NUM_IMAGES = 1;
    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_IN_MONTH = 30;
    private static final int YEARS_IN_YEAR = 1;

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserPointsRepository userPointsRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final GenerationProperties generationProperties;

    /**
     * Получить все успешно оплаченные платежи для админ-панели.
     *
     * @return поток платежей, отсортированных по дате создания (новые первыми)
     */
    public Flux<AdminPaymentDTO> getAllPayments() {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .flatMap(this::mapPaymentToDTO)
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    }

    /**
     * Получить всех пользователей для админ-панели.
     *
     * @return поток пользователей, отсортированных по дате создания (новые первыми)
     */
    public Flux<AdminUserDTO> getAllUsers() {
        return userRepository.findAll()
                .flatMap(this::mapUserToDTO)
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    }

    /**
     * Поиск пользователей по фильтру.
     * Ищет по ID, username, email, telegram username, telegram ID.
     *
     * @param query поисковый запрос (может быть null для получения всех пользователей с лимитом)
     * @param limit максимальное количество результатов
     * @return поток найденных пользователей, отсортированных по дате создания (новые первыми)
     */
    public Flux<AdminUserDTO> searchUsers(String query, int limit) {
        Flux<User> usersFlux = buildUserSearchQuery(query, limit);
        
        return usersFlux
                .flatMap(this::mapUserToDTO)
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .take(limit);
    }

    /**
     * Получить статистику для админ-панели.
     *
     * @return статистика по пользователям, платежам и регистрациям
     */
    public Mono<AdminStatsDTO> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.with(LocalTime.MIN);
        LocalDateTime weekStart = now.minusDays(DAYS_IN_WEEK).with(LocalTime.MIN);
        LocalDateTime monthStart = now.minusDays(DAYS_IN_MONTH).with(LocalTime.MIN);
        LocalDateTime yearStart = now.minusYears(YEARS_IN_YEAR).with(LocalTime.MIN);

        Mono<Long> totalUsersMono = withRetry(userRepository.count());
        Mono<Long> totalPaymentsMono = countPaymentsSince(null);
        Mono<Long> todayPaymentsMono = countPaymentsSince(todayStart);
        Mono<BigDecimal> totalRevenueMono = calculateTotalRevenue();
        Mono<BigDecimal> todayRevenueMono = calculateRevenueSince(todayStart);
        Mono<BigDecimal> weekRevenueMono = calculateRevenueSince(weekStart);
        Mono<BigDecimal> monthRevenueMono = calculateRevenueSince(monthStart);
        Mono<BigDecimal> yearRevenueMono = calculateRevenueSince(yearStart);
        Mono<Long> todayRegistrationsMono = countRegistrationsSince(todayStart);
        Mono<Long> weekRegistrationsMono = countRegistrationsSince(weekStart);
        Mono<Long> monthRegistrationsMono = countRegistrationsSince(monthStart);
        Mono<Long> yearRegistrationsMono = countRegistrationsSince(yearStart);

        Mono<Tuple8<Long, Long, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal>> firstZip =
                Mono.zip(totalUsersMono, totalPaymentsMono, todayPaymentsMono, totalRevenueMono, todayRevenueMono,
                        weekRevenueMono, monthRevenueMono, yearRevenueMono);
        
        Mono<Tuple4<Long, Long, Long, Long>> secondZip =
                Mono.zip(todayRegistrationsMono, weekRegistrationsMono, monthRegistrationsMono, yearRegistrationsMono);

        return Mono.zip(firstZip, secondZip)
                .map(tuple -> buildAdminStatsDTO(tuple.getT1(), tuple.getT2()));
    }

    /**
     * Получить статистику генераций пользователя за период.
     *
     * @param userId идентификатор пользователя
     * @param startDate начало периода (может быть null для всех записей)
     * @param endDate конец периода (может быть null для всех записей)
     * @return статистика генераций пользователя
     * @throws IllegalArgumentException если пользователь не найден
     */
    public Mono<UserStatisticsDTO> getUserStatistics(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return withRetry(userRepository.findById(userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь с ID " + userId + " не найден")))
                .flatMap(user -> {
                    Criteria dateCriteria = buildDateCriteria(userId, startDate, endDate);
                    Flux<ImageGenerationHistory> historyFlux = r2dbcEntityTemplate.select(
                            Query.query(dateCriteria),
                            ImageGenerationHistory.class
                    );

                    return historyFlux
                            .collectList()
                            .flatMap(histories -> buildUserStatisticsDTO(user, histories, startDate, endDate));
                });
    }

    // ==================== Приватные вспомогательные методы ====================

    /**
     * Преобразовать платеж в DTO для админ-панели.
     */
    private Mono<AdminPaymentDTO> mapPaymentToDTO(Payment payment) {
        Mono<User> userMono = withRetry(userRepository.findById(payment.getUserId()))
                .switchIfEmpty(Mono.just(createUnknownUser()));
        
        return userMono.map(user -> AdminPaymentDTO.fromPayment(payment, user));
    }

    /**
     * Преобразовать пользователя в DTO для админ-панели.
     */
    private Mono<AdminUserDTO> mapUserToDTO(User user) {
        Mono<UserPoints> pointsMono = withRetry(userPointsRepository.findByUserId(user.getId()))
                .switchIfEmpty(Mono.just(createEmptyUserPoints(user.getId())));
        
        return pointsMono.map(points -> buildAdminUserDTO(user, points));
    }

    /**
     * Построить запрос для поиска пользователей.
     */
    private Flux<User> buildUserSearchQuery(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return userRepository.findAll().take(limit);
        }
        
        String searchQuery = query.trim().toLowerCase();
        Long userId = tryParseUserId(searchQuery);
        
        if (userId != null) {
            return r2dbcEntityTemplate.select(
                    Query.query(Criteria.where("id").is(userId)),
                    User.class
            );
        }
        
        return userRepository.findAll()
                .filter(user -> matchesSearchQuery(user, searchQuery))
                .take(limit);
    }

    /**
     * Проверить, соответствует ли пользователь поисковому запросу.
     */
    private boolean matchesSearchQuery(User user, String searchQuery) {
        return (user.getUsername() != null && user.getUsername().toLowerCase().contains(searchQuery)) ||
               (user.getEmail() != null && user.getEmail().toLowerCase().contains(searchQuery)) ||
               (user.getTelegramUsername() != null && user.getTelegramUsername().toLowerCase().contains(searchQuery)) ||
               (user.getTelegramFirstName() != null && user.getTelegramFirstName().toLowerCase().contains(searchQuery)) ||
               (user.getTelegramId() != null && user.getTelegramId().toString().contains(searchQuery));
    }

    /**
     * Попытаться распарсить строку как ID пользователя.
     */
    private Long tryParseUserId(String query) {
        try {
            return Long.parseLong(query);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Построить критерии для фильтрации по датам.
     */
    private Criteria buildDateCriteria(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = Criteria.where("user_id").is(userId);
        
        if (startDate != null) {
            criteria = criteria.and("created_at").greaterThanOrEquals(startDate);
        }
        if (endDate != null) {
            criteria = criteria.and("created_at").lessThanOrEquals(endDate);
        }
        
        return criteria;
    }

    /**
     * Построить DTO статистики пользователя.
     */
    private Mono<UserStatisticsDTO> buildUserStatisticsDTO(User user, List<ImageGenerationHistory> histories,
                                                           LocalDateTime startDate, LocalDateTime endDate) {
        long regularCount = calculateRegularModelCount(histories);
        long proCount = calculateProModelCount(histories);
        Map<String, Long> proByResolution = calculateProModelByResolution(histories);
        long totalCount = calculateTotalCount(histories);
        
        PointsStatistics pointsStats = calculatePointsStatistics(histories);
        
        return Mono.just(UserStatisticsDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .startDate(startDate)
                .endDate(endDate)
                .regularModelCount(regularCount)
                .proModelCount(proCount)
                .proModelByResolution(proByResolution)
                .totalCount(totalCount)
                .totalPointsSpent(pointsStats.totalPointsSpent())
                .regularModelPointsSpent(pointsStats.regularModelPointsSpent())
                .proModelPointsSpent(pointsStats.proModelPointsSpent())
                .proModelPointsByResolution(pointsStats.proModelPointsByResolution())
                .build());
    }

    /**
     * Подсчитать количество изображений по обычной модели.
     */
    private long calculateRegularModelCount(List<ImageGenerationHistory> histories) {
        return histories.stream()
                .filter(this::isRegularModel)
                .mapToLong(h -> getNumImages(h))
                .sum();
    }

    /**
     * Подсчитать количество изображений по ПРО модели.
     */
    private long calculateProModelCount(List<ImageGenerationHistory> histories) {
        return histories.stream()
                .filter(this::isProModel)
                .mapToLong(h -> getNumImages(h))
                .sum();
    }

    /**
     * Подсчитать количество изображений по ПРО модели с разбивкой по разрешениям.
     */
    private Map<String, Long> calculateProModelByResolution(List<ImageGenerationHistory> histories) {
        Map<String, Long> proByResolution = initializeResolutionMap();
        
        histories.stream()
                .filter(this::isProModel)
                .forEach(h -> {
                    Integer numImages = getNumImages(h);
                    String resolutionKey = normalizeResolution(h.getResolution());
                    proByResolution.put(resolutionKey, proByResolution.get(resolutionKey) + numImages);
                });
        
        return proByResolution;
    }

    /**
     * Подсчитать общее количество изображений.
     */
    private long calculateTotalCount(List<ImageGenerationHistory> histories) {
        return histories.stream()
                .mapToLong(h -> getNumImages(h))
                .sum();
    }

    /**
     * Подсчитать статистику по поинтам.
     */
    private PointsStatistics calculatePointsStatistics(List<ImageGenerationHistory> histories) {
        long regularModelPointsSpent = 0L;
        long proModelPointsSpent = 0L;
        Map<String, Long> proModelPointsByResolution = initializeResolutionMap();

        for (ImageGenerationHistory h : histories) {
            Integer numImages = getNumImages(h);
            GenerationModelType modelType = h.getGenerationModelType();
            Resolution resolution = h.getResolutionEnum();
            
            Integer pointsForGeneration = generationProperties.getPointsNeeded(
                    modelType != null ? modelType : GenerationModelType.NANO_BANANA,
                    resolution,
                    numImages
            );
            
            if (modelType == GenerationModelType.NANO_BANANA_PRO) {
                proModelPointsSpent += pointsForGeneration;
                String resolutionKey = getResolutionKey(resolution);
                proModelPointsByResolution.put(resolutionKey, 
                        proModelPointsByResolution.get(resolutionKey) + pointsForGeneration);
            } else {
                regularModelPointsSpent += pointsForGeneration;
            }
        }

        long totalPointsSpent = regularModelPointsSpent + proModelPointsSpent;
        
        return new PointsStatistics(
                totalPointsSpent,
                regularModelPointsSpent,
                proModelPointsSpent,
                proModelPointsByResolution
        );
    }

    /**
     * Проверить, является ли модель обычной (не ПРО).
     */
    private boolean isRegularModel(ImageGenerationHistory history) {
        String modelType = history.getModelType();
        return modelType == null || 
               modelType.equals(GenerationModelType.NANO_BANANA.getName()) ||
               !modelType.equals(GenerationModelType.NANO_BANANA_PRO.getName());
    }

    /**
     * Проверить, является ли модель ПРО.
     */
    private boolean isProModel(ImageGenerationHistory history) {
        return GenerationModelType.NANO_BANANA_PRO.getName().equals(history.getModelType());
    }

    /**
     * Получить количество изображений из истории генерации.
     */
    private Integer getNumImages(ImageGenerationHistory history) {
        return history.getNumImages() != null ? history.getNumImages() : DEFAULT_NUM_IMAGES;
    }

    /**
     * Нормализовать разрешение (привести к верхнему регистру и проверить валидность).
     */
    private String normalizeResolution(String resolution) {
        if (resolution == null) {
            return DEFAULT_RESOLUTION;
        }
        
        String normalized = resolution.toUpperCase();
        if (normalized.equals(RESOLUTION_1K) || 
            normalized.equals(RESOLUTION_2K) || 
            normalized.equals(RESOLUTION_4K)) {
            return normalized;
        }
        
        return DEFAULT_RESOLUTION;
    }

    /**
     * Получить ключ разрешения из enum.
     */
    private String getResolutionKey(Resolution resolution) {
        if (resolution == null) {
            return DEFAULT_RESOLUTION;
        }
        
        return switch (resolution) {
            case RESOLUTION_1K -> RESOLUTION_1K;
            case RESOLUTION_2K -> RESOLUTION_2K;
            case RESOLUTION_4K -> RESOLUTION_4K;
            default -> DEFAULT_RESOLUTION;
        };
    }

    /**
     * Инициализировать карту разрешений нулевыми значениями.
     */
    private Map<String, Long> initializeResolutionMap() {
        Map<String, Long> map = new HashMap<>();
        map.put(RESOLUTION_1K, 0L);
        map.put(RESOLUTION_2K, 0L);
        map.put(RESOLUTION_4K, 0L);
        return map;
    }

    /**
     * Построить DTO статистики для админ-панели.
     */
    private AdminStatsDTO buildAdminStatsDTO(
            Tuple8<Long, Long, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal> first,
            Tuple4<Long, Long, Long, Long> second) {
                    return AdminStatsDTO.builder()
                            .totalUsers(first.getT1())
                            .totalPayments(first.getT2())
                            .todayPayments(first.getT3())
                            .totalRevenue(first.getT4())
                            .todayRevenue(first.getT5())
                            .weekRevenue(first.getT6())
                            .monthRevenue(first.getT7())
                            .yearRevenue(first.getT8())
                            .todayRegistrations(second.getT1())
                            .weekRegistrations(second.getT2())
                            .monthRegistrations(second.getT3())
                            .yearRegistrations(second.getT4())
                            .build();
    }

    /**
     * Построить DTO пользователя для админ-панели.
     */
    private AdminUserDTO buildAdminUserDTO(User user, UserPoints points) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .telegramId(user.getTelegramId())
                .telegramUsername(user.getTelegramUsername())
                .telegramFirstName(user.getTelegramFirstName())
                .telegramPhotoUrl(user.getTelegramPhotoUrl())
                .points(points.getPoints())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Создать пустой объект поинтов пользователя.
     */
    private UserPoints createEmptyUserPoints(Long userId) {
        return UserPoints.builder()
                .userId(userId)
                .points(0)
                .build();
    }

    /**
     * Создать объект неизвестного пользователя.
     */
    private User createUnknownUser() {
        return User.builder()
                .username(UNKNOWN_USER)
                .email("")
                .telegramUsername("")
                .telegramFirstName("")
                .telegramPhotoUrl("")
                .build();
    }

    /**
     * Подсчитать количество регистраций с указанной даты.
     */
    private Mono<Long> countRegistrationsSince(LocalDateTime since) {
        return r2dbcEntityTemplate.count(
                Query.query(Criteria.where("created_at").greaterThanOrEquals(since)),
                User.class
        );
    }

    /**
     * Подсчитать количество платежей с указанной даты.
     */
    private Mono<Long> countPaymentsSince(LocalDateTime since) {
        Query query = since == null
                ? Query.query(Criteria.where("status").is(PaymentStatus.PAID.name()))
                : Query.query(Criteria.where("created_at").greaterThanOrEquals(since)
                        .and("status").is(PaymentStatus.PAID.name()));
        
        return r2dbcEntityTemplate.count(query, Payment.class);
    }

    /**
     * Подсчитать общую выручку.
     */
    private Mono<BigDecimal> calculateTotalRevenue() {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }

    /**
     * Подсчитать выручку с указанной даты.
     */
    private Mono<BigDecimal> calculateRevenueSince(LocalDateTime since) {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .filter(payment -> payment.getPaidAt() != null && payment.getPaidAt().isAfter(since))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }

    /**
     * Вспомогательный класс для хранения статистики по поинтам.
     */
    private record PointsStatistics(
            long totalPointsSpent,
            long regularModelPointsSpent,
            long proModelPointsSpent,
            Map<String, Long> proModelPointsByResolution
    ) {}

}