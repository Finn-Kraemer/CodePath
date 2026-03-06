-- V5__add_announcement_display_mode.sql
ALTER TABLE global_announcements ADD COLUMN display_mode VARCHAR(20) NOT NULL DEFAULT 'HEADER';
