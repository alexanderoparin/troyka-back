package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table(value = "user_avatar", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatar {

    @Id
    private Long userId;

    private String avatarUrl;

    @CreatedDate
    private LocalDateTime createdAt;
}