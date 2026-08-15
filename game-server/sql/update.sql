/*
* DB changes since f2f77fe (15.05.2026)
 */

CREATE TABLE IF NOT EXISTS `player_gear_sets` (
  `player_id` int NOT NULL,
  `set_name` varchar(32) NOT NULL,
  `item_unique_id` int NOT NULL,
  `equipment_slot` bigint NOT NULL,
  PRIMARY KEY (`player_id`,`set_name`,`item_unique_id`),
  CONSTRAINT `pgs_player_fk` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `player_stigma_sets` (
  `player_id` int NOT NULL,
  `set_name` varchar(32) NOT NULL,
  `item_unique_id` int NOT NULL,
  `equipment_slot` bigint NOT NULL,
  PRIMARY KEY (`player_id`,`set_name`,`item_unique_id`),
  CONSTRAINT `pss_player_fk` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE FROM inventory WHERE item_id IN (182007170, 188100252, 188100253, 188100254, 188100255, 188100256);

ALTER TABLE `bookmark`
	CHANGE COLUMN `char_id` `player_id` INT NOT NULL FIRST,
	CHANGE COLUMN `name` `name` VARCHAR(27) NOT NULL AFTER `player_id`,
	CHANGE COLUMN `world_id` `world_id` INT NOT NULL AFTER `name`,
	DROP COLUMN `id`,
	DROP PRIMARY KEY,
	ADD PRIMARY KEY (`player_id`, `name`),
	ADD CONSTRAINT `bookmark_ibfk_1` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

DROP TABLE `ingameshop`;
DROP TABLE `ingameshop_log`;
