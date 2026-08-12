-- Apply once before enabling gameserver.webshop.api.enable.
-- order_id is the idempotency key owned by the webshop.
CREATE TABLE `webshop_deliveries` (
  `order_id` varchar(64) NOT NULL,
  `player_id` int NOT NULL,
  `character_name` varchar(16) NOT NULL,
  `item_id` int NOT NULL,
  `quantity` bigint NOT NULL,
  `status` enum('PENDING','PROCESSING','DELIVERED','FAILED') NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delivered_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_webshop_deliveries_player_status` (`player_id`, `status`),
  KEY `idx_webshop_deliveries_status_updated` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
