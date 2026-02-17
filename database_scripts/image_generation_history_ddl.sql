-- Текущий DDL таблицы troyka.image_generation_history (для справки и миграций).
-- INSERT из ImageGenerationHistoryRepository.saveQueueRequest передаёт колонки в том же порядке.

create table troyka.image_generation_history
(
    id               bigserial
        primary key,
    user_id          bigint                                               not null
        constraint fk_image_generation_history_user
            references troyka."user"
            on delete cascade,
    prompt           text                                                 not null,
    created_at       timestamp   default now()                            not null,
    session_id       bigint                                               not null
        constraint fk_image_history_session
            references troyka.sessions
            on delete cascade,
    input_image_urls jsonb,
    image_urls       jsonb,
    style_id         integer     default 1
        constraint fk_image_generation_history_style_id
            references troyka.art_styles,
    aspect_ratio     varchar(10) default '1:1'::character varying         not null,
    fal_request_id   varchar(255),
    queue_status     varchar(50),
    queue_position   integer,
    updated_at       timestamp,
    num_images       integer,
    model_type       varchar(50) default 'nano-banana'::character varying not null,
    resolution       varchar(10),
    deleted          boolean     default false                            not null,
    points_cost      integer,
    cost_usd         numeric(10, 3),
    provider         varchar(50) default 'FAL_AI'::character varying      not null
);

comment on column troyka.image_generation_history.style_id is 'Идентификатор стиля изображения (ссылка на art_styles.id), по умолчанию 1 (Без стиля)';
comment on column troyka.image_generation_history.aspect_ratio is 'Соотношение сторон изображения (21:9, 16:9, 3:2, 4:3, 5:4, 1:1, 4:5, 3:4, 2:3, 9:16). По умолчанию: 1:1';
comment on column troyka.image_generation_history.fal_request_id is 'Идентификатор запроса в очереди Fal.ai';
comment on column troyka.image_generation_history.queue_status is 'Статус запроса в очереди: IN_QUEUE, IN_PROGRESS, COMPLETED, FAILED';
comment on column troyka.image_generation_history.queue_position is 'Позиция запроса в очереди Fal.ai';
comment on column troyka.image_generation_history.updated_at is 'Дата и время последнего обновления статуса запроса';
comment on column troyka.image_generation_history.deleted is 'Флаг удаления записи (soft delete). true означает, что запись помечена как удаленная.';
comment on column troyka.image_generation_history.points_cost is 'стоимость генерации в поинтах';
comment on column troyka.image_generation_history.cost_usd is 'стоимость генерации в долларах';
comment on column troyka.image_generation_history.provider is 'Провайдер генерации изображений: FAL_AI или LAOZHANG_AI';

alter table troyka.image_generation_history owner to postgres;

create index idx_image_history_session_id on troyka.image_generation_history (session_id);
create index idx_image_history_input_urls on troyka.image_generation_history using gin (input_image_urls);
create index idx_image_generation_history_session_created on troyka.image_generation_history (session_id asc, created_at desc);
create index idx_image_generation_history_image_urls_gin on troyka.image_generation_history using gin (image_urls);
create index idx_image_generation_history_fal_request_id on troyka.image_generation_history (fal_request_id);
create index idx_image_generation_history_user_queue_status on troyka.image_generation_history (user_id, queue_status);
create index idx_image_generation_history_session_queue_status on troyka.image_generation_history (session_id, queue_status);
